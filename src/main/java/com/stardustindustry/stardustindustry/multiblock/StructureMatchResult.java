package com.stardustindustry.stardustindustry.multiblock;

import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * The outcome of testing a {@link StructureDefinition} against the world.
 *
 * <p>A result is either a complete match, or a report of exactly which
 * positions are wrong and what the player should place there. The second case
 * is what drives the structure projector: the controller can ask for the first
 * failing part and render a ghost block at that world position.</p>
 *
 * @param matched     true when every required part was satisfied
 * @param controller  world position of the controller block
 * @param placement   mapping from authored offset to the world position it maps to
 * @param failures    the parts that did not match, empty on success
 */
public record StructureMatchResult(
        boolean matched,
        BlockPos controller,
        Map<BlockPos, BlockPos> placement,
        java.util.List<StructureFailure> failures) {

    public boolean failed() {
        return !matched;
    }

    /** The first position the player needs to fix, or {@code null} when matched. */
    public StructureFailure firstFailure() {
        return failures.isEmpty() ? null : failures.get(0);
    }

    /** A world position that the definition expects, translated by this match. */
    public BlockPos worldPos(BlockPos offset) {
        BlockPos mapped = placement.get(offset);
        return mapped != null ? mapped : controller.offset(offset);
    }

    /**
     * One position that did not satisfy its expected part.
     *
     * @param offset    authored offset relative to the controller
     * @param worldPos  the world position that failed
     * @param role      the role the definition expected there
     * @param expected  a short human-readable description of what was expected
     */
    public record StructureFailure(BlockPos offset, BlockPos worldPos, PartRole role, String expected) {}
}
