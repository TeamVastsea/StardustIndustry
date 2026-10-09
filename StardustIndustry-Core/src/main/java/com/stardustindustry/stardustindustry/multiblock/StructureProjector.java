package com.stardustindustry.stardustindustry.multiblock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Turns a failed {@link StructureMatchResult} into a list of ghost blocks the
 * client should draw, so the player can see exactly what the structure needs.
 *
 * <p>Projection is a pure function of a match result, so it is equally usable
 * from the controller's "show me the build" keybind and from a datapack tool.
 * Rendering is left to the client layer; this class only produces data.</p>
 */
public final class StructureProjector {

    private StructureProjector() {}

    /**
     * One ghost block to render.
     *
     * @param worldPos    where the missing block belongs
     * @param role        what kind of block it should be
     * @param description short human-readable label, e.g. "structural casing"
     */
    public record GhostBlock(BlockPos worldPos, PartRole role, String description) {}

    /**
     * Every position that still needs attention. Empty when the structure is
     * complete, which lets callers treat an empty list as "formed".
     */
    public static List<GhostBlock> project(StructureMatchResult result) {
        List<GhostBlock> ghosts = new ArrayList<>(result.failures().size());
        for (StructureMatchResult.StructureFailure failure : result.failures()) {
            ghosts.add(new GhostBlock(failure.worldPos(), failure.role(), failure.expected()));
        }
        return ghosts;
    }

    /**
     * Only the single next position to fix. Showing one highlighted block at a
     * time keeps the overlay readable on large machine shells.
     */
    public static GhostBlock nextGhost(StructureMatchResult result) {
        StructureMatchResult.StructureFailure failure = result.firstFailure();
        if (failure == null) {
            return null;
        }
        return new GhostBlock(failure.worldPos(), failure.role(), failure.expected());
    }
}
