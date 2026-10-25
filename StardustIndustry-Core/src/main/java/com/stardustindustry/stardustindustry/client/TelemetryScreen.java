package com.stardustindustry.stardustindustry.client;

import java.util.Locale;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.menu.TelemetryMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Quiet industrial panel shared by the electrical control and diagnostic screens. */
public abstract class TelemetryScreen<T extends TelemetryMenu> extends AbstractContainerScreen<T> {
    protected static final int PANEL_WIDTH = 230;
    protected static final int PANEL_HEIGHT = 254;
    protected static final int TEXT = 0xFF30343A;
    protected static final int MUTED = 0xFF666B72;
    protected static final int GOOD = 0xFF257A45;
    protected static final int WARN = 0xFFC07818;
    protected static final int BAD = 0xFFB33A32;

    protected TelemetryScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 146;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFE1E3E5);
        graphics.fill(x, y, x + imageWidth, y + 2, 0xFFFFFFFF);
        graphics.fill(x, y, x + 2, y + imageHeight, 0xFFFFFFFF);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, 0xFF555A60);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, 0xFF555A60);
        graphics.fill(x + 7, y + 16, x + imageWidth - 7, y + 17, 0xFF8C9298);
        renderTelemetry(graphics, menu.snapshot());
    }

    protected abstract void renderTelemetry(GuiGraphics graphics, ElectricalTelemetrySnapshot data);

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 6, TEXT, false);
        if (menu.hasPlayerInventory()) {
            graphics.drawString(font, playerInventoryTitle, 35, inventoryLabelY, MUTED, false);
        }
    }

    protected final void line(GuiGraphics graphics, Component text, int x, int y, int colour) {
        graphics.drawString(font, text, leftPos + x, topPos + y, colour, false);
    }

    protected final void bar(GuiGraphics graphics, int x, int y, int width, int height,
                             double fraction, int colour) {
        int left = leftPos + x;
        int top = topPos + y;
        graphics.fill(left, top, left + width, top + height, 0xFF777C82);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, 0xFF252A2F);
        int filled = (int) Math.round((width - 2) * Math.clamp(fraction, 0.0, 1.0));
        if (filled > 0) graphics.fill(left + 1, top + 1, left + 1 + filled, top + height - 1, colour);
    }

    protected static Component status(ElectricalTelemetrySnapshot data) {
        String key = data.faultId().isEmpty() || "none".equals(data.faultId())
                ? (data.energized() ? "screen.stardustindustry.telemetry.energized"
                : "screen.stardustindustry.telemetry.offline")
                : "fault.stardustindustry." + data.faultId();
        return Component.translatable(key);
    }

    protected static int statusColour(ElectricalTelemetrySnapshot data) {
        if (!data.faultId().isEmpty() && !"none".equals(data.faultId())) return BAD;
        return data.energized() ? GOOD : MUTED;
    }

    protected static String power(long watts) {
        return quantity(watts, "W");
    }

    protected static String energy(long joules) {
        if (joules >= 3_600_000L) return String.format(Locale.ROOT, "%.2f kWh", joules / 3_600_000.0);
        return quantity(joules, "J");
    }

    protected static String quantity(long value, String unit) {
        long absolute = Math.abs(value);
        if (absolute >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2f G%s", value / 1e9, unit);
        if (absolute >= 1_000_000L) return String.format(Locale.ROOT, "%.2f M%s", value / 1e6, unit);
        if (absolute >= 1_000L) return String.format(Locale.ROOT, "%.2f k%s", value / 1e3, unit);
        return value + " " + unit;
    }

    protected static String current(double amps) {
        return amps >= 1_000.0
                ? String.format(Locale.ROOT, "%.2f kA", amps / 1_000.0)
                : String.format(Locale.ROOT, "%.2f A", amps);
    }

    protected static double ratio(long value, long maximum) {
        return maximum <= 0L ? 0.0 : value / (double) maximum;
    }
}
