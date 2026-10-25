package com.stardustindustry.stardustindustry.energy;

import java.util.Locale;

/** Exact unit conversions and the DC-equivalent equations used by the grid solver. */
public final class ElectricalUnits {
    public static final int TICKS_PER_SECOND = 20;
    public static final long JOULES_PER_KILOWATT_HOUR = 3_600_000L;

    private ElectricalUnits() {}

    public static double currentAmps(long powerWatts, int volts) {
        if (powerWatts <= 0 || volts <= 0) {
            return 0.0;
        }
        return powerWatts / (double) volts;
    }

    public static long powerWatts(int volts, double currentAmps) {
        if (volts <= 0 || currentAmps <= 0.0 || !Double.isFinite(currentAmps)) {
            return 0L;
        }
        double watts = volts * currentAmps;
        return watts >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(watts);
    }

    /** Ohm's law for one conductor: the voltage dropped across {@code resistanceOhms}. */
    public static double voltageDropVolts(double currentAmps, double resistanceOhms) {
        if (currentAmps <= 0.0 || resistanceOhms <= 0.0) {
            return 0.0;
        }
        return currentAmps * resistanceOhms;
    }

    /** Joule loss for one conductor, expressed as {@code I * U_drop}. */
    public static double resistiveLossWatts(double currentAmps, double resistanceOhms) {
        if (currentAmps <= 0.0) {
            return 0.0;
        }
        return currentAmps * voltageDropVolts(currentAmps, resistanceOhms);
    }

    public static double lineLossWatts(double currentAmps, double ohmsPerConductor,
                                       WiringMode wiring) {
        return resistiveLossWatts(currentAmps, ohmsPerConductor) * wiring.requiredConductors();
    }

    public static long kilowattHoursToJoules(long kilowattHours) {
        return Math.multiplyExact(kilowattHours, JOULES_PER_KILOWATT_HOUR);
    }

    public static double joulesToKilowattHours(long joules) {
        return joules / (double) JOULES_PER_KILOWATT_HOUR;
    }

    /** Maximum continuous watts representable by this many whole joules in one game tick. */
    public static long wattsFromJoulesPerTick(long joules) {
        long safeJoules = Math.max(0L, joules);
        return safeJoules > Long.MAX_VALUE / TICKS_PER_SECOND
                ? Long.MAX_VALUE : safeJoules * TICKS_PER_SECOND;
    }

    public static String formatKilowattHours(long joules) {
        double kwh = joulesToKilowattHours(joules);
        if (kwh >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2f GWh", kwh / 1_000_000.0);
        }
        if (kwh >= 1_000.0) {
            return String.format(Locale.ROOT, "%.2f MWh", kwh / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.2f kWh", kwh);
    }

    /** Converts continuous power to whole joules without losing sub-tick remainders. */
    public static TickEnergy tickEnergy(long powerWatts, long remainderWattTicks) {
        long watts = Math.max(0L, powerWatts);
        long remainder = Math.max(0L, remainderWattTicks);
        long subJouleWattTicks = watts % TICKS_PER_SECOND + remainder % TICKS_PER_SECOND;
        return new TickEnergy(watts / TICKS_PER_SECOND + remainder / TICKS_PER_SECOND
                + subJouleWattTicks / TICKS_PER_SECOND,
                subJouleWattTicks % TICKS_PER_SECOND);
    }

    public record TickEnergy(long joules, long remainderWattTicks) {}
}
