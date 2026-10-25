package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.DestructiveFaultTracker;
import com.stardustindustry.stardustindustry.energy.ElectricalAccountKey;
import com.stardustindustry.stardustindustry.energy.ElectricalPortRole;
import com.stardustindustry.stardustindustry.energy.ElectricalPortGroupKey;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Runtime state shared by generators, transformers, protection and FE converters. */
public final class ElectricalDeviceBlockEntity extends BlockEntity {
    private boolean switchClosed;
    private boolean stepUp = true;
    private boolean feToNative = true;
    private EnergyTier creativeTier = EnergyTier.LV;
    private WiringMode creativeWiring = WiringMode.THREE_PHASE_4_WIRE;
    private WiringMode generatorWiring = WiringMode.SINGLE_PHASE_2_WIRE;
    private long storedJoules;
    private long passThroughJoules;
    private long passThroughReceivedJoules;
    private long preparedPassThroughOutputWatts;
    private boolean passThroughInputUpdated;
    private long outputConversionRemainder;
    private long feInputConversionRemainder;
    private final FuelBurnState fuel = new FuelBurnState();
    private final GeneratorFuelInventory fuelInventory = new GeneratorFuelInventory(this::changed);
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
        if (DeviceEnergyPolicy.isPassThrough(kind())) {
            // In-line equipment owns no energy store. Its input circuit is solved before its
            // output circuit, so this balance exists only during the current level tick.
            passThroughJoules = Math.min(passThroughJoules,
                    DeviceEnergyPolicy.roundingCarryJoules(kind()));
            passThroughReceivedJoules = 0L;
            preparedPassThroughOutputWatts = 0L;
            passThroughInputUpdated = false;
        }
        transferAdjacentFe();
        // Fuel is converted lazily by Port.extractJoules(). Merely being loaded,
        // switched off, or connected to an idle grid must not consume an item.
    }

    /**
     * Moves FE through the converter's fixed back/top/bottom faces.
     *
     * <p>The capability remains available for pipes, but a directly adjacent battery is also
     * useful without a pipe that happens to push or pull in the required direction. Transfers
     * still pass through {@link #feStorage(Direction)}, so the nameplate limit and 95% conversion
     * loss are identical for capability users and direct neighbours.</p>
     */
    private void transferAdjacentFe() {
        if (level == null || level.isClientSide() || !kind().converter() || !switchClosed) {
            return;
        }
        Direction[] feSides = {facing().getOpposite(), Direction.UP, Direction.DOWN};
        for (Direction feSide : feSides) {
            BlockPos neighbourPos = worldPosition.relative(feSide);
            if (!level.hasChunkAt(neighbourPos)) {
                continue;
            }
            IEnergyStorage neighbour = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, neighbourPos, feSide.getOpposite());
            IEnergyStorage converter = feStorage(feSide);
            if (neighbour == null || converter == null) {
                continue;
            }
            if (feToNative) {
                pullFe(neighbour, converter);
            } else {
                pushFe(converter, neighbour);
            }
        }
    }

    private static void pullFe(IEnergyStorage source, IEnergyStorage destination) {
        int capacity = destination.receiveEnergy(Integer.MAX_VALUE, true);
        int transferable = source.extractEnergy(capacity, true);
        if (transferable <= 0) {
            return;
        }
        int extracted = source.extractEnergy(transferable, false);
        int accepted = destination.receiveEnergy(extracted, false);
        if (accepted < extracted) {
            source.receiveEnergy(extracted - accepted, false);
        }
    }

    private static void pushFe(IEnergyStorage source, IEnergyStorage destination) {
        int available = source.extractEnergy(Integer.MAX_VALUE, true);
        int transferable = destination.receiveEnergy(available, true);
        if (transferable <= 0) {
            return;
        }
        int extracted = source.extractEnergy(transferable, false);
        int accepted = destination.receiveEnergy(extracted, false);
        if (accepted < extracted) {
            source.receiveEnergy(extracted - accepted, false);
        }
    }

    public void toggleSwitch() {
        if (fuseBlown && kind().fuseBox()) {
            return;
        }
        setSwitchClosed(!switchClosed);
    }

    public void setSwitchClosed(boolean closed) {
        switchClosed = closed && !(fuseBlown && kind().fuseBox());
        if (!switchClosed) {
            clearPassThroughEnergy();
        }
        syncSwitchState();
    }

    /** Installs one correctly rated replacement link and leaves the box open for a deliberate reset. */
    public boolean replaceFuse(EnergyTier replacementTier) {
        ElectricalDeviceKind deviceKind = kind();
        if (!deviceKind.fuseBox() || !fuseBlown || replacementTier != deviceKind.lowTier()) {
            return false;
        }
        fuseBlown = false;
        switchClosed = false;
        inputFaults.reset();
        inputState = GridState.offline(inputPort.wiring(), FaultKind.OPEN_SWITCH, false);
        outputState = GridState.offline(sourcePort.wiring(), FaultKind.OPEN_SWITCH, false);
        syncSwitchState();
        changed();
        return true;
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
        }
        configurationChanged();
    }

    public boolean addFuel(int ticks) {
        if (kind() != ElectricalDeviceKind.FUEL_GENERATOR || ticks <= 0) {
            return false;
        }
        fuel.addBurnTicks(ticks, kind().ratedPowerWatts());
        changed();
        return true;
    }

    public net.neoforged.neoforge.items.IItemHandler fuelItemHandler() {
        return kind() == ElectricalDeviceKind.FUEL_GENERATOR ? fuelInventory.capability() : null;
    }

    /** Full two-slot view used only by the server-created generator menu. */
    public net.neoforged.neoforge.items.IItemHandler generatorMenuHandler() {
        return kind() == ElectricalDeviceKind.FUEL_GENERATOR ? fuelInventory.menuHandler() : null;
    }

    /** Best live grid state for the device's front-panel readout. */
    public GridState telemetryState() {
        ElectricalDeviceKind deviceKind = kind();
        if (deviceKind.source() || deviceKind.converter() && feToNative) {
            return outputState;
        }
        if (deviceKind.converter()) {
            return inputState;
        }
        if (outputState.energized() || outputState.deliveredPowerWatts() > 0L
                || outputState.fault() != FaultKind.OPEN_SWITCH) {
            return outputState;
        }
        return inputState;
    }

    public long telemetryStoredJoules() {
        if (kind() == ElectricalDeviceKind.FUEL_GENERATOR) {
            return fuel.storedJoules();
        }
        return DeviceEnergyPolicy.persistentCapacity(kind()) > 0L ? storedJoules : 0L;
    }

    public long telemetryCapacityJoules() {
        return DeviceEnergyPolicy.persistentCapacity(kind());
    }

    public long telemetryRequestedPowerWatts() {
        ElectricalDeviceKind deviceKind = kind();
        if (deviceKind.source() || deviceKind.converter() && feToNative) {
            return 0L;
        }
        return inputPort.requestedPowerWatts();
    }

    public String telemetryModeId() {
        ElectricalDeviceKind deviceKind = kind();
        if (deviceKind.transformer()) return stepUp ? "step_up" : "step_down";
        if (deviceKind.converter()) return feToNative ? "fe_to_native" : "native_to_fe";
        if (deviceKind.fuseBox() && fuseBlown) return "fuse_blown";
        return switchClosed ? "closed" : "open";
    }

    public boolean insertFuelItem(net.minecraft.world.item.ItemStack stack) {
        if (kind() != ElectricalDeviceKind.FUEL_GENERATOR || stack.isEmpty()) {
            return false;
        }
        net.minecraft.world.item.ItemStack one = stack.copyWithCount(1);
        if (!fuelInventory.capability().insertItem(GeneratorFuelInventory.FUEL_SLOT, one, true).isEmpty()) {
            return false;
        }
        fuelInventory.capability().insertItem(GeneratorFuelInventory.FUEL_SLOT, one, false);
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
        return DeviceEnergyPolicy.persistentCapacity(kind());
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
        long available = EnergyConversion.convertInput(storedJoules, efficiency,
                outputConversionRemainder).amount();
        if (joules <= 0L || joules > available) {
            return 0L;
        }
        EnergyConversion.Result consumption = EnergyConversion.consumeInputForOutput(
                joules, efficiency, outputConversionRemainder);
        if (consumption.amount() > storedJoules) {
            return 0L;
        }
        if (!simulate) {
            storedJoules -= consumption.amount();
            outputConversionRemainder = consumption.remainder();
            changed();
        }
        return joules;
    }

    private void clearPassThroughEnergy() {
        passThroughJoules = 0L;
        passThroughReceivedJoules = 0L;
        preparedPassThroughOutputWatts = 0L;
        passThroughInputUpdated = false;
    }

    private long receivePassThrough(long joules, boolean simulate) {
        long capacity = DeviceEnergyPolicy.passThroughJoulesPerTick(kind());
        long accepted = Math.min(Math.max(0L, joules),
                Math.max(0L, capacity - passThroughReceivedJoules));
        if (!simulate && accepted > 0L) {
            passThroughJoules += accepted;
            passThroughReceivedJoules += accepted;
        }
        return accepted;
    }

    private long extractPassThrough(long joules, boolean simulate) {
        double efficiency = kind().nativeOutputEfficiency(feToNative);
        long available = EnergyConversion.convertInput(passThroughJoules, efficiency,
                outputConversionRemainder).amount();
        if (joules <= 0L) {
            return 0L;
        }
        if (joules > available) {
            return 0L;
        }
        EnergyConversion.Result consumption = EnergyConversion.consumeInputForOutput(
                joules, efficiency, outputConversionRemainder);
        if (consumption.amount() > passThroughJoules) {
            return 0L;
        }
        if (!simulate) {
            passThroughJoules -= consumption.amount();
            outputConversionRemainder = consumption.remainder();
        }
        return joules;
    }

    private boolean hasLivePassThroughInput() {
        if (!passThroughInputUpdated || !inputState.energized()
                || !inputState.sourceSwitchClosed()) {
            return false;
        }
        if (inputState.fault() == FaultKind.UNDERVOLTAGE) {
            return inputState.deliveredPowerWatts() > 0L;
        }
        return inputState.fault() == FaultKind.NONE
                || inputState.fault() == FaultKind.OVERCURRENT;
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

    public net.neoforged.neoforge.energy.IEnergyStorage feStorage(Direction side) {
        boolean feSide = side == facing().getOpposite() || side == Direction.UP || side == Direction.DOWN;
        if (!kind().converter() || !feSide) {
            return null;
        }
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                if (!feToNative || !switchClosed) return 0;
                long gameTick = level == null ? 0L : level.getGameTime();
                int limited = feTransferLimit.limit(amount, kind().ratedPowerWatts(), gameTick);
                long freeJoules = Math.max(0L, bufferCapacity() - storedJoules);
                int acceptedFe = (int) Math.min(limited,
                        EnergyConversion.maxInputForOutputCapacity(freeJoules,
                                kind().efficiency(), feInputConversionRemainder));
                EnergyConversion.Result conversion = EnergyConversion.convertInput(
                        acceptedFe, kind().efficiency(), feInputConversionRemainder);
                if (!simulate && acceptedFe > 0) {
                    storedJoules += conversion.amount();
                    feInputConversionRemainder = conversion.remainder();
                    feTransferLimit.record(acceptedFe, gameTick);
                    changed();
                }
                return acceptedFe;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                if (feToNative || !switchClosed) return 0;
                long gameTick = level == null ? 0L : level.getGameTime();
                int limited = feTransferLimit.limit(amount, kind().ratedPowerWatts(), gameTick);
                long maxFe = EnergyConversion.convertInput(storedJoules, kind().efficiency(),
                        outputConversionRemainder).amount();
                int extracted = (int) Math.min(limited, Math.min(Integer.MAX_VALUE, maxFe));
                EnergyConversion.Result consumption = EnergyConversion.consumeInputForOutput(
                        extracted, kind().efficiency(), outputConversionRemainder);
                if (!simulate && extracted > 0) {
                    storedJoules -= consumption.amount();
                    outputConversionRemainder = consumption.remainder();
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
        @Override
        public long maxPowerWatts() {
            return ProtectionModel.maximumThroughputWatts(kind());
        }
        @Override public Object energyAccount() {
            return new ElectricalAccountKey(worldPosition, "device");
        }
        @Override public Object portGroup() {
            return new ElectricalPortGroupKey(worldPosition, output ? "output" : "input");
        }
        @Override public boolean allowsSameTickPassThrough() {
            return kind().transformer() || kind().protection();
        }
        @Override
        public void preparePassThroughDemandWatts(long outputWatts) {
            if (output && DeviceEnergyPolicy.isPassThrough(kind())) {
                preparedPassThroughOutputWatts = Math.max(0L, outputWatts);
            }
        }
        @Override public long overcurrentTripWatts() {
            return output && kind().protection() ? kind().ratedPowerWatts() : 0L;
        }

        @Override
        public long requestedPowerWatts() {
            if (output || !enabled()) return 0L;
            if (DeviceEnergyPolicy.isPassThrough(kind())) {
                return DeviceEnergyPolicy.inputPowerForOutput(
                        kind(), preparedPassThroughOutputWatts);
            }
            return Math.min(maxPowerWatts(), Math.max(0L, bufferCapacity() - storedJoules)
                    * ElectricalUnits.TICKS_PER_SECOND);
        }

        @Override
        public long availablePowerWatts() {
            if (!output || !enabled()) return 0L;
            if (kind().source()) {
                return kind() == ElectricalDeviceKind.CREATIVE_SOURCE
                        ? maxPowerWatts()
                        : fuel.hasFuel()
                                ? Math.min(maxPowerWatts(),
                                        ElectricalUnits.wattsFromJoulesPerTick(fuel.storedJoules()))
                                : fuelInventory.canRefill() ? maxPowerWatts() : 0L;
            }
            if (DeviceEnergyPolicy.isPassThrough(kind())) {
                if (!hasLivePassThroughInput()) {
                    return 0L;
                }
                long bufferedOutput = ElectricalUnits.wattsFromJoulesPerTick(
                        EnergyConversion.convertInput(passThroughJoules,
                                kind().nativeOutputEfficiency(feToNative),
                                outputConversionRemainder).amount());
                // An energized transformer or closed protection device still establishes
                // no-load voltage with zero whole joules buffered. Any positive extraction
                // remains fail-closed, so advertised capacity cannot create energy.
                return bufferedOutput > 0L ? Math.min(maxPowerWatts(), bufferedOutput)
                        : DeviceEnergyPolicy.maxPassThroughOutputWatts(kind());
            }
            long outputJoules = EnergyConversion.convertInput(storedJoules,
                    kind().nativeOutputEfficiency(feToNative), outputConversionRemainder).amount();
            return Math.min(maxPowerWatts(), ElectricalUnits.wattsFromJoulesPerTick(outputJoules));
        }

        @Override
        public long receiveJoules(long amount, boolean simulate) {
            if (output) return 0L;
            return DeviceEnergyPolicy.isPassThrough(kind())
                    ? receivePassThrough(amount, simulate) : receive(amount, simulate);
        }

        @Override
        public long extractJoules(long amount, boolean simulate) {
            if (!output || amount <= 0L) return 0L;
            if (kind() == ElectricalDeviceKind.CREATIVE_SOURCE) return amount;
            if (kind() == ElectricalDeviceKind.FUEL_GENERATOR) {
                if (!fuel.hasFuel()) {
                    if (simulate) {
                        return fuelInventory.canRefill() ? amount : 0L;
                    }
                    if (!fuelInventory.refill(fuel, kind().ratedPowerWatts())) {
                        return 0L;
                    }
                }
                long extracted = fuel.extractJoules(amount, simulate);
                if (!simulate && extracted > 0L) changed();
                return extracted;
            }
            if (DeviceEnergyPolicy.isPassThrough(kind())) {
                return extractPassThrough(amount, simulate);
            }
            return extract(amount, simulate, kind().nativeOutputEfficiency(feToNative));
        }

        @Override
        public void updateGridState(GridState state) {
            if (output) outputState = state; else inputState = state;
            if (!output && DeviceEnergyPolicy.isPassThrough(kind())) {
                passThroughInputUpdated = true;
            }
            FaultKind fault = state.fault();
            boolean inputSafetyFault = !output && kind().breaker()
                    && (fault == FaultKind.OVERVOLTAGE || fault == FaultKind.MISSING_PHASE);
            if (switchClosed && (output && ProtectionModel.shouldTrip(kind(), state)
                    || inputSafetyFault)) {
                tripProtection();
                return;
            }
            if (!output) {
                DestructiveFaultTracker.Confirmation confirmed = inputFaults.update(state, tier());
                // The confirmation window is persistent safety state. Grid-state
                // updates normally do not alter inventory or switch state, so no
                // other path would mark this block entity for saving as it advances.
                setChanged();
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
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, strength, false, Level.ExplosionInteraction.NONE);
        // Remove after the blast so the inventory drops are not spawned inside
        // the very explosion that caused the device failure.
        level.destroyBlock(worldPosition, true);
    }

    /** Drops the generator's unconsumed fuel and empty containers exactly once. */
    void dropFuelInventory() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (net.minecraft.world.item.ItemStack stack : fuelInventory.drain()) {
            net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
        }
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
        tag.putLong("StoredJoules", DeviceEnergyPolicy.persistentCapacity(kind()) > 0L
                ? storedJoules : 0L);
        tag.putLong("OutputConversionRemainder", outputConversionRemainder);
        tag.putLong("FeInputConversionRemainder", feInputConversionRemainder);
        tag.put("FuelEnergy", fuel.save());
        tag.put("FuelInventory", fuelInventory.save(registries));
        tag.putBoolean("FuseBlown", fuseBlown);
        tag.put("InputFaults", inputFaults.save());
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
        storedJoules = DeviceEnergyPolicy.persistentCapacity(kind()) > 0L
                ? Math.min(DeviceEnergyPolicy.persistentCapacity(kind()),
                        Math.max(0L, tag.getLong("StoredJoules")))
                : 0L;
        clearPassThroughEnergy();
        outputConversionRemainder = EnergyConversion.normalizeRemainder(
                tag.getLong("OutputConversionRemainder"), kind().efficiency());
        feInputConversionRemainder = EnergyConversion.normalizeRemainder(
                tag.getLong("FeInputConversionRemainder"), kind().efficiency());
        if (tag.contains("FuelEnergy")) {
            fuel.load(tag.getCompound("FuelEnergy"), kind().ratedPowerWatts());
        } else {
            fuel.load(tag.getInt("QueuedFuelTicks"), tag.getInt("BurningFuelTicks"));
        }
        if (tag.contains("FuelInventory")) {
            fuelInventory.load(tag.getCompound("FuelInventory"), registries);
        }
        fuseBlown = tag.getBoolean("FuseBlown");
        inputFaults.load(tag.getCompound("InputFaults"));
        inputState = GridState.offline(inputPort.wiring(), FaultKind.OPEN_SWITCH, false);
        outputState = GridState.offline(sourcePort.wiring(), FaultKind.OPEN_SWITCH, false);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

/** Energy ownership rules kept separate from the block entity's UI and fault state. */
final class DeviceEnergyPolicy {
    private DeviceEnergyPolicy() {}

    static boolean isPassThrough(ElectricalDeviceKind kind) {
        return kind.transformer() || kind.protection();
    }

    static long persistentCapacity(ElectricalDeviceKind kind) {
        return kind.converter() ? saturatingMultiply(kind.ratedPowerWatts(), 2L) : 0L;
    }

    static long passThroughJoulesPerTick(ElectricalDeviceKind kind) {
        if (!isPassThrough(kind)) return 0L;
        return saturatingAdd(passThroughLimitWatts(kind), ElectricalUnits.TICKS_PER_SECOND - 1L)
                / ElectricalUnits.TICKS_PER_SECOND;
    }

    static long roundingCarryJoules(ElectricalDeviceKind kind) {
        return isPassThrough(kind) && kind.nativeOutputEfficiency(true) < 1.0 ? 1L : 0L;
    }

    static long inputPowerForOutput(ElectricalDeviceKind kind, long outputWatts) {
        if (!isPassThrough(kind) || outputWatts <= 0L) return 0L;
        return Math.min(passThroughLimitWatts(kind), EnergyConversion.inputForOutput(
                outputWatts, kind.nativeOutputEfficiency(true)));
    }

    static long maxPassThroughOutputWatts(ElectricalDeviceKind kind) {
        if (!isPassThrough(kind)) return 0L;
        long limitWatts = passThroughLimitWatts(kind);
        return Math.min(limitWatts, EnergyConversion.outputForInput(
                limitWatts, kind.nativeOutputEfficiency(true)));
    }

    private static long passThroughLimitWatts(ElectricalDeviceKind kind) {
        return ProtectionModel.maximumThroughputWatts(kind);
    }

    static long saturatingAdd(long first, long second) {
        long safeFirst = Math.max(0L, first);
        long safeSecond = Math.max(0L, second);
        return safeSecond > Long.MAX_VALUE - safeFirst ? Long.MAX_VALUE : safeFirst + safeSecond;
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) return 0L;
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }
}
