package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.GridState;

/** Stateless trip policy shared by breakers, fuse boxes, and grid-routing tests. */
public final class ProtectionModel {
    private ProtectionModel() {}

    public static boolean shouldTrip(ElectricalDeviceKind kind, GridState state) {
        if (!kind.protection()) {
            return false;
        }
        FaultKind fault = state.fault();
        boolean overloaded = fault == FaultKind.OVERCURRENT
                || state.currentAmps() > kind.ratedCurrentAmps();
        return overloaded || kind.breaker()
                && (fault == FaultKind.OVERVOLTAGE || fault == FaultKind.MISSING_PHASE);
    }

    /** Faults an overload-only fuse cannot clear must remain visible to upstream protection. */
    public static boolean shouldRelayUpstream(ElectricalDeviceKind kind, GridState state) {
        FaultKind fault = state.fault();
        return kind.fuseBox()
                && (fault == FaultKind.OVERVOLTAGE || fault == FaultKind.MISSING_PHASE);
    }
}
