package com.stardustindustry.stardustindustry.client;

import java.util.ArrayList;
import java.util.List;

import com.stardustindustry.stardustindustry.machine.MachineParamsData;
import com.stardustindustry.stardustindustry.machine.MachineParamsMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The machine parameter screen: what the player sees after sneak-right-clicking
 * a machine with the installation tool.
 *
 * <p>It is a pure read-out of the server-built {@link MachineParamsData}
 * snapshot, so there is no client-side model to keep in sync and no risk of the
 * screen and the machine disagreeing. The one control is the dismantle button,
 * which sends a request packet rather than acting locally: the server validates
 * it exactly as if the drop had been caused by the tool.</p>
 *
 * <h2>Two layouts</h2>
 * A snapshot that carries a {@link MachineParamsData.TankParams} is drawn with
 * the vessel layout — a bright "formed" headline, then the size, the structure
 * kind, the volume, the held fluid (in both languages) and the ports. Every
 * other machine falls back to the generic tier/speed/energy read-out. The choice
 * is made from the data alone, so the screen never has to know which block it
 * belongs to.
 */
public class MachineParamsScreen extends AbstractContainerScreen<MachineParamsMenu> {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 170;

    /** Bright yellow, for the "structure formed" headline. */
    private static final int COLOUR_HEADLINE = 0xFFFF55;
    /** Dark body text, the conventional Minecraft GUI black. */
    private static final int COLOUR_BODY = 0x404040;

    public MachineParamsScreen(MachineParamsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        MachineParamsData data = menu.data();

        // The button is only offered for an installed machine; nothing else has
        // a ledger to replay, so there would be nothing to dismantle. A tank
        // forms on its own and is torn down by breaking it, so it has no
        // dismantle button at all.
        if (data.tank() == null) {
            Button dismantle = Button.builder(
                            Component.translatable("screen.stardustindustry.machine_params.dismantle"),
                            button -> sendDismantle())
                    .bounds(leftPos + imageWidth / 2 - 60, topPos + imageHeight - 26, 120, 20)
                    .build();
            dismantle.active = data.installed();
            addRenderableWidget(dismantle);
        }
    }

