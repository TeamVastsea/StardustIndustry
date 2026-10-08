package com.stardustindustry.stardustindustry.multiblock.model;

/**
 * What a position in a {@link StructureModel} expects.
 *
 * <p>A static machine is authored cell by cell. Most cells are a fixed block
 * ({@link #FIXED}); the bottom layer is made of free {@link #BASE_SLOT} cells
 * the player fills with a port, a filler or a base block; and one cell is the
 * {@link #CONTROLLER}.</p>
 */
public enum StructureSlotType {
    /** A fixed body block: the matcher must accept the world block here. */
    FIXED,

    /**
     * A free base-layer cell. It must hold a port, a filler or a level-bearing
     * base block, and may not be left empty. Which of the three is placed
     * decides the cell's {@code BlockRole}.
     */
    BASE_SLOT,

    /** The controller block; owns the block entity and the logic. */
    CONTROLLER
}
