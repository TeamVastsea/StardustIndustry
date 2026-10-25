package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind;
import com.stardustindustry.stardustindustry.energy.device.ProtectionModel;

import net.minecraft.core.BlockPos;

class GridPowerModelTest {
    @Test
    void liveCableTelemetryUsesTheSolvedBranchCurrentAndResistance() {
        CableConnection cable = CableConnection.of(CableAmpacity.A150, CableInsulation.CLASS_2,
                new TerminalEndpoint(BlockPos.ZERO, 0),
                new TerminalEndpoint(new BlockPos(12, 0, 0), 0));

        EnergizedCable live = ElectricalGridService.measureEnergizedCable(
                cable, EnergyTier.MV.volts(), 30.0, 0.4);

        assertEquals(12.0, live.voltageDropVolts(), 1.0e-9);
        assertEquals(live.currentAmps() * live.voltageDropVolts(),
                live.lineLossWatts(), 1.0e-9);
        assertTrue(live.voltageDropVolts() > 0.0);
    }

    @Test
    void radialFlowUsesEachBranchCurrentAndCoolsAnIdleBranch() {
        var trunk = new RadialCableFlow.Branch<>("trunk", "source", "junction", 1.0);
        var branchA = new RadialCableFlow.Branch<>("a", "junction", "load-a", 2.0);
        var branchB = new RadialCableFlow.Branch<>("b", "junction", "load-b", 3.0);
        var idle = new RadialCableFlow.Branch<>("idle", "junction", "idle-load", 4.0);

        RadialCableFlow.Result<String> result = RadialCableFlow.solve(
                List.of("source"), List.of(trunk, branchA, branchB, idle),
                Map.of("source", 30.0, "load-a", -10.0, "load-b", -20.0));

        assertTrue(result.supported());
        assertEquals(30.0, result.currentAmpsByEdge().get("trunk"), 0.0);
        assertEquals(10.0, result.currentAmpsByEdge().get("a"), 0.0);
        assertEquals(20.0, result.currentAmpsByEdge().get("b"), 0.0);
        assertEquals(0.0, result.currentAmpsByEdge().get("idle"), 0.0);
        assertEquals(30.0 * 30.0 + 10.0 * 10.0 * 2.0 + 20.0 * 20.0 * 3.0,
                result.lossWatts(), 0.0);
    }

    @Test
    void cyclicAndParallelTopologiesFailClosed() {
        var triangle = RadialCableFlow.solve(List.of("a"), List.of(
                        new RadialCableFlow.Branch<>("ab", "a", "b", 1.0),
                        new RadialCableFlow.Branch<>("bc", "b", "c", 1.0),
                        new RadialCableFlow.Branch<>("ca", "c", "a", 1.0)),
                Map.of("a", 1.0, "b", -1.0));
        var parallel = RadialCableFlow.solve(List.of("a"), List.of(
                        new RadialCableFlow.Branch<>("first", "a", "b", 1.0),
                        new RadialCableFlow.Branch<>("second", "a", "b", 1.0)),
                Map.of("a", 1.0, "b", -1.0));

        assertFalse(triangle.supported());
        assertFalse(parallel.supported());
        assertTrue(triangle.currentAmpsByEdge().isEmpty());
        assertTrue(parallel.currentAmpsByEdge().isEmpty());
    }

    @Test
    void downstreamStorageBackfeedOffsetsUpstreamCurrent() {
        var trunk = new RadialCableFlow.Branch<>("trunk", "source", "junction", 1.0);
        var load = new RadialCableFlow.Branch<>("load", "junction", "consumer", 1.0);
        var storage = new RadialCableFlow.Branch<>("storage", "junction", "battery", 1.0);

        RadialCableFlow.Result<String> result = RadialCableFlow.solve(
                List.of("source"), List.of(trunk, load, storage),
                Map.of("source", 10.0, "battery", 10.0, "consumer", -20.0));

        assertTrue(result.supported());
        assertEquals(10.0, result.currentAmpsByEdge().get("trunk"), 0.0);
        assertEquals(20.0, result.currentAmpsByEdge().get("load"), 0.0);
        assertEquals(10.0, result.currentAmpsByEdge().get("storage"), 0.0);
    }

