package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class CableElectricalTest {
    @Test
    void cableRunKeyIgnoresColorAndEndpointDirection() {
        TerminalEndpoint a = new TerminalEndpoint(new BlockPos(-2, 4, 8), 1);
        TerminalEndpoint b = new TerminalEndpoint(new BlockPos(7, 4, 8), 3);
        CableConnection cable = new CableConnection(CableAmpacity.A150, CableInsulation.CLASS_2,
                CableColor.UNDYED, a, b);
        CableConnection reversedAndRecolored = new CableConnection(CableAmpacity.A150,
                CableInsulation.CLASS_2, CableColor.ofDyeIndex(14), b, a);

        assertEquals(cable.runKey(), cable.withColor(CableColor.ofDyeIndex(11)).runKey());
        assertEquals(cable.runKey(), reversedAndRecolored.runKey());
        assertEquals(new CableRunKey(CableAmpacity.A150, CableInsulation.CLASS_2, a, b),
                new CableRunKey(CableAmpacity.A150, CableInsulation.CLASS_2, b, a));
        assertNotEquals(cable.runKey(), new CableRunKey(CableAmpacity.A500,
                CableInsulation.CLASS_2, a, b));
        assertNotEquals(cable.runKey(), new CableRunKey(CableAmpacity.A150,
                CableInsulation.CLASS_1, a, b));
    }

    @Test
    void recoloringCannotResetAccumulatedCableHeat() {
        TerminalEndpoint a = new TerminalEndpoint(BlockPos.ZERO, 0);
        TerminalEndpoint b = new TerminalEndpoint(new BlockPos(1, 0, 0), 0);
        CableConnection cable = new CableConnection(CableAmpacity.A150, CableInsulation.CLASS_2,
                CableColor.ofDyeIndex(14), a, b);
        CableConnection recolored = cable.withColor(CableColor.ofDyeIndex(11));
        Map<CableRunKey, CableThermalModel.State> thermalStates = new HashMap<>();
        CableThermalModel.State state = CableThermalModel.State.COLD;

        for (int tick = 1; tick < 60; tick++) {
            CableConnection currentView = tick % 2 == 0 ? cable : recolored;
            state = ElectricalGridService.advanceCableThermalState(
                    thermalStates, currentView, 5.0);
            assertFalse(CableThermalModel.failed(state), "failed after only " + tick + " ticks");
        }
        state = ElectricalGridService.advanceCableThermalState(thermalStates, cable, 5.0);

        assertTrue(CableThermalModel.failed(state));
        assertFalse(thermalStates.containsKey(cable.runKey()));
    }

    @Test
    void insulationDeratesAmpacityButDoesNotChangeResistance() {
        TerminalEndpoint a = new TerminalEndpoint(BlockPos.ZERO, 0);
        TerminalEndpoint b = new TerminalEndpoint(new BlockPos(1, 0, 0), 0);
        CableConnection bare = new CableConnection(CableAmpacity.A150, CableInsulation.BARE,
                CableColor.UNDYED, a, b);
        CableConnection insulated = new CableConnection(CableAmpacity.A150, CableInsulation.CLASS_2,
                CableColor.UNDYED, a, b);

        assertEquals(bare.ohmsPerBlock(), insulated.ohmsPerBlock(), 0.0f);
        assertEquals(150, bare.effectiveAmpacity());
        assertEquals(120, insulated.effectiveAmpacity());
    }

    @Test
    void storageDisplaySelectsKilowattMegawattAndGigawattHours() {
        long joules = ElectricalUnits.kilowattHoursToJoules(100L);
        assertEquals(360_000_000L, joules);
        assertEquals("100.00 kWh", ElectricalUnits.formatKilowattHours(joules));
        assertEquals("1.50 MWh", ElectricalUnits.formatKilowattHours(
                ElectricalUnits.kilowattHoursToJoules(1_500L)));
        assertEquals("2.00 GWh", ElectricalUnits.formatKilowattHours(
                ElectricalUnits.kilowattHoursToJoules(2_000_000L)));
    }

    @Test
    void heavyCableFailureTargetsBothDistinctTerminalBlocks() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = new BlockPos(8, 2, 3);
        CableConnection span = new CableConnection(CableAmpacity.A150, CableInsulation.BARE,
                CableColor.UNDYED, new TerminalEndpoint(first, 0), new TerminalEndpoint(second, 2));
        CableConnection sameRack = new CableConnection(CableAmpacity.A150, CableInsulation.BARE,
                CableColor.UNDYED, new TerminalEndpoint(first, 0), new TerminalEndpoint(first, 1));

        assertEquals(List.of(first, second), ElectricalGridService.failureExplosionPositions(span));
        assertEquals(List.of(first), ElectricalGridService.failureExplosionPositions(sameRack));
    }
}
