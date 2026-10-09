package com.stardustindustry.stardustindustry.multiblock.modifier;

import java.util.EnumMap;
import java.util.Map;

import com.stardustindustry.stardustindustry.multiblock.provider.FillerKind;

/**
 * Central table of what each {@link FillerKind} contributes.
 *
 * <p>The modifier a filler applies is balance data, not structure logic, so it
 * lives here rather than inside the structure provider. Every kind registers its
 * {@link FillerModifier} exactly once during startup; a machine structure is
 * then evaluated purely in terms of this table, and rebalancing a filler is a
 * one-line change that touches no evaluation code.</p>
 *
 * <p>A kind with no registered modifier falls back to
 * {@link FillerModifier#NONE}, so a newly added enum constant is inert (rather
 * than crashing) until its numbers are decided.</p>
 */
public final class FillerRegistry {

    private static final Map<FillerKind, FillerModifier> MODIFIERS = new EnumMap<>(FillerKind.class);

    private FillerRegistry() {}

    /** Declares what {@code kind} contributes. Registering twice is a programming error. */
    public static void register(FillerKind kind, FillerModifier modifier) {
        FillerModifier previous = MODIFIERS.put(kind, modifier);
        if (previous != null) {
            throw new IllegalStateException("Filler kind " + kind + " already registered with " + previous);
        }
    }

    /** The contribution of {@code kind}, or {@link FillerModifier#NONE} when unregistered. */
    public static FillerModifier modifierOf(FillerKind kind) {
        return MODIFIERS.getOrDefault(kind, FillerModifier.NONE);
    }

    /** True when {@code kind} has explicit numbers. */
    public static boolean isRegistered(FillerKind kind) {
        return MODIFIERS.containsKey(kind);
    }

    /**
     * Registers the default contributions of every built-in filler kind.
     *
     * <p>Kept here rather than in a static initialiser so the numbers are an
     * explicit, reviewable list and the call happens at a known point during
     * common setup.</p>
     */
    public static void registerDefaults() {
        register(FillerKind.GRINDING_CORE, FillerModifier.speedTrade(1.0f, 1.2f));
        register(FillerKind.HEAT_EXCHANGER_CORE, FillerModifier.saving(-0.3f));
        register(FillerKind.PARALLEL_CORE, FillerModifier.parallel(1, -0.4f, 0.5f));
        register(FillerKind.BUFFER_CORE, FillerModifier.buffer(0.5f));
    }
}
