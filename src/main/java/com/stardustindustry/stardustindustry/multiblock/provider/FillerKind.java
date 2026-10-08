package com.stardustindustry.stardustindustry.multiblock.provider;

import com.stardustindustry.stardustindustry.multiblock.BlockRole;

/**
 * The identity of an engineering block (filler), independent of its block.
 *
 * <p>Fillers are counted by <em>kind</em>, not by block state, so several
 * visually different blocks (one per tier, say) can share the same modifier
 * contribution. A filler registry (added with the modifier system) maps world
 * blocks to a kind and a kind to its
 * {@link com.stardustindustry.stardustindustry.multiblock.modifier.FillerModifier}.</p>
 *
 * <p>The set is deliberately small and stable; new fillers are added here as
 * they are designed. Each kind carries a human-readable description used by the
 * machine parameter screen.</p>
 */
public enum FillerKind {
    /** Raises processing speed at the cost of a larger energy draw. */
    GRINDING_CORE("grinding_core", "speed +100%, energy +120%"),

    /** Lowers the energy draw of every recipe. */
    HEAT_EXCHANGER_CORE("heat_exchanger_core", "energy -30%"),

    /** Adds parallel recipe slots at the cost of speed and energy. */
    PARALLEL_CORE("parallel_core", "parallel +1, speed -40%, energy +50%"),

    /** Enlarges internal buffers. */
    BUFFER_CORE("buffer_core", "buffer +50%");

    private final String id;
    private final String summary;

    FillerKind(String id, String summary) {
        this.id = id;
        this.summary = summary;
    }

    /** Stable identifier, used for translation keys and serialisation. */
    public String id() {
        return id;
    }

    /** Short human-readable summary of the trade this filler offers. */
    public String summary() {
        return summary;
    }

    /** The role a world position has when it holds a filler of this kind. */
    public BlockRole role() {
        return BlockRole.FILLER;
    }
}
