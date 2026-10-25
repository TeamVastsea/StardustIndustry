package com.stardustindustry.stardustindustry.energy;

/** Pure overload/cooldown curve used by the grid solver and unit tests. */
public final class CableThermalModel {
    private static final double FAILURE_EPSILON = 1.0e-12;
    private static final int FULL_COOLDOWN_TICKS = 30 * ElectricalUnits.TICKS_PER_SECOND;
    public static final int DESTRUCTIVE_CONFIRMATION_TICKS = 40;

    private CableThermalModel() {}

    public static double nextHeat(double currentHeat, double currentRatio) {
        double heat = Math.max(0.0, Math.min(1.0, currentHeat));
        if (currentRatio < 1.2) {
            return Math.max(0.0, heat - 1.0 / (30.0 * ElectricalUnits.TICKS_PER_SECOND));
        }
        double secondsToFailure = currentRatio >= 5.0 ? 3.0 : currentRatio >= 2.0 ? 15.0 : 60.0;
        double next = heat + 1.0 / (secondsToFailure * ElectricalUnits.TICKS_PER_SECOND);
        return next >= 1.0 - FAILURE_EPSILON ? 1.0 : next;
    }

    public static boolean failed(double heat) {
        return heat >= 1.0;
    }

    /** Cools an inactive cable for several ticks without iterating tick by tick. */
    public static State coolForTicks(State current, long ticks) {
        if (ticks <= 0L || current.cold()) {
            return current;
        }
        double cooledHeat = Math.max(0.0,
                current.heat() - Math.min(ticks, FULL_COOLDOWN_TICKS) / (double) FULL_COOLDOWN_TICKS);
        return new State(cooledHeat, 0);
    }

    /** Advances heat and the independent continuous-overload confirmation gate. */
    public static State nextState(State current, double currentRatio) {
        boolean overloaded = currentRatio >= 1.2;
        int confirmedTicks = overloaded
                ? Math.min(DESTRUCTIVE_CONFIRMATION_TICKS, current.continuousOverloadTicks() + 1)
                : 0;
        return new State(nextHeat(current.heat(), currentRatio), confirmedTicks);
    }

    public static boolean failed(State state) {
        return failed(state.heat())
                && state.continuousOverloadTicks() >= DESTRUCTIVE_CONFIRMATION_TICKS;
    }

    public record State(double heat, int continuousOverloadTicks) {
        public static final State COLD = new State(0.0, 0);

        public State {
            heat = Math.max(0.0, Math.min(1.0, heat));
            continuousOverloadTicks = Math.max(0,
                    Math.min(DESTRUCTIVE_CONFIRMATION_TICKS, continuousOverloadTicks));
        }

        public boolean cold() {
            return heat == 0.0 && continuousOverloadTicks == 0;
        }
    }
}
