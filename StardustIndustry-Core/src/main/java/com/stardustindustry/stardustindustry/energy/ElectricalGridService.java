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
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModCapabilityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Builds conductor components from loaded terminal racks and solves each active circuit once per tick. */
public final class ElectricalGridService {
    private static final int SOURCE_FLOW_SETTLE_ITERATIONS = 16;
    private static final Map<Level, Set<BlockPos>> RACKS = new WeakHashMap<>();
    private static final Map<Level, GridEnergyTransaction.Remainders> ENERGY_REMAINDERS = new WeakHashMap<>();
    private static final Map<Level, Set<IElectricalPort>> TRACKED_PORTS = new WeakHashMap<>();
    private static final Map<Level, Map<CableRunId, EnergizedCable>> ENERGIZED_CABLES = new WeakHashMap<>();

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
            ENERGIZED_CABLES.remove(level);
            resetTrackedPorts(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) {
            solve(level);
            if (level instanceof ServerLevel serverLevel) {
                CableShockService.tick(serverLevel, energizedCables(level));
            }
        }
    }

    /** Immutable live-voltage snapshot from the most recent solve. */
    public static Collection<EnergizedCable> energizedCables(Level level) {
        Map<CableRunId, EnergizedCable> cables = ENERGIZED_CABLES.get(level);
        return cables == null ? List.of() : cables.values();
    }

    /** Live measurements for one physical run, or {@code null} when it is not energized. */
    public static EnergizedCable energizedCable(Level level, CableRunId runId) {
        Map<CableRunId, EnergizedCable> cables = ENERGIZED_CABLES.get(level);
        return cables == null || runId == null ? null : cables.get(runId);
    }

    private static void solve(Level level) {
        ServerLevel serverLevel = (ServerLevel) level;
        Set<BlockPos> registered = RACKS.get(level);
        if (registered == null || registered.isEmpty()) {
            resetTrackedPorts(level);
            ENERGY_REMAINDERS.remove(level);
            ENERGIZED_CABLES.remove(level);
            return;
        }

        Map<CableRunId, EnergizedCable> energizedCables = new LinkedHashMap<>();

        List<TerminalMountBlockEntity> racks = new ArrayList<>();
        registered.removeIf(pos -> {
            if (!ElectricalChunkAvailability.isActiveNow(serverLevel, pos)) return false;
            var chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack) {
                racks.add(rack);
                return false;
            }
            return true;
        });
        racks.sort(Comparator.comparing(TerminalMountBlockEntity::getBlockPos,
                ElectricalGridService::compareBlockPos));

        DisjointSet conductors = new DisjointSet();
        Set<TerminalEndpoint> loadedEndpoints = new HashSet<>();
        Map<CableRunId, CableConnection> collectedCables = new LinkedHashMap<>();
        ElectricalNetworkSavedData networkData = ElectricalNetworkSavedData.get(serverLevel);
        for (TerminalMountBlockEntity rack : racks) {
            Map<CableRunId, CableConnection> rackCables = new LinkedHashMap<>();
            for (int cell : rack.terminalCells()) {
                TerminalEndpoint endpoint = new TerminalEndpoint(rack.getBlockPos(), cell);
                conductors.add(endpoint);
                loadedEndpoints.add(endpoint);
                // Block entities cache cables for rendering only; the SavedData record is the
                // sole electrical authority, including during an offline recolour or reload.
                for (CableConnection cable : networkData.cablesAt(endpoint)) {
                    collectedCables.put(cable.runId(), cable);
                    rackCables.put(cable.runId(), cable);
                }
            }
            // A chunk can resume ticking without reconstructing its block entities, so onLoad
            // alone cannot make offline mutations visible in the rendering cache.
            rack.replaceCableCacheFromNetwork(rackCables.values());
        }
        List<CableConnection> cables = collectedCables.values().stream()
                .sorted(ElectricalGridService::compareCables)
                .toList();
        for (CableConnection cable : cables) {
            if (conductors.contains(cable.a()) && conductors.contains(cable.b())) {
                conductors.union(cable.a(), cable.b());
            }
        }
        Set<TerminalEndpoint> unloadedConductorRoots = loadedEndpointsWithUnloadedPeers(
                cables, loadedEndpoints,
                pos -> ElectricalChunkAvailability.isActiveNow(serverLevel, pos)).stream()
                .map(conductors::find)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Map<Object, PortAccumulator> groupedPorts = new LinkedHashMap<>();
        for (TerminalMountBlockEntity rack : racks) {
            BlockPos portPos = rack.getBlockPos().relative(rack.facing());
            if (!ElectricalChunkAvailability.isActiveNow(serverLevel, portPos)) {
                continue;
            }
            IElectricalPort port = level.getCapability(ModCapabilityTypes.ELECTRICAL_PORT, portPos,
                    rack.facing().getOpposite());
            if (port == null) continue;
            List<TerminalEndpoint> endpoints = rack.terminalCells().stream()
                    .sorted()
                    .map(cell -> new TerminalEndpoint(rack.getBlockPos(), cell))
                    .toList();
            Object groupKey = Objects.requireNonNull(port.portGroup(), "portGroup");
            PortAccumulator accumulator = groupedPorts.computeIfAbsent(groupKey,
                    ignored -> new PortAccumulator(port));
            accumulator.add(port, rack.tier(), endpoints);
        }
        List<PortBinding> bindings = groupedPorts.values().stream()
                .map(accumulator -> bind(accumulator, conductors, unloadedConductorRoots))
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
        Map<CableRunId, CableLoad> cableLoads = new LinkedHashMap<>();
        List<List<PortBinding>> circuitList = new ArrayList<>(circuits.values());
        List<List<IElectricalPort>> circuitPorts = circuitList.stream()
                .map(circuit -> circuit.stream().map(PortBinding::port).toList())
                .toList();
        List<Integer> solveOrder = circuitSolveOrder(circuitPorts);
        preparePassThroughDemands(level, circuitList, solveOrder, cables, conductors);
        for (int index : solveOrder) {
            solveCircuit(level, circuitList.get(index), cables, conductors,
                    sourceConflicts, transfers, cableLoads, faultUpstreamCircuits,
                    energizedCables);
        }
        updateUnmatchedPorts(incomplete, bindings, sourceConflicts, faultUpstreamCircuits);
        applyCableHeat(level, cableLoads.values());
        Set<CableRunId> connectedCableRuns = new HashSet<>();
        cables.stream()
                .filter(cable -> bothEndpointsPresent(cable, loadedEndpoints))
                .forEach(cable -> connectedCableRuns.add(cable.runId()));
        Set<CableRunId> activeCableRuns = new HashSet<>();
        cableLoads.values().forEach(load -> activeCableRuns.add(load.cable().runId()));
        coolInactiveCables(level, connectedCableRuns, activeCableRuns);
        ENERGIZED_CABLES.put(level,
                Collections.unmodifiableMap(new LinkedHashMap<>(energizedCables)));
    }

    /**
     * Propagates receiver demand from downstream circuits to in-line equipment before any
     * transaction commits. Reverse dependency order gives a transformer or protection input the
     * fully settled demand of every downstream pass-through stage in the same game tick.
     */
    private static void preparePassThroughDemands(Level level,
                                                  List<List<PortBinding>> circuits,
                                                  List<Integer> solveOrder,
                                                  List<CableConnection> allCables,
                                                  DisjointSet conductors) {
        for (List<PortBinding> circuit : circuits) {
            circuit.stream()
                    .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                    .filter(binding -> binding.port().allowsSameTickPassThrough())
                    .forEach(binding -> binding.port().preparePassThroughDemandWatts(0L));
        }

        for (int orderIndex = solveOrder.size() - 1; orderIndex >= 0; orderIndex--) {
            List<PortBinding> circuit = circuits.get(solveOrder.get(orderIndex));
            List<PortBinding> passThroughSources = circuit.stream()
                    .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                    .filter(binding -> binding.port().allowsSameTickPassThrough())
                    .filter(binding -> binding.port().enabled())
                    .toList();
            long dedicatedSourceCount = circuit.stream()
                    .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                    .filter(binding -> binding.port().enabled())
                    .count();
            if (passThroughSources.size() != 1 || dedicatedSourceCount != 1L) continue;

            PortBinding source = passThroughSources.getFirst();
            List<PortBinding> compatible = circuit.stream()
                    .filter(binding -> binding.port().enabled())
                    .filter(binding -> binding.port().wiring() == source.port().wiring())
                    .filter(binding -> binding.port().tier() == source.port().tier())
                    .toList();
            List<PowerTarget> loads = requestedPowerTargets(compatible,
                    ElectricalPortRole.LOAD);
            List<PowerTarget> storageReceivers = requestedPowerTargets(compatible,
                    ElectricalPortRole.STORAGE);
            long loadDemandWatts = totalTargetWatts(loads);
            long requestedWatts = saturatingAdd(loadDemandWatts,
                    totalTargetWatts(storageReceivers));
            if (requestedWatts <= 0L) continue;

            CircuitTopology topology = buildTopology(level, source, allCables, conductors);
            if (!topology.supported()) continue;
            CircuitPowerPlan plan = planPower(requestedWatts,
                    List.of(new PowerTarget(source, source.port().maxPowerWatts())),
                    delivered -> prioritizedReceiverFlows(delivered, loadDemandWatts,
                            loads, storageReceivers),
                    false, topology, source.port().wiring(), source.port().tier());
            source.port().preparePassThroughDemandWatts(
                    flowWatts(plan.sourceDraws(), source));
        }
    }

    private static List<PowerTarget> requestedPowerTargets(List<PortBinding> bindings,
                                                            ElectricalPortRole role) {
        Set<Object> seen = new HashSet<>();
        List<PowerTarget> targets = new ArrayList<>();
        for (PortBinding binding : bindings) {
            if (binding.port().electricalRole() != role
                    || !seen.add(binding.port().energyAccount())) {
                continue;
            }
            long watts = Math.min(Math.max(0L, binding.port().requestedPowerWatts()),
                    Math.max(0L, binding.port().maxPowerWatts()));
            if (watts > 0L) targets.add(new PowerTarget(binding, watts));
        }
        return List.copyOf(targets);
    }

    static boolean hasCompleteConductorBinding(WiringMode wiring, int endpointCount,
                                               int conductorRootCount, boolean tierValid) {
        int required = wiring.requiredConductors();
        return tierValid && endpointCount == required && conductorRootCount == required;
    }

    private static PortBinding bind(PortAccumulator accumulator, DisjointSet conductors,
                                    Set<TerminalEndpoint> unloadedConductorRoots) {
        List<TerminalEndpoint> endpoints = accumulator.endpoints.stream().distinct().sorted().toList();
        Map<TerminalEndpoint, TerminalEndpoint> endpointByRoot = new LinkedHashMap<>();
        for (TerminalEndpoint endpoint : endpoints) {
            endpointByRoot.putIfAbsent(conductors.find(endpoint), endpoint);
        }
        List<TerminalEndpoint> roots = endpointByRoot.keySet().stream().sorted().toList();
        Map<TerminalEndpoint, TerminalEndpoint> sortedEndpointsByRoot = new LinkedHashMap<>();
        roots.forEach(root -> sortedEndpointsByRoot.put(root, endpointByRoot.get(root)));
        boolean unloadedConductor = roots.stream().anyMatch(unloadedConductorRoots::contains);
        return new PortBinding(accumulator.port, endpoints, roots,
                Collections.unmodifiableMap(sortedEndpointsByRoot), accumulator.tierValid,
                List.copyOf(accumulator.members), unloadedConductor);
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
                        || !Objects.equals(input.port().energyAccount(), account)) {
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
        bindings.forEach(binding -> current.addAll(binding.members()));
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
                                     Map<CableRunId, CableLoad> cableLoads,
                                     Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits,
                                     Map<CableRunId, EnergizedCable> energizedCables) {
        List<PortBinding> closedSources = bindings.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                .filter(binding -> binding.port().enabled())
                .toList();
        boolean anyClosedSource = !closedSources.isEmpty();
        GridSourcePolicy.Selection sourceSelection = GridSourcePolicy.select(
                bindings.stream().map(PortBinding::port).toList());

        for (PortBinding binding : bindings) {
            if (!binding.port().enabled()) {
                updateGridState(binding, GridState.offline(binding.port().wiring(),
                        FaultKind.OPEN_SWITCH, anyClosedSource));
            }
        }
        List<PortBinding> enabled = bindings.stream().filter(binding -> binding.port().enabled()).toList();
        if (sourceSelection.kind() == GridSourcePolicy.Kind.NONE) {
            enabled.forEach(binding -> updateGridState(binding, GridState.offline(
                    binding.port().wiring(), FaultKind.UNDERVOLTAGE, anyClosedSource)));
            return;
        }
        if (sourceSelection.kind() == GridSourcePolicy.Kind.CONFLICT) {
            PortBinding first = closedSources.getFirst();
            CircuitTopology topology = buildTopology(level, first, allCables, conductors);
            int highestVolts = closedSources.stream()
                    .mapToInt(binding -> binding.port().wiring().nominalVolts(binding.port().tier()))
                    .max().orElse(0);
            recordEnergizedCables(topology, highestVolts, Map.of(), energizedCables);
            enabled.forEach(binding -> updateGridState(binding, new GridState(true, true,
                    first.port().tier(), first.port().wiring(), 0L, 0.0,
                    FaultKind.MULTIPLE_GRID_SOURCES)));
            return;
        }

        PortBinding source = bindings.stream()
                .filter(binding -> binding.port() == sourceSelection.voltageSource())
                .findFirst().orElseThrow();
        boolean storageIsland = sourceSelection.kind() == GridSourcePolicy.Kind.STORAGE_ISLAND;
        EnergyTier supplyTier = source.port().tier();
        WiringMode supplyWiring = source.port().wiring();
        int nominalVolts = supplyWiring.nominalVolts(supplyTier);
        CircuitTopology topology = buildTopology(level, source, allCables, conductors);
        recordEnergizedCables(topology, nominalVolts, Map.of(), energizedCables);
        if (sourceSelection.kind() == GridSourcePolicy.Kind.DEDICATED_SOURCE
                && sourceConflicts.contains(source)) {
            enabled.forEach(binding -> updateGridState(binding, new GridState(true, true,
                    source.port().tier(), source.port().wiring(), 0L, 0.0,
                    FaultKind.MULTIPLE_GRID_SOURCES)));
            return;
        }
        List<PortBinding> compatible = new ArrayList<>();
        for (PortBinding binding : enabled) {
            if (binding.port().wiring() != supplyWiring) {
                updateFaultedPort(binding, source, new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.MISSING_PHASE), faultUpstreamCircuits);
            } else if (!binding.port().tier().accepts(supplyTier)) {
                updateFaultedPort(binding, source, new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.OVERVOLTAGE), faultUpstreamCircuits);
            } else if (binding.port().tier() != supplyTier) {
                updateGridState(binding, new GridState(true, true, supplyTier, supplyWiring,
                        0L, 0.0, FaultKind.UNDERVOLTAGE));
            } else {
                compatible.add(binding);
            }
        }
        if (!topology.supported()) {
            compatible.forEach(binding -> updateGridState(binding, new GridState(true, true,
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
        List<PowerTarget> primarySource = sourceWatts > 0L
                ? List.of(new PowerTarget(source, sourceWatts)) : List.of();
        CircuitPowerPlan plan;
        long requestedCircuitWatts;
        if (storageIsland) {
            requestedCircuitWatts = demandWatts;
            plan = planPower(demandWatts, storageSources,
                    delivered -> allocateFlows(delivered, loads), false, topology,
                    supplyWiring, supplyTier);
        } else {
            CircuitPowerPlan primaryLoadPlan = planPower(demandWatts, primarySource,
                    delivered -> allocateFlows(delivered, loads), false, topology,
                    supplyWiring, supplyTier);
            if (primaryLoadPlan.deliveredWatts() == demandWatts) {
                long requestedWithCharge = saturatingAdd(demandWatts, storageDemandWatts);
                requestedCircuitWatts = requestedWithCharge;
                plan = planPower(requestedWithCharge, primarySource,
                        delivered -> prioritizedReceiverFlows(delivered, demandWatts,
                                loads, storageReceivers), false, topology, supplyWiring, supplyTier);
            } else {
                requestedCircuitWatts = demandWatts;
                List<PowerTarget> allSources = new ArrayList<>(primarySource);
                allSources.addAll(storageSources);
                plan = planPower(demandWatts, allSources,
                        delivered -> allocateFlows(delivered, loads), true, topology,
                        supplyWiring, supplyTier);
            }
        }

        GridEnergyTransaction transaction = new GridEnergyTransaction(
                ENERGY_REMAINDERS.computeIfAbsent(level,
                        ignored -> new GridEnergyTransaction.Remainders(
                                ElectricalNetworkSavedData.get((ServerLevel) level))));
        plan.sourceDraws().forEach(flow -> transaction.extract(flow.binding().port(), flow.watts()));
        plan.receivers().forEach(flow -> transaction.receive(flow.binding().port(), flow.watts()));
        if (!transaction.commit()) {
            compatible.forEach(binding -> updateGridState(binding, new GridState(true, true,
                    supplyTier, supplyWiring, 0L, 0.0, FaultKind.UNDERVOLTAGE)));
            recordCableLoads(topology.cables(), Map.of(), cableLoads);
            return;
        }

        plan.sourceDraws().forEach(flow -> transfers.recordExtracted(flow.binding().port(), flow.watts()));
        plan.receivers().forEach(flow -> transfers.recordReceived(flow.binding().port(), flow.watts()));
        updatePortStates(compatible, source, loads, plan, supplyTier, supplyWiring,
                requestedCircuitWatts);
        recordCableLoads(topology.cables(), plan.currentAmpsByCable(), cableLoads);
        recordEnergizedCables(topology, nominalVolts, plan.currentAmpsByCable(),
                energizedCables);
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
                                              boolean primarySourceFirst,
                                              CircuitTopology topology, WiringMode wiring,
                                              EnergyTier tier) {
        long capacityWatts = totalTargetWatts(sources);
        LongFunction<FlowSnapshot> snapshotAt = delivered -> flowSnapshot(delivered, sources,
                receiverFactory.apply(delivered), topology, primarySourceFirst, wiring, tier);
        long deliveredWatts = deliverablePower(requestedWatts, capacityWatts, delivered -> {
            FlowSnapshot snapshot = snapshotAt.apply(delivered);
            return snapshot.supported() ? snapshot.lossWatts() : Long.MAX_VALUE;
        });
        FlowSnapshot snapshot = snapshotAt.apply(deliveredWatts);
        if (!snapshot.supported()) return CircuitPowerPlan.empty();
        return new CircuitPowerPlan(deliveredWatts, snapshot.lossWatts(), snapshot.sourceDraws(),
                snapshot.sourceInjections(), snapshot.receivers(), snapshot.currentAmpsByCable());
    }

    private static FlowSnapshot flowSnapshot(long deliveredWatts, List<PowerTarget> sources,
                                             List<PowerFlow> receivers, CircuitTopology topology,
                                             boolean primarySourceFirst, WiringMode wiring,
                                             EnergyTier tier) {
        List<PowerFlow> sourceDraws = allocateSourceFlows(
                deliveredWatts, sources, primarySourceFirst);
        FlowSnapshot snapshot = FlowSnapshot.unsupported();
        for (int iteration = 0; iteration < SOURCE_FLOW_SETTLE_ITERATIONS; iteration++) {
            List<PowerFlow> sourceInjections = topologySourceFlows(deliveredWatts, sourceDraws);
            snapshot = solveFlowSnapshot(sourceDraws, sourceInjections, receivers,
                    topology, wiring, tier);
            if (!snapshot.supported()) return snapshot;

            long requiredSourceWatts = saturatingAdd(deliveredWatts, snapshot.lossWatts());
            List<PowerFlow> nextSourceDraws = allocateSourceFlows(
                    requiredSourceWatts, sources, primarySourceFirst);
            if (samePowerFlows(sourceDraws, nextSourceDraws)) {
                return snapshot;
            }
            sourceDraws = nextSourceDraws;
        }

        List<PowerFlow> sourceInjections = topologySourceFlows(deliveredWatts, sourceDraws);
        return solveFlowSnapshot(sourceDraws, sourceInjections, receivers,
                topology, wiring, tier);
    }

    private static FlowSnapshot solveFlowSnapshot(List<PowerFlow> sourceDraws,
                                                  List<PowerFlow> sourceInjections,
                                                  List<PowerFlow> receivers,
                                                  CircuitTopology topology,
                                                  WiringMode wiring, EnergyTier tier) {
        Map<TerminalEndpoint, Double> nodeCurrent = new HashMap<>();
        for (PowerFlow source : sourceInjections) {
            addPortCurrent(nodeCurrent, source.binding(), wiring, tier, source.watts(), 1.0);
        }
        for (PowerFlow receiver : receivers) {
            addPortCurrent(nodeCurrent, receiver.binding(), wiring, tier, receiver.watts(), -1.0);
        }
        RadialCableFlow.Result<CableConnection> result = RadialCableFlow.solve(
                topology.conductorRoots(), topology.branches(), nodeCurrent);
        if (!result.supported()) return FlowSnapshot.unsupported();
        return new FlowSnapshot(true, roundedLoss(result.lossWatts()), sourceDraws,
                sourceInjections, List.copyOf(receivers), result.currentAmpsByEdge());
    }

    /**
     * Converts actual source draw shares (which include line loss) into balanced topology
     * injections whose sum is the useful power delivered to receivers.
     */
    static List<Long> topologyInjectionShares(long deliveredWatts, List<Long> sourceDrawWatts) {
        return PowerAllocator.proportional(Math.max(0L, deliveredWatts), sourceDrawWatts);
    }

    private static List<PowerFlow> topologySourceFlows(long deliveredWatts,
                                                       List<PowerFlow> sourceDraws) {
        List<Long> shares = topologyInjectionShares(deliveredWatts,
                sourceDraws.stream().map(PowerFlow::watts).toList());
        List<PowerFlow> injections = new ArrayList<>();
        for (int index = 0; index < sourceDraws.size(); index++) {
            long share = shares.get(index);
            if (share > 0L) {
                injections.add(new PowerFlow(sourceDraws.get(index).binding(), share));
            }
        }
        return List.copyOf(injections);
    }

    private static boolean samePowerFlows(List<PowerFlow> first, List<PowerFlow> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            PowerFlow a = first.get(index);
            PowerFlow b = second.get(index);
            if (!a.binding().equals(b.binding()) || a.watts() != b.watts()) return false;
        }
        return true;
    }

    private static void addPortCurrent(Map<TerminalEndpoint, Double> nodeCurrent,
                                       PortBinding binding, WiringMode wiring, EnergyTier tier,
                                       long powerWatts, double direction) {
        List<TerminalEndpoint> conductors = new ArrayList<>(binding.endpointsByRoot().values());
        if (conductors.size() != wiring.requiredConductors()) {
            throw new IllegalArgumentException("binding conductor count does not match " + wiring);
        }
        for (int index = 0; index < conductors.size(); index++) {
            double current = wiring.conductorCurrentAmps(powerWatts, tier, index) * direction;
            nodeCurrent.merge(conductors.get(index), current, Double::sum);
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
        return allocateFlows(targets,
                PowerAllocator.proportional(availableWatts,
                        targets.stream().map(PowerTarget::watts).toList()));
    }

    private static List<PowerFlow> allocateSourceFlows(long availableWatts,
                                                       List<PowerTarget> targets,
                                                       boolean primarySourceFirst) {
        List<Long> requests = targets.stream().map(PowerTarget::watts).toList();
        List<Long> shares = primarySourceFirst
                ? PowerAllocator.primaryThenProportional(availableWatts, requests)
                : PowerAllocator.proportional(availableWatts, requests);
        return allocateFlows(targets, shares);
    }

    private static List<PowerFlow> allocateFlows(List<PowerTarget> targets, List<Long> shares) {
        List<PowerFlow> flows = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            long share = shares.get(index);
            if (share > 0L) flows.add(new PowerFlow(targets.get(index).binding(), share));
        }
        return List.copyOf(flows);
    }

    private static void updatePortStates(List<PortBinding> compatible, PortBinding primarySource,
                                         List<PowerTarget> loadRequests, CircuitPowerPlan plan,
                                         EnergyTier supplyTier, WiringMode wiring,
                                         long requestedCircuitWatts) {
        long totalDrawWatts = saturatingSum(
                plan.sourceDraws().stream().map(PowerFlow::watts).toList());
        double efficiency = totalDrawWatts <= 0L ? 0.0
                : Math.clamp(plan.deliveredWatts() / (double) totalDrawWatts, 0.0, 1.0);
        for (PortBinding binding : compatible) {
            long received = flowWatts(plan.receivers(), binding);
            long injected = flowWatts(plan.sourceInjections(), binding);
            long drawn = flowWatts(plan.sourceDraws(), binding);
            long displayedPower = binding.equals(primarySource) || injected > 0L ? drawn : received;
            long currentPower = injected > 0L ? injected : received;
            double current = wiring.lineCurrentAmps(currentPower, supplyTier);
            FaultKind fault = FaultKind.NONE;
            if (isMeasuredOvercurrent(binding.port(), current, wiring, supplyTier)) {
                fault = FaultKind.OVERCURRENT;
            } else if (binding.port().electricalRole() == ElectricalPortRole.LOAD
                    && received < targetWatts(loadRequests, binding)) {
                fault = FaultKind.UNDERVOLTAGE;
            }
            long requested = binding.equals(primarySource)
                    ? requestedCircuitWatts
                    : binding.port().electricalRole() == ElectricalPortRole.STORAGE
                            ? binding.port().requestedPowerWatts()
                            : targetWatts(loadRequests, binding);
            long netPower = received - drawn;
            updateGridState(binding, new GridState(true, true, supplyTier, wiring,
                    displayedPower, current, fault, requested, netPower,
                    plan.lossWatts(), efficiency));
        }
    }

    /** Uses settled port current, so unmet demand and remote storage cannot cause a false trip. */
    static boolean isMeasuredOvercurrent(IElectricalPort port, double currentAmps,
                                         WiringMode wiring, EnergyTier tier) {
        long tripWatts = port.overcurrentTripWatts();
        if (tripWatts <= 0L || !Double.isFinite(currentAmps)) {
            return false;
        }
        double tripCurrentAmps = wiring.lineCurrentAmps(tripWatts, tier);
        return currentAmps > tripCurrentAmps;
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
        Set<Object> seen = new HashSet<>();
        List<PowerTarget> targets = new ArrayList<>();
        for (PortBinding binding : bindings) {
            if (!seen.add(binding.port().energyAccount())) continue;
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
        List<PortBinding> connectedDedicatedSources = all.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.SOURCE)
                .toList();
        List<PortBinding> connectedStorageSources = all.stream()
                .filter(binding -> binding.port().electricalRole() == ElectricalPortRole.STORAGE)
                .toList();
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
                    updateGridState(binding, new GridState(true, true, binding.port().tier(),
                            binding.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                } else if (incompleteSet.contains(binding)) {
                    updateGridState(binding, GridState.offline(binding.port().wiring(),
                            FaultKind.INCOMPLETE_WIRING, binding.port().enabled()));
                }
                continue;
            }
            if (!binding.tierValid()) {
                updateGridState(binding, GridState.offline(binding.port().wiring(),
                        FaultKind.INCOMPLETE_WIRING, false));
                continue;
            }
            if (!binding.port().enabled()) {
                if (incompleteSet.contains(binding)) {
                    updateGridState(binding, GridState.offline(binding.port().wiring(),
                            FaultKind.OPEN_SWITCH, false));
                }
                continue;
            }

            PortBinding exactConnectedSource = connectedDedicatedSources.stream()
                    .filter(candidate -> candidate.roots().equals(binding.roots()))
                    .findFirst()
                    .or(() -> connectedStorageSources.stream()
                            .filter(candidate -> candidate.roots().equals(binding.roots()))
                            .findFirst())
                    .orElse(null);
            if (exactConnectedSource != null && isSolvedExactCircuit(
                    !incompleteSet.contains(binding), !incompleteSet.contains(exactConnectedSource))) {
                // solveCircuit already assigned this exact circuit's state. An
                // in-line source may have passed its whole buffer downstream in
                // that transaction, so post-transaction availability must not
                // overwrite the settled state with a false undervoltage.
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
            if (overlappingSources.isEmpty()) {
                updateGridState(binding, GridState.offline(binding.port().wiring(),
                        offlineFaultWithoutSource(incompleteSet.contains(binding)), false));
                continue;
            }
            if (overlappingDedicatedSources.size() > 1) {
                PortBinding first = overlappingSources.get(0);
                updateGridState(binding, new GridState(true, true, first.port().tier(),
                        first.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                continue;
            }

            PortBinding source = exactSource != null ? exactSource : overlappingSources.get(0);
            if (sourceConflicts.contains(source)) {
                updateGridState(binding, new GridState(true, true, source.port().tier(),
                        source.port().wiring(), 0L, 0.0, FaultKind.MULTIPLE_GRID_SOURCES));
                continue;
            }
            if ((incompleteSet.contains(binding) || incompleteSet.contains(source))
                    && (binding.unloadedConductor() || source.unloadedConductor())) {
                // A saved run crossing into an unloaded chunk is an unknown conductor, not
                // evidence of a live phase loss. Fail de-energized until every participating
                // terminal is loaded; never ask for the far block entity here.
                updateGridState(binding, GridState.offline(binding.port().wiring(),
                        FaultKind.UNDERVOLTAGE, false));
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

    /** A complete local port with no loaded source is de-energized, never a live phase fault. */
    static FaultKind offlineFaultWithoutSource(boolean incompleteBinding) {
        return incompleteBinding ? FaultKind.INCOMPLETE_WIRING : FaultKind.UNDERVOLTAGE;
    }

    /** Half-loaded runs stay outside the active thermal solver until both terminals return. */
    static boolean bothEndpointsPresent(CableConnection cable,
                                        Collection<TerminalEndpoint> loadedEndpoints) {
        return cable != null && loadedEndpoints != null
                && loadedEndpoints.contains(cable.a()) && loadedEndpoints.contains(cable.b());
    }

    /**
     * Finds the loaded ends of authoritative runs whose opposite chunk is unavailable.
     * The supplied predicate must be a non-loading active-chunk check. A chunk pending load or
     * unload may be present at FULL status without a ticking holder, but it must not keep
     * participating in the live circuit.
     */
    static Set<TerminalEndpoint> loadedEndpointsWithUnloadedPeers(
            Collection<CableConnection> cables,
            Collection<TerminalEndpoint> loadedEndpoints,
            Predicate<BlockPos> positionLoaded) {
        Set<TerminalEndpoint> result = new HashSet<>();
        if (cables == null || loadedEndpoints == null || positionLoaded == null) {
            return Set.of();
        }
        for (CableConnection cable : cables) {
            if (cable == null) continue;
            boolean aPresent = loadedEndpoints.contains(cable.a());
            boolean bPresent = loadedEndpoints.contains(cable.b());
            if (aPresent && !bPresent && !positionLoaded.test(cable.b().pos())) {
                result.add(cable.a());
            }
            if (bPresent && !aPresent && !positionLoaded.test(cable.a().pos())) {
                result.add(cable.b());
            }
        }
        return Set.copyOf(result);
    }

    static FaultKind faultForUnmatchedConnection(EnergyTier receivingTier, EnergyTier supplyTier) {
        if (!receivingTier.accepts(supplyTier)) {
            return FaultKind.OVERVOLTAGE;
        }
        return receivingTier != supplyTier ? FaultKind.UNDERVOLTAGE : FaultKind.MISSING_PHASE;
    }

    private static void updateFaultedPort(PortBinding affected, PortBinding source, GridState state,
                                          Map<IElectricalPort, List<IElectricalPort>> faultUpstreamCircuits) {
        updateGridState(affected, state);
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
        Map<Object, AccountCircuits> byAccount = new HashMap<>();
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
                                         Map<CableRunId, CableLoad> cableLoads) {
        for (CableConnection cable : cables) {
            double currentAmps = Math.max(0.0, currentAmpsByCable.getOrDefault(cable, 0.0));
            cableLoads.merge(cable.runId(), new CableLoad(cable, currentAmps),
                    (first, next) -> new CableLoad(first.cable(),
                            first.currentAmps() + next.currentAmps()));
        }
    }

    private static void recordEnergizedCables(
            CircuitTopology topology, int volts,
            Map<CableConnection, Double> currentAmpsByCable,
            Map<CableRunId, EnergizedCable> energizedCables) {
        if (volts <= 0) return;
        Map<CableConnection, Double> resistanceByCable = new HashMap<>();
        for (RadialCableFlow.Branch<TerminalEndpoint, CableConnection> branch : topology.branches()) {
            resistanceByCable.put(branch.edge(), branch.resistanceOhms());
        }
        for (CableConnection cable : topology.cables()) {
            EnergizedCable next = measureEnergizedCable(cable, volts,
                    currentAmpsByCable.getOrDefault(cable, 0.0),
                    resistanceByCable.getOrDefault(cable, 0.0));
            energizedCables.merge(cable.runId(), next,
                    (first, second) -> {
                        if (first.volts() != second.volts()) {
                            return first.volts() > second.volts() ? first : second;
                        }
                        return first.currentAmps() > second.currentAmps() ? first : second;
                    });
        }
    }

    static EnergizedCable measureEnergizedCable(CableConnection cable, int volts,
                                                 double measuredAmps, double resistanceOhms) {
        double currentAmps = Double.isFinite(measuredAmps) ? Math.max(0.0, measuredAmps) : 0.0;
        double resistance = Double.isFinite(resistanceOhms)
                ? Math.max(0.0, resistanceOhms) : 0.0;
        double voltageDropVolts = ElectricalUnits.voltageDropVolts(currentAmps, resistance);
        long lineLossWatts = roundedLoss(ElectricalUnits.resistiveLossWatts(
                currentAmps, resistance));
        return new EnergizedCable(cable, volts, currentAmps, voltageDropVolts, lineLossWatts);
    }

    private static void applyCableHeat(Level level, Collection<CableLoad> cableLoads) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        long gameTick = level.getGameTime();
        List<CableConnection> failed = new ArrayList<>();
        for (CableLoad load : cableLoads) {
            CableConnection cable = load.cable();
            double currentAmps = load.currentAmps();
            double ratio = currentAmps / Math.max(1.0, cable.effectiveAmpacity());
            CableThermalModel.State state = advanceCableThermalState(data, cable, ratio, gameTick);
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
            Map<CableRunId, CableThermalModel.State> thermalStates,
            CableConnection cable, double currentRatio) {
        CableRunId runId = cable.runId();
        CableThermalModel.State state = CableThermalModel.nextState(
                thermalStates.getOrDefault(runId, CableThermalModel.State.COLD), currentRatio);
        if (state.cold() || CableThermalModel.failed(state)) {
            thermalStates.remove(runId);
        } else {
            thermalStates.put(runId, state);
        }
        return state;
    }

    static CableThermalModel.State advanceCableThermalState(
            ElectricalNetworkSavedData data, CableConnection cable,
            double currentRatio, long gameTick) {
        CableRunId runId = cable.runId();
        CableThermalModel.State state = CableThermalModel.nextState(
                data.thermalState(runId, gameTick), currentRatio);
        data.setThermalState(runId, state, gameTick);
        return state;
    }

    /** Both physical ends arc on a heavy-cable failure; terminals on one block explode only once. */
    static List<BlockPos> failureExplosionPositions(CableConnection cable) {
        BlockPos first = cable.a().pos();
        BlockPos second = cable.b().pos();
        return first.equals(second) ? List.of(first) : List.of(first, second);
    }

    private static void coolInactiveCables(Level level, Set<CableRunId> connectedCableRuns,
                                           Set<CableRunId> activeCableRuns) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        long gameTick = level.getGameTime();
        for (CableRunId run : connectedCableRuns) {
            if (activeCableRuns.contains(run)) continue;
            CableThermalModel.State current = data.thermalState(run, gameTick);
            CableThermalModel.State cooled = CableThermalModel.nextState(current, 0.0);
            data.setThermalState(run, cooled, gameTick);
        }
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

    private static void updateGridState(PortBinding binding, GridState state) {
        IElectricalPort primary = binding.port();
        primary.updateGridState(state);
        for (IElectricalPort member : binding.members()) {
            if (member != primary) {
                member.updateGridStateReplica(state);
            }
        }
    }

    static boolean compatiblePortGroup(IElectricalPort first, IElectricalPort next) {
        return first.tier() == next.tier()
                && first.wiring() == next.wiring()
                && first.electricalRole() == next.electricalRole()
                && first.enabled() == next.enabled()
                && first.maxPowerWatts() == next.maxPowerWatts()
                && first.allowsSameTickPassThrough() == next.allowsSameTickPassThrough()
                && Objects.equals(first.energyAccount(), next.energyAccount());
    }

    private record PortBinding(IElectricalPort port, List<TerminalEndpoint> endpoints,
                               List<TerminalEndpoint> roots,
                               Map<TerminalEndpoint, TerminalEndpoint> endpointsByRoot,
                               boolean tierValid, List<IElectricalPort> members,
                               boolean unloadedConductor) {}

    private record PowerTarget(PortBinding binding, long watts) {}

    private record PowerFlow(PortBinding binding, long watts) {}

    private record CableLoad(CableConnection cable, double currentAmps) {}

    private record CircuitTopology(
            List<CableConnection> cables,
            List<RadialCableFlow.Branch<TerminalEndpoint, CableConnection>> branches,
            List<TerminalEndpoint> conductorRoots,
            boolean supported) {}

    private record FlowSnapshot(boolean supported, long lossWatts,
                                List<PowerFlow> sourceDraws,
                                List<PowerFlow> sourceInjections,
                                List<PowerFlow> receivers,
                                Map<CableConnection, Double> currentAmpsByCable) {
        static FlowSnapshot unsupported() {
            return new FlowSnapshot(false, 0L, List.of(), List.of(), List.of(), Map.of());
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
        private final Set<IElectricalPort> members =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean tierValid = true;

        private PortAccumulator(IElectricalPort port) {
            this.port = port;
        }

        private void add(IElectricalPort member, EnergyTier rackTier,
                         Collection<TerminalEndpoint> newEndpoints) {
            tierValid &= rackTier == member.tier() && compatiblePortGroup(port, member);
            members.add(member);
            endpoints.addAll(newEndpoints);
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
