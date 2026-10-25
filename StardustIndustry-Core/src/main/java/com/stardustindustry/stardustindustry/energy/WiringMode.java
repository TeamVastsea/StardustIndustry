package com.stardustindustry.stardustindustry.energy;

/** Supported balanced conductor arrangements. Phase angle and reactive power are not simulated. */
public enum WiringMode {
    SINGLE_PHASE_2_WIRE("single_phase_2_wire", 2, 1, 1, false),
    THREE_PHASE_3_WIRE("three_phase_3_wire", 3, 3, 0, true),
    THREE_PHASE_4_WIRE("three_phase_4_wire", 4, 3, 1, true);

    private final String id;
    private final int conductors;
    private final int phaseConductors;
    private final int neutralConductors;
    private final boolean threePhase;

    WiringMode(String id, int conductors, int phaseConductors, int neutralConductors,
               boolean threePhase) {
        this.id = id;
        this.conductors = conductors;
        this.phaseConductors = phaseConductors;
        this.neutralConductors = neutralConductors;
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

    public int phaseConductors() {
        return phaseConductors;
    }

    public int neutralConductors() {
        return neutralConductors;
    }

    /** Conductors carrying current in the balanced model. */
    public int currentCarryingConductors() {
        return threePhase ? phaseConductors : conductors;
    }

    public int nominalVolts(EnergyTier tier) {
        return threePhase ? tier.volts() : tier.singlePhaseVolts();
    }

    /** RMS line current for the supplied real power at unity power factor. */
    public double lineCurrentAmps(long powerWatts, EnergyTier tier) {
        return ElectricalUnits.currentAmps(powerWatts, nominalVolts(tier), this);
    }

    /** Real power represented by an RMS line current at unity power factor. */
    public long powerWatts(double lineCurrentAmps, EnergyTier tier) {
        return ElectricalUnits.powerWatts(nominalVolts(tier), lineCurrentAmps, this);
    }

    /** Current in one deterministic conductor slot; the balanced neutral is slot 3. */
    public double conductorCurrentAmps(long powerWatts, EnergyTier tier, int conductorIndex) {
        if (conductorIndex < 0 || conductorIndex >= conductors) {
            throw new IndexOutOfBoundsException("conductor " + conductorIndex + " of " + conductors);
        }
        if (this == THREE_PHASE_4_WIRE && conductorIndex == 3) {
            return 0.0;
        }
        return lineCurrentAmps(powerWatts, tier);
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
