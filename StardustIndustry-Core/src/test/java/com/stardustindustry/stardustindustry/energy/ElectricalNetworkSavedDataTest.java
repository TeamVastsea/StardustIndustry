package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Vector3f;

class ElectricalNetworkSavedDataTest {

    @Test
    void cableSpanAllowsExactlySixtyFourBlocksButRejectsMore() {
        assertTrue(CableService.withinMaximumSpan(new Vector3f(0, 0, 0), new Vector3f(64, 0, 0)));
        assertFalse(CableService.withinMaximumSpan(
                new Vector3f(0, 0, 0), new Vector3f(64.01f, 0, 0)));
    }

    @Test
    void runIdentityIgnoresDirectionAndProduct() {
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);

        assertEquals(new CableRunId(a, b), new CableRunId(b, a));
        assertEquals(cable(a, b).runId(),
                new CableConnection(CableAmpacity.A500, CableInsulation.CLASS_2,
                        CableColor.ofDyeIndex(14), b, a).runId());
    }

    @Test
    void oneEndpointPairCannotCarryTwoProducts() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);

        assertTrue(data.connect(cable(a, b)).changed());
        CableMutationResult duplicate = data.connect(new CableConnection(
                CableAmpacity.A500, CableInsulation.CLASS_2, CableColor.UNDYED, a, b));

        assertEquals(CableMutationResult.Status.DUPLICATE_ENDPOINTS, duplicate.status());
        assertFalse(duplicate.changed());
        assertEquals(1, data.records().size());
        assertEquals(CableAmpacity.A150, data.cable(new CableRunId(a, b)).ampacity());
    }

    @Test
    void branchesAreAllowedButCyclesAreRejected() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);
        TerminalEndpoint c = endpoint(2);
        TerminalEndpoint d = endpoint(3);

        assertTrue(data.connect(cable(a, b)).changed());
        assertTrue(data.connect(cable(b, c)).changed());
        assertTrue(data.connect(cable(b, d)).changed());
        assertEquals(CableMutationResult.Status.WOULD_CREATE_CYCLE,
                data.connect(cable(c, a)).status());
        assertEquals(3, data.records().size());
    }

    @Test
    void deletingAnEdgeReopensTheTopology() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);
        TerminalEndpoint c = endpoint(2);
        CableConnection ab = cable(a, b);
        CableConnection bc = cable(b, c);

        data.connect(ab);
        data.connect(bc);
        assertTrue(data.disconnect(bc).changed());
        assertTrue(data.connect(cable(a, c)).changed());
        assertNull(data.cable(bc.runId()));
    }

    @Test
    void removingARackDisconnectsAndTombstonesEveryRemoteRun() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        BlockPos rack = new BlockPos(0, 64, 0);
        CableConnection first = cable(
                new TerminalEndpoint(rack, 0), new TerminalEndpoint(new BlockPos(8, 64, 0), 0));
        CableConnection second = cable(
                new TerminalEndpoint(rack, 1), new TerminalEndpoint(new BlockPos(16, 64, 0), 0));
        data.connect(first);
        data.connect(second);

        assertEquals(2, data.disconnectAll(rack).size());

        assertTrue(data.records().isEmpty());
        assertTrue(data.hasTombstone(first.runId()));
        assertTrue(data.hasTombstone(second.runId()));
        assertEquals(CableMutationResult.Status.TOMBSTONED,
                data.migrateLegacy(first).status());
        assertEquals(CableMutationResult.Status.TOMBSTONED,
                data.migrateLegacy(second).status());
    }

    @Test
    void recoloringPreservesThermalState() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        CableConnection cable = cable(endpoint(0), endpoint(1));
        CableThermalModel.State hot = new CableThermalModel.State(0.625, 19);
        data.connect(cable);
        data.setThermalState(cable.runId(), hot);

        CableMutationResult result = data.recolor(cable, CableColor.ofDyeIndex(11));

        assertTrue(result.changed());
        assertEquals(hot, data.thermalState(cable.runId()));
        assertEquals(CableColor.ofDyeIndex(11), data.cable(cable.runId()).color());
    }

    @Test
    void offlineCoolingUsesElapsedWorldTicksButIsCapped() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        CableConnection cable = cable(endpoint(0), endpoint(1));
        data.connect(cable);
        data.setThermalState(cable.runId(), new CableThermalModel.State(1.0, 40), 10L);

        CableThermalModel.State cooled = data.thermalState(cable.runId(), 10_000L);

        double expected = 1.0 - ElectricalNetworkSavedData.MAX_OFFLINE_COOLING_TICKS
                / (30.0 * ElectricalUnits.TICKS_PER_SECOND);
        assertEquals(expected, cooled.heat(), 1.0e-12);
        assertEquals(0, cooled.continuousOverloadTicks());
        assertEquals(9_999L, data.record(cable.runId()).lastThermalUpdateTick());
    }

    @Test
    void tombstoneBlocksAnUnloadedLegacyHalfFromReturning() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        CableConnection oldHalf = cable(endpoint(0), endpoint(1));

        assertEquals(CableMutationResult.Status.NOT_FOUND, data.disconnect(oldHalf).status());
        assertTrue(data.hasTombstone(oldHalf.runId()));
        assertEquals(CableMutationResult.Status.TOMBSTONED, data.migrateLegacy(oldHalf).status());
        assertNull(data.cable(oldHalf.runId()));
    }

    @Test
    void explicitlyReconnectingClearsATombstone() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        CableConnection cable = cable(endpoint(0), endpoint(1));
        data.disconnect(cable);

        assertTrue(data.connect(cable).changed());
        assertFalse(data.hasTombstone(cable.runId()));
        CableConnection staleLegacyHalf = new CableConnection(CableAmpacity.A500,
                CableInsulation.CLASS_2, CableColor.ofDyeIndex(14), cable.b(), cable.a());
        assertEquals(CableMutationResult.Status.DUPLICATE_ENDPOINTS,
                data.migrateLegacy(staleLegacyHalf).status());
        assertEquals(cable, data.cable(cable.runId()));
    }

    @Test
    void twoLegacyHalvesMigrateIntoOneDeterministicRecord() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);
        CableConnection fromB = new CableConnection(CableAmpacity.A500, CableInsulation.CLASS_2,
                CableColor.ofDyeIndex(14), b, a);
        CableConnection fromA = new CableConnection(CableAmpacity.A150, CableInsulation.CLASS_1,
                CableColor.ofDyeIndex(11), a, b);

        assertTrue(data.migrateLegacy(fromB).changed());
        assertTrue(data.migrateLegacy(fromA).changed());

        assertEquals(1, data.records().size());
        assertEquals(fromA, data.cable(new CableRunId(a, b)));
    }

    @Test
    void thermalUpdatesDoNotFinalizeAPartiallyMigratedLegacyRun() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        TerminalEndpoint a = endpoint(0);
        TerminalEndpoint b = endpoint(1);
        CableConnection firstHalf = new CableConnection(CableAmpacity.A500,
                CableInsulation.CLASS_2, CableColor.ofDyeIndex(14), b, a);
        CableConnection preferredSecondHalf = new CableConnection(CableAmpacity.A150,
                CableInsulation.CLASS_1, CableColor.ofDyeIndex(11), a, b);
        CableThermalModel.State hot = new CableThermalModel.State(0.5, 10);

        data.migrateLegacy(firstHalf);
        data.setThermalState(firstHalf.runId(), hot, 50L);
        assertTrue(data.migrateLegacy(preferredSecondHalf).changed());

        assertEquals(preferredSecondHalf, data.cable(firstHalf.runId()));
        assertEquals(hot, data.thermalState(firstHalf.runId()));
        assertTrue(data.record(firstHalf.runId()).legacyMigration());
    }

    @Test
    void nbtRoundTripKeepsColourHeatAndTombstones() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        CableConnection kept = cable(endpoint(0), endpoint(1));
        CableConnection deleted = cable(endpoint(2), endpoint(3));
        CableColor blue = CableColor.ofDyeIndex(11);
        data.connect(kept);
        data.recolor(kept, blue);
        CableThermalModel.State hot = new CableThermalModel.State(0.75, 23);
        data.setThermalState(kept.runId(), hot, 1234L);
        data.disconnect(deleted);

        ElectricalAccountKey account = new ElectricalAccountKey(new BlockPos(8, 70, -4), "battery");
        data.setEnergyRemainder(account, false, 7L);
        data.setEnergyRemainder(account, true, 13L);

        CompoundTag saved = data.save(new CompoundTag(), null);
        ElectricalNetworkSavedData loaded = ElectricalNetworkSavedData.load(saved, null);

        assertEquals(blue, loaded.cable(kept.runId()).color());
        assertEquals(hot, loaded.thermalState(kept.runId()));
        assertEquals(1234L, loaded.record(kept.runId()).lastThermalUpdateTick());
        assertTrue(loaded.hasTombstone(deleted.runId()));
        assertEquals(CableMutationResult.Status.TOMBSTONED,
                loaded.migrateLegacy(deleted).status());
        assertEquals(7L, loaded.energyRemainder(account, false));
        assertEquals(13L, loaded.energyRemainder(account, true));

        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders(loaded);
        remainders.set(account, GridEnergyTransaction.Direction.RECEIVE, 19L);
        assertEquals(19L, loaded.energyRemainder(account, false));
    }

    @Test
    void unversionedDataMigratesInPlaceAndWritesCurrentVersion() {
        ElectricalNetworkSavedData original = new ElectricalNetworkSavedData();
        CableConnection cable = cable(endpoint(0), endpoint(1));
        original.connect(cable);
        original.recolor(cable, CableColor.ofDyeIndex(14));
        CompoundTag unversioned = original.save(new CompoundTag(), null);
        unversioned.remove("Version");

        ElectricalNetworkSavedData migrated = ElectricalNetworkSavedData.load(unversioned, null);

        assertTrue(migrated.isDirty());
        assertEquals(CableColor.ofDyeIndex(14), migrated.cable(cable.runId()).color());
        CompoundTag rewritten = migrated.save(new CompoundTag(), null);
        assertEquals(1, rewritten.getInt("Version"));
    }

    @Test
    void futureVersionIsPreservedAndRejectsDowngradeMutations() {
        CompoundTag future = new CompoundTag();
        future.putInt("Version", 2);
        future.putString("FutureOnlyField", "preserve-me");

        ElectricalNetworkSavedData loaded = ElectricalNetworkSavedData.load(future, null);

        assertTrue(loaded.isReadOnly());
        assertFalse(new ElectricalNetworkSavedData().isReadOnly());
        assertEquals(CableMutationResult.Status.UNSUPPORTED_DATA_VERSION,
                loaded.connect(cable(endpoint(0), endpoint(1))).status());
        loaded.setEnergyRemainder(
                new ElectricalAccountKey(new BlockPos(4, 64, 0), "future"), false, 7L);
        CompoundTag resaved = loaded.save(new CompoundTag(), null);
        assertEquals(2, resaved.getInt("Version"));
        assertEquals("preserve-me", resaved.getString("FutureOnlyField"));
        assertFalse(loaded.isDirty());
    }

    @Test
    void persistentRemainderAdapterPrunesAccountsThatAreNoLongerPresent() {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        ElectricalAccountKey removed = new ElectricalAccountKey(
                new BlockPos(8, 70, -4), "removed");
        ElectricalAccountKey retained = new ElectricalAccountKey(
                new BlockPos(9, 70, -4), "retained");
        data.setEnergyRemainder(removed, false, 7L);
        data.setEnergyRemainder(removed, true, 13L);
        data.setEnergyRemainder(retained, false, 11L);

        GridEnergyTransaction.Remainders remainders = new GridEnergyTransaction.Remainders(data);
        remainders.retainAccounts(java.util.List.of(retained));

        assertEquals(0L, remainders.get(removed, GridEnergyTransaction.Direction.RECEIVE));
        assertEquals(0L, remainders.get(removed, GridEnergyTransaction.Direction.EXTRACT));
        assertEquals(11L, remainders.get(retained, GridEnergyTransaction.Direction.RECEIVE));

        ElectricalNetworkSavedData reloaded = ElectricalNetworkSavedData.load(
                data.save(new CompoundTag(), null), null);
        assertEquals(0L, reloaded.energyRemainder(removed, false));
        assertEquals(0L, reloaded.energyRemainder(removed, true));
        assertEquals(11L, reloaded.energyRemainder(retained, false));
    }

    @Test
    void malformedNbtEntriesAreSkipped() {
        CompoundTag root = new CompoundTag();
        ListTag cables = new ListTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putLong("APos", BlockPos.ZERO.asLong());
        malformed.putInt("ACell", 0);
        malformed.putLong("BPos", new BlockPos(1, 0, 0).asLong());
        malformed.putInt("BCell", 0);
        malformed.putString("Ampacity", "not_a_real_product");
        malformed.putString("Insulation", CableInsulation.CLASS_1.id());
        cables.add(malformed);
        root.put("Cables", cables);

        ElectricalNetworkSavedData loaded = ElectricalNetworkSavedData.load(root, null);

        assertTrue(loaded.records().isEmpty());
    }

    private static TerminalEndpoint endpoint(int x) {
        return new TerminalEndpoint(new BlockPos(x, 64, 0), 0);
    }

    private static CableConnection cable(TerminalEndpoint a, TerminalEndpoint b) {
        return new CableConnection(CableAmpacity.A150, CableInsulation.CLASS_1,
                CableColor.UNDYED, a, b);
    }
}
