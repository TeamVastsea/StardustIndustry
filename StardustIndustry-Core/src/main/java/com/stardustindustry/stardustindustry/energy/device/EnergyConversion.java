package com.stardustindustry.stardustindustry.energy.device;

/** Whole-joule conversion helpers used at exactly one device boundary. */
final class EnergyConversion {
    private EnergyConversion() {}

    static long outputForInput(long input, double efficiency) {
        if (input <= 0L || efficiency <= 0.0) return 0L;
        double output = input * Math.min(1.0, efficiency);
        return output >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(output);
    }

    static long inputForOutput(long output, double efficiency) {
        if (output <= 0L || efficiency <= 0.0) return 0L;
        double input = output / Math.min(1.0, efficiency);
        return input >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(input);
    }
}
