package com.stardustindustry.stardustindustry.multiblock.modifier;

/**
 * The contribution a single engineering block (filler) makes to a machine.
 *
 * <p>Fillers trade one property for another rather than handing out free power:
 * a grinding core raises speed but raises energy draw more, a heat exchanger
 * lowers energy draw but adds nothing else, a parallel core buys throughput by
 * giving up speed. Expressing the trade as deltas on a
 * {@link ModifierSet} keeps the balance in one place and lets a data-driven
 * registry describe new fillers without new code.</p>
 *
 * <p>All deltas are relative additions: {@code speedDelta = 1.0f} means
 * "+100% speed"; deltas are added onto a base of {@code 1.0f} for multipliers
 * and {@code 0} for {@code parallelDelta}.</p>
 *
 * @param speedDelta    added to the speed multiplier
 * @param energyDelta   added to the energy multiplier
 * @param parallelDelta added to the parallel bonus
 * @param bufferDelta   added to the buffer multiplier
 */
public record FillerModifier(
        float speedDelta,
        float energyDelta,
        int parallelDelta,
        float bufferDelta) {

    /** A filler that changes nothing; useful as a placeholder or default. */
    public static final FillerModifier NONE = new FillerModifier(0.0f, 0.0f, 0, 0.0f);

    /** Convenience factory for a pure speed/energy trade. */
    public static FillerModifier speedTrade(float speedDelta, float energyDelta) {
        return new FillerModifier(speedDelta, energyDelta, 0, 0.0f);
    }

    /** Convenience factory for a pure energy-saving filler. */
    public static FillerModifier saving(float energyDelta) {
        return new FillerModifier(0.0f, energyDelta, 0, 0.0f);
    }

    /** Convenience factory for a pure parallelism filler. */
    public static FillerModifier parallel(int parallelDelta, float speedDelta, float energyDelta) {
        return new FillerModifier(speedDelta, energyDelta, parallelDelta, 0.0f);
    }

    /** Convenience factory for a pure buffer filler. */
    public static FillerModifier buffer(float bufferDelta) {
        return new FillerModifier(0.0f, 0.0f, 0, bufferDelta);
    }
}
