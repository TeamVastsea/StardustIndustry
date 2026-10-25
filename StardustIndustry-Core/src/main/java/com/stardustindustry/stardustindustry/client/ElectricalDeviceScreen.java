package com.stardustindustry.stardustindustry.client;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.menu.ElectricalDeviceMenu;
import com.stardustindustry.stardustindustry.network.DeviceControlPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Control panel for generators, transformers, protection and FE converters. */
public final class ElectricalDeviceScreen extends TelemetryScreen<ElectricalDeviceMenu> {
    private Button switchButton;
    private Button configureButton;
    private Button voltageButton;

    public ElectricalDeviceScreen(ElectricalDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        switchButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> send(
                        DeviceControlPayload.Action.TOGGLE_SWITCH))
                .bounds(leftPos + 12, topPos + 123, 62, 20).build());
        configureButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.stardustindustry.telemetry.configure"),
                        ignored -> send(DeviceControlPayload.Action.CYCLE_CONFIGURATION))
                .bounds(leftPos + 80, topPos + 123, 68, 20).build());
        voltageButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.stardustindustry.telemetry.voltage"),
                        ignored -> send(DeviceControlPayload.Action.CYCLE_CREATIVE_TIER))
                .bounds(leftPos + 154, topPos + 123, 64, 20).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    private void updateButton() {
        if (switchButton != null) {
            switchButton.setMessage(Component.translatable(menu.snapshot().switchClosed()
                    ? "screen.stardustindustry.telemetry.open" : "screen.stardustindustry.telemetry.close"));
        }
        String key = menu.snapshot().nameKey();
        if (configureButton != null) {
            configureButton.active = key.endsWith("fuel_generator")
                    || key.endsWith("creative_power_source") || key.contains("transformer_")
                    || key.endsWith("fe_converter");
        }
        if (voltageButton != null) {
            voltageButton.active = key.endsWith("creative_power_source");
        }
    }

    private void send(DeviceControlPayload.Action action) {
        PacketDistributor.sendToServer(new DeviceControlPayload(menu.targetPos(), action));
    }

    @Override
    protected void renderTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data) {
        line(graphics, Component.translatable(data.nameKey()), 10, 23, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.status", status(data)),
                10, 36, statusColour(data));
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.tier_wiring",
                data.tierId().isEmpty() ? "-" : data.tierId().toUpperCase(),
                data.wiringId().isEmpty() ? "-" : Component.translatable(
                        "message.stardustindustry.electrical.wiring." + data.wiringId())), 10, 49, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.power",
                power(data.requestedPowerWatts()), power(data.deliveredPowerWatts())), 10, 62, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.phase_current",
                current(data.phaseAAmps()), current(data.phaseBAmps()), current(data.phaseCAmps()),
                current(data.neutralAmps())), 10, 75, TEXT);
        boolean generator = "block.stardustindustry.fuel_generator".equals(data.nameKey());
        if (!generator && !data.modeId().isEmpty()) {
            line(graphics, Component.translatable("screen.stardustindustry.telemetry.mode",
                    Component.translatable("device_mode.stardustindustry." + data.modeId())), 10, 88, TEXT);
        }
        if (data.capacityJoules() > 0L || data.storedJoules() > 0L) {
            line(graphics, Component.translatable("screen.stardustindustry.telemetry.energy",
                    energy(data.storedJoules()), data.capacityJoules() > 0L
                            ? energy(data.capacityJoules()) : "-"), 10, generator ? 88 : 101, TEXT);
            if (!generator && data.capacityJoules() > 0L) {
                bar(graphics, 10, 114, 208, 7,
                        ratio(data.storedJoules(), data.capacityJoules()), 0xFF43A66B);
            }
        }
    }
}