    @Test
    void proportionalAllocationIsCompleteStableAndOverflowSafe() {
        assertEquals(List.of(100L, 100L, 99L),
                PowerAllocator.proportional(299L, List.of(100L, 100L, 100L)));
        assertEquals(List.of(Long.MAX_VALUE / 2 + 1L, Long.MAX_VALUE / 2),
                PowerAllocator.proportional(Long.MAX_VALUE,
                        List.of(Long.MAX_VALUE, Long.MAX_VALUE)));
    }

    @Test
    void dedicatedSourceIsFilledBeforeStorageSharesTheRemainingDeficit() {
        assertEquals(List.of(20_000L, 2_500L, 2_500L),
                PowerAllocator.primaryThenProportional(
                        25_000L, List.of(20_000L, 100_000L, 100_000L)));
        assertEquals(List.of(10_000L, 0L, 0L),
                PowerAllocator.primaryThenProportional(
                        10_000L, List.of(20_000L, 100_000L, 100_000L)));
    }

    @Test
    void storageCoveringOnlyLineLossStillInjectsTopologyCurrent() {
        List<Long> sourceDraws = PowerAllocator.primaryThenProportional(
                10_100L, List.of(10_000L, 10_000L));

        List<Long> injections = ElectricalGridService.topologyInjectionShares(
                10_000L, sourceDraws);

        assertEquals(10_100L, sourceDraws.stream().mapToLong(Long::longValue).sum());
        assertEquals(10_000L, injections.stream().mapToLong(Long::longValue).sum());
        assertTrue(injections.get(1) > 0L,
                "storage paying the line loss must carry current on its branch");
    }

    @Test
    void failedPreflightDoesNotMutateEitherEndpointOrRemainders() {
        Object sourceAccount = new Object();
        Object targetAccount = new Object();
        FakePort source = FakePort.source(sourceAccount, 10L);
        FakePort fullTarget = FakePort.load(targetAccount, 0L);
        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders();
        GridEnergyTransaction transaction = new GridEnergyTransaction(remainders);
        transaction.extract(source, 20L);
        transaction.receive(fullTarget, 20L);

        assertFalse(transaction.commit());
        assertEquals(10L, source.joules);
        assertEquals(0L, fullTarget.joules);
        assertEquals(0, source.actualCalls);
        assertEquals(0, fullTarget.actualCalls);
        assertEquals(0L, remainders.get(sourceAccount, GridEnergyTransaction.Direction.EXTRACT));
        assertEquals(0L, remainders.get(targetAccount, GridEnergyTransaction.Direction.RECEIVE));
    }

    @Test
    void sharedAccountSharesRemainderButDirectionsRemainIndependent() {
        Object account = new Object();
        FakePort first = FakePort.load(account, 10L);
        FakePort second = FakePort.load(account, 10L);
        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders();

        GridEnergyTransaction firstReceive = new GridEnergyTransaction(remainders);
        firstReceive.receive(first, 1L);
        assertTrue(firstReceive.commit());
        assertEquals(1L, remainders.get(account, GridEnergyTransaction.Direction.RECEIVE));

        GridEnergyTransaction extract = new GridEnergyTransaction(remainders);
        extract.extract(first, 1L);
        assertTrue(extract.commit());
        assertEquals(1L, remainders.get(account, GridEnergyTransaction.Direction.EXTRACT));

        GridEnergyTransaction secondReceive = new GridEnergyTransaction(remainders);
        secondReceive.receive(second, 19L);
        assertTrue(secondReceive.commit());
        assertEquals(1L, second.joules);
        assertEquals(0L, remainders.get(account, GridEnergyTransaction.Direction.RECEIVE));
        assertEquals(1L, remainders.get(account, GridEnergyTransaction.Direction.EXTRACT));
    }

    @Test
    void oneAccountCannotChargeAndDischargeInsideOneTransaction() {
        Object account = new Object();
        GridEnergyTransaction transaction = new GridEnergyTransaction(
                new GridEnergyTransaction.Remainders());
        transaction.extract(FakePort.source(account, 10L), 20L);
        assertThrows(IllegalArgumentException.class,
                () -> transaction.receive(FakePort.load(account, 10L), 20L));
    }

