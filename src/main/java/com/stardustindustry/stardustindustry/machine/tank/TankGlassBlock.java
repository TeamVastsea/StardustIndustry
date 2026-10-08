package com.stardustindustry.stardustindustry.machine.tank;

import net.minecraft.world.level.block.Block;

/**
 * A tank glass block: a transparent stand-in for a shell panel.
 *
 * <p>It is structurally identical to {@link TankShellBlock} — the evaluator
 * accepts it in the same face positions — but it is transparent, so the player
 * can watch the fluid level inside. The two are separate blocks only so they can
 * have different models and recipes.</p>
 */
public class TankGlassBlock extends Block {

    public TankGlassBlock(Properties properties) {
        super(properties);
    }
}
