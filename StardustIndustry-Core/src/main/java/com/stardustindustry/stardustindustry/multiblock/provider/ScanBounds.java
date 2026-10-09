package com.stardustindustry.stardustindustry.multiblock.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The axis-aligned box a dynamic machine occupies, as found by the scanner.
 *
 * <p>All coordinates are inclusive. The box is derived, never authored: the
 * scanner walks out from the controller along each axis until it meets the
 * shell, so a tank's size is whatever the player actually built.</p>
 *
 * @param min the lowest corner, inclusive
 * @param max the highest corner, inclusive
 */
public record ScanBounds(BlockPos min, BlockPos max) {

    /** The size along one axis, in blocks. */
    public int size(Direction.Axis axis) {
        return switch (axis) {
            case X -> max.getX() - min.getX() + 1;
            case Y -> max.getY() - min.getY() + 1;
            case Z -> max.getZ() - min.getZ() + 1;
        };
    }

    /** True when {@code pos} lies inside the box, edges included. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * How many axes {@code pos} is pinned to an edge on.
     *
     * <p>0 means interior, 1 means a face, 2 means an edge of the box and 3 a
     * corner. The dynamic rules map these to interior/face/frame directly, so
     * the count is the one number the validator needs.</p>
     */
    public int edgesTouched(BlockPos pos) {
        int count = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) {
            count++;
        }
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) {
            count++;
        }
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) {
            count++;
        }
        return count;
    }
}