    @Test
    void remainderAccountsArePrunedByIdentityWhenPortsDisappear() {
        Object removed = new Object();
        Object retained = new Object();
        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders();
        GridEnergyTransaction transaction = new GridEnergyTransaction(remainders);
        transaction.receive(FakePort.load(removed, 10L), 1L);
        assertTrue(transaction.commit());
        assertEquals(1L, remainders.get(removed, GridEnergyTransaction.Direction.RECEIVE));

        remainders.retainAccounts(List.of(retained));

        assertEquals(0L, remainders.get(removed, GridEnergyTransaction.Direction.RECEIVE));
    }

    @Test
    void sharedAccountLedgerEnforcesOneDirectionAndOnePowerBudget() {
        Object account = new Object();
        BudgetPort first = new BudgetPort(account, 100L, false, ElectricalPortRole.STORAGE);
        BudgetPort second = new BudgetPort(account, 100L, false, ElectricalPortRole.STORAGE);
        GridTransferLedger ledger = new GridTransferLedger();

        assertEquals(100L, ledger.extractableWatts(first));
        ledger.recordExtracted(first, 60L);
        assertEquals(40L, ledger.extractableWatts(second));
        assertEquals(0L, ledger.receivableWatts(second));
    }

    @Test
    void stableAccountKeysShareBudgetsAndRemaindersAcrossReloadedPortObjects() {
        ElectricalAccountKey firstKey = new ElectricalAccountKey(new BlockPos(4, 5, 6), "machine-energy");
        ElectricalAccountKey reloadedKey = new ElectricalAccountKey(new BlockPos(4, 5, 6), "machine-energy");
        BudgetPort first = new BudgetPort(firstKey, 100L, false, ElectricalPortRole.STORAGE);
        BudgetPort reloaded = new BudgetPort(reloadedKey, 100L, false, ElectricalPortRole.STORAGE);
        GridTransferLedger ledger = new GridTransferLedger();

        ledger.recordExtracted(first, 60L);
        assertEquals(40L, ledger.extractableWatts(reloaded));

        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders();
        remainders.set(firstKey, GridEnergyTransaction.Direction.EXTRACT, 17L);
        assertEquals(17L, remainders.get(reloadedKey, GridEnergyTransaction.Direction.EXTRACT));
    }

    @Test
    void passThroughAccountHasIndependentReceiveAndExtractBudgets() {
        Object account = new Object();
        BudgetPort input = new BudgetPort(account, 100L, true, ElectricalPortRole.LOAD);
        BudgetPort output = new BudgetPort(account, 150L, true, ElectricalPortRole.SOURCE);
        GridTransferLedger ledger = new GridTransferLedger();

        ledger.recordReceived(input, 60L);
        assertEquals(40L, ledger.receivableWatts(input));
        assertEquals(150L, ledger.extractableWatts(output));

        ledger.recordExtracted(output, 90L);
        assertEquals(40L, ledger.receivableWatts(input));
        assertEquals(60L, ledger.extractableWatts(output));
    }

    @Test
    void passThroughCircuitsAreOrderedFromInputToOutputAcrossADeviceChain() {
        Object transformer = new Object();
        Object breaker = new Object();
        BudgetPort transformerOutput = new BudgetPort(transformer, 100L, true,
                ElectricalPortRole.SOURCE);
        BudgetPort breakerOutput = new BudgetPort(breaker, 100L, true,
                ElectricalPortRole.SOURCE);
        BudgetPort transformerInput = new BudgetPort(transformer, 100L, true,
                ElectricalPortRole.LOAD);
        BudgetPort breakerInput = new BudgetPort(breaker, 100L, true,
                ElectricalPortRole.LOAD);

        List<Integer> order = ElectricalGridService.circuitSolveOrder(List.of(
                List.of(breakerOutput),
                List.of(transformerOutput, breakerInput),
                List.of(transformerInput)));

        assertEquals(List.of(2, 1, 0), order);
    }

