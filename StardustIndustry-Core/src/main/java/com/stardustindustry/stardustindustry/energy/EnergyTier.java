package com.stardustindustry.stardustindustry.energy;

import net.minecraft.util.StringRepresentable;

/** The four physical voltage levels used by the native electrical system. */
public enum EnergyTier implements StringRepresentable {
    /**
     * Low voltage: 380 V three-phase. 220 V single-phase is a wiring variant
     * inside this same tier, not a separate level.
     */
    LV("lv", 380, 220, 5_000),
    /** Medium voltage: 10 kV, the workhorse for transmission. */
    MV("mv", 10_000, 10_000, 20_000),
    /** High voltage: 110 kV, bulk transmission. */
    HV("hv", 110_000, 110_000, 80_000),
    /** Extreme high voltage: 500 kV, long-distance transmission / generation side. */
    EHV("ehv", 500_000, 500_000, 320_000);

    private final String name;
    private final int lineVolts;
    private final int singlePhaseVolts;
    private final int fluidTransfer;

    EnergyTier(String name, int lineVolts, int singlePhaseVolts, int fluidTransfer) {
        this.name = name;
        this.lineVolts = lineVolts;
        this.singlePhaseVolts = singlePhaseVolts;
        this.fluidTransfer = fluidTransfer;
    }

    /** Canonical lowercase name, used for translation keys and serialisation. */
    @Override
    public String getSerializedName() {
        return name;
    }

    /** Physical line-to-line voltage in volts. */
    public int volts() {
        return lineVolts;
    }

    /** Nominal line-to-neutral voltage for single-phase wiring. */
    public int singlePhaseVolts() {
        return singlePhaseVolts;
    }

    /**
     * The throughput, in mB/t, a fluid port of this tier is rated for.
     *
     * <p>Fluid rates follow their own ladder rather than the energy one: each
     * tier is four times the last, so a port upgrade is a clear step (5, 20, 80
     * and 320 buckets <em>per tick</em>). The numbers are stored on the tier
     * rather than derived from voltage because the two ladders are deliberately
     * independent — being four times faster is a fluid decision, not an
     * electrical one.</p>
     */
    public int fluidTransfer() {
        return fluidTransfer;
    }

    /** True when a connection of this tier can carry the {@code incoming} voltage. */
    public boolean accepts(EnergyTier incoming) {
        return incoming.lineVolts <= this.lineVolts;
    }

    /** Number of voltage levels between this rating and an incoming supply. */
    public int overvoltageSteps(EnergyTier incoming) {
        return Math.max(0, incoming.ordinal() - ordinal());
    }

    /**
     * Suffix of this tier's translation key, <b>without the {@code tier.} prefix</b>.
     *
     * <p>Callers wrap it themselves ({@code "tier." + tier.translationKey()}). The
     * prefix used to be included here as well, which produced
     * {@code tier.tier.stardustindustry.lv} and a raw missing-translation key
     * in the terminal tooltip.</p>
     */
    public String translationKey() {
        return com.stardustindustry.stardustindustry.StardustIndustry.MODID + "." + name;
    }
}
