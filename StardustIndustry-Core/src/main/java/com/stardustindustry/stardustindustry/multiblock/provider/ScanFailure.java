package com.stardustindustry.stardustindustry.multiblock.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * One position a structure provider expected to be built differently.
 *
 * <p>A failure is the unit of player feedback: it names the world position, the
 * role the structure wanted there, and a short description of what was expected.
 * The projector turns a list of these into ghost blocks, and the installation
 * tool lists them as text.</p>
 *
 * <p>The expectation is stored as a translation key plus arguments rather than a
 * finished sentence, so the same scan reports in whatever language the client
 * uses. {@link #expectationComponent()} is what callers show to a player.</p>
 *
 * @param worldPos    the position that needs attention, in world coordinates
 * @param role        the role the structure expected at that position
 * @param expectation a translation key describing what should be placed
 * @param args        the arguments that key needs, if any
 */
public record ScanFailure(BlockPos worldPos, com.stardustindustry.stardustindustry.multiblock.BlockRole role,
                          String expectation, Object... args) {

    /** The expectation as a translatable component, ready to show a player. */
    public Component expectationComponent() {
        return Component.translatable(expectation, args);
    }
}
