package com.stardustindustry.stardustindustry.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/**
 * Rotates a structure offset to match the controller's horizontal facing.
 *
 * <p>Structures are authored assuming the controller faces north. When the
 * controller in the world faces another direction, every offset is rotated
 * around the vertical axis by the difference. Only the four horizontal
 * directions are supported, because machines never sit upside down.</p>
 */
public final class StructureRotation {

    private StructureRotation() {}

    /**
     * Rotation that maps the authored north-facing layout onto a controller
     * facing {@code facing}.
     */
    public static Rotation forFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            // Up/down controllers fall back to the unrotated layout.
            default -> Rotation.NONE;
        };
    }

    /** Applies a rotation to an offset around the origin. */
    public static BlockPos rotate(BlockPos offset, Rotation rotation) {
        return switch (rotation) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
        };
    }

    /** Rotation that turns the authored layout to match the placed controller. */
    public static Rotation between(Direction authored, Direction facing) {
        int steps = (stepsOf(facing) - stepsOf(authored) + 4) % 4;
        return switch (steps) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int stepsOf(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }
}
