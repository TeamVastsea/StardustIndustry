package com.stardustindustry.stardustindustry.machine.storage;

import net.minecraft.world.level.block.Block;

/** An inert internal energy cell whose tier and capacity are read by the station scanner. */
public final class StorageCellBlock extends Block {

    private final StorageTier stationTier;

    public StorageCellBlock(Properties properties, StorageTier stationTier) {
        super(properties);
        this.stationTier = stationTier;
    }

    public StorageTier stationTier() {
        return stationTier;
    }
}
