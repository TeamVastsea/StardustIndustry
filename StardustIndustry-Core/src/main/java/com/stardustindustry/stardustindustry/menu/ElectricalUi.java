package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.energy.CableRunId;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Server-only helpers for opening electrical menus with their initial snapshot. */
public final class ElectricalUi {
    private ElectricalUi() {}

    public static void openDevice(ServerPlayer player, ElectricalDeviceBlockEntity device) {
        ElectricalTelemetrySnapshot snapshot = capture(player, device.getBlockPos());
        player.openMenu(new SimpleMenuProvider(
                        (id, inventory, ignored) -> new ElectricalDeviceMenu(id, inventory, device, snapshot),
                        Component.translatable("screen.stardustindustry.electrical_device")),
                buffer -> ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, snapshot));
    }

    public static void openMachine(ServerPlayer player, MachineBlockEntity machine) {
        ElectricalTelemetrySnapshot snapshot = capture(player, machine.getBlockPos());
        if (machine instanceof CrusherBlockEntity crusher) {
            player.openMenu(new SimpleMenuProvider(
                            (id, inventory, ignored) -> new CrusherMenu(id, inventory, crusher, snapshot),
                            Component.translatable("screen.stardustindustry.crusher")),
                    buffer -> ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, snapshot));
        } else if (machine instanceof StorageStationBlockEntity) {
            player.openMenu(new SimpleMenuProvider(
                            (id, inventory, ignored) -> new StorageStationMenu(id, inventory, snapshot),
                            Component.translatable("screen.stardustindustry.storage_station")),
                    buffer -> ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, snapshot));
        } else {
            openDiagnostic(player, machine.getBlockPos());
        }
    }

    public static void openDiagnostic(ServerPlayer player, BlockPos pos) {
        ElectricalTelemetrySnapshot snapshot = capture(player, pos);
        player.openMenu(new SimpleMenuProvider(
                        (id, inventory, ignored) -> new ElectricalDiagnosticMenu(id, inventory, snapshot),
                        Component.translatable("screen.stardustindustry.electrical_diagnostic")),
                buffer -> {
                    ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, snapshot);
                    buffer.writeBoolean(false);
                });
    }

    public static void openCableDiagnostic(ServerPlayer player, BlockPos clickedRack,
                                           CableRunId cableRun) {
        ElectricalTelemetrySnapshot snapshot = ElectricalTelemetrySnapshot.captureCable(
                (ServerLevel) player.level(), clickedRack, cableRun);
        player.openMenu(new SimpleMenuProvider(
                        (id, inventory, ignored) -> new ElectricalDiagnosticMenu(
                                id, inventory, snapshot, cableRun),
                        Component.translatable("screen.stardustindustry.electrical_diagnostic")),
                buffer -> {
                    ElectricalTelemetrySnapshot.STREAM_CODEC.encode(buffer, snapshot);
                    buffer.writeBoolean(true);
                    CableRunId.STREAM_CODEC.encode(buffer, cableRun);
                });
    }

    private static ElectricalTelemetrySnapshot capture(ServerPlayer player, BlockPos pos) {
        return ElectricalTelemetrySnapshot.capture((ServerLevel) player.level(), pos);
    }
}
