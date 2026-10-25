package com.stardustindustry.stardustindustry.client;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.menu.CrusherMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Crusher inventory with energy, process progress and electrical fault readout. */
public final class CrusherScreen extends TelemetryScreen<CrusherMenu> {
    public CrusherScreen(CrusherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data) {
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.status", status(data)),
                10, 23, statusColour(data));
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.power",
                power(data.requestedPowerWatts()), power(data.deliveredPowerWatts())), 10, 36, TEXT);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.energy",
                energy(data.storedJoules()), energy(data.capacityJoules())), 10, 49, TEXT);
        bar(graphics, 10, 63, 208, 8, ratio(data.storedJoules(), data.capacityJoules()), 0xFF43A66B);
        line(graphics, Component.translatable("screen.stardustindustry.crusher.input"), 35, 103, MUTED);
        line(graphics, Component.translatable("screen.stardustindustry.crusher.outputs"), 112, 103, MUTED);
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.progress",
                data.progressPermille() / 10), 10, 118, TEXT);
        bar(graphics, 74, 118, 120, 8, data.progressPermille() / 1_000.0, 0xFFD79B35);
    }
}
