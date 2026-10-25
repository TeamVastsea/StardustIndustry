package com.stardustindustry.stardustindustry.energy;

/** Electrical conditions exposed to devices and protection equipment. */
public enum FaultKind {
    NONE,
    OPEN_SWITCH,
    INCOMPLETE_WIRING,
    MISSING_PHASE,
    UNDERVOLTAGE,
    OVERVOLTAGE,
    OVERCURRENT,
    MULTIPLE_GRID_SOURCES,
    UNSUPPORTED_TOPOLOGY
}
