package com.stardustindustry.stardustindustry.machine.tank;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tank glass block: a transparent stand-in for a shell panel.
 *
 * <p>It is structurally identical to {@link TankShellBlock} — the evaluator
 * accepts it in the same face positions — but it is transparent, so the player
 * can watch the fluid level inside. The two are separate blocks only so they can
 * have different models and recipes.</p>
 *
 * <h2>No internal faces</h2>
 * Because the block does not occlude, Minecraft would otherwise draw the
 * touching faces of two adjacent panes. Those coincident faces are what produced
 * a bright grid of seams across a glass wall: at every block boundary two
 * translucent quads overlap and the blend line shows. {@link #skipRendering}
 * suppresses a face when the neighbour is the same glass, so the wall renders as
 * a single pane from either side.
 *
 * <p>This is the only connected-texture behaviour the base mod keeps, because it
 * removes a rendering artefact rather than adding art. Full connected textures
 * are a separate add-on mod (see {@code docs/connected-textures-plan.md}).
 */
public class TankGlassBlock extends Block {

    public TankGlassBlock(Properties properties) {
        super(properties);
    }

    /**
     * Skips a face that touches another pane of the same glass.
     *
     * <p>Without this the two coincident faces of neighbouring panes both draw,
     * and their translucent blend shows up as a seam line across the wall. Any
     * other neighbour (including the shell) still draws normally, so the window
     * keeps a clean boundary against the steel it is set into.</p>
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
    }
}
