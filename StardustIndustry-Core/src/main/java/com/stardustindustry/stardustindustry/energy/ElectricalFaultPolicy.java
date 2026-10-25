package com.stardustindustry.stardustindustry.energy;

/** Shared gate for destructive faults that must be both energized and continuously confirmed. */
public final class ElectricalFaultPolicy {
    private ElectricalFaultPolicy() {}

    public static boolean shouldConfirm(GridState state, FaultKind fault) {
        return state.energized() && state.sourceSwitchClosed() && state.fault() == fault;
    }
}
