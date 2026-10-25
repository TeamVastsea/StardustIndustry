package com.stardustindustry.stardustindustry.energy.device;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.GridState;
import com.stardustindustry.stardustindustry.energy.WiringMode;

class ProtectionModelTest {
    @Test
    void solverOvercurrentSignalTripsProtectionAtTheHardPowerLimit() {
        for (ElectricalDeviceKind kind : new ElectricalDeviceKind[] {
                ElectricalDeviceKind.LV_BREAKER,
                ElectricalDeviceKind.LV_FUSE_BOX
        }) {
            GridState state = state(kind.ratedCurrentAmps(), FaultKind.OVERCURRENT);
            assertTrue(ProtectionModel.shouldTrip(kind, state), kind.name());
        }
    }

    @Test
    void measuredCurrentTripsOnlyAboveTheRating() {
        ElectricalDeviceKind breaker = ElectricalDeviceKind.LV_BREAKER;

        assertFalse(ProtectionModel.shouldTrip(breaker,
                state(breaker.ratedCurrentAmps(), FaultKind.NONE)));
        assertTrue(ProtectionModel.shouldTrip(breaker,
                state(breaker.ratedCurrentAmps() + 0.001, FaultKind.NONE)));
    }

    @Test
    void requestedOverloadDoesNotTripWhenMeasuredCurrentIsBelowRating() {
        ElectricalDeviceKind breaker = ElectricalDeviceKind.LV_BREAKER;
        GridState state = new GridState(true, true, EnergyTier.LV,
                WiringMode.THREE_PHASE_4_WIRE, breaker.ratedPowerWatts(),
                breaker.ratedCurrentAmps() - 1.0, FaultKind.NONE,
                breaker.ratedPowerWatts() * 2L, breaker.ratedPowerWatts(), 0L, 1.0);

        assertFalse(ProtectionModel.shouldTrip(breaker, state));
    }

    @Test
    void protectionCanCarryAnOverloadLongEnoughToMeasureAndTripIt() {
        for (ElectricalDeviceKind kind : new ElectricalDeviceKind[] {
                ElectricalDeviceKind.LV_BREAKER,
                ElectricalDeviceKind.LV_FUSE_BOX
        }) {
            assertTrue(ProtectionModel.maximumThroughputWatts(kind) > kind.ratedPowerWatts(),
                    kind.name());
        }
    }

    @Test
    void ordinaryDevicesIgnoreProtectionSignals() {
        assertFalse(ProtectionModel.shouldTrip(ElectricalDeviceKind.FUEL_GENERATOR,
                state(1_000_000.0, FaultKind.OVERCURRENT)));
    }

    @Test
    void breakerTripsForVoltageAndPhaseFaults() {
        ElectricalDeviceKind breaker = ElectricalDeviceKind.LV_BREAKER;

        assertTrue(ProtectionModel.shouldTrip(breaker, state(0.0, FaultKind.OVERVOLTAGE)));
        assertTrue(ProtectionModel.shouldTrip(breaker, state(0.0, FaultKind.MISSING_PHASE)));
        assertFalse(ProtectionModel.shouldTrip(breaker, state(0.0, FaultKind.UNDERVOLTAGE)));
    }

    @Test
    void fuseRespondsToOverloadOnly() {
        ElectricalDeviceKind fuse = ElectricalDeviceKind.LV_FUSE_BOX;

        assertTrue(ProtectionModel.shouldTrip(fuse, state(0.0, FaultKind.OVERCURRENT)));
        assertFalse(ProtectionModel.shouldTrip(fuse, state(0.0, FaultKind.OVERVOLTAGE)));
        assertFalse(ProtectionModel.shouldTrip(fuse, state(0.0, FaultKind.MISSING_PHASE)));
        assertFalse(ProtectionModel.shouldTrip(fuse, state(0.0, FaultKind.UNDERVOLTAGE)));
    }

    @Test
    void onlyFuseRelaysUnhandledPhaseAndVoltageFaultsUpstream() {
        GridState missingPhase = state(0.0, FaultKind.MISSING_PHASE);
        GridState overvoltage = state(0.0, FaultKind.OVERVOLTAGE);

        assertTrue(ProtectionModel.shouldRelayUpstream(ElectricalDeviceKind.LV_FUSE_BOX,
                missingPhase));
        assertTrue(ProtectionModel.shouldRelayUpstream(ElectricalDeviceKind.LV_FUSE_BOX,
                overvoltage));
        assertFalse(ProtectionModel.shouldRelayUpstream(ElectricalDeviceKind.LV_BREAKER,
                missingPhase));
        assertFalse(ProtectionModel.shouldRelayUpstream(ElectricalDeviceKind.TRANSFORMER_LV_MV,
                missingPhase));
        assertFalse(ProtectionModel.shouldRelayUpstream(ElectricalDeviceKind.FUEL_GENERATOR,
                overvoltage));
    }

    private static GridState state(double currentAmps, FaultKind fault) {
        return new GridState(true, true, EnergyTier.LV, WiringMode.THREE_PHASE_4_WIRE,
                0L, currentAmps, fault);
    }
}
