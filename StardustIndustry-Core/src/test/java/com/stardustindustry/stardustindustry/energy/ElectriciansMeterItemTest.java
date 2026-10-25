package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

class ElectriciansMeterItemTest {
    @Test
    void terminalDiagnosticTargetsPickedCableWhenPresent() {
        BlockPos rack = new BlockPos(2, 64, -3);
        CableConnection cable = CableConnection.of(
                CableAmpacity.A150, CableInsulation.CLASS_1,
                new TerminalEndpoint(rack, 4),
                new TerminalEndpoint(new BlockPos(12, 70, 8), 1));

        assertNull(ElectriciansMeterItem.diagnosticRequest(rack, null).cableRun());
        assertEquals(cable.runId(),
                ElectriciansMeterItem.diagnosticRequest(rack, cable).cableRun());
    }
}
