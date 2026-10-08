package com.stardustindustry.stardustindustry.machine.tank;

import net.minecraft.world.level.block.Block;

/**
 * A tank frame block: the twelve edges of the tank's box.
 *
 * <p>A frame carries no tier and no behaviour. It exists so the structure
 * evaluator can tell "this cell is an edge, and an edge must be frame" from any
 * other cell, and so the player has a distinct, stronger-looking material for
 * the ribs of the container.</p>
 */
public class TankFrameBlock extends Block {

    public TankFrameBlock(Properties properties) {
        super(properties);
    }
}
