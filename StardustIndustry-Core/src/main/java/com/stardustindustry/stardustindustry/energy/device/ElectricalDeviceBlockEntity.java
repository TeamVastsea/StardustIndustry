package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.DestructiveFaultTracker;
import com.stardustindustry.stardustindustry.energy.ElectricalPortRole;
import com.stardustindustry.stardustindustry.energy.ElectricalUnits;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.GridState;
import com.stardustindustry.stardustindustry.energy.IElectricalPort;
import com.stardustindustry.stardustindustry.energy.WiringMode;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime state shared by generators, transformers, protection and FE converters. */
public final class ElectricalDeviceBlockEntity extends BlockEntity {
    private boolean switchClosed;
    private boolean stepUp = true;
    private boolean feToNative = true;
    private EnergyTier creativeTier = EnergyTier.LV;
    private WiringMode creativeWiring = WiringMode.THREE_PHASE_4_WIRE;
    private WiringMode generatorWiring = WiringMode.SINGLE_PHASE_2_WIRE;
    private long storedJoules;
    private final FuelBurnState fuel = new FuelBurnState();
    private final PerTickPowerLimit feTransferLimit = new PerTickPowerLimit();
    private final DestructiveFaultTracker inputFaults = new DestructiveFaultTracker();
    private boolean fuseBlown;
    private GridState inputState;
    private GridState outputState;

    private final IElectricalPort sourcePort = new Port(true);
    private final IElectricalPort inputPort = new Port(false);

