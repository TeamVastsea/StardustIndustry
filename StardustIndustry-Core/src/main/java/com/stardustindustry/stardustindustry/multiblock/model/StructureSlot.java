package com.stardustindustry.stardustindustry.multiblock.model;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One authored cell of a {@link StructureModel}.
 *
 * <p>The offset is relative to the controller, in the model's authored
 * orientation (facing north). The matcher is only meaningful for
 * {@link StructureSlotType#FIXED}; base slots accept a family of blocks decided
 * at evaluation time, and the controller slot is matched against the model's
 * own controller block.</p>
 *
 * @param offset  position relative to the controller
 * @param type    what the cell expects
 * @param matcher accepts the world block state for a {@code FIXED} cell
 */
public record StructureSlot(BlockPos offset, StructureSlotType type, Predicate<BlockState> matcher) {

    /** True for a {@code FIXED} cell whose matcher accepts {@code state}. */
    public boolean matches(BlockState state) {
        return type == StructureSlotType.FIXED && matcher.test(state);
    }
}
