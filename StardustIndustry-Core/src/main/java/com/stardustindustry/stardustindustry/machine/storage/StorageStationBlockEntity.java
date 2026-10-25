package com.stardustindustry.stardustindustry.machine.storage;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.module.JouleStorageModule;
import com.stardustindustry.stardustindustry.multiblock.provider.StorageStationStructureProvider;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Controller and shared native joule storage for a formed storage station. */
public final class StorageStationBlockEntity extends MachineBlockEntity {

    private final StorageTier stationTier;
    private final JouleStorageModule energy;
    private StorageMode storageMode = StorageMode.AUTO;
    /** Last successful scan, retained across reload until the first live revalidation. */
    private int validatedCellCount;

    public StorageStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_STATION_CONTROLLER.get(), pos, state, tierOf(state).machineTier());
        this.stationTier = tierOf(state);
        this.energy = modules().provide(ResourceType.ENERGY,
                modules().add(new JouleStorageModule(stationTier.energyTier(), 1L,
                        stationTier.nominalPowerWatts())));
        energy.setCapacityOverride(this::currentCapacityJoules);
        energy.setPowerOverride(() -> isFormed() ? stationTier.nominalPowerWatts() : 0L);
        initialiseModules();
    }

    private static StorageTier tierOf(BlockState state) {
        if (state.getBlock() instanceof StorageStationControllerBlock controller) {
            return controller.stationTier();
        }
        throw new IllegalArgumentException("Storage station block entity requires a station controller block");
    }

    public StorageTier stationTier() {
        return stationTier;
    }

    public JouleStorageModule energy() {
        return energy;
    }

    public StorageMode storageMode() {
        return storageMode;
    }

    public boolean setStorageMode(StorageMode mode) {
        StorageMode next = mode == null ? StorageMode.AUTO : mode;
        if (storageMode == next) {
            return false;
        }
        storageMode = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public StorageMode cycleStorageMode() {
        setStorageMode(storageMode.next());
        return storageMode;
    }

    public long currentCapacityJoules() {
        if (!isFormed()) {
            return 0L;
        }
        int evaluatedCells = StorageStationStructureProvider.cellCount(evaluation());
        int cells = evaluatedCells > 0 ? evaluatedCells : validatedCellCount;
        return stationTier.capacityJoulesForCells(cells);
    }

    /** Maximum charging request at the current state of charge and storage policy. */
    public long requestedPowerWatts() {
        if (!storageMode.allowsCharge() || !isFormed()) {
            return 0L;
        }
        long headroom = Math.max(0L, energy.capacityJoules() - energy.storedJoules());
        return Math.min(energy.maxPowerWatts(),
                com.stardustindustry.stardustindustry.energy.ElectricalUnits
                        .wattsFromJoulesPerTick(headroom));
    }

    @Override
    public StructureProvider provider() {
        return StorageStationStructureProvider.INSTANCE;
    }

    @Override
    public boolean supportsProjection() {
        return false;
    }

    @Override
    protected long idleDrawWatts() {
        return 0L;
    }

    @Override
    protected void serverTickFormed(Level level, BlockPos pos, BlockState state) {
        int evaluatedCells = StorageStationStructureProvider.cellCount(evaluation());
        if (evaluatedCells > 0 && evaluatedCells != validatedCellCount) {
            validatedCellCount = evaluatedCells;
            long ventedJoules = energy.clampToCapacity();
            if (ventedJoules > 0L) {
                broadcastNearby(Component.translatable(
                        "message.stardustindustry.storage_station.capacity_reduced",
                        com.stardustindustry.stardustindustry.energy.ElectricalUnits
                                .formatKilowattHours(ventedJoules)), 24.0);
            }
            setChanged();
        }
    }

    @Override
    protected void onFormedChanged(boolean nowFormed) {
        String key = nowFormed
                ? "message.stardustindustry.storage_station.formed"
                : "message.stardustindustry.storage_station.unformed";
        if (nowFormed) {
            broadcastNearby(Component.translatable(key,
                    StorageStationStructureProvider.cellCount(evaluation()),
                    com.stardustindustry.stardustindustry.energy.ElectricalUnits.formatKilowattHours(
                            currentCapacityJoules())), 24.0);
        } else {
            broadcastNearby(Component.translatable(key), 24.0);
        }
    }

    @Override
    protected void saveMachine(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("ValidatedStorageCells", validatedCellCount);
        tag.putString("StorageMode", storageMode.id());
    }

    @Override
    protected void loadMachine(CompoundTag tag, HolderLookup.Provider registries) {
        validatedCellCount = Math.max(0, tag.getInt("ValidatedStorageCells"));
        storageMode = StorageMode.byId(tag.getString("StorageMode"));
    }
}