    private void sendDismantle() {
        // Ask the server; it re-checks reach and installed state before acting.
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.stardustindustry.stardustindustry.network.DismantleRequestPayload(
                        menu.data().controllerPos()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // A light panel, so the dark body text the vessel layout asks for stays
        // readable. Vanilla's own container grey with a simple bevel.
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineParamsData data = menu.data();
        int lineY = 8;
        int index = 0;
        for (Component line : lines(data)) {
            // The vessel layout colours its first line as a headline; the
            // generic layout keeps one flat colour.
            int colour = index == 0 && data.tank() != null ? COLOUR_HEADLINE : COLOUR_BODY;
            graphics.drawString(font, line, 10, lineY, colour, false);
            lineY += 12;
            index++;
        }
    }

    /** Formats the snapshot into the lines the screen draws. */
    private List<Component> lines(MachineParamsData data) {
        return data.tank() != null ? vesselLines(data) : genericLines(data);
    }

    /**
     * The vessel layout: state, size, kind, volume, fluid and ports.
     *
     * <p>An unformed tank still shows the headline — in its own colour the
     * headline reads as a success, so an unformed one uses a neutral line
     * instead and stops there.</p>
     */
    private List<Component> vesselLines(MachineParamsData data) {
        MachineParamsData.TankParams tank = data.tank();
        List<Component> lines = new ArrayList<>();

        if (!data.formed()) {
            lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.unformed"));
            return lines;
        }

        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.formed"));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.size",
                tank.sizeX(), tank.sizeY(), tank.sizeZ()));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.structure",
                Component.translatable("structure.stardustindustry." + tank.structureName())));

        // Capacity is already in mB on the snapshot; the vessel layout speaks in
        // whole buckets (B), which is how a player sizes a tank.
        int capacityBuckets = data.fluidCapacity() / 1000;
        int storedBuckets = Math.max(0, data.fluidAmount()) / 1000;
        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.capacity",
                capacityBuckets));

        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.fluid",
                tank.fluidKey().isEmpty()
                        ? Component.translatable("screen.stardustindustry.machine_params.fluid.empty")
                        : fluidName(tank)));

        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.stored",
                storedBuckets));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.remaining",
                Math.max(0, capacityBuckets - storedBuckets)));

        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.ports",
                portList(tank.ports())));

        lines.add(Component.translatable("screen.stardustindustry.machine_params.vessel.max_rate",
                maxRate(tank)));

        return lines;
    }

    /**
     * Renders the port counts, one entry per tier, each as
     * {@code "LV 流体端口 ×1"}. An empty list becomes a single "none" entry so
     * the line is never blank.
     */
    private Component portList(String encoded) {
        if (encoded.isEmpty()) {
            return Component.translatable("screen.stardustindustry.machine_params.vessel.ports.none");
        }
        var result = Component.empty();
        boolean first = true;
        for (String entry : encoded.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length != 2) {
                continue;
            }
            if (!first) {
                result.append("，");
            }
            first = false;
            result.append(Component.translatable("screen.stardustindustry.machine_params.vessel.port_entry",
                    Component.translatable("tier.stardustindustry." + parts[0]).getString(),
                    parts[1]));
        }
        return result;
    }

    /**
     * The fastest port, as {@code "LV 5B/s"}. The stored rate is mB per tick, so
     * it is converted to buckets per second for display.
     */
    private Component maxRate(MachineParamsData.TankParams tank) {
        if (tank.maxRateTier().isEmpty()) {
            return Component.translatable("screen.stardustindustry.machine_params.vessel.ports.none");
        }
        double bucketsPerSecond = tank.maxRateMbPerTick() * 20.0 / 1000.0;
        String rate = bucketsPerSecond == Math.floor(bucketsPerSecond)
                ? Long.toString((long) bucketsPerSecond)
                : String.format("%.2f", bucketsPerSecond);
        return Component.translatable("screen.stardustindustry.machine_params.vessel.rate_entry",
                Component.translatable("tier.stardustindustry." + tank.maxRateTier()).getString(),
                rate);
    }

    /** The generic layout: tier, status, multipliers, buffers and fluid. */
    private List<Component> genericLines(MachineParamsData data) {
        List<Component> lines = new ArrayList<>();

        String tier = data.tierName().isEmpty()
                ? Component.translatable("screen.stardustindustry.machine_params.tier.none").getString()
                : Component.translatable("tier.stardustindustry." + data.tierName()).getString();
        lines.add(Component.translatable("screen.stardustindustry.machine_params.tier", tier));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.status",
                Component.translatable(data.formed()
                        ? "screen.stardustindustry.machine_params.status.formed"
                        : "screen.stardustindustry.machine_params.status.unformed").getString()));

        lines.add(Component.translatable("screen.stardustindustry.machine_params.speed",
                formatMultiplier(data.speed())));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.energy",
                formatMultiplier(data.energy())));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.parallel",
                String.valueOf(data.parallel())));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.buffer",
                formatMultiplier(data.buffer())));
        lines.add(Component.translatable("screen.stardustindustry.machine_params.fillers",
                String.valueOf(data.fillerCount())));

        if (data.energyMax() >= 0) {
            lines.add(Component.translatable("screen.stardustindustry.machine_params.energy_stored",
                    data.energyStored(), data.energyMax()));
        }

        if (data.fluidCapacity() >= 0) {
            lines.add(Component.translatable("screen.stardustindustry.machine_params.fluid",
                    data.fluidName().isEmpty()
                            ? Component.translatable("screen.stardustindustry.machine_params.fluid.empty").getString()
                            : data.fluidName()));
            lines.add(Component.translatable("screen.stardustindustry.machine_params.fluid_amount",
                    data.fluidAmount(), data.fluidCapacity()));
            lines.add(Component.translatable("screen.stardustindustry.machine_params.fluid_in",
                    data.fluidInRate()));
            lines.add(Component.translatable("screen.stardustindustry.machine_params.fluid_out",
                    data.fluidOutRate()));
        }
        return lines;
    }

    /**
     * The held fluid as {@code "水 Water"}: the player's own language first, then
     * the English name the server resolved.
     *
     * <p>Both halves are needed at once because the two languages come from
     * different places — the current language only exists on the client, and
     * English only on the server — so neither side can build the whole line
     * alone. When the names already match, one is shown.</p>
     */
    private Component fluidName(MachineParamsData.TankParams tank) {
        String current = net.minecraft.locale.Language.getInstance().getOrDefault(tank.fluidKey());
        String english = tank.fluidNameEn();
        if (english.isEmpty() || english.equals(current)) {
            return Component.literal(current);
        }
        return Component.literal(current + " " + english);
    }

    /** Renders a multiplier as a signed percentage, e.g. {@code 2.2} -> "+120%". */
    private static String formatMultiplier(float multiplier) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }
}
