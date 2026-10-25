package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModCapabilityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Builds conductor components from loaded terminal racks and solves each active circuit once per tick. */
public final class ElectricalGridService {
    private static final Map<Level, Set<BlockPos>> RACKS = new WeakHashMap<>();
    private static final Map<Level, GridEnergyTransaction.Remainders> ENERGY_REMAINDERS = new WeakHashMap<>();
    private static final Map<Level, Map<CableRunKey, CableThermalModel.State>> CABLE_THERMAL_STATE =
            new WeakHashMap<>();
    private static final Map<Level, Set<IElectricalPort>> TRACKED_PORTS = new WeakHashMap<>();

    private ElectricalGridService() {}

    public static void registerRack(TerminalMountBlockEntity rack) {
        Level level = rack.getLevel();
        if (level != null && !level.isClientSide()) {
            RACKS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(rack.getBlockPos().immutable());
        }
    }

    public static void unregisterRack(TerminalMountBlockEntity rack) {
        Level level = rack.getLevel();
        if (level == null) return;
        Set<BlockPos> positions = RACKS.get(level);
        if (positions == null) return;
        positions.remove(rack.getBlockPos());
        if (positions.isEmpty()) {
            RACKS.remove(level);
            ENERGY_REMAINDERS.remove(level);
            CABLE_THERMAL_STATE.remove(level);
            resetTrackedPorts(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) solve(level);
    }

    private static void solve(Level level) {
        Set<BlockPos> registered = RACKS.get(level);
        if (registered == null || registered.isEmpty()) {
            resetTrackedPorts(level);
            ENERGY_REMAINDERS.remove(level);
            CABLE_THERMAL_STATE.remove(level);
            return;
        }

        List<TerminalMountBlockEntity> racks = new ArrayList<>();
        registered.removeIf(pos -> {
            if (!level.isLoaded(pos)) return false;
            if (level.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack) {
                racks.add(rack);
                return false;
            }
            return true;
        });
        racks.sort(Comparator.comparing(TerminalMountBlockEntity::getBlockPos,
                ElectricalGridService::compareBlockPos));

        DisjointSet conductors = new DisjointSet();
        Set<CableConnection> collectedCables = new LinkedHashSet<>();
        for (TerminalMountBlockEntity rack : racks) {
            for (int cell : rack.terminalCells()) {
                conductors.add(new TerminalEndpoint(rack.getBlockPos(), cell));
            }
            collectedCables.addAll(rack.cables());
        }
        List<CableConnection> cables = collectedCables.stream()
                .sorted(ElectricalGridService::compareCables)
                .toList();
        for (CableConnection cable : cables) {
            if (conductors.contains(cable.a()) && conductors.contains(cable.b())) {
                conductors.union(cable.a(), cable.b());
            }
        }

        Map<IElectricalPort, PortAccumulator> groupedPorts = new IdentityHashMap<>();
        for (TerminalMountBlockEntity rack : racks) {
            BlockPos portPos = rack.getBlockPos().relative(rack.facing());
            IElectricalPort port = level.getCapability(ModCapabilityTypes.ELECTRICAL_PORT, portPos,
                    rack.facing().getOpposite());
            if (port == null) continue;
            List<TerminalEndpoint> endpoints = rack.terminalCells().stream()
                    .sorted()
                    .map(cell -> new TerminalEndpoint(rack.getBlockPos(), cell))
                    .toList();
            PortAccumulator accumulator = groupedPorts.computeIfAbsent(port,
                    ignored -> new PortAccumulator(port));
            accumulator.tierValid &= rack.tier() == port.tier();
            accumulator.endpoints.addAll(endpoints);
        }
        List<PortBinding> bindings = groupedPorts.values().stream()
                .map(accumulator -> bind(accumulator, conductors))
                .sorted(ElectricalGridService::compareBindings)
                .toList();
        GridEnergyTransaction.Remainders remainders = ENERGY_REMAINDERS.get(level);
        if (remainders != null) {
            remainders.retainAccounts(bindings.stream()
                    .map(binding -> binding.port().energyAccount())
                    .toList());
        }
        resetPortsNoLongerPresent(level, bindings);

        Map<CircuitKey, List<PortBinding>> circuits = new LinkedHashMap<>();
        List<PortBinding> incomplete = new ArrayList<>();
        for (PortBinding binding : bindings) {
            if (!hasCompleteConductorBinding(binding.port().wiring(), binding.endpoints().size(),
                    binding.roots().size(), binding.tierValid())) {
                incomplete.add(binding);
                continue;
            }
            circuits.computeIfAbsent(new CircuitKey(binding.roots()), ignored -> new ArrayList<>())
                    .add(binding);
        }

        Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits =
                buildFaultUpstreamCircuits(bindings, circuits);
        Set<PortBinding> sourceConflicts = findSourceConflicts(bindings);
        GridTransferLedger transfers = new GridTransferLedger();
        Map<CableRunKey, CableLoad> cableLoads = new LinkedHashMap<>();
        List<List<PortBinding>> circuitList = new ArrayList<>(circuits.values());
        List<List<IElectricalPort>> circuitPorts = circuitList.stream()
                .map(circuit -> circuit.stream().map(PortBinding::port).toList())
                .toList();
        for (int index : circuitSolveOrder(circuitPorts)) {
            solveCircuit(level, circuitList.get(index), cables, conductors,
                    sourceConflicts, transfers, cableLoads, faultUpstreamCircuits);
        }
        updateUnmatchedPorts(incomplete, bindings, sourceConflicts, faultUpstreamCircuits);
        applyCableHeat(level, cableLoads.values());
        Set<CableRunKey> connectedCableRuns = new HashSet<>();
        cables.forEach(cable -> connectedCableRuns.add(cable.runKey()));
        coolInactiveCables(level, connectedCableRuns, cableLoads.keySet());
    }

    static boolean hasCompleteConductorBinding(WiringMode wiring, int endpointCount,
                                               int conductorRootCount, boolean tierValid) {
        int required = wiring.requiredConductors();
        return tierValid && endpointCount == required && conductorRootCount == required;
    }

    private static PortBinding bind(PortAccumulator accumulator, DisjointSet conductors) {
        List<TerminalEndpoint> endpoints = accumulator.endpoints.stream().distinct().sorted().toList();
        Map<TerminalEndpoint, TerminalEndpoint> endpointByRoot = new LinkedHashMap<>();
        for (TerminalEndpoint endpoint : endpoints) {
            endpointByRoot.putIfAbsent(conductors.find(endpoint), endpoint);
        }
        List<TerminalEndpoint> roots = endpointByRoot.keySet().stream().sorted().toList();
        Map<TerminalEndpoint, TerminalEndpoint> sortedEndpointsByRoot = new LinkedHashMap<>();
        roots.forEach(root -> sortedEndpointsByRoot.put(root, endpointByRoot.get(root)));
        return new PortBinding(accumulator.port, endpoints, roots,
                Collections.unmodifiableMap(sortedEndpointsByRoot), accumulator.tierValid);
    }

    /** Maps an in-line device's output source to the exact circuit feeding its input. */
    private static Map<IElectricalPort, List<IElectricalPort>> buildFaultUpstreamCircuits(
            List<PortBinding> bindings, Map<CircuitKey, List<PortBinding>> circuits) {
        Map<IElectricalPort, List<IElectricalPort>> result = new IdentityHashMap<>();
        for (PortBinding output : bindings) {
            if (output.port().electricalRole() != ElectricalPortRole.SOURCE
                    || !output.port().allowsSameTickPassThrough()) {
                continue;
            }
            Object account = output.port().energyAccount();
            for (PortBinding input : bindings) {
                if (input.port().electricalRole() != ElectricalPortRole.LOAD
                        || input.port().energyAccount() != account) {
                    continue;
                }
                List<PortBinding> circuit = circuits.get(new CircuitKey(input.roots()));
                if (circuit != null && circuit.contains(input)) {
                    result.put(output.port(), circuit.stream().map(PortBinding::port).toList());
                    break;
                }
            }
        }
        return result;
    }

    private static void resetPortsNoLongerPresent(Level level, List<PortBinding> bindings) {
        Set<IElectricalPort> current = Collections.newSetFromMap(new WeakHashMap<>());
        bindings.forEach(binding -> current.add(binding.port()));
        Set<IElectricalPort> previous = TRACKED_PORTS.put(level, current);
        if (previous == null) return;
        for (IElectricalPort port : previous) {
            if (!current.contains(port)) {
                port.updateGridState(GridState.offline(port.wiring(), FaultKind.INCOMPLETE_WIRING, false));
            }
        }
    }

    private static void resetTrackedPorts(Level level) {
        Set<IElectricalPort> previous = TRACKED_PORTS.remove(level);
        if (previous == null) return;
        for (IElectricalPort port : previous) {
            port.updateGridState(GridState.offline(port.wiring(), FaultKind.INCOMPLETE_WIRING, false));
        }
    }

    private static void solveCircuit(Level level, List<PortBinding> bindings,
                                     List<CableConnection> allCables, DisjointSet conductors,
                                     Set<PortBinding> sourceConflicts, GridTransferLedger transfers,
                                     Map<CableRunKey, CableLoad> cableLoads,
                                     Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits) {
        List<PortBinding> closedSources = bindings.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                .filter(binding -> binding.port().enabled())
                .toList();
        boolean anyClosedSource = !closedSources.isEmpty();
        GridSourcePolicy.Selection sourceSelection = GridSourcePolicy.select(
                bindings.stream().map(PortBinding::port).toList());

        for (PortBinding binding : bindings) {
            if (!binding.port().enabled()) {
                binding.port().updateGridState(GridState.offline(binding.port().wiring(),
                        FaultKind.OPEN_SWITCH, anyClosedSource));
            }
        }
        List<PortBinding> enabled = bindings.stream().filter(binding -> binding.port().enabled()).toList();
        if (sourceSelection.kind() == GridSourcePolicy.Kind.NONE) {
            enabled.forEach(binding -> binding.port().updateGridState(GridState.offline(
                    binding.port().wiring(), FaultKind.UNDERVOLTAGE, anyClosedSource)));
            return;
        }
        if (sourceSelection.kind() == GridSourcePolicy.Kind.CONFLICT) {
            PortBinding first = closedSources.stream()
                    .filter(binding -> binding.port().availablePowerWatts() > 0L)
                    .findFirst().orElseThrow();
            enabled.forEach(binding -> binding.port().updateGridState(new GridState(true, true,
                    first.port().tier(), first.port().wiring(), 0L, 0.0,
                    FaultKind.MULTIPLE_GRID_SOURCES)));
            return;
        }

        PortBinding source = bindings.stream()
                .filter(binding -> binding.port() == sourceSelection.voltageSource())
                .findFirst().orElseThrow();
        if (sourceSelection.kind() == GridSourcePolicy.Kind.DEDICATED_SOURCE
                && sourceConflicts.contains(source)) {
            enabled.forEach(binding -> binding.port().updateGridState(new GridState(true, true,
                    source.port().tier(), source.port().wiring(), 0L, 0.0,
                    FaultKind.MULTIPLE_GRID_SOURCES)));
            return;
        }
        boolean storageIsland = sourceSelection.kind() == GridSourcePolicy.Kind.STORAGE_ISLAND;
        EnergyTier supplyTier = source.port().tier();
        WiringMode supplyWiring = source.port().wiring();
        List<PortBinding> compatible = new ArrayList<>();
        for (PortBinding binding : enabled) {
            if (binding.port().wiring() != supplyWiring) {
                updateFaultedPort(binding, source, new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.MISSING_PHASE), faultUpstreamCircuits);
            } else if (!binding.port().tier().accepts(supplyTier)) {
                updateFaultedPort(binding, source, new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.OVERVOLTAGE), faultUpstreamCircuits);
            } else if (binding.port().tier() != supplyTier) {
                binding.port().updateGridState(new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.UNDERVOLTAGE));
            } else {
                compatible.add(binding);
            }
        }

        CircuitTopology topology = buildTopology(level, source, allCables, conductors);
        if (!topology.supported()) {
            compatible.forEach(binding -> binding.port().updateGridState(new GridState(true, true,
                    supplyTier, supplyWiring, 0L, 0.0, FaultKind.UNSUPPORTED_TOPOLOGY)));
            recordCableLoads(topology.cables(), Map.of(), cableLoads);
            return;
        }

        List<PortBinding> loadBindings = compatible.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.LOAD)
                .toList();
        List<PowerTarget> loads = uniquePowerTargets(loadBindings, transfers, false);
        long demandWatts = totalTargetWatts(loads);
        long sourceWatts = storageIsland ? 0L : transfers.extractableWatts(source.port());
        List<PortBinding> storageBindings = compatible.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.STORAGE)
                .toList();
        List<PowerTarget> storageSources = uniquePowerTargets(storageBindings, transfers, true);
        List<PowerTarget> storageReceivers = uniquePowerTargets(storageBindings, transfers, false);
        long storageDemandWatts = totalTargetWatts(storageReceivers);
        int nominalVolts = supplyWiring.nominalVolts(supplyTier);

        List<PowerTarget> primarySource = sourceWatts > 0L
                ? List.of(new PowerTarget(source, sourceWatts)) : List.of();
        CircuitPowerPlan plan;
        long requestedCircuitWatts;
        if (storageIsland) {
            requestedCircuitWatts = demandWatts;
            plan = planPower(demandWatts, storageSources,
                    delivered -> allocateFlows(delivered, loads), topology,
                    nominalVolts);
        } else {
            CircuitPowerPlan primaryLoadPlan = planPower(demandWatts, primarySource,
                    delivered -> allocateFlows(delivered, loads), topology,
                    nominalVolts);
            if (primaryLoadPlan.deliveredWatts() == demandWatts) {
                long requestedWithCharge = saturatingAdd(demandWatts, storageDemandWatts);
                requestedCircuitWatts = requestedWithCharge;
                plan = planPower(requestedWithCharge, primarySource,
                        delivered -> prioritizedReceiverFlows(delivered, demandWatts,
                                loads, storageReceivers), topology, nominalVolts);
            } else {
                requestedCircuitWatts = demandWatts;
                List<PowerTarget> allSources = new ArrayList<>(primarySource);
                allSources.addAll(storageSources);
                plan = planPower(demandWatts, allSources,
                        delivered -> allocateFlows(delivered, loads), topology,
                        nominalVolts);
            }
        }

        GridEnergyTransaction transaction = new GridEnergyTransaction(
                ENERGY_REMAINDERS.computeIfAbsent(level,
                        ignored -> new GridEnergyTransaction.Remainders()));
        plan.sourceDraws().forEach(flow -> transaction.extract(flow.binding().port(), flow.watts()));
        plan.receivers().forEach(flow -> transaction.receive(flow.binding().port(), flow.watts()));
        if (!transaction.commit()) {
            compatible.forEach(binding -> binding.port().updateGridState(new GridState(true, true,
                    supplyTier, supplyWiring, 0L, 0.0, FaultKind.UNDERVOLTAGE)));
            recordCableLoads(topology.cables(), Map.of(), cableLoads);
            return;
        }

        plan.sourceDraws().forEach(flow -> transfers.recordExtracted(flow.binding().port(), flow.watts()));
        plan.receivers().forEach(flow -> transfers.recordReceived(flow.binding().port(), flow.watts()));
        updatePortStates(compatible, source, loads, plan, supplyTier, supplyWiring, nominalVolts,
                requestedCircuitWatts, sourceWatts);
        recordCableLoads(topology.cables(), plan.currentAmpsByCable(), cableLoads);
    }

    private static CircuitTopology buildTopology(Level level, PortBinding source,
                                                 List<CableConnection> allCables,
                                                 DisjointSet conductors) {
        Set<TerminalEndpoint> roots = new HashSet<>(source.roots());
        List<CableConnection> cables = allCables.stream()
                .filter(cable -> conductors.contains(cable.a()) && conductors.contains(cable.b()))
                .filter(cable -> {
                    TerminalEndpoint rootA = conductors.find(cable.a());
                    return roots.contains(rootA) && rootA.equals(conductors.find(cable.b()));
                })
                .toList();
        List<RadialCableFlow.Branch<TerminalEndpoint, CableConnection>> branches = new ArrayList<>();
        boolean anchorsValid = true;
        for (CableConnection cable : cables) {
            var a = CableService.anchorOf(level, cable.a());
            var b = CableService.anchorOf(level, cable.b());
            if (a == null || b == null) {
                anchorsValid = false;
                continue;
            }
            double resistance = cable.resistance(CableConnection.electricalLengthBlocks(a, b));
            branches.add(new RadialCableFlow.Branch<>(cable, cable.a(), cable.b(), resistance));
        }
        List<TerminalEndpoint> conductorRoots = source.roots().stream()
                .map(source.endpointsByRoot()::get)
                .toList();
        boolean topologyValid = anchorsValid
                && RadialCableFlow.solve(conductorRoots, branches, Map.of()).supported();
        return new CircuitTopology(cables, branches, conductorRoots, topologyValid);
    }

    private static CircuitPowerPlan planPower(long requestedWatts, List<PowerTarget> sources,
                                              LongFunction<List<PowerFlow>> receiverFactory,
                                              CircuitTopology topology, int nominalVolts) {
        long capacityWatts = totalTargetWatts(sources);
        LongFunction<FlowSnapshot> snapshotAt = delivered -> flowSnapshot(delivered, sources,
                receiverFactory.apply(delivered), topology, nominalVolts);
        long deliveredWatts = deliverablePower(requestedWatts, capacityWatts, delivered -> {
            FlowSnapshot snapshot = snapshotAt.apply(delivered);
            return snapshot.supported() ? snapshot.lossWatts() : Long.MAX_VALUE;
        });
        FlowSnapshot snapshot = snapshotAt.apply(deliveredWatts);
        if (!snapshot.supported()) return CircuitPowerPlan.empty();
        long requiredSourceWatts = saturatingAdd(deliveredWatts, snapshot.lossWatts());
        List<PowerFlow> sourceDraws = allocateFlows(requiredSourceWatts, sources);
        return new CircuitPowerPlan(deliveredWatts, snapshot.lossWatts(), sourceDraws,
                snapshot.sourceInjections(), snapshot.receivers(), snapshot.currentAmpsByCable());
    }

    private static FlowSnapshot flowSnapshot(long deliveredWatts, List<PowerTarget> sources,
                                             List<PowerFlow> receivers, CircuitTopology topology,
                                             int nominalVolts) {
        List<PowerFlow> sourceInjections = allocateFlows(deliveredWatts, sources);
        Map<TerminalEndpoint, Double> nodeCurrent = new HashMap<>();
        for (PowerFlow source : sourceInjections) {
            addPortCurrent(nodeCurrent, source.binding(),
                    ElectricalUnits.currentAmps(source.watts(), nominalVolts));
        }
        for (PowerFlow receiver : receivers) {
            addPortCurrent(nodeCurrent, receiver.binding(),
                    -ElectricalUnits.currentAmps(receiver.watts(), nominalVolts));
        }
        RadialCableFlow.Result<CableConnection> result = RadialCableFlow.solve(
                topology.conductorRoots(), topology.branches(), nodeCurrent);
        if (!result.supported()) return FlowSnapshot.unsupported();
        return new FlowSnapshot(true, roundedLoss(result.lossWatts()), sourceInjections,
                List.copyOf(receivers), result.currentAmpsByEdge());
    }

    private static void addPortCurrent(Map<TerminalEndpoint, Double> nodeCurrent,
                                       PortBinding binding, double currentAmps) {
        for (TerminalEndpoint endpoint : binding.endpointsByRoot().values()) {
            nodeCurrent.merge(endpoint, currentAmps, Double::sum);
        }
    }

    private static List<PowerFlow> prioritizedReceiverFlows(long deliveredWatts, long loadDemandWatts,
                                                            List<PowerTarget> loads,
                                                            List<PowerTarget> storageReceivers) {
        long deliveredToLoads = Math.min(deliveredWatts, loadDemandWatts);
        List<PowerFlow> flows = new ArrayList<>(allocateFlows(deliveredToLoads, loads));
        long chargeWatts = Math.max(0L, deliveredWatts - deliveredToLoads);
        flows.addAll(allocateFlows(chargeWatts, storageReceivers));
        return flows;
    }

    private static List<PowerFlow> allocateFlows(long availableWatts, List<PowerTarget> targets) {
        List<Long> shares = PowerAllocator.proportional(availableWatts,
                targets.stream().map(PowerTarget::watts).toList());
        List<PowerFlow> flows = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            long share = shares.get(index);
            if (share > 0L) flows.add(new PowerFlow(targets.get(index).binding(), share));
        }
        return List.copyOf(flows);
    }

    private static void updatePortStates(List<PortBinding> compatible, PortBinding primarySource,
                                         List<PowerTarget> loadRequests, CircuitPowerPlan plan,
                                         EnergyTier supplyTier, WiringMode wiring, int nominalVolts,
                                         long requestedCircuitWatts, long primarySourceWatts) {
        for (PortBinding binding : compatible) {
            long received = flowWatts(plan.receivers(), binding);
            long injected = flowWatts(plan.sourceInjections(), binding);
            long drawn = flowWatts(plan.sourceDraws(), binding);
            long displayedPower = binding.equals(primarySource) || injected > 0L ? drawn : received;
            long currentPower = injected > 0L ? injected : received;
            double current = ElectricalUnits.currentAmps(currentPower, nominalVolts);
            FaultKind fault = FaultKind.NONE;
            if (binding.equals(primarySource) && signalsOvercurrent(binding.port(),
                    requestedCircuitWatts, primarySourceWatts, plan.deliveredWatts())) {
                fault = FaultKind.OVERCURRENT;
            } else if (binding.port().electricalRole() == ElectricalPortRole.LOAD
                    && received < targetWatts(loadRequests, binding)) {
                fault = FaultKind.UNDERVOLTAGE;
            }
            binding.port().updateGridState(new GridState(true, true, supplyTier, wiring,
                    displayedPower, current, fault));
        }
    }

    /** Distinguishes a saturated protection device from upstream starvation or local storage support. */
    static boolean signalsOvercurrent(IElectricalPort source, long requestedWatts,
                                      long availableWatts, long deliveredWatts) {
        long tripWatts = source.overcurrentTripWatts();
        return tripWatts > 0L
                && requestedWatts > tripWatts
                && availableWatts >= tripWatts
                && deliveredWatts < requestedWatts;
    }

    private static long flowWatts(List<PowerFlow> flows, PortBinding binding) {
        return flows.stream().filter(flow -> flow.binding().equals(binding))
                .mapToLong(PowerFlow::watts).findFirst().orElse(0L);
    }

    private static long targetWatts(List<PowerTarget> targets, PortBinding binding) {
        return targets.stream().filter(target -> target.binding().equals(binding))
                .mapToLong(PowerTarget::watts).findFirst().orElse(0L);
    }

    private static Set<PortBinding> findSourceConflicts(List<PortBinding> bindings) {
        List<PortBinding> energizedSources = bindings.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                .filter(binding -> binding.port().enabled())
                .filter(binding -> binding.port().availablePowerWatts() > 0L)
                .toList();
        Set<PortBinding> conflicts = new HashSet<>();
        for (int first = 0; first < energizedSources.size(); first++) {
            PortBinding a = energizedSources.get(first);
            for (int second = first + 1; second < energizedSources.size(); second++) {
                PortBinding b = energizedSources.get(second);
                if (sharesConductor(a, b)) {
                    conflicts.add(a);
                    conflicts.add(b);
                }
            }
        }
        return conflicts;
    }

    private static List<PowerTarget> uniquePowerTargets(List<PortBinding> bindings,
                                                         GridTransferLedger transfers,
                                                         boolean extracting) {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        List<PowerTarget> targets = new ArrayList<>();
        for (PortBinding binding : bindings) {
            if (seen.put(binding.port().energyAccount(), Boolean.TRUE) != null) continue;
            long watts = extracting
                    ? transfers.extractableWatts(binding.port())
                    : transfers.receivableWatts(binding.port());
            if (watts > 0L) targets.add(new PowerTarget(binding, watts));
        }
        return targets;
    }

    private static void updateUnmatchedPorts(List<PortBinding> incomplete, List<PortBinding> all,
                                             Set<PortBinding> sourceConflicts,
                                             Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits) {
        List<PortBinding> dedicatedSources = all.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                .filter(binding -> binding.port().enabled())
                .filter(binding -> binding.port().availablePowerWatts() > 0L)
                .toList();
        List<PortBinding> storageSources = all.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.STORAGE)
                .filter(binding -> binding.port().enabled())
                .filter(binding -> binding.port().availablePowerWatts() > 0L)
                .toList();
        Set<PortBinding> incompleteSet = new HashSet<>(incomplete);
        for (PortBinding binding : all) {
            if (binding.port().electricalRole() == ElectricalPortRole.SOURCE) {
                if (sourceConflicts.contains(binding)) {
                    binding.port().updateGridState(new GridState(true, true, binding.port().tier(),
                            binding.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                } else if (incompleteSet.contains(binding)) {
                    binding.port().updateGridState(GridState.offline(binding.port().wiring(),
                            FaultKind.INCOMPLETE_WIRING, binding.port().enabled()));
                }
                continue;
            }
            if (!binding.tierValid()) {
                binding.port().updateGridState(GridState.offline(binding.port().wiring(),
                        FaultKind.INCOMPLETE_WIRING, false));
                continue;
            }
            if (!binding.port().enabled()) {
                if (incompleteSet.contains(binding)) {
                    binding.port().updateGridState(GridState.offline(binding.port().wiring(),
                            FaultKind.OPEN_SWITCH, false));
                }
                continue;
            }

            List<PortBinding> overlappingDedicatedSources = dedicatedSources.stream()
                    .filter(candidate -> sharesConductor(candidate, binding))
                    .toList();
            List<PortBinding> overlappingStorageSources = storageSources.stream()
                    .filter(candidate -> sharesConductor(candidate, binding))
                    .toList();
            List<PortBinding> overlappingSources = overlappingDedicatedSources.isEmpty()
                    ? overlappingStorageSources : overlappingDedicatedSources;
            PortBinding exactSource = overlappingSources.stream()
                    .filter(candidate -> candidate.roots().equals(binding.roots()))
                    .findFirst()
                    .orElse(null);
            boolean exactCircuit = exactSource != null && isSolvedExactCircuit(
                    !incompleteSet.contains(binding), !incompleteSet.contains(exactSource));
            if (exactCircuit) continue;
            if (overlappingSources.isEmpty()) {
                if (incompleteSet.contains(binding)) {
                    binding.port().updateGridState(GridState.offline(binding.port().wiring(),
                            FaultKind.INCOMPLETE_WIRING, false));
                }
                continue;
            }
            if (overlappingDedicatedSources.size() > 1) {
                PortBinding first = overlappingSources.get(0);
                binding.port().updateGridState(new GridState(true, true, first.port().tier(),
                        first.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                continue;
            }

            PortBinding source = exactSource != null ? exactSource : overlappingSources.get(0);
            if (sourceConflicts.contains(source)) {
                binding.port().updateGridState(new GridState(true, true, source.port().tier(),
                        source.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                continue;
            }
            FaultKind fault = faultForUnmatchedConnection(binding.port().tier(), source.port().tier());
            updateFaultedPort(binding, source, new GridState(true, true, source.port().tier(),
                    source.port().wiring(), 0L, 0.0, fault), faultUpstreamCircuits);
        }
    }

    static boolean isSolvedExactCircuit(boolean receivingPortComplete, boolean sourcePortComplete) {
        return receivingPortComplete && sourcePortComplete;
    }

    static FaultKind faultForUnmatchedConnection(EnergyTier receivingTier, EnergyTier supplyTier) {
        if (!receivingTier.accepts(supplyTier)) {
            return FaultKind.OVERVOLTAGE;
        }
        return receivingTier != supplyTier ? FaultKind.UNDERVOLTAGE : FaultKind.MISSING_PHASE;
    }

    private static void updateFaultedPort(PortBinding affected, PortBinding source, GridState state,
                                          Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits) {
        affected.port().updateGridState(state);
        if (affected.port() != source.port()
                && affected.port().electricalRole() != ElectricalPortRole.SOURCE) {
            reportDownstreamFault(source.port(), state,
                    port -> activeFaultSource(faultUpstreamCircuits.get(port)));
        }
    }

    static void reportDownstreamFault(IElectricalPort source, GridState state) {
        reportDownstreamFault(source, state, ignored -> null);
    }

    static void reportDownstreamFault(IElectricalPort source, GridState state,
                                      Function<IElectricalPort, IElectricalPort> upstreamSource) {
        Set<IElectricalPort> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IElectricalPort current = source;
        while (current != null && visited.add(current)) {
            if (!current.updateDownstreamFault(state)) {
                return;
            }
            current = upstreamSource.apply(current);
        }
    }

    private static IElectricalPort activeFaultSource(List<IElectricalPort> circuit) {
        if (circuit == null) {
            return null;
        }
        GridSourcePolicy.Selection selection = GridSourcePolicy.select(circuit);
        return selection.kind() == GridSourcePolicy.Kind.DEDICATED_SOURCE
                || selection.kind() == GridSourcePolicy.Kind.STORAGE_ISLAND
                ? selection.voltageSource() : null;
    }

    private static boolean sharesConductor(PortBinding a, PortBinding b) {
        return a.roots().stream().anyMatch(b.roots()::contains);
    }

    /** Stable dependency order that lets an in-line device receive before its source side runs. */
    static List<Integer> circuitSolveOrder(List<? extends List<? extends IElectricalPort>> circuits) {
        Map<Object, AccountCircuits> byAccount = new IdentityHashMap<>();
        for (int index = 0; index < circuits.size(); index++) {
            for (IElectricalPort port : circuits.get(index)) {
                if (!port.allowsSameTickPassThrough()) continue;
                AccountCircuits account = byAccount.computeIfAbsent(port.energyAccount(),
                        ignored -> new AccountCircuits());
                if (port.electricalRole() == ElectricalPortRole.LOAD) {
                    account.inputs.add(index);
                } else if (port.electricalRole() == ElectricalPortRole.SOURCE) {
                    account.outputs.add(index);
                }
            }
        }

        List<Set<Integer>> outgoing = new ArrayList<>(circuits.size());
        int[] incoming = new int[circuits.size()];
        for (int index = 0; index < circuits.size(); index++) {
            outgoing.add(new LinkedHashSet<>());
        }
        for (AccountCircuits account : byAccount.values()) {
            for (int input : account.inputs) {
                for (int output : account.outputs) {
                    if (input != output && outgoing.get(input).add(output)) {
                        incoming[output]++;
                    }
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int index = 0; index < incoming.length; index++) {
            if (incoming[index] == 0) ready.add(index);
        }
        List<Integer> result = new ArrayList<>(circuits.size());
        boolean[] emitted = new boolean[circuits.size()];
        while (!ready.isEmpty()) {
            int index = ready.remove();
            result.add(index);
            emitted[index] = true;
            for (int next : outgoing.get(index)) {
                if (--incoming[next] == 0) ready.add(next);
            }
        }
        // A closed device loop has no valid topological order; keep its prior stable order.
        for (int index = 0; index < circuits.size(); index++) {
            if (!emitted[index]) result.add(index);
        }
        return List.copyOf(result);
    }

    static long deliverablePower(long requestedWatts, long capacityWatts,
                                 LongUnaryOperator lossFunction) {
        long requested = Math.max(0L, requestedWatts);
        long capacity = Math.max(0L, capacityWatts);
        long low = 0L;
        long high = Math.min(requested, capacity);
        while (low < high) {
            long middle = low + (high - low) / 2L;
            if (middle == low) middle++;
            long loss = Math.max(0L, lossFunction.applyAsLong(middle));
            if (loss != Long.MAX_VALUE && loss <= capacity - middle) {
                low = middle;
            } else {
                high = middle - 1L;
            }
        }
        return low;
    }

    static long deliverablePower(long requestedWatts, long capacityWatts, int nominalVolts,
                                 double resistance) {
        return deliverablePower(requestedWatts, capacityWatts,
                delivered -> transmissionLossWatts(delivered, nominalVolts, resistance));
    }

    private static long transmissionLossWatts(long deliveredWatts, int nominalVolts,
                                              double resistance) {
        double currentAmps = ElectricalUnits.currentAmps(deliveredWatts, nominalVolts);
        return roundedLoss(ElectricalUnits.resistiveLossWatts(currentAmps,
                Math.max(0.0, resistance)));
    }

    private static long roundedLoss(double lossWatts) {
        if (!Double.isFinite(lossWatts) || lossWatts >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(0L, Math.round(lossWatts));
    }

    private static void recordCableLoads(List<CableConnection> cables,
                                         Map<CableConnection, Double> currentAmpsByCable,
                                         Map<CableRunKey, CableLoad> cableLoads) {
        for (CableConnection cable : cables) {
            double currentAmps = Math.max(0.0, currentAmpsByCable.getOrDefault(cable, 0.0));
            cableLoads.merge(cable.runKey(), new CableLoad(cable, currentAmps),
                    (first, next) -> new CableLoad(first.cable(),
                            first.currentAmps() + next.currentAmps()));
        }
    }

    private static void applyCableHeat(Level level, Collection<CableLoad> cableLoads) {
        Map<CableRunKey, CableThermalModel.State> thermalStates =
                CABLE_THERMAL_STATE.computeIfAbsent(level, ignored -> new HashMap<>());
        List<CableConnection> failed = new ArrayList<>();
        for (CableLoad load : cableLoads) {
            CableConnection cable = load.cable();
            double currentAmps = load.currentAmps();
            double ratio = currentAmps / Math.max(1.0, cable.effectiveAmpacity());
            CableThermalModel.State state = advanceCableThermalState(thermalStates, cable, ratio);
            if (CableThermalModel.failed(state)) {
                failed.add(cable);
            }
        }
        for (CableConnection cable : failed) {
            CableService.disconnect(level, cable);
            if (cable.ampacity().amps() >= 150) {
                for (BlockPos pos : failureExplosionPositions(cable)) {
                    level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            1.0f, false, Level.ExplosionInteraction.NONE);
                }
            }
        }
    }

    static CableThermalModel.State advanceCableThermalState(
            Map<CableRunKey, CableThermalModel.State> thermalStates,
            CableConnection cable, double currentRatio) {
        CableRunKey runKey = cable.runKey();
        CableThermalModel.State state = CableThermalModel.nextState(
                thermalStates.getOrDefault(runKey, CableThermalModel.State.COLD), currentRatio);
        if (state.cold() || CableThermalModel.failed(state)) {
            thermalStates.remove(runKey);
        } else {
            thermalStates.put(runKey, state);
        }
        return state;
    }

    /** Both physical ends arc on a heavy-cable failure; terminals on one block explode only once. */
    static List<BlockPos> failureExplosionPositions(CableConnection cable) {
        BlockPos first = cable.a().pos();
        BlockPos second = cable.b().pos();
        return first.equals(second) ? List.of(first) : List.of(first, second);
    }

    private static void coolInactiveCables(Level level, Set<CableRunKey> connectedCableRuns,
                                           Set<CableRunKey> activeCableRuns) {
        Map<CableRunKey, CableThermalModel.State> thermalStates = CABLE_THERMAL_STATE.get(level);
        if (thermalStates == null) return;
        thermalStates.keySet().removeIf(run -> !connectedCableRuns.contains(run));
        for (CableRunKey run : new ArrayList<>(thermalStates.keySet())) {
            if (activeCableRuns.contains(run)) continue;
            CableThermalModel.State cooled = CableThermalModel.nextState(
                    thermalStates.get(run), 0.0);
            if (cooled.cold()) thermalStates.remove(run); else thermalStates.put(run, cooled);
        }
        if (thermalStates.isEmpty()) CABLE_THERMAL_STATE.remove(level);
    }

    private static long totalTargetWatts(Collection<PowerTarget> targets) {
        return saturatingSum(targets.stream().map(PowerTarget::watts).toList());
    }

    private static long saturatingSum(Collection<Long> values) {
        long result = 0L;
        for (long value : values) result = saturatingAdd(result, value);
        return result;
    }

    private static long saturatingAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    private static int compareBlockPos(BlockPos a, BlockPos b) {
        int x = Integer.compare(a.getX(), b.getX());
        if (x != 0) return x;
        int y = Integer.compare(a.getY(), b.getY());
        return y != 0 ? y : Integer.compare(a.getZ(), b.getZ());
    }

    private static int compareCables(CableConnection a, CableConnection b) {
        int first = a.a().compareTo(b.a());
        if (first != 0) return first;
        int second = a.b().compareTo(b.b());
        if (second != 0) return second;
        int ampacity = Integer.compare(a.ampacity().ordinal(), b.ampacity().ordinal());
        if (ampacity != 0) return ampacity;
        int insulation = Integer.compare(a.insulation().ordinal(), b.insulation().ordinal());
        return insulation != 0 ? insulation : Integer.compare(a.color().dyeColor(), b.color().dyeColor());
    }

    private static int compareBindings(PortBinding a, PortBinding b) {
        int shared = Math.min(a.endpoints().size(), b.endpoints().size());
        for (int index = 0; index < shared; index++) {
            int endpoint = a.endpoints().get(index).compareTo(b.endpoints().get(index));
            if (endpoint != 0) return endpoint;
        }
        int size = Integer.compare(a.endpoints().size(), b.endpoints().size());
        if (size != 0) return size;
        int role = Integer.compare(a.port().electricalRole().ordinal(), b.port().electricalRole().ordinal());
        if (role != 0) return role;
        int tier = Integer.compare(a.port().tier().ordinal(), b.port().tier().ordinal());
        return tier != 0 ? tier : Integer.compare(a.port().wiring().ordinal(), b.port().wiring().ordinal());
    }

    private record PortBinding(IElectricalPort port, List<TerminalEndpoint> endpoints,
                               List<TerminalEndpoint> roots,
                               Map<TerminalEndpoint, TerminalEndpoint> endpointsByRoot,
                               boolean tierValid) {}

    private record PowerTarget(PortBinding binding, long watts) {}

    private record PowerFlow(PortBinding binding, long watts) {}

    private record CableLoad(CableConnection cable, double currentAmps) {}

    private record CircuitTopology(
            List<CableConnection> cables,
            List<RadialCableFlow.Branch<TerminalEndpoint, CableConnection>> branches,
            List<TerminalEndpoint> conductorRoots,
            boolean supported) {}

    private record FlowSnapshot(boolean supported, long lossWatts,
                                List<PowerFlow> sourceInjections,
                                List<PowerFlow> receivers,
                                Map<CableConnection, Double> currentAmpsByCable) {
        static FlowSnapshot unsupported() {
            return new FlowSnapshot(false, 0L, List.of(), List.of(), Map.of());
        }
    }

    private record CircuitPowerPlan(long deliveredWatts, long lossWatts,
                                    List<PowerFlow> sourceDraws,
                                    List<PowerFlow> sourceInjections,
                                    List<PowerFlow> receivers,
                                    Map<CableConnection, Double> currentAmpsByCable) {
        static CircuitPowerPlan empty() {
            return new CircuitPowerPlan(0L, 0L, List.of(), List.of(), List.of(), Map.of());
        }
    }

    private static final class PortAccumulator {
        private final IElectricalPort port;
        private final List<TerminalEndpoint> endpoints = new ArrayList<>();
        private boolean tierValid = true;

        private PortAccumulator(IElectricalPort port) {
            this.port = port;
        }
    }

    private static final class AccountCircuits {
        private final Set<Integer> inputs = new LinkedHashSet<>();
        private final Set<Integer> outputs = new LinkedHashSet<>();
    }

    private record CircuitKey(List<TerminalEndpoint> roots) {
        CircuitKey {
            roots = roots.stream().sorted().toList();
        }
    }

    private static final class DisjointSet {
        private final Map<TerminalEndpoint, TerminalEndpoint> parent = new HashMap<>();

        void add(TerminalEndpoint endpoint) {
            parent.putIfAbsent(endpoint, endpoint);
        }

        boolean contains(TerminalEndpoint endpoint) {
            return parent.containsKey(endpoint);
        }

        TerminalEndpoint find(TerminalEndpoint endpoint) {
            TerminalEndpoint current = parent.get(endpoint);
            if (current == null) return endpoint;
            if (!current.equals(endpoint)) {
                current = find(current);
                parent.put(endpoint, current);
            }
            return current;
        }

        void union(TerminalEndpoint a, TerminalEndpoint b) {
            TerminalEndpoint rootA = find(a);
            TerminalEndpoint rootB = find(b);
            if (rootA.equals(rootB)) return;
            if (rootA.compareTo(rootB) <= 0) parent.put(rootB, rootA); else parent.put(rootA, rootB);
        }
    }
}
