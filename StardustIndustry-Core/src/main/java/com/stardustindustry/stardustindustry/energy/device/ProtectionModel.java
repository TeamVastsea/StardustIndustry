package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.GridState;
import com.stardustindustry.stardustindustry.energy.WiringMode;

/** Stateless trip policy shared by breakers, fuse boxes, and grid-routing tests. */
public final class ProtectionModel {
    private ProtectionModel() {}

    public static boolean shouldTrip(ElectricalDeviceKind kind, GridState state) {
        if (!kind.protection()) {
            return false;
        }
        FaultKind fault = state.fault();
        boolean overloaded = fault == FaultKind.OVERCURRENT
                || state.currentAmps() > tripCurrentAmps(kind);
        return overloaded || kind.breaker()
                && (fault == FaultKind.OVERVOLTAGE || fault == FaultKind.MISSING_PHASE);
    }

    /** Lets protection observe and interrupt an overload instead of capping it at nameplate current. */
    public static long maximumThroughputWatts(ElectricalDeviceKind kind) {
        long rated = kind.ratedPowerWatts();
        if (!kind.protection()) {
            return rated;
        }
        return rated > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : rated * 2L;
    }

    private static double tripCurrentAmps(ElectricalDeviceKind kind) {
        WiringMode wiring = kind.lowTier() == EnergyTier.LV
                ? WiringMode.THREE_PHASE_4_WIRE
                : WiringMode.THREE_PHASE_3_WIRE;
        return wiring.lineCurrentAmps(kind.ratedPowerWatts(), kind.lowTier());
    }

    /** Faults an overload-only fuse cannot clear must remain visible to upstream protection. */
    public static boolean shouldRelayUpstream(ElectricalDeviceKind kind, GridState state) {
        FaultKind fault = state.fault();
        return kind.fuseBox()
                && (fault == FaultKind.OVERVOLTAGE || fault == FaultKind.MISSING_PHASE);
    }
}
