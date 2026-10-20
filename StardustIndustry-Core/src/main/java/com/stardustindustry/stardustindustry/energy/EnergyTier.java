package com.stardustindustry.stardustindustry.energy;

import net.minecraft.util.StringRepresentable;

/**
 * Voltage tiers used across the whole power system.
 *
 * <p>Four tiers follow the long-standing convention used by industrial mods
 * (IC2, GregTech): {@code LV} through {@code EHV}. <b>This enum separates two
 * ideas that older code merged into a single number</b>:</p>
 *
 * <ul>
 *   <li>{@link #volts()} — the <b>physical line voltage</b> the tier runs at
 *       (380 V / 10 kV / 110 kV / 500 kV). This is what decides whether a
 *       connection is allowed at all: a wire or machine is rated for a voltage,
 *       and anything above that rating breaks it. See
 *       {@code docs/electric-power-system-design.md}.</li>
 *   <li>{@link #powerClass()} — the game's <b>FE/t "power class"</b>, the
 *       long-standing balance number (32 / 128 / 512 / 2048). It is deliberately
 *       <b>not</b> equal to {@link #volts()}: the physical anchor
 *       ({@code 1 FE = 1 J}) is not fixed yet, so the FE numbers stay as they
 *       were to keep existing machines balanced.
 *       {@code maxTransfer() = powerClass * maxAmperage}.</li>
 * </ul>
 *
 * <p>{@link #maxAmperage()} is the true ampacity of one connection at this tier,
 * on the same scale as a wire's {@code I_max}.</p>
 */
public enum EnergyTier implements StringRepresentable {
    /**
     * Low voltage: 380 V three-phase. 220 V single-phase is a wiring variant
     * inside this same tier, not a separate level.
     */
    LV("lv", 380, 32, 1, 64, 5_000),
    /** Medium voltage: 10 kV, the workhorse for transmission. */
    MV("mv", 10_000, 128, 2, 1_024, 20_000),
    /** High voltage: 110 kV, bulk transmission. */
    HV("hv", 110_000, 512, 4, 8_192, 80_000),
    /** Extreme high voltage: 500 kV, long-distance transmission / generation side. */
    EHV("ehv", 500_000, 2_048, 8, 65_536, 320_000);

    private final String name;
    private final int volts;
    private final int powerClass;
    private final int maxAmperage;
    private final int maxBuffer;
    private final int fluidTransfer;

    EnergyTier(String name, int volts, int powerClass, int maxAmperage, int maxBuffer, int fluidTransfer) {
        this.name = name;
        this.volts = volts;
        this.powerClass = powerClass;
        this.maxAmperage = maxAmperage;
        this.maxBuffer = maxBuffer;
        this.fluidTransfer = fluidTransfer;
    }

    /** Canonical lowercase name, used for translation keys and serialisation. */
    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * The physical line voltage this tier runs at, in volts
     * (380 / 10 000 / 110 000 / 500 000).
     *
     * <p>Used for insulation and over-voltage checks and for display. It is
     * <b>not</b> the FE balance number — see {@link #powerClass()}.</p>
     */
    public int volts() {
        return volts;
    }

    /**
     * The FE/t carried by one amp at this tier — a game-balance number, not a
     * physical wattage (the {@code 1 FE = 1 J} anchor is not fixed yet).
     *
     * <p>Formerly {@code voltage()}. Kept unchanged (32 / 128 / 512 / 2048) so
     * existing machine buffers and transfer rates do not shift.</p>
     */
    public int powerClass() {
        return powerClass;
    }

    /** How many amps a single connection of this tier can carry. */
    public int maxAmperage() {
        return maxAmperage;
    }

    /** Maximum instantaneous transfer of one connection: {@code powerClass * amperage}. */
    public int maxTransfer() {
        return powerClass * maxAmperage;
    }

    /** A sensible internal energy buffer for a machine of this tier. */
    public int maxBuffer() {
        return maxBuffer;
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
        return incoming.volts <= this.volts;
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