    @Test
    void bufferedPassThroughConservesEnergyAcrossTwoCircuitsInOneTick() {
        BufferedAccount account = new BufferedAccount(10L);
        BufferedPort input = new BufferedPort(account, ElectricalPortRole.LOAD, 100L);
        BufferedPort output = new BufferedPort(account, ElectricalPortRole.SOURCE, 100L);
        GridTransferLedger ledger = new GridTransferLedger();
        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders();

        long receivedWatts = ledger.receivableWatts(input);
        GridEnergyTransaction inputCircuit = new GridEnergyTransaction(remainders);
        inputCircuit.receive(input, receivedWatts);
        assertTrue(inputCircuit.commit());
        ledger.recordReceived(input, receivedWatts);
        assertEquals(5L, account.storedJoules);

        long extractedWatts = ledger.extractableWatts(output);
        GridEnergyTransaction outputCircuit = new GridEnergyTransaction(remainders);
        outputCircuit.extract(output, extractedWatts);
        assertTrue(outputCircuit.commit());
        ledger.recordExtracted(output, extractedWatts);

        assertEquals(receivedWatts, extractedWatts);
        assertEquals(0L, account.storedJoules);
    }

    @Test
    void protectionSignalUsesSettledCurrentInsteadOfRequestedPower() {
        IElectricalPort protection = new IElectricalPort() {
            @Override public EnergyTier tier() { return EnergyTier.LV; }
            @Override public WiringMode wiring() { return WiringMode.THREE_PHASE_4_WIRE; }
            @Override public ElectricalPortRole electricalRole() { return ElectricalPortRole.SOURCE; }
            @Override public boolean enabled() { return true; }
            @Override public long maxPowerWatts() { return 114_000L; }
            @Override public long overcurrentTripWatts() { return 57_000L; }
        };
        WiringMode wiring = protection.wiring();
        double ratedCurrent = wiring.lineCurrentAmps(
                protection.overcurrentTripWatts(), protection.tier());

        assertFalse(ElectricalGridService.isMeasuredOvercurrent(protection,
                ratedCurrent - 0.001, wiring, protection.tier()),
                "an oversized request with lower actual current must not trip");
        assertFalse(ElectricalGridService.isMeasuredOvercurrent(protection,
                ratedCurrent, wiring, protection.tier()),
                "the exact nameplate current must not trip");
        assertTrue(ElectricalGridService.isMeasuredOvercurrent(protection,
                ratedCurrent + 0.001, wiring, protection.tier()));
    }

    @Test
    void downstreamFaultIsReportedThroughTheSourceMonitoringContract() {
        MonitoringSource source = new MonitoringSource();
        GridState missingPhase = new GridState(true, true, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 0L, 0.0, FaultKind.MISSING_PHASE);

        ElectricalGridService.reportDownstreamFault(source, missingPhase);

        assertEquals(missingPhase, source.downstreamState);
    }

    @Test
    void unhandledPhaseAndVoltageFaultsRelayPastFuseToUpstreamBreaker() {
        for (FaultKind fault : List.of(FaultKind.MISSING_PHASE, FaultKind.OVERVOLTAGE)) {
            GridState state = new GridState(true, true, EnergyTier.MV,
                    WiringMode.THREE_PHASE_3_WIRE, 0L, 0.0, fault);
            ProtectionMonitor fuse = new ProtectionMonitor(ElectricalDeviceKind.LV_FUSE_BOX);
            ProtectionMonitor breaker = new ProtectionMonitor(ElectricalDeviceKind.LV_BREAKER);

            ElectricalGridService.reportDownstreamFault(fuse, state,
                    current -> current == fuse ? breaker : null);

            assertFalse(fuse.tripped, "the overload-only fuse must ignore " + fault);
            assertTrue(breaker.tripped, "the upstream breaker must receive " + fault);
        }
    }

    @Test
    void energizedPartialBindingTripsBreakerButNotOverloadOnlyFuse() {
        WiringMode wiring = WiringMode.THREE_PHASE_4_WIRE;
        assertFalse(ElectricalGridService.hasCompleteConductorBinding(wiring, 2, 2, true));
        FaultKind fault = ElectricalGridService.faultForUnmatchedConnection(
                EnergyTier.LV, EnergyTier.LV);
        assertEquals(FaultKind.MISSING_PHASE, fault);
        GridState poweredPartialWiring = new GridState(true, true, EnergyTier.LV,
                wiring, 0L, 0.0, fault);
        ProtectionMonitor breaker = new ProtectionMonitor(ElectricalDeviceKind.LV_BREAKER);
        ProtectionMonitor fuse = new ProtectionMonitor(ElectricalDeviceKind.LV_FUSE_BOX);

        ElectricalGridService.reportDownstreamFault(breaker, poweredPartialWiring);
        ElectricalGridService.reportDownstreamFault(fuse, poweredPartialWiring);

        assertTrue(breaker.tripped);
        assertFalse(fuse.tripped);
    }

