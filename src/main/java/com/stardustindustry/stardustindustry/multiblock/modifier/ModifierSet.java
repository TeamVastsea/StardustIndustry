package com.stardustindustry.stardustindustry.multiblock.modifier;

/**
 * The final, structure-derived multipliers a machine runs with.
 *
 * <p>A {@code ModifierSet} is the sum of every engineering block (filler)
 * contribution found in a machine structure, expressed as a small, fixed set of
 * numbers. It is deliberately independent of the machine's voltage tier: the
 * tier decides the <em>ceiling</em> (transfer rate, buffer base, idle draw)
 * while this record decides the <em>relative</em> multipliers
 * (speed, energy, parallel, buffer). The two are multiplied together where a
 * machine actually uses them.</p>
 *
 * <p>Values are multipliers, not percentages: {@code 2.0f} means "double".
 * A filler that raises speed by 100% and energy by 120% contributes
 * {@code speedDelta = 1.0f} and {@code energyDelta = 1.2f}, and the set adds
 * those deltas onto the base of {@code 1.0f}.</p>
 *
 * @param speedMultiplier  progress-rate multiplier, {@code >= 0}
 * @param energyMultiplier FE-per-tick cost multiplier, {@code >= 0}
 * @param parallelBonus    extra recipe slots on top of the machine's own
 * @param bufferMultiplier multiplier applied to internal buffer capacities
 */
public record ModifierSet(
        float speedMultiplier,
        float energyMultiplier,
        int parallelBonus,
        float bufferMultiplier) {

    /** The neutral set: no fillers installed, machine runs at its own defaults. */
    public static final ModifierSet BASE = new ModifierSet(1.0f, 1.0f, 0, 1.0f);

    /** Recommended ceilings, so a wall of one filler type cannot run away. */
    public static final float MAX_SPEED_MULTIPLIER = 5.0f;
    public static final float MIN_ENERGY_MULTIPLIER = 0.2f;
    public static final int MAX_PARALLEL_BONUS = 8;

    public ModifierSet {
        speedMultiplier = clamp(speedMultiplier, 0.0f, MAX_SPEED_MULTIPLIER);
        energyMultiplier = clamp(energyMultiplier, MIN_ENERGY_MULTIPLIER, Float.MAX_VALUE);
        parallelBonus = Math.max(0, Math.min(parallelBonus, MAX_PARALLEL_BONUS));
        bufferMultiplier = Math.max(0.0f, bufferMultiplier);
    }

    /** Adds a filler's deltas onto this set, returning a new clamped set. */
    public ModifierSet plus(FillerModifier filler) {
        return new ModifierSet(
                speedMultiplier + filler.speedDelta(),
                energyMultiplier + filler.energyDelta(),
                parallelBonus + filler.parallelDelta(),
                bufferMultiplier + filler.bufferDelta());
    }

    /** Adds several fillers, one after another. */
    public ModifierSet plusAll(Iterable<FillerModifier> fillers) {
        ModifierSet result = this;
        for (FillerModifier filler : fillers) {
            result = result.plus(filler);
        }
        return result;
    }

    /** Applies this set's speed multiplier to a base processing time, in ticks. */
    public int effectiveProcessingTime(int baseTicks) {
        return Math.max(1, Math.round(baseTicks / speedMultiplier));
    }

    /** Applies this set's energy multiplier to a base energy cost, in FE. */
    public int effectiveEnergyCost(int baseCost) {
        return Math.max(1, Math.round(baseCost * energyMultiplier));
    }

    /** Applies this set's buffer multiplier to a base capacity. */
    public int effectiveBuffer(int baseCapacity) {
        return Math.max(1, Math.round(baseCapacity * bufferMultiplier));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
