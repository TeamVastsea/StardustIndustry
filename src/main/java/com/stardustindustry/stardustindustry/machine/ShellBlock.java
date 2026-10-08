package com.stardustindustry.stardustindustry.machine;

import net.minecraft.world.level.block.Block;

/**
 * An inert face block: the six faces of a dynamic machine, minus the edges.
 *
 * <p>Shell blocks carry no tier and no modifier; they only close the container.
 * Several materials may be registered as shell so a player can pick a look or a
 * cheaper material without changing the machine's behaviour, which is why the
 * classifier keys on the {@code SHELL} part type rather than on this class.</p>
 */
public class ShellBlock extends Block {

    public ShellBlock(Properties properties) {
        super(properties);
    }
}