    @Test
    void anIncompleteExactSourceDoesNotSuppressThePoweredWiringFault() {
        assertTrue(ElectricalGridService.isSolvedExactCircuit(true, true));
        assertFalse(ElectricalGridService.isSolvedExactCircuit(true, false),
                "an exact-root source excluded as incomplete was never part of the solved circuit");
    }

    @Test
    void unloadedSourceLeavesACompleteLocalLoadDeenergizedWithoutAPhaseFault() {
        assertEquals(FaultKind.UNDERVOLTAGE,
                ElectricalGridService.offlineFaultWithoutSource(false));
        assertEquals(FaultKind.INCOMPLETE_WIRING,
                ElectricalGridService.offlineFaultWithoutSource(true));
    }

    @Test
    void electricalChunksMustBeBothTickingAndPresent() {
        assertTrue(ElectricalChunkAvailability.isActiveNow(true, true));
        assertFalse(ElectricalChunkAvailability.isActiveNow(false, true),
                "a pending-unload FULL chunk is not electrically active");
        assertFalse(ElectricalChunkAvailability.isActiveNow(true, false),
                "a pending-load ticking ticket is not enough without the chunk");
        assertFalse(ElectricalChunkAvailability.isActiveNow(false, false));
    }

    @Test
    void aCableEntersTheLoadedThermalSolverOnlyWhenBothEndpointsArePresent() {
        TerminalEndpoint a = new TerminalEndpoint(new BlockPos(0, 64, 0), 0);
        TerminalEndpoint b = new TerminalEndpoint(new BlockPos(32, 64, 0), 0);
        CableConnection cable = CableConnection.of(
                CableAmpacity.A150, CableInsulation.CLASS_1, a, b);

        assertFalse(ElectricalGridService.bothEndpointsPresent(cable, java.util.Set.of(a)));
        assertTrue(ElectricalGridService.bothEndpointsPresent(cable, java.util.Set.of(a, b)));
    }

    @Test
    void halfLoadedRunIsClassifiedWithoutResolvingItsRemoteEndpoint() {
        TerminalEndpoint local = new TerminalEndpoint(new BlockPos(15, 64, 0), 0);
        TerminalEndpoint remote = new TerminalEndpoint(new BlockPos(16, 64, 0), 0);
        CableConnection cable = CableConnection.of(
                CableAmpacity.A150, CableInsulation.CLASS_1, local, remote);
        java.util.concurrent.atomic.AtomicInteger probes = new java.util.concurrent.atomic.AtomicInteger();

        var boundary = ElectricalGridService.loadedEndpointsWithUnloadedPeers(
                List.of(cable), java.util.Set.of(local), pos -> {
                    probes.incrementAndGet();
                    return pos.getX() < 16;
                });

        assertEquals(java.util.Set.of(local), boundary);
        assertEquals(1, probes.get(), "only the absent endpoint's chunk should be checked");
        assertTrue(ElectricalGridService.loadedEndpointsWithUnloadedPeers(
                List.of(cable), java.util.Set.of(local, remote), pos -> false).isEmpty(),
                "a fully loaded run must not be classified as an unknown conductor");
    }

    private static final class FakePort implements IElectricalPort {
        private final Object account;
        private final ElectricalPortRole role;
        private final long capacity;
        private long joules;
        private int actualCalls;

        private FakePort(Object account, ElectricalPortRole role, long capacity, long joules) {
            this.account = account;
            this.role = role;
            this.capacity = capacity;
            this.joules = joules;
        }

        static FakePort source(Object account, long joules) {
            return new FakePort(account, ElectricalPortRole.SOURCE, joules, joules);
        }

