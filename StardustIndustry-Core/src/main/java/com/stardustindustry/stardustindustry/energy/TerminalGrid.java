package com.stardustindustry.stardustindustry.energy;

/**
 * How a terminal mount's grid is laid out, and how many cells it has.
 *
 * <p>A terminal mount is a rack that holds individual terminals on a regular
 * grid on one face. The grid shrinks as the voltage rises, because the pins get
 * physically bigger: a low-voltage rack packs 16 small pins, while a high-voltage
 * insulator post occupies a whole block on its own. See
 * {@code docs/electric-power-system-design.md} §6.3.</p>
 */
public enum TerminalGrid {
    /** 4 x 4 = 16 cells. Low voltage: 220 V / 380 V. */
    GRID_4x4(4, 4),
    /** 2 x 2 = 4 cells. Medium voltage: 10 kV. */
    GRID_2x2(2, 2),
    /** 1 x 1 = 1 cell. High voltage and above: 110 kV / 500 kV. */
    GRID_1x1(1, 1);

    private final int columns;
    private final int rows;

    TerminalGrid(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
    }

    /** Cells along the grid's horizontal axis. */
    public int columns() {
        return columns;
    }

    /** Cells along the grid's vertical axis. */
    public int rows() {
        return rows;
    }

    /** Total number of terminals the rack can hold. */
    public int cellCount() {
        return columns * rows;
    }

    /**
     * The grid an energy tier uses.
     *
     * <p>Kept on the tier rather than the block so that "how dense is this
     * tier's rack" is a property of the voltage ladder, not of whichever block
     * happens to implement it.</p>
     */
    public static TerminalGrid of(EnergyTier tier) {
        return switch (tier) {
            case LV -> GRID_4x4;
            case MV -> GRID_2x2;
            case HV, EHV -> GRID_1x1;
        };
    }

    /** True when {@code cell} is inside this grid. */
    public boolean contains(int cell) {
        return cell >= 0 && cell < cellCount();
    }

    /** The cell index of grid coordinates, or {@code -1} when outside the grid. */
    public int cellAt(int column, int row) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            return -1;
        }
        return row * columns + column;
    }

    /** Column of a cell index. */
    public int columnOf(int cell) {
        return cell % columns;
    }

    /** Row of a cell index. */
    public int rowOf(int cell) {
        return cell / columns;
    }
}
