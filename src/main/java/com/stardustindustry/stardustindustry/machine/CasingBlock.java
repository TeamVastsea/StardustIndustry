package com.stardustindustry.stardustindustry.machine;

import net.minecraft.world.level.block.Block;

/**
 * A structural casing block for multiblocks.
 *
 * <p>Casing is inert: it holds no block entity and does nothing on its own. Its
 * only purpose is to satisfy the {@code CASING} positions of a
 * {@link com.stardustindustry.stardustindustry.multiblock.StructureDefinition},
 * which is what lets a player enclose a machine with a plain, cheap material.
 * Because structure parts match against a predicate rather than a fixed block,
 * a casing block can be one of several registered materials once more are
 * added.</p>
 */
public class CasingBlock extends Block {

    public CasingBlock(Properties properties) {
        super(properties);
    }
}
