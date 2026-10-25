package com.stardustindustry.stardustindustry.energy;

/** Supported conductor arrangements. Phase angle and power factor are intentionally not simulated. */
public enum WiringMode {
    SINGLE_PHASE_2_WIRE("single_phase_2_wire", 2, false),
    THREE_PHASE_3_WIRE("three_phase_3_wire", 3, true),
    THREE_PHASE_4_WIRE("three_phase_4_wire", 4, true);

    private final String id;
    private final int conductors;
    private final boolean threePhase;

    WiringMode(String id, int conductors, boolean threePhase) {
        this.id = id;
        this.conductors = conductors;
        this.threePhase = threePhase;
    }

    public String id() {
        return id;
    }

    public int requiredConductors() {
        return conductors;
    }

    public boolean threePhase() {
        return threePhase;
    }

    public int nominalVolts(EnergyTier tier) {
        return threePhase ? tier.volts() : tier.singlePhaseVolts();
    }

    public static WiringMode byId(String id) {
        for (WiringMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return SINGLE_PHASE_2_WIRE;
    }
}
