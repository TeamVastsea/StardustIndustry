package com.stardustindustry.stardustindustry.energy;

import net.minecraft.util.StringRepresentable;

/**
 * Voltage tiers used across the whole power system.
 *
 * <p>Four tiers follow the long-standing convention used by industrial mods
 * (IC2, GregTech): {@code LV} through {@code EHV}. Each tier fixes a voltage
 * and a per-connection current rating, so the maximum transfer of a cable is
 * simply {@code voltage * maxAmperage}. Machines and cables declare the tiers
 * they accept, which is what makes transformers necessary: a cable only carries
 * a connection whose voltage is at or below its own rating.</p>
 *
 * <p>All numbers are intentionally round and readable rather than physically
 * exact. The goal is a system where the player can reason about it ("this is a
 * 512 V line") without solving load-flow equations.</p>
 */
public enum EnergyTier implements StringRepresentable {
    /** Low voltage: the entry tier, hand-fed generators and starter machines. */
    LV("lv", 32, 1, 64),
    /** Medium voltage: the workhorse tier for small processing lines. */
    MV("mv", 128, 2, 1_024),
    /** High voltage: bulk ore processing and first real power plants. */
    HV("hv", 512, 4, 8_192),
    /** Extreme high voltage: nuclear output and long-distance transmission. */
    EHV("ehv", 2_048, 8, 65_536);

    private final String name;
    private final int voltage;
    private final int maxAmperage;
    private final int maxBuffer;

    EnergyTier(String name, int voltage, int maxAmperage, int maxBuffer) {
        this.name = name;
        this.voltage = voltage;
        this.maxAmperage = maxAmperage;
        this.maxBuffer = maxBuffer;
    }

    /** Canonical lowercase name, used for translation keys and serialisation. */
    @Override
    public String getSerializedName() {
        return name;
    }

    /** The voltage, in FE/t, that one amp at this tier carries. */
    public int voltage() {
        return voltage;
    }

    /** How many amps a single connection of this tier can carry. */
    public int maxAmperage() {
        return maxAmperage;
    }

    /** Maximum instantaneous transfer of one connection: {@code voltage * amperage}. */
    public int maxTransfer() {
        return voltage * maxAmperage;
    }

    /** A sensible internal energy buffer for a machine of this tier. */
    public int maxBuffer() {
        return maxBuffer;
    }

    /**
     * The throughput, in mB/t, a fluid port of this tier is rated for.
     *
     * <p>Fluid rates deliberately follow a gentler curve than energy: a tank's
     * ports are about how quickly a pipe can empty a vessel, and an EHV port is
     * already fast enough to matter without the number running away. The value
     * scales with the tier's voltage so the ordering always matches the voltage
     * ordering, which is what a player expects when they upgrade a port.</p>
     */
    public int fluidTransfer() {
        return voltage * maxAmperage * 2;
    }

    /** True when {@code incoming} may be carried directly by a connection of this tier. */
    public boolean accepts(EnergyTier incoming) {
        return incoming.voltage <= this.voltage;
    }

    public String translationKey() {
        return "tier." + com.stardustindustry.stardustindustry.StardustIndustry.MODID + "." + name;
    }
}
