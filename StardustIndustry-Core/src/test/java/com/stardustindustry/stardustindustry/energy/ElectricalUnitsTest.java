package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.machine.storage.StorageTier;

class ElectricalUnitsTest {
    @Test
    void usesBalancedPowerEquationForEveryWiringMode() {
        for (WiringMode wiring : WiringMode.values()) {
            int nominalVolts = wiring.nominalVolts(EnergyTier.LV);
            long powerWatts = wiring.powerWatts(30.0, EnergyTier.LV);

            assertEquals(30.0, ElectricalUnits.currentAmps(powerWatts, nominalVolts, wiring),
                    0.001, wiring.name());
        }
    }

    @Test
    void balancedThreePhaseNeutralCarriesNoCurrent() {
        long powerWatts = 10_000L;
        WiringMode wiring = WiringMode.THREE_PHASE_4_WIRE;

        assertEquals(15.193, wiring.lineCurrentAmps(powerWatts, EnergyTier.LV), 0.001);
        assertEquals(15.193, wiring.conductorCurrentAmps(powerWatts, EnergyTier.LV, 0), 0.001);
        assertEquals(15.193, wiring.conductorCurrentAmps(powerWatts, EnergyTier.LV, 2), 0.001);
        assertEquals(0.0, wiring.conductorCurrentAmps(powerWatts, EnergyTier.LV, 3), 0.0);
    }

    @Test
    void ohmsLawVoltageDropFeedsJouleLoss() {
        double currentAmps = 30.0;
        double resistanceOhms = 0.4;
        double voltageDropVolts = ElectricalUnits.voltageDropVolts(currentAmps,
                resistanceOhms);

        assertEquals(12.0, voltageDropVolts, 1.0e-9);
        assertEquals(360.0, ElectricalUnits.resistiveLossWatts(currentAmps,
                resistanceOhms), 1.0e-9);
        assertEquals(currentAmps * voltageDropVolts,
                ElectricalUnits.resistiveLossWatts(currentAmps, resistanceOhms), 0.0);
    }

    @Test
    void lineLossCountsOnlyCurrentCarryingConductors() {
        assertEquals(200.0, ElectricalUnits.lineLossWatts(10.0, 1.0,
                WiringMode.SINGLE_PHASE_2_WIRE), 1.0e-9);
        assertEquals(300.0, ElectricalUnits.lineLossWatts(10.0, 1.0,
                WiringMode.THREE_PHASE_3_WIRE), 1.0e-9);
        assertEquals(300.0, ElectricalUnits.lineLossWatts(10.0, 1.0,
                WiringMode.THREE_PHASE_4_WIRE), 1.0e-9);
    }

    @Test
    void tickRemainderPreservesSubJouleEnergy() {
        long remainder = 0L;
        long joules = 0L;
        for (int tick = 0; tick < 20; tick++) {
            ElectricalUnits.TickEnergy energy = ElectricalUnits.tickEnergy(1L, remainder);
            joules += energy.joules();
            remainder = energy.remainderWattTicks();
        }
        assertEquals(1L, joules);
        assertEquals(0L, remainder);
    }

    @Test
    void destructiveFaultRequiresContinuousConfirmation() {
        DelayedFault fault = new DelayedFault(3);
        assertFalse(fault.update(true));
        assertFalse(fault.update(false));
        assertFalse(fault.update(true));
        assertFalse(fault.update(true));
        assertTrue(fault.update(true));
        assertTrue(fault.latched());
        assertFalse(fault.update(true));
    }

    @Test
    void missingPhaseRequiresAnEnergizedClosedSource() {
        GridState energizedMissingPhase = new GridState(true, true, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 0L, 0.0, FaultKind.MISSING_PHASE);
        GridState disconnectedMissingPhase = new GridState(false, true, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 0L, 0.0, FaultKind.MISSING_PHASE);
        GridState openSourceMissingPhase = new GridState(true, false, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, 0L, 0.0, FaultKind.MISSING_PHASE);

        assertTrue(ElectricalFaultPolicy.shouldConfirm(energizedMissingPhase, FaultKind.MISSING_PHASE));
        assertFalse(ElectricalFaultPolicy.shouldConfirm(disconnectedMissingPhase, FaultKind.MISSING_PHASE));
        assertFalse(ElectricalFaultPolicy.shouldConfirm(openSourceMissingPhase, FaultKind.MISSING_PHASE));
    }

    @Test
    void transmissionLimitIncludesLineLoss() {
        assertEquals(62L, ElectricalGridService.deliverablePower(100L, 100L, 10,
                1.0),
                "62 W delivered plus 38 W rounded loss exactly consumes 100 W");
        assertEquals(100L, ElectricalGridService.deliverablePower(100L, 100L, 10,
                0.0));
    }

    @Test
    void cableOverloadUsesDocumentedTimeCurveAndCoolsWhenDeenergized() {
        assertEquals(1.0, heatAfterTicks(1.2, 1_200), 0.0);
        assertTrue(heatAfterTicks(1.2, 1_199) < 1.0);
        assertEquals(1.0, heatAfterTicks(2.0, 300), 0.0);
        assertTrue(heatAfterTicks(2.0, 299) < 1.0);
        assertEquals(1.0, heatAfterTicks(5.0, 60), 0.0);
        assertTrue(heatAfterTicks(5.0, 59) < 1.0);

        double heat = heatAfterTicks(2.0, 150);
        for (int tick = 0; tick < 600; tick++) {
            heat = CableThermalModel.nextHeat(heat, 0.0);
        }
        assertEquals(0.0, heat, 0.0);
    }

    @Test
    void preheatedCableStillNeedsFortyNewContinuousOverloadTicks() {
        CableThermalModel.State state = new CableThermalModel.State(0.999, 39);
        state = CableThermalModel.nextState(state, 0.0);
        assertEquals(0, state.continuousOverloadTicks());

        for (int tick = 1; tick < CableThermalModel.DESTRUCTIVE_CONFIRMATION_TICKS; tick++) {
            state = CableThermalModel.nextState(state, 5.0);
            assertFalse(CableThermalModel.failed(state), "failed after only " + tick + " ticks");
        }
        state = CableThermalModel.nextState(state, 5.0);
        assertTrue(CableThermalModel.failed(state));
    }

    @Test
    void storageRatingsUseBalancedThreePhasePower() {
        for (StorageTier tier : StorageTier.values()) {
            WiringMode wiring = tier == StorageTier.LV
                    ? WiringMode.THREE_PHASE_4_WIRE : WiringMode.THREE_PHASE_3_WIRE;
            assertEquals(tier.amperage(), wiring.lineCurrentAmps(tier.nominalPowerWatts(),
                    tier.energyTier()), 0.001,
                    tier.name());
        }
        assertEquals(65_818L, StorageTier.LV.nominalPowerWatts());
    }

    private static double heatAfterTicks(double ratio, int ticks) {
        double heat = 0.0;
        for (int tick = 0; tick < ticks; tick++) {
            heat = CableThermalModel.nextHeat(heat, ratio);
        }
        return heat;
    }
}
