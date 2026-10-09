package com.stardustindustry.stardustindustry.multiblock;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One authored position of a {@link StructureDefinition}.
 *
 * @param offset  position relative to the controller
 * @param role    behaviour this position plays when the structure forms
 * @param matcher accepts the world block state that may occupy this position
 */
public record StructurePart(BlockPos offset, PartRole role, Predicate<BlockState> matcher) {

    /** True when the block state at this position satisfies the part matcher. */
    public boolean matches(BlockState state) {
        return matcher.test(state);
    }
}
