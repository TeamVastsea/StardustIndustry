package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LowVoltageWiringTest {
    @Test
    void energyTierExposesExactlyFourPhysicalVoltageLevels() {
        assertEquals(List.of(EnergyTier.LV, EnergyTier.MV, EnergyTier.HV, EnergyTier.EHV),
                List.of(EnergyTier.values()));
        assertEquals(380, EnergyTier.LV.volts());
        assertEquals(220, EnergyTier.LV.singlePhaseVolts());
        assertEquals(10_000, EnergyTier.MV.volts());
        assertEquals(110_000, EnergyTier.HV.volts());
        assertEquals(500_000, EnergyTier.EHV.volts());
    }

    @Test
    void lowVoltageWiringModesExposeTheirPhysicalConductorCounts() {
        assertEquals(2, WiringMode.SINGLE_PHASE_2_WIRE.requiredConductors());
        assertEquals(3, WiringMode.THREE_PHASE_3_WIRE.requiredConductors());
        assertEquals(4, WiringMode.THREE_PHASE_4_WIRE.requiredConductors());

        assertEquals(220, WiringMode.SINGLE_PHASE_2_WIRE.nominalVolts(EnergyTier.LV));
        assertEquals(380, WiringMode.THREE_PHASE_3_WIRE.nominalVolts(EnergyTier.LV));
        assertEquals(380, WiringMode.THREE_PHASE_4_WIRE.nominalVolts(EnergyTier.LV));
    }

    @Test
    void missingOrUnknownSavedWiringDefaultsToSinglePhase() {
        assertEquals(WiringMode.SINGLE_PHASE_2_WIRE, WiringMode.byId(""));
        assertEquals(WiringMode.SINGLE_PHASE_2_WIRE, WiringMode.byId("unknown"));
        assertEquals(WiringMode.THREE_PHASE_4_WIRE,
                WiringMode.byId(WiringMode.THREE_PHASE_4_WIRE.id()));
    }

    @Test
    void bindingRequiresExactlyOneIndependentEndpointPerConductor() {
        for (WiringMode wiring : WiringMode.values()) {
            int required = wiring.requiredConductors();
            assertTrue(ElectricalGridService.hasCompleteConductorBinding(
                    wiring, required, required, true), wiring.name());
            assertFalse(ElectricalGridService.hasCompleteConductorBinding(
                    wiring, required - 1, required - 1, true), wiring.name());
            assertFalse(ElectricalGridService.hasCompleteConductorBinding(
                    wiring, required, required - 1, true), wiring.name());
            assertFalse(ElectricalGridService.hasCompleteConductorBinding(
                    wiring, required + 1, required + 1, true), wiring.name());
            assertFalse(ElectricalGridService.hasCompleteConductorBinding(
                    wiring, required, required, false), wiring.name());
        }
    }
}
