package com.stardustindustry.stardustindustry.multiblock;

/**
 * The function a single world position serves inside an evaluated structure.
 *
 * <p>This is the <em>observed</em> role of a position, as opposed to
 * {@link PartRole}, which is the <em>authored</em> expectation in a static
 * definition. A dynamic structure has no authored layout, so the scanner
 * derives a {@code BlockRole} for every cell it examines. Both static and
 * dynamic providers report their result using this type, which is what lets the
 * machine layer, the projector and the user interface treat them uniformly.</p>
 */
public enum BlockRole {
    /** A structural edge/rib of a dynamic box, or a fixed body block of a static machine. */
    FRAME,

    /** A face block of a dynamic box (a shell panel), or a fixed casing in a static machine. */
    PANEL,

    /** A free base-layer cell of a static machine: port, filler or base block. */
    BASE_SLOT,

    /** The controller block; owns the block entity and the logic. */
    CONTROLLER,

    /** A functional port (item, fluid, energy) exposing a capability. */
    PORT,

    /** A control port exposing configurable signal channels. */
    CONTROL_PORT,

    /** An engineering block placed for its modifier contribution. */
    FILLER,

    /** A default base block filling a slot that has neither port nor filler. */
    BASE,

    /** An interior cell of a dynamic box: air or a filler. */
    INTERIOR
}
