package com.stardustindustry.stardustindustry.energy;

/** Last server-side result delivered to one native electrical port. */
public record GridState(
        boolean energized,
        boolean sourceSwitchClosed,
        EnergyTier supplyTier,
        WiringMode wiring,
        long deliveredPowerWatts,
        double currentAmps,
        FaultKind fault) {

    public static GridState offline(WiringMode wiring, FaultKind fault, boolean sourceSwitchClosed) {
        return new GridState(false, sourceSwitchClosed, null, wiring, 0L, 0.0, fault);
    }
}