    public ElectricalDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRICAL_DEVICE.get(), pos, state);
        WiringMode initialWiring = kind() == ElectricalDeviceKind.FUEL_GENERATOR
                ? generatorWiring : WiringMode.THREE_PHASE_4_WIRE;
        inputState = GridState.offline(initialWiring, FaultKind.OPEN_SWITCH, false);
        outputState = inputState;
    }

    private ElectricalDeviceKind kind() {
        return getBlockState().getBlock() instanceof ElectricalDeviceBlock block
                ? block.kind() : ElectricalDeviceKind.FUEL_GENERATOR;
    }

    private Direction facing() {
        return getBlockState().getValue(ElectricalDeviceBlock.FACING);
    }

    public IElectricalPort portFor(Direction side) {
        if (side == null) {
            return null;
        }
        ElectricalDeviceKind kind = kind();
        boolean outputSide = side == facing() || side == facing().getClockWise()
                || side == facing().getCounterClockWise();
        boolean inputSide = side == facing().getOpposite() || side == Direction.UP || side == Direction.DOWN;
        if (kind.source() || kind.converter()) {
            return outputSide ? (kind.converter() && !feToNative ? inputPort : sourcePort) : null;
        }
        if (kind.transformer() || kind.protection()) {
            if (outputSide) {
                return sourcePort;
            }
            if (inputSide) {
                return inputPort;
            }
        }
        return null;
    }

    public void serverTick() {
        // Fuel time is consumed by extractJoules only, so an idle closed generator wastes no fuel.
    }

    public void toggleSwitch() {
        if (fuseBlown && kind().fuseBox()) {
            return;
        }
        setSwitchClosed(!switchClosed);
    }

    public void setSwitchClosed(boolean closed) {
        switchClosed = closed && !(fuseBlown && kind().fuseBox());
        syncSwitchState();
    }

    public void cycleCreativeTier() {
        if (kind() == ElectricalDeviceKind.CREATIVE_SOURCE) {
            creativeTier = EnergyTier.values()[(creativeTier.ordinal() + 1) % EnergyTier.values().length];
            creativeWiring = creativeTier == EnergyTier.LV
                    ? WiringMode.THREE_PHASE_4_WIRE : WiringMode.THREE_PHASE_3_WIRE;
            configurationChanged();
        }
    }

    public void cycleConfiguration() {
        ElectricalDeviceKind kind = kind();
        if (kind.transformer()) {
            stepUp = !stepUp;
        } else if (kind.converter()) {
            feToNative = !feToNative;
        } else if (kind == ElectricalDeviceKind.FUEL_GENERATOR) {
            generatorWiring = switch (generatorWiring) {
                case SINGLE_PHASE_2_WIRE -> WiringMode.THREE_PHASE_3_WIRE;
                case THREE_PHASE_3_WIRE -> WiringMode.THREE_PHASE_4_WIRE;
                case THREE_PHASE_4_WIRE -> WiringMode.SINGLE_PHASE_2_WIRE;
            };
        } else if (kind == ElectricalDeviceKind.CREATIVE_SOURCE && creativeTier == EnergyTier.LV) {
            creativeWiring = switch (creativeWiring) {
                case SINGLE_PHASE_2_WIRE -> WiringMode.THREE_PHASE_3_WIRE;
                case THREE_PHASE_3_WIRE -> WiringMode.THREE_PHASE_4_WIRE;
                case THREE_PHASE_4_WIRE -> WiringMode.SINGLE_PHASE_2_WIRE;
            };
        } else if (kind.fuseBox()) {
            fuseBlown = false;
        }
        configurationChanged();
    }

    public boolean addFuel(int ticks) {
        if (kind() != ElectricalDeviceKind.FUEL_GENERATOR || ticks <= 0) {
            return false;
        }
        fuel.addTicks(ticks);
        changed();
        return true;
    }

    public Component statusText() {
        ElectricalDeviceKind kind = kind();
        Component state = Component.translatable(switchClosed
                ? "message.stardustindustry.electrical.status.on"
                : "message.stardustindustry.electrical.status.off");
        if (kind == ElectricalDeviceKind.CREATIVE_SOURCE) {
            return Component.empty()
                    .append(state)
                    .append(" | ")
                    .append(Component.translatable("tier." + creativeTier.translationKey()))
                    .append(" | ")
                    .append(Component.translatable(
                            "message.stardustindustry.electrical.wiring." + creativeWiring.id()));
        }
        if (kind == ElectricalDeviceKind.FUEL_GENERATOR) {
            return Component.translatable("message.stardustindustry.electrical.status.wiring",
                    state,
                    Component.translatable("message.stardustindustry.electrical.wiring."
                            + generatorWiring.id()),
                    generatorWiring.requiredConductors());
        }
        if (kind.transformer()) {
            return Component.empty()
                    .append(state)
                    .append(" | ")
                    .append(Component.translatable(stepUp
                            ? "message.stardustindustry.electrical.status.step_up"
                            : "message.stardustindustry.electrical.status.step_down"));
        }
        if (kind.converter()) {
            return Component.empty()
                    .append(state)
                    .append(" | ")
                    .append(Component.translatable(feToNative
                            ? "message.stardustindustry.electrical.status.fe_to_native"
                            : "message.stardustindustry.electrical.status.native_to_fe"));
        }
        if (fuseBlown) {
            return Component.translatable("message.stardustindustry.electrical.status.tripped");
        }
        return state;
    }

    private long bufferCapacity() {
        return Math.max(1L, kind().ratedPowerWatts() * 2L);
    }

    private long receive(long joules, boolean simulate) {
        long accepted = Math.min(Math.max(0L, joules), Math.max(0L, bufferCapacity() - storedJoules));
        if (!simulate && accepted > 0L) {
            storedJoules += accepted;
            changed();
        }
        return accepted;
    }

    private long extract(long joules, boolean simulate, double efficiency) {
        long inputNeeded = EnergyConversion.inputForOutput(joules, efficiency);
        if (inputNeeded > storedJoules) {
            return 0L;
        }
        if (!simulate && inputNeeded > 0L) {
            storedJoules -= inputNeeded;
            changed();
        }
        return joules;
    }

    private void syncSwitchState() {
        if (level != null) {
            BlockState state = getBlockState();
            if (state.getValue(ElectricalDeviceBlock.POWERED) != switchClosed) {
                level.setBlock(worldPosition, state.setValue(ElectricalDeviceBlock.POWERED, switchClosed), 3);
            }
        }
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Applies an electrical configuration atomically and makes cached capabilities re-query it. */
    private void configurationChanged() {
        setSwitchClosed(false);
        inputFaults.reset();
        inputState = GridState.offline(inputPort.wiring(), FaultKind.OPEN_SWITCH, false);
        outputState = GridState.offline(sourcePort.wiring(), FaultKind.OPEN_SWITCH, false);
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
        changed();
    }

    public net.neoforged.neoforge.energy.IEnergyStorage feStorage() {
        if (!kind().converter()) {
            return null;
        }
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                if (!feToNative || !switchClosed) return 0;
                long gameTick = level == null ? 0L : level.getGameTime();
                int limited = feTransferLimit.limit(amount, kind().ratedPowerWatts(), gameTick);
                long converted = EnergyConversion.outputForInput(limited, kind().efficiency());
                long acceptedJoules = receive(converted, simulate);
                int acceptedFe = (int) Math.min(limited,
                        EnergyConversion.inputForOutput(acceptedJoules, kind().efficiency()));
                if (!simulate) feTransferLimit.record(acceptedFe, gameTick);
                return acceptedFe;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                if (feToNative || !switchClosed) return 0;
                long gameTick = level == null ? 0L : level.getGameTime();
                int limited = feTransferLimit.limit(amount, kind().ratedPowerWatts(), gameTick);
                long maxFe = EnergyConversion.outputForInput(storedJoules, kind().efficiency());
                int extracted = (int) Math.min(limited, Math.min(Integer.MAX_VALUE, maxFe));
                long usedJoules = EnergyConversion.inputForOutput(extracted, kind().efficiency());
                if (!simulate) {
                    storedJoules -= Math.min(storedJoules, usedJoules);
                    feTransferLimit.record(extracted, gameTick);
                    changed();
                }
                return extracted;
            }

            @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, storedJoules); }
            @Override public int getMaxEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, bufferCapacity()); }
            @Override public boolean canExtract() { return !feToNative && switchClosed; }
            @Override public boolean canReceive() { return feToNative && switchClosed; }
        };
    }

    private final class Port implements IElectricalPort {
        private final boolean output;
        private Port(boolean output) { this.output = output; }

        @Override
        public EnergyTier tier() {
            ElectricalDeviceKind kind = kind();
            if (kind == ElectricalDeviceKind.CREATIVE_SOURCE) return creativeTier;
            if (kind.transformer()) {
                EnergyTier input = stepUp ? kind.lowTier() : kind.highTier();
                EnergyTier result = stepUp ? kind.highTier() : kind.lowTier();
                return output ? result : input;
            }
            return kind.lowTier();
        }

        @Override
        public WiringMode wiring() {
            if (kind() == ElectricalDeviceKind.CREATIVE_SOURCE) return creativeWiring;
            if (kind() == ElectricalDeviceKind.FUEL_GENERATOR) return generatorWiring;
            return tier() == EnergyTier.LV ? WiringMode.THREE_PHASE_4_WIRE : WiringMode.THREE_PHASE_3_WIRE;
        }

        @Override public ElectricalPortRole electricalRole() { return output ? ElectricalPortRole.SOURCE : ElectricalPortRole.LOAD; }
        @Override public boolean enabled() { return switchClosed && !(fuseBlown && kind().fuseBox()); }
        @Override public long maxPowerWatts() { return kind().ratedPowerWatts(); }
        @Override public Object energyAccount() { return ElectricalDeviceBlockEntity.this; }
        @Override public boolean allowsSameTickPassThrough() {
            return kind().transformer() || kind().protection();
        }
        @Override public long overcurrentTripWatts() {
            return kind().protection() ? kind().ratedPowerWatts() : 0L;
        }

        @Override
        public long requestedPowerWatts() {
            if (output || !enabled()) return 0L;
            return Math.min(maxPowerWatts(), Math.max(0L, bufferCapacity() - storedJoules)
                    * ElectricalUnits.TICKS_PER_SECOND);
        }

        @Override
        public long availablePowerWatts() {
            if (!output || !enabled()) return 0L;
            if (kind().source()) {
                return kind() == ElectricalDeviceKind.CREATIVE_SOURCE || fuel.hasFuel()
                        ? maxPowerWatts() : 0L;
            }
            long outputJoules = EnergyConversion.outputForInput(storedJoules,
                    kind().nativeOutputEfficiency(feToNative));
            return Math.min(maxPowerWatts(), ElectricalUnits.wattsFromJoulesPerTick(outputJoules));
        }

        @Override
        public long receiveJoules(long amount, boolean simulate) {
            return output ? 0L : receive(amount, simulate);
        }

        @Override
        public long extractJoules(long amount, boolean simulate) {
            if (!output || amount <= 0L) return 0L;
            if (kind() == ElectricalDeviceKind.CREATIVE_SOURCE) return amount;
            if (kind() == ElectricalDeviceKind.FUEL_GENERATOR) {
                Long gameTick = level == null ? null : level.getGameTime();
                if (!fuel.extract(simulate, gameTick)) return 0L;
                if (!simulate) changed();
                return amount;
            }
            return extract(amount, simulate, kind().nativeOutputEfficiency(feToNative));
        }

        @Override
        public void updateGridState(GridState state) {
            if (output) outputState = state; else inputState = state;
            if (switchClosed && ProtectionModel.shouldTrip(kind(), state)) {
                tripProtection();
                return;
            }
            if (!output) {
                DestructiveFaultTracker.Confirmation confirmed = inputFaults.update(state, tier());
                if (confirmed != null) {
                    failDevice(confirmed.explosionStrength());
                }
            }
        }

        @Override
        public boolean updateDownstreamFault(GridState state) {
            if (!output || !switchClosed) {
                return false;
            }
            if (ProtectionModel.shouldTrip(kind(), state)) {
                tripProtection();
                return false;
            }
            return ProtectionModel.shouldRelayUpstream(kind(), state);
        }
    }

    private void tripProtection() {
        inputFaults.reset();
        fuseBlown = kind().fuseBox();
        setSwitchClosed(false);
    }

    private void failDevice(float strength) {
        if (level == null || level.isClientSide()) {
            return;
        }
        level.destroyBlock(worldPosition, true);
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, strength, false, Level.ExplosionInteraction.NONE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("SwitchClosed", switchClosed);
        tag.putBoolean("StepUp", stepUp);
        tag.putBoolean("FeToNative", feToNative);
        tag.putString("CreativeTier", creativeTier.getSerializedName());
        tag.putString("CreativeWiring", creativeWiring.id());
        tag.putString("GeneratorWiring", generatorWiring.id());
        tag.putLong("StoredJoules", storedJoules);
        tag.putInt("QueuedFuelTicks", fuel.queuedTicks());
        tag.putInt("BurningFuelTicks", fuel.burningTicks());
        tag.putBoolean("FuseBlown", fuseBlown);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        switchClosed = tag.getBoolean("SwitchClosed");
        stepUp = !tag.contains("StepUp") || tag.getBoolean("StepUp");
        feToNative = !tag.contains("FeToNative") || tag.getBoolean("FeToNative");
        for (EnergyTier tier : EnergyTier.values()) {
            if (tier.getSerializedName().equals(tag.getString("CreativeTier"))) creativeTier = tier;
        }
        creativeWiring = WiringMode.byId(tag.getString("CreativeWiring"));
        generatorWiring = WiringMode.byId(tag.getString("GeneratorWiring"));
        storedJoules = Math.max(0L, tag.getLong("StoredJoules"));
        fuel.load(tag.getInt("QueuedFuelTicks"), tag.getInt("BurningFuelTicks"));
        fuseBlown = tag.getBoolean("FuseBlown");
        inputFaults.reset();
        inputState = GridState.offline(inputPort.wiring(), FaultKind.OPEN_SWITCH, false);
        outputState = GridState.offline(sourcePort.wiring(), FaultKind.OPEN_SWITCH, false);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
