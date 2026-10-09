package com.stardustindustry.stardustindustry.machine.tank;

import net.minecraft.world.level.block.Block;

/**
 * A tank frame block: the twelve edges of the tank's box.
 *
 * <p>A frame carries no tier and no behaviour. It exists so the structure
 * evaluator can tell "this cell is an edge, and an edge must be frame" from any
 * other cell, and so the player has a distinct, stronger-looking material for
 * the ribs of the container.</p>
 *
 * <p>The frame renders as an ordinary cube. An earlier attempt gave it connected
 * textures so a run of ribs would read as one beam; that is being handled by a
 * separate add-on mod instead (see {@code docs/connected-textures-plan.md}), so
 * the base mod keeps the plain block model.</p>
 */
public class TankFrameBlock extends Block {

    public TankFrameBlock(Properties properties) {
        super(properties);
    }
}
