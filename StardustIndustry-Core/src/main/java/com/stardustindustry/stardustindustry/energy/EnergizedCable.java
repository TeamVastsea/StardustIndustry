package com.stardustindustry.stardustindustry.energy;

import java.util.Objects;

/** One physical cable and its live electrical measurements from the latest grid solve. */
public record EnergizedCable(CableConnection cable, int volts, double currentAmps,
                             double voltageDropVolts, long lineLossWatts) {
    public EnergizedCable {
        Objects.requireNonNull(cable, "cable");
        volts = Math.max(0, volts);
        currentAmps = Double.isFinite(currentAmps) ? Math.max(0.0, currentAmps) : 0.0;
        voltageDropVolts = Double.isFinite(voltageDropVolts)
                ? Math.max(0.0, voltageDropVolts) : 0.0;
        lineLossWatts = Math.max(0L, lineLossWatts);
    }

    public EnergizedCable(CableConnection cable, int volts) {
        this(cable, volts, 0.0, 0.0, 0L);
    }
}
