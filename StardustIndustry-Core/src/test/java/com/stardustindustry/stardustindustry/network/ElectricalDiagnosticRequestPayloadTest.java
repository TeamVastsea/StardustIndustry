package com.stardustindustry.stardustindustry.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.CableRunId;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;

import net.minecraft.core.BlockPos;

class ElectricalDiagnosticRequestPayloadTest {
    @Test
    void cableRunIdentityIsIndependentOfEndpointOrder() {
        TerminalEndpoint first = new TerminalEndpoint(new BlockPos(2, 64, -3), 4);
        TerminalEndpoint second = new TerminalEndpoint(new BlockPos(12, 70, 8), 1);

        assertEquals(new CableRunId(first, second), new CableRunId(second, first));
    }

    @Test
    void cableRequestMustTargetOneOfTheClickedRacksEndpoints() {
        TerminalEndpoint first = new TerminalEndpoint(new BlockPos(2, 64, -3), 4);
        TerminalEndpoint second = new TerminalEndpoint(new BlockPos(12, 70, 8), 1);
        CableRunId run = new CableRunId(first, second);

        assertTrue(ElectricalDiagnosticRequestPayload.cable(first.pos(), run)
                .targetsCableOnClickedRack());
        assertTrue(ElectricalDiagnosticRequestPayload.cable(second.pos(), run)
                .targetsCableOnClickedRack());
        assertFalse(ElectricalDiagnosticRequestPayload.cable(new BlockPos(3, 64, -3), run)
                .targetsCableOnClickedRack());
    }

    @Test
    void terminalRequestHasNoCableConstraint() {
        ElectricalDiagnosticRequestPayload request = ElectricalDiagnosticRequestPayload.terminal(
                new BlockPos(2, 64, -3));

        assertNull(request.cableRun());
        assertTrue(request.targetsCableOnClickedRack());
    }
}
