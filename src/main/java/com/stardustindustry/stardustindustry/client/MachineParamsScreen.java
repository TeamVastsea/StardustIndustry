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
 */
public class MachineParamsScreen extends AbstractContainerScreen<MachineParamsMenu> {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 150;

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
        // a ledger to replay, so there would be nothing to dismantle.
        Button dismantle = Button.builder(
                        Component.translatable("screen.stardustindustry.machine_params.dismantle"),
                        button -> sendDismantle())
                .bounds(leftPos + imageWidth / 2 - 60, topPos + imageHeight - 26, 120, 20)
                .build();
        dismantle.active = data.installed();
        addRenderableWidget(dismantle);
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
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xC0101010);
        graphics.fill(x, y, x + imageWidth, y + 1, 0xFF606060);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF606060);
        graphics.fill(x, y, x + 1, y + imageHeight, 0xFF606060);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF606060);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineParamsData data = menu.data();
        int lineY = 8;
        for (Component line : lines(data)) {
            graphics.drawString(font, line, 10, lineY, 0xE0E0E0, false);
            lineY += 12;
        }
    }

    /** Formats the snapshot into the lines the screen draws. */
    private List<Component> lines(MachineParamsData data) {
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

    /** Renders a multiplier as a signed percentage, e.g. {@code 2.2} -> "+120%". */
    private static String formatMultiplier(float multiplier) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }
}
