package com.stardustindustry.stardustindustry.energy;

/**
 * Where a terminal sits inside its mount, and where its cable foot is in world
 * space.
 *
 * <h2>Why this is not just an {@code int} cell</h2>
 * <p>A cell index means nothing without the rack it belongs to: cell 5 of one
 * mount is a completely different place from cell 5 of another, and the same
 * cell looks different on a wall, a floor and a ceiling. A cable endpoint is only
 * meaningful as "<b>this cell of that rack</b>", so the pair travels together.</p>
 *
 * <p>The record is the address of one end of a cable. Two equal endpoints mean
 * the same terminal, which is what lets the connection store be keyed by a pair
 * without worrying about which side of the cable asked.</p>
 *
 * <h2>Geometry lives in {@link TerminalGeometry}</h2>
 * <p>Turning an endpoint into a world position is the one piece of maths shared
 * by the renderer, the interaction code and (later) the power grid. It is kept
 * out of this record so the record stays a pure value: serialisable, comparable,
 * and free of any dependency on a loaded level.</p>
 *
 * @param pos  the block position of the terminal mount
 * @param cell the grid cell the terminal is bolted to, in the rack's own frame
 */
public record TerminalEndpoint(net.minecraft.core.BlockPos pos, int cell) implements Comparable<TerminalEndpoint> {

    /**
     * Orders endpoints by position then cell.
     *
     * <p>A total order is needed to key a cable's two ends by its smaller
     * endpoint first. Without that, "A to B" and "B to A" would be two different
     * connections, and pulling the same cable twice from opposite directions
     * would quietly create a duplicate (§6.3.5: one terminal, one cable).</p>
     */
    @Override
    public int compareTo(TerminalEndpoint other) {
        int byX = Integer.compare(pos.getX(), other.pos.getX());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(pos.getY(), other.pos.getY());
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(pos.getZ(), other.pos.getZ());
        if (byZ != 0) {
            return byZ;
        }
        return Integer.compare(cell, other.cell);
    }

    /** True when this endpoint lies inside the same block as {@code other}. */
    public boolean sameBlock(TerminalEndpoint other) {
        return pos.equals(other.pos);
    }
}
