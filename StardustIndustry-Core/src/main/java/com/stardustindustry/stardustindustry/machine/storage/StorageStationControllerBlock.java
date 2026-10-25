package com.stardustindustry.stardustindustry.machine.storage;

import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/** The single tiered controller mounted in a storage station's shell. */
public final class StorageStationControllerBlock extends MachineBlock {

    private final StorageTier stationTier;

    public StorageStationControllerBlock(Properties properties, StorageTier stationTier) {
        super(properties);
        this.stationTier = stationTier;
    }

    public StorageTier stationTier() {
        return stationTier;
    }

    @Override
    public BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.STORAGE_STATION_CONTROLLER.get();
    }
}
