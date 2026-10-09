package com.stardustindustry.stardustindustry.multiblock;

/**
 * The role a position plays inside a multiblock structure.
 *
 * <p>The role decides how a matched position behaves once the structure forms:
 * whether it is solid casing, a port that exposes a capability, the controller
 * the player right-clicks, or an internal slot machines may write into. The
 * structure matcher reports which expected role each world position satisfies,
 * and the controller uses that report to wire up its capabilities.</p>
 */
public enum PartRole {
    /** An inert structural block. Contributes shape only. */
    CASING,

    /** The block the player interacts with; owns the block entity and logic. */
    CONTROLLER,

    /** A position that must be an item port; exposes an item handler. */
    PORT_ITEM,

    /** A position that must be a fluid port; exposes a fluid handler. */
    PORT_FLUID,

    /** A position that must be a gas port; exposes a gas handler. */
    PORT_GAS,

    /** A position that must be an energy port; exposes an energy storage. */
    PORT_ENERGY,

    /**
     * A position that may be any registered port type. The matcher accepts an
     * item, fluid or energy port here, and the formed structure exposes whatever
     * capability the placed block provides.
     */
    PORT_ANY,

    /**
     * A structural position that is not required to exist, but is filled in by
     * the structure projector so the player knows what to build.
     */
    OPTIONAL_CASING,

    /**
     * A free base-layer cell of a static machine. The cell must hold one of a
     * port, a filler, or a level-bearing base block, and may not be left empty.
     * Which of the three is placed decides the cell's observed
     * {@link BlockRole}.
     */
    BASE_SLOT;

    /** True when this role accepts one of the port blocks. */
    public boolean isPort() {
        return this == PORT_ITEM || this == PORT_FLUID || this == PORT_GAS
                || this == PORT_ENERGY || this == PORT_ANY;
    }

    /** True when this role is a free base cell rather than a fixed block. */
    public boolean isBaseSlot() {
        return this == BASE_SLOT;
    }
}
