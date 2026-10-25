package com.stardustindustry.stardustindustry.energy;

/** Last server-side result delivered to one native electrical port. */
public record GridState(
        boolean energized,
        boolean sourceSwitchClosed,
        EnergyTier supplyTier,
        WiringMode wiring,
        long deliveredPowerWatts,
        double currentAmps,
        FaultKind fault,
        long requestedPowerWatts,
        long netPowerWatts,
        long lineLossWatts,
        double efficiency) {

    /** Backwards-compatible constructor for fault paths that do not carry metering detail. */
    public GridState(boolean energized, boolean sourceSwitchClosed, EnergyTier supplyTier,
                     WiringMode wiring, long deliveredPowerWatts, double currentAmps,
                     FaultKind fault) {
        this(energized, sourceSwitchClosed, supplyTier, wiring, deliveredPowerWatts,
                currentAmps, fault, deliveredPowerWatts, deliveredPowerWatts, 0L,
                deliveredPowerWatts > 0L ? 1.0 : 0.0);
    }

    public GridState {
        wiring = wiring == null ? WiringMode.SINGLE_PHASE_2_WIRE : wiring;
        fault = fault == null ? FaultKind.NONE : fault;
        deliveredPowerWatts = Math.max(0L, deliveredPowerWatts);
        requestedPowerWatts = Math.max(0L, requestedPowerWatts);
        lineLossWatts = Math.max(0L, lineLossWatts);
        currentAmps = Double.isFinite(currentAmps) ? Math.max(0.0, currentAmps) : 0.0;
        efficiency = Double.isFinite(efficiency) ? Math.clamp(efficiency, 0.0, 1.0) : 0.0;
    }

    public static GridState offline(WiringMode wiring, FaultKind fault, boolean sourceSwitchClosed) {
        return new GridState(false, sourceSwitchClosed, null, wiring, 0L, 0.0,
                fault, 0L, 0L, 0L, 0.0);
    }
}