        static FakePort load(Object account, long capacity) {
            return new FakePort(account, ElectricalPortRole.LOAD, capacity, 0L);
        }

        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.SINGLE_PHASE_2_WIRE; }
        @Override public ElectricalPortRole electricalRole() { return role; }
        @Override public boolean enabled() { return true; }
        @Override public long maxPowerWatts() { return Long.MAX_VALUE; }
        @Override public Object energyAccount() { return account; }

        @Override
        public long receiveJoules(long amount, boolean simulate) {
            long accepted = Math.min(amount, capacity - joules);
            if (!simulate) {
                actualCalls++;
                joules += accepted;
            }
            return accepted;
        }

        @Override
        public long extractJoules(long amount, boolean simulate) {
            long extracted = Math.min(amount, joules);
            if (!simulate) {
                actualCalls++;
                joules -= extracted;
            }
            return extracted;
        }
    }

    private static final class MonitoringSource implements IElectricalPort {
        private GridState downstreamState;

        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.THREE_PHASE_4_WIRE; }
        @Override public ElectricalPortRole electricalRole() { return ElectricalPortRole.SOURCE; }
        @Override public boolean enabled() { return true; }
        @Override public long maxPowerWatts() { return 1L; }
        @Override public boolean updateDownstreamFault(GridState state) {
            downstreamState = state;
            return false;
        }
    }

    private static final class ProtectionMonitor implements IElectricalPort {
        private final ElectricalDeviceKind kind;
        private boolean tripped;

        private ProtectionMonitor(ElectricalDeviceKind kind) {
            this.kind = kind;
        }

        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.THREE_PHASE_4_WIRE; }
        @Override public ElectricalPortRole electricalRole() { return ElectricalPortRole.SOURCE; }
        @Override public boolean enabled() { return !tripped; }
        @Override public long maxPowerWatts() { return kind.ratedPowerWatts(); }
        @Override public boolean updateDownstreamFault(GridState state) {
            tripped = ProtectionModel.shouldTrip(kind, state);
            return !tripped && ProtectionModel.shouldRelayUpstream(kind, state);
        }
    }

    private record BudgetPort(Object energyAccount, long maxPowerWatts,
                              boolean allowsSameTickPassThrough,
                              ElectricalPortRole electricalRole) implements IElectricalPort {
        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.SINGLE_PHASE_2_WIRE; }
        @Override public boolean enabled() { return true; }
        @Override public long requestedPowerWatts() { return maxPowerWatts; }
        @Override public long availablePowerWatts() { return maxPowerWatts; }
    }

    private static final class BufferedAccount {
        private final long capacityJoules;
        private long storedJoules;

        private BufferedAccount(long capacityJoules) {
            this.capacityJoules = capacityJoules;
        }
    }

    private record BufferedPort(BufferedAccount energyAccount, ElectricalPortRole electricalRole,
                                long maxPowerWatts) implements IElectricalPort {
        @Override public EnergyTier tier() { return EnergyTier.LV; }
        @Override public WiringMode wiring() { return WiringMode.SINGLE_PHASE_2_WIRE; }
        @Override public boolean enabled() { return true; }
        @Override public boolean allowsSameTickPassThrough() { return true; }

        @Override
        public long requestedPowerWatts() {
            return Math.min(maxPowerWatts, ElectricalUnits.wattsFromJoulesPerTick(
                    energyAccount.capacityJoules - energyAccount.storedJoules));
        }

        @Override
        public long availablePowerWatts() {
            return Math.min(maxPowerWatts,
                    ElectricalUnits.wattsFromJoulesPerTick(energyAccount.storedJoules));
        }

        @Override
        public long receiveJoules(long amount, boolean simulate) {
            if (electricalRole != ElectricalPortRole.LOAD) return 0L;
            long accepted = Math.min(amount,
                    energyAccount.capacityJoules - energyAccount.storedJoules);
            if (!simulate) energyAccount.storedJoules += accepted;
            return accepted;
        }

        @Override
        public long extractJoules(long amount, boolean simulate) {
            if (electricalRole != ElectricalPortRole.SOURCE) return 0L;
            long extracted = Math.min(amount, energyAccount.storedJoules);
            if (!simulate) energyAccount.storedJoules -= extracted;
            return extracted;
        }
    }
}
