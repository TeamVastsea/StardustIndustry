package com.stardustindustry.stardustindustry;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server/common configuration for Stardust Industry.
 *
 * <p>These values are read once at load time and govern world-affecting or
 * balance-affecting behaviour, so they live on the common config and are
 * shared by every player connected to a world.</p>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_LOADING = BUILDER
            .comment("Log a line when a Stardust Industry machine is loaded from disk.")
            .define("logMachineLoading", false);

    public static final ModConfigSpec.IntValue MACHINE_BASE_ENERGY_STORAGE = BUILDER
            .comment("Baseline energy buffer (in FE) every Stardust Industry machine starts with.")
            .defineInRange("machineBaseEnergyStorage", 20_000, 0, Integer.MAX_VALUE);

    // ---- tanks ----

    public static final ModConfigSpec.IntValue TANK_BUCKETS_PER_AIR_BLOCK = BUILDER
            .comment("Buckets of fluid each interior air block of a tank stores (1 bucket = 1000 mB).")
            .defineInRange("tankBucketsPerAirBlock", 128, 1, Integer.MAX_VALUE / 1000);

    public static final ModConfigSpec.IntValue TANK_MAX_SIZE = BUILDER
            .comment("Largest size a tank may be along one axis, in blocks (3-9 by design; the reference build uses 9).")
            .defineInRange("tankMaxSize", 9, 3, 64);

    public static final ModConfigSpec.IntValue TANK_MIN_SIZE = BUILDER
            .comment("Smallest size a tank may be along one axis, in blocks. Must be at least 3 (a 2x2x2 tank holds nothing).")
            .defineInRange("tankMinSize", 3, 3, 64);

    static final ModConfigSpec SPEC = BUILDER.build();
}
