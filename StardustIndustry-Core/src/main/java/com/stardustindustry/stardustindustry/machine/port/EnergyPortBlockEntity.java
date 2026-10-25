package com.stardustindustry.stardustindustry.machine.port;

import com.stardustindustry.stardustindustry.energy.DestructiveFaultTracker;
import com.stardustindustry.stardustindustry.energy.ElectricalPortRole;
import com.stardustindustry.stardustindustry.energy.ElectricalUnits;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.GridState;
import com.stardustindustry.stardustindustry.energy.IElectricalPort;
import com.stardustindustry.stardustindustry.energy.WiringMode;
import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.machine.module.JouleStorageModule;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The energy port: exposes the bound machine's native joule storage to a terminal-backed grid.
 *
 * <p>The base class owns multiblock membership; this class owns native-grid
 * transfer state and delayed destructive fault handling.</p>
 */
public class EnergyPortBlockEntity extends MachinePortBlockEntity
        implements IElectricalPort {

    private final DestructiveFaultTracker destructiveFaults = new DestructiveFaultTracker();
    private WiringMode configuredWiring;
    private GridState gridState;

    public EnergyPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_PORT.get(), pos, state, PartRole.PORT_ENERGY);
        configuredWiring = tier() == EnergyTier.LV
                ? WiringMode.SINGLE_PHASE_2_WIRE : WiringMode.THREE_PHASE_3_WIRE;
        gridState = GridState.offline(configuredWiring, FaultKind.INCOMPLETE_WIRING, false);
    }

    @Override
    public EnergyTier tier() {
        return getBlockState().getBlock() instanceof MachinePortBlock port && port.tier() != null
                ? port.tier() : EnergyTier.LV;
    }

    @Override
    public WiringMode wiring() {
        return configuredWiring;
    }

    /** Only LV exposes selectable single/three-phase wiring; higher tiers are fixed three-phase. */
    public boolean canConfigureWiring() {
        return tier() == EnergyTier.LV;
    }

    /** Cycles the LV interface and leaves it de-energized until the next grid solve. */
    public boolean cycleWiring() {
        if (!canConfigureWiring()) {
            return false;
        }
        configuredWiring = switch (configuredWiring) {
            case SINGLE_PHASE_2_WIRE -> WiringMode.THREE_PHASE_3_WIRE;
            case THREE_PHASE_3_WIRE -> WiringMode.THREE_PHASE_4_WIRE;
            case THREE_PHASE_4_WIRE -> WiringMode.SINGLE_PHASE_2_WIRE;
        };
        destructiveFaults.reset();
        gridState = GridState.offline(configuredWiring, FaultKind.INCOMPLETE_WIRING, false);
        invalidatePortCapabilities();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    public ElectricalPortRole electricalRole() {
        return controller() instanceof StorageStationBlockEntity
                ? ElectricalPortRole.STORAGE : ElectricalPortRole.LOAD;
    }

    @Override
    public boolean enabled() {
        return controller() != null && controller().isFormed();
    }

    private JouleStorageModule storage() {
        var machine = controller();
        return machine == null ? null : machine.modules().get(JouleStorageModule.class);
    }

    @Override
    public long maxPowerWatts() {
        var storage = storage();
        return storage == null ? 0L : storage.maxPowerWatts();
    }

    @Override
    public Object energyAccount() {
        JouleStorageModule storage = storage();
        return storage == null ? this : storage;
    }

    @Override
    public long requestedPowerWatts() {
        var storage = storage();
        if (storage == null) {
            return 0L;
        }
        long headroomPerSecond = ElectricalUnits.wattsFromJoulesPerTick(
                Math.max(0L, storage.capacityJoules() - storage.storedJoules()));
        return Math.min(maxPowerWatts(), headroomPerSecond);
    }

    @Override
    public long availablePowerWatts() {
        var storage = storage();
        if (storage == null) {
            return 0L;
        }
        return Math.min(maxPowerWatts(), ElectricalUnits.wattsFromJoulesPerTick(storage.storedJoules()));
    }

    @Override
    public long receiveJoules(long amount, boolean simulate) {
        var storage = storage();
        return storage == null ? 0L : storage.receiveJoules(amount, simulate);
    }

    @Override
    public long extractJoules(long amount, boolean simulate) {
        var storage = storage();
        return storage == null ? 0L : storage.extractJoules(amount, simulate);
    }

    @Override
    public void updateGridState(GridState state) {
        gridState = state;
        DestructiveFaultTracker.Confirmation confirmed = destructiveFaults.update(state, tier());
        if (confirmed != null) {
            failPort(confirmed.explosionStrength());
        }
        setChanged();
    }

    public GridState gridState() {
        return gridState;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Wiring", configuredWiring.id());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        configuredWiring = tier() == EnergyTier.LV
                ? WiringMode.byId(tag.getString("Wiring"))
                : WiringMode.THREE_PHASE_3_WIRE;
        destructiveFaults.reset();
        gridState = GridState.offline(configuredWiring, FaultKind.INCOMPLETE_WIRING, false);
    }

    private void failPort(float strength) {
        if (level == null || level.isClientSide()) {
            return;
        }
        level.destroyBlock(worldPosition, true);
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, strength, false, Level.ExplosionInteraction.NONE);
    }
}
