package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

/** Fixed ratings for the first playable electrical equipment set. */
public enum ElectricalDeviceKind {
    FUEL_GENERATOR(EnergyTier.LV, null, 20_000L, 0.0),
    CREATIVE_SOURCE(EnergyTier.LV, null, 500_000_000L, 0.0),
    TRANSFORMER_LV_MV(EnergyTier.LV, EnergyTier.MV, 24_000L, 0.98),
    TRANSFORMER_MV_HV(EnergyTier.MV, EnergyTier.HV, 1_700_000L, 0.98),
    TRANSFORMER_HV_EHV(EnergyTier.HV, EnergyTier.EHV, 38_000_000L, 0.98),
    LV_BREAKER(EnergyTier.LV, 150, 1.0),
    MV_BREAKER(EnergyTier.MV, 500, 1.0),
    HV_BREAKER(EnergyTier.HV, 1_500, 1.0),
    EHV_BREAKER(EnergyTier.EHV, 5_000, 1.0),
    LV_FUSE_BOX(EnergyTier.LV, 150, 1.0),
    MV_FUSE_BOX(EnergyTier.MV, 500, 1.0),
    HV_FUSE_BOX(EnergyTier.HV, 1_500, 1.0),
    EHV_FUSE_BOX(EnergyTier.EHV, 5_000, 1.0),
    LV_FE_CONVERTER(EnergyTier.LV, 100, 0.95),
    MV_FE_CONVERTER(EnergyTier.MV, 250, 0.95),
    HV_FE_CONVERTER(EnergyTier.HV, 500, 0.95),
    EHV_FE_CONVERTER(EnergyTier.EHV, 1_000, 0.95);

    private final EnergyTier lowTier;
    private final EnergyTier highTier;
    private final long ratedPowerWatts;
    private final double efficiency;
    private final int ratedCurrentAmps;

    ElectricalDeviceKind(EnergyTier lowTier, EnergyTier highTier, long ratedPowerWatts,
                         double efficiency) {
        this(lowTier, highTier, ratedPowerWatts, efficiency, 0);
    }

    ElectricalDeviceKind(EnergyTier tier, int ratedCurrentAmps, double efficiency) {
        this(tier, null, defaultWiring(tier).powerWatts(ratedCurrentAmps, tier),
                efficiency, ratedCurrentAmps);
    }

    private static com.stardustindustry.stardustindustry.energy.WiringMode defaultWiring(EnergyTier tier) {
        return tier == EnergyTier.LV
                ? com.stardustindustry.stardustindustry.energy.WiringMode.THREE_PHASE_4_WIRE
                : com.stardustindustry.stardustindustry.energy.WiringMode.THREE_PHASE_3_WIRE;
    }

    private ElectricalDeviceKind(EnergyTier lowTier, EnergyTier highTier, long ratedPowerWatts,
                                 double efficiency, int ratedCurrentAmps) {
        this.lowTier = lowTier;
        this.highTier = highTier;
        this.ratedPowerWatts = ratedPowerWatts;
        this.efficiency = efficiency;
        this.ratedCurrentAmps = ratedCurrentAmps;
    }

    public EnergyTier lowTier() { return lowTier; }
    public EnergyTier highTier() { return highTier; }
    public long ratedPowerWatts() { return ratedPowerWatts; }
    public double efficiency() { return efficiency; }
    public int ratedCurrentAmps() { return ratedCurrentAmps; }
    public double nativeOutputEfficiency(boolean feToNative) {
        return converter() && feToNative ? 1.0 : efficiency;
    }
    public boolean transformer() { return name().startsWith("TRANSFORMER_"); }
    public boolean breaker() { return name().endsWith("_BREAKER"); }
    public boolean fuseBox() { return name().endsWith("_FUSE_BOX"); }
    public boolean converter() { return name().endsWith("_FE_CONVERTER"); }
    public boolean protection() { return breaker() || fuseBox(); }
    public boolean source() { return this == FUEL_GENERATOR || this == CREATIVE_SOURCE; }
}
