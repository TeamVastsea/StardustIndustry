package com.stardustindustry.stardustindustry.energy;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.machine.module.JouleStorageModule;
import com.stardustindustry.stardustindustry.machine.module.RecipeRunnerModule;
import com.stardustindustry.stardustindustry.machine.port.EnergyPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Immutable server-authored readout shown by all electrical control screens. */
public record ElectricalTelemetrySnapshot(
        BlockPos targetPos,
        String nameKey,
        Subject subject,
        boolean energized,
        boolean switchClosed,
        boolean formed,
        boolean running,
        String tierId,
        String wiringId,
        String faultId,
        String modeId,
        long requestedPowerWatts,
        long deliveredPowerWatts,
        long netPowerWatts,
        double phaseAAmps,
        double phaseBAmps,
        double phaseCAmps,
        double neutralAmps,
        long lineLossWatts,
        double efficiency,
        double maximumCableTemperatureC,
        double cableAmpacityAmps,
        long storedJoules,
        long capacityJoules,
        long estimatedSeconds,
        int progressPermille) {

    public enum Subject {
        DEVICE,
        CRUSHER,
        STORAGE,
        ENERGY_PORT,
        TERMINAL,
        CABLE,
        MACHINE,
        UNKNOWN
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricalTelemetrySnapshot> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ElectricalTelemetrySnapshot decode(RegistryFriendlyByteBuf buffer) {
                    return new ElectricalTelemetrySnapshot(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            subject(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                            buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.VAR_LONG.decode(buffer),
                            ByteBufCodecs.VAR_LONG.decode(buffer),
                            buffer.readLong(),
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                            ByteBufCodecs.VAR_LONG.decode(buffer), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble(),
                            ByteBufCodecs.VAR_LONG.decode(buffer), ByteBufCodecs.VAR_LONG.decode(buffer),
                            buffer.readLong(), ByteBufCodecs.VAR_INT.decode(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ElectricalTelemetrySnapshot value) {
                    BlockPos.STREAM_CODEC.encode(buffer, value.targetPos);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.nameKey);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.subject.name());
                    buffer.writeBoolean(value.energized);
                    buffer.writeBoolean(value.switchClosed);
                    buffer.writeBoolean(value.formed);
                    buffer.writeBoolean(value.running);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.tierId);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.wiringId);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.faultId);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.modeId);
                    ByteBufCodecs.VAR_LONG.encode(buffer, value.requestedPowerWatts);
                    ByteBufCodecs.VAR_LONG.encode(buffer, value.deliveredPowerWatts);
                    buffer.writeLong(value.netPowerWatts);
                    buffer.writeDouble(value.phaseAAmps);
                    buffer.writeDouble(value.phaseBAmps);
                    buffer.writeDouble(value.phaseCAmps);
                    buffer.writeDouble(value.neutralAmps);
                    ByteBufCodecs.VAR_LONG.encode(buffer, value.lineLossWatts);
                    buffer.writeDouble(value.efficiency);
                    buffer.writeDouble(value.maximumCableTemperatureC);
                    buffer.writeDouble(value.cableAmpacityAmps);
                    ByteBufCodecs.VAR_LONG.encode(buffer, value.storedJoules);
                    ByteBufCodecs.VAR_LONG.encode(buffer, value.capacityJoules);
                    buffer.writeLong(value.estimatedSeconds);
                    ByteBufCodecs.VAR_INT.encode(buffer, value.progressPermille);
                }
            };

    public ElectricalTelemetrySnapshot {
        targetPos = targetPos == null ? BlockPos.ZERO : targetPos.immutable();
        nameKey = safe(nameKey);
        subject = subject == null ? Subject.UNKNOWN : subject;
        tierId = safe(tierId);
        wiringId = safe(wiringId);
        faultId = safe(faultId);
        modeId = safe(modeId);
        requestedPowerWatts = Math.max(0L, requestedPowerWatts);
        deliveredPowerWatts = Math.max(0L, deliveredPowerWatts);
        lineLossWatts = Math.max(0L, lineLossWatts);
        efficiency = finite(efficiency, 0.0, 1.0);
        maximumCableTemperatureC = finite(maximumCableTemperatureC, 0.0, 10_000.0);
        cableAmpacityAmps = finite(cableAmpacityAmps, 0.0, Double.MAX_VALUE);
        storedJoules = Math.max(0L, storedJoules);
        capacityJoules = Math.max(0L, capacityJoules);
        estimatedSeconds = Math.max(-1L, estimatedSeconds);
        progressPermille = Math.clamp(progressPermille, 0, 1_000);
    }

    public static ElectricalTelemetrySnapshot capture(ServerLevel level, BlockPos target) {
        BlockEntity blockEntity = level.getBlockEntity(target);
        if (blockEntity instanceof ElectricalDeviceBlockEntity device) {
            return device(level, device);
        }
        if (blockEntity instanceof EnergyPortBlockEntity port) {
            return energyPort(level, port);
        }
        if (blockEntity instanceof StorageStationBlockEntity station) {
            return storage(level, station);
        }
        if (blockEntity instanceof CrusherBlockEntity crusher) {
            return crusher(level, crusher);
        }
        if (blockEntity instanceof MachineBlockEntity machine) {
            return machine(level, machine);
        }
        if (blockEntity instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity) {
            return terminal(level, target);
        }
        return empty(level, target);
    }

    /** Captures one physical cable by stable endpoint identity. */
    public static ElectricalTelemetrySnapshot captureCable(
            ServerLevel level, BlockPos clickedRack, CableRunId runId) {
        if (runId == null) {
            return empty(level, clickedRack);
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(level);
        CableConnection cable = data.cable(runId);
        if (cable == null || !runId.touches(clickedRack)) {
            return empty(level, clickedRack);
        }
        return cableSnapshot(clickedRack, cable,
                ElectricalGridService.energizedCable(level, runId),
                data.thermalState(runId, level.getGameTime()));
    }

    static ElectricalTelemetrySnapshot cableSnapshot(
            BlockPos clickedRack, CableConnection cable, EnergizedCable live,
            CableThermalModel.State thermal) {
        int volts = live == null ? 0 : live.volts();
        double currentAmps = live == null ? 0.0 : live.currentAmps();
        long lineLossWatts = live == null ? 0L : live.lineLossWatts();
        double temperatureC = 20.0 + (thermal == null ? 0.0 : thermal.heat()) * 180.0;
        EnergyTier tier = tierForVolts(volts);
        return new ElectricalTelemetrySnapshot(
                clickedRack,
                "item.stardustindustry.cable_" + cable.ampacity().id()
                        + "a_" + cable.insulation().id(),
                Subject.CABLE,
                volts > 0,
                volts > 0,
                true,
                currentAmps > 0.0,
                id(tier),
                Integer.toString(volts),
                FaultKind.NONE.name().toLowerCase(Locale.ROOT),
                cableColourId(cable.color()),
                0L,
                0L,
                0L,
                currentAmps,
                0.0,
                0.0,
                0.0,
                lineLossWatts,
                0.0,
                temperatureC,
                cable.effectiveAmpacity(),
                0L,
                0L,
                -1L,
                0);
    }

    private static ElectricalTelemetrySnapshot device(ServerLevel level, ElectricalDeviceBlockEntity device) {
        GridState state = device.telemetryState();
        long stored = device.telemetryStoredJoules();
        long capacity = device.telemetryCapacityJoules();
        return fromGrid(device.getBlockPos(), device.getBlockState().getBlock().getDescriptionId(),
                Subject.DEVICE, true, false, device.telemetryModeId(), state,
                device.telemetryRequestedPowerWatts(), stored, capacity, -1L, 0,
                cableMetrics(level, device.getBlockPos()));
    }

    private static ElectricalTelemetrySnapshot crusher(ServerLevel level, CrusherBlockEntity crusher) {
        RecipeRunnerModule runner = crusher.modules().get(RecipeRunnerModule.class);
        GridState state = crusher.primaryGridState();
        int progress = runner == null ? 0 : Math.round(runner.progressFraction() * 1_000.0f);
        long requested = runner != null && runner.isRunning()
                ? runner.powerWatts() : crusher.requestedPowerWatts();
        return fromGrid(crusher.getBlockPos(), crusher.getBlockState().getBlock().getDescriptionId(),
                Subject.CRUSHER, crusher.isFormed(), runner != null && runner.isRunning(), "", state,
                requested, crusher.energy().storedJoules(), crusher.energy().capacityJoules(), -1L,
                progress, machineCableMetrics(level, crusher));
    }

    private static ElectricalTelemetrySnapshot storage(ServerLevel level, StorageStationBlockEntity station) {
        GridState state = station.primaryGridState();
        long stored = station.energy().storedJoules();
        long capacity = station.energy().capacityJoules();
        long net = state == null ? 0L : state.netPowerWatts();
        long seconds = estimateSeconds(stored, capacity, net);
        return fromGrid(station.getBlockPos(), station.getBlockState().getBlock().getDescriptionId(),
                Subject.STORAGE, station.isFormed(), false, station.storageMode().id(), state,
                station.requestedPowerWatts(), stored, capacity, seconds, 0,
                machineCableMetrics(level, station));
    }

    private static ElectricalTelemetrySnapshot machine(ServerLevel level, MachineBlockEntity machine) {
        JouleStorageModule energy = machine.modules().get(JouleStorageModule.class);
        long stored = energy == null ? 0L : energy.storedJoules();
        long capacity = energy == null ? 0L : energy.capacityJoules();
        return fromGrid(machine.getBlockPos(), machine.getBlockState().getBlock().getDescriptionId(),
                Subject.MACHINE, machine.isFormed(), false, "", machine.primaryGridState(),
                0L, stored, capacity, -1L, 0, machineCableMetrics(level, machine));
    }

    private static ElectricalTelemetrySnapshot energyPort(ServerLevel level, EnergyPortBlockEntity port) {
        MachineBlockEntity controller = port.controller();
        JouleStorageModule energy = controller == null ? null : controller.modules().get(JouleStorageModule.class);
        long stored = energy == null ? 0L : energy.storedJoules();
        long capacity = energy == null ? 0L : energy.capacityJoules();
        String mode = controller instanceof StorageStationBlockEntity station ? station.storageMode().id() : "";
        return fromGrid(port.getBlockPos(), port.getBlockState().getBlock().getDescriptionId(),
                Subject.ENERGY_PORT, controller != null && controller.isFormed(), false, mode,
                port.gridState(), port.requestedPowerWatts(), stored, capacity, -1L, 0,
                cableMetrics(level, port.getBlockPos()));
    }

    private static ElectricalTelemetrySnapshot terminal(ServerLevel level, BlockPos pos) {
        CableMetrics cables = cableMetrics(level, pos);
        EnergyTier tier = level.getBlockState(pos).getBlock() instanceof TerminalMountBlock mount
                ? mount.tier() : null;
        boolean energized = ElectricalGridService.energizedCables(level).stream()
                .anyMatch(entry -> entry.cable().a().pos().equals(pos)
                        || entry.cable().b().pos().equals(pos));
        return new ElectricalTelemetrySnapshot(pos, level.getBlockState(pos).getBlock().getDescriptionId(),
                Subject.TERMINAL, energized, energized, true, false,
                id(tier), "", FaultKind.NONE.name().toLowerCase(Locale.ROOT), "",
                0L, 0L, 0L, 0.0, 0.0, 0.0, 0.0,
                0L, 0.0, cables.temperatureC(), cables.ampacity(), 0L, 0L, -1L, 0);
    }

    private static ElectricalTelemetrySnapshot empty(ServerLevel level, BlockPos pos) {
        return new ElectricalTelemetrySnapshot(pos, level.getBlockState(pos).getBlock().getDescriptionId(),
                Subject.UNKNOWN, false, false, false, false, "", "",
                FaultKind.NONE.name().toLowerCase(Locale.ROOT), "", 0L, 0L, 0L,
                0.0, 0.0, 0.0, 0.0, 0L, 0.0, 0.0, 0.0,
                0L, 0L, -1L, 0);
    }

    private static ElectricalTelemetrySnapshot fromGrid(
            BlockPos pos, String nameKey, Subject subject, boolean formed, boolean running,
            String mode, GridState state, long requestedFallback, long stored, long capacity,
            long estimatedSeconds, int progress, CableMetrics cables) {
        GridState actual = state == null
                ? GridState.offline(WiringMode.SINGLE_PHASE_2_WIRE, FaultKind.INCOMPLETE_WIRING, false)
                : state;
        EnergyTier tier = actual.supplyTier();
        WiringMode wiring = actual.wiring();
        double current = actual.currentAmps();
        double a = current;
        double b = wiring.threePhase() ? current : 0.0;
        double c = wiring.threePhase() ? current : 0.0;
        double neutral = wiring == WiringMode.SINGLE_PHASE_2_WIRE ? current : 0.0;
        long requested = actual.requestedPowerWatts() > 0L
                ? actual.requestedPowerWatts() : Math.max(0L, requestedFallback);
        return new ElectricalTelemetrySnapshot(pos, nameKey, subject, actual.energized(),
                actual.sourceSwitchClosed(), formed, running, id(tier), wiring.id(),
                actual.fault().name().toLowerCase(Locale.ROOT), safe(mode), requested,
                actual.deliveredPowerWatts(), actual.netPowerWatts(), a, b, c, neutral,
                actual.lineLossWatts(), actual.efficiency(), cables.temperatureC(), cables.ampacity(),
                stored, capacity, estimatedSeconds, progress);
    }

    private static CableMetrics cableMetrics(ServerLevel level, BlockPos pos) {
        return cableMetrics(level, connectedRackPositions(level, Set.of(pos)));
    }

    private static CableMetrics machineCableMetrics(ServerLevel level, MachineBlockEntity machine) {
        Set<BlockPos> connectionPoints = new HashSet<>(machine.boundPortPositions());
        connectionPoints.add(machine.getBlockPos());
        return cableMetrics(level, connectedRackPositions(level, connectionPoints));
    }

    /** Finds racks electrically mounted to the supplied device/port positions. */
    private static Set<BlockPos> connectedRackPositions(ServerLevel level,
                                                         Set<BlockPos> connectionPoints) {
        Set<BlockPos> racks = new HashSet<>();
        for (BlockPos point : connectionPoints) {
            if (level.getBlockEntity(point)
                    instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity) {
                racks.add(point);
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos candidate = point.relative(direction);
                if (level.getBlockEntity(candidate)
                        instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity rack
                        && candidate.relative(rack.facing()).equals(point)) {
                    racks.add(candidate.immutable());
                }
            }
        }
        return racks;
    }

    private static CableMetrics cableMetrics(ServerLevel level, Set<BlockPos> rackPositions) {
        double temperature = 0.0;
        double ampacity = 0.0;
        boolean found = false;
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(level);
        for (ElectricalNetworkSavedData.CableRecord record : data.records()) {
            CableConnection cable = record.connection();
            if (!rackPositions.contains(cable.a().pos()) && !rackPositions.contains(cable.b().pos())) {
                continue;
            }
            found = true;
            CableThermalModel.State thermal = data.thermalState(cable.runId(), level.getGameTime());
            temperature = Math.max(temperature, 20.0 + thermal.heat() * 180.0);
            ampacity = ampacity == 0.0 ? cable.effectiveAmpacity()
                    : Math.min(ampacity, cable.effectiveAmpacity());
        }
        return new CableMetrics(found ? temperature : 0.0, ampacity);
    }

    private static long estimateSeconds(long stored, long capacity, long netWatts) {
        if (netWatts > 0L) {
            return Math.max(0L, capacity - stored) / netWatts;
        }
        if (netWatts < 0L) {
            return stored / Math.max(1L, -netWatts);
        }
        return -1L;
    }

    /** Avoids network churn for sub-pixel bar changes and harmless meter noise. */
    public boolean materiallyDiffers(ElectricalTelemetrySnapshot other) {
        if (other == null) return true;
        return subject != other.subject || !nameKey.equals(other.nameKey)
                || energized != other.energized
                || switchClosed != other.switchClosed || formed != other.formed
                || running != other.running || !tierId.equals(other.tierId)
                || !wiringId.equals(other.wiringId) || !faultId.equals(other.faultId)
                || !modeId.equals(other.modeId) || capacityJoules != other.capacityJoules
                || progressPermille / 10 != other.progressPermille / 10
                || percent(storedJoules, capacityJoules) != percent(other.storedJoules, other.capacityJoules)
                || relativeDifference(requestedPowerWatts, other.requestedPowerWatts) >= 0.05
                || relativeDifference(deliveredPowerWatts, other.deliveredPowerWatts) >= 0.05
                || relativeDifference(netPowerWatts, other.netPowerWatts) >= 0.05
                || relativeDifference(lineLossWatts, other.lineLossWatts) >= 0.05
                || Math.abs(phaseAAmps - other.phaseAAmps) >= 1.0
                || Math.abs(efficiency - other.efficiency) >= 0.01
                || Math.abs(maximumCableTemperatureC - other.maximumCableTemperatureC) >= 2.0
                || Math.abs(cableAmpacityAmps - other.cableAmpacityAmps) >= 1.0;
    }

    private static int percent(long value, long maximum) {
        return maximum <= 0L ? 0 : (int) Math.clamp(value * 100L / maximum, 0L, 100L);
    }

    private static double relativeDifference(long first, long second) {
        long scale = Math.max(1L, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs((double) first - second) / scale;
    }

    private static Subject subject(String id) {
        try {
            return Subject.valueOf(id);
        } catch (IllegalArgumentException ignored) {
            return Subject.UNKNOWN;
        }
    }

    private static String id(EnergyTier tier) {
        return tier == null ? "" : tier.getSerializedName();
    }

    private static EnergyTier tierForVolts(int volts) {
        if (volts <= 0) return null;
        for (EnergyTier tier : EnergyTier.values()) {
            if (tier.volts() == volts || tier.singlePhaseVolts() == volts) {
                return tier;
            }
        }
        return null;
    }

    private static String cableColourId(CableColor colour) {
        if (colour == null || !colour.isPainted()) return "undyed";
        int dyeIndex = colour.dyeIndex();
        if (dyeIndex >= 0) {
            return new String[] {
                    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
            }[dyeIndex];
        }
        return String.format(Locale.ROOT, "#%06X", colour.dyeColor());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static double finite(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
    }

    private record CableMetrics(double temperatureC, double ampacity) {}
}
