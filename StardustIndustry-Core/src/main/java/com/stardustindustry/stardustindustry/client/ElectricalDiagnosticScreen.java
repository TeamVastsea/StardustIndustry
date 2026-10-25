package com.stardustindustry.stardustindustry.client;

import java.util.Locale;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.menu.ElectricalDiagnosticMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Read-only electrician's meter display. */
public final class ElectricalDiagnosticScreen extends TelemetryScreen<ElectricalDiagnosticMenu> {
    public ElectricalDiagnosticScreen(ElectricalDiagnosticMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data) {
        if (data.subject() == ElectricalTelemetrySnapshot.Subject.CABLE) {
            renderCableTelemetry(graphics, data);
            return;
        }

        int y = 24;
        line(graphics, Component.translatable(data.nameKey()), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.status", status(data)),
                10, y, statusColour(data)); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.tier_wiring",
                data.tierId().isEmpty() ? "-" : data.tierId().toUpperCase(Locale.ROOT),
                data.wiringId().isEmpty() ? "-" : Component.translatable(
                        "message.stardustindustry.electrical.wiring." + data.wiringId())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.power",
                power(data.requestedPowerWatts()), power(data.deliveredPowerWatts())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.phase_current",
                current(data.phaseAAmps()), current(data.phaseBAmps()), current(data.phaseCAmps()),
                current(data.neutralAmps())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.loss_efficiency",
                power(data.lineLossWatts()), Math.round(data.efficiency() * 100.0)), 10, y, TEXT); y += 14;
        if (data.cableAmpacityAmps() > 0.0) {
            line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable",
                    current(data.cableAmpacityAmps()), String.format(Locale.ROOT, "%.1f C",
                            data.maximumCableTemperatureC())), 10, y, TEXT); y += 14;
        }
        if (data.capacityJoules() > 0L) {
            line(graphics, Component.translatable("screen.stardustindustry.telemetry.energy",
                    energy(data.storedJoules()), energy(data.capacityJoules())), 10, y, TEXT); y += 14;
            bar(graphics, 10, y, 208, 10, ratio(data.storedJoules(), data.capacityJoules()), 0xFF43A66B);
        }
    }

    private void renderCableTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data) {
        int y = 24;
        line(graphics, Component.translatable(data.nameKey()), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_colour",
                cableColour(data.modeId())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.status", status(data)),
                10, y, statusColour(data)); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_voltage",
                voltage(data.wiringId()), data.tierId().isEmpty()
                        ? "-" : data.tierId().toUpperCase(Locale.ROOT)), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_current",
                current(data.phaseAAmps())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_loss",
                power(data.lineLossWatts())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_rating",
                current(data.cableAmpacityAmps())), 10, y, TEXT); y += 14;
        line(graphics, Component.translatable("screen.stardustindustry.telemetry.cable_temperature",
                String.format(Locale.ROOT, "%.1f C", data.maximumCableTemperatureC())), 10, y, TEXT);
    }

    private static Component cableColour(String id) {
        if (id == null || id.isEmpty() || "undyed".equals(id)) {
            return Component.translatable("screen.stardustindustry.telemetry.cable_colour.undyed");
        }
        if (id.startsWith("#")) {
            return Component.literal(id.toUpperCase(Locale.ROOT));
        }
        return Component.translatable("color.minecraft." + id);
    }

    private static String voltage(String id) {
        if (id == null || id.isEmpty()) return "-";
        try {
            return quantity(Long.parseLong(id), "V");
        } catch (NumberFormatException ignored) {
            return id;
        }
    }
}
