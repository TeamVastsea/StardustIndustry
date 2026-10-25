package com.stardustindustry.stardustindustry.client;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.machine.storage.StorageMode;
import com.stardustindustry.stardustindustry.menu.StorageStationMenu;
import com.stardustindustry.stardustindustry.network.StorageModePayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Storage station state-of-charge and policy controls. */
public final class StorageStationScreen extends TelemetryScreen<StorageStationMenu> {
    public StorageStationScreen(StorageStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 10;
        int y = topPos + 123;
        for (StorageMode mode : StorageMode.values()) {
            addRenderableWidget(Button.builder(
                            Component.translatable("storage_mode.stardustindustry." + mode.id()),
                            ignored -> PacketDistributor.sendToServer(
                                    new StorageModePayload(menu.targetPos(), mode)))
                    .bounds(x, y, 50, 20).build());
            x += 54;
        }
    }

    @Override
    protected void renderTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data) {
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.status", status(data)),
                10, 23, statusColour(data));
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.mode",
                Component.translatable("storage_mode.stardustindustry." + data.modeId())), 10, 36, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.energy",
                energy(data.storedJoules()), energy(data.capacityJoules())), 10, 49, TEXT);
        bar(graphics, 10, 64, 208, 12, ratio(data.storedJoules(), data.capacityJoules()), 0xFF43A66B);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.soc",
                Math.round(ratio(data.storedJoules(), data.capacityJoules()) * 100.0)), 10, 82, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.net_power",
                power(data.netPowerWatts())), 10, 95, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.eta",
                data.estimatedSeconds() < 0 ? "-" : data.estimatedSeconds() + " s"), 10, 108, TEXT);
    }
}
