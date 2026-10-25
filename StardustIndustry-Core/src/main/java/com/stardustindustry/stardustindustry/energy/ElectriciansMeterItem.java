package com.stardustindustry.stardustindustry.energy;

import java.util.List;

import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.port.EnergyPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.CablePicking;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.network.ElectricalDiagnosticRequestPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Handheld meter that opens a read-only, server-fed diagnostic session. */
public final class ElectriciansMeterItem extends Item {
    public ElectriciansMeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockEntity target = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(target instanceof ElectricalDeviceBlockEntity)
                && !(target instanceof MachineBlockEntity)
                && !(target instanceof EnergyPortBlockEntity)
                && !(target instanceof TerminalMountBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (target instanceof TerminalMountBlockEntity mount) {
            requestTerminalDiagnostic(context.getClickedPos(), mount);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            com.stardustindustry.stardustindustry.menu.ElectricalUi.openDiagnostic(
                    player, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    /** True when an empty main-hand click must be left for a meter in the offhand. */
    public static boolean offhandMeterOwnsEmptyMainHand(ItemStack mainHand, ItemStack offhand) {
        return mainHand.isEmpty() && offhand.getItem() instanceof ElectriciansMeterItem;
    }

    /** Sends the client-owned cable aim, or the rack itself when no cable was hit. */
    public static void requestTerminalDiagnostic(BlockPos rackPos, TerminalMountBlockEntity mount) {
        if (mount.getLevel() == null || !mount.getLevel().isClientSide()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                diagnosticRequest(rackPos, CablePicking.pick(mount)));
    }

    static ElectricalDiagnosticRequestPayload diagnosticRequest(BlockPos rackPos,
                                                                  CableConnection hitCable) {
        return hitCable == null
                ? ElectricalDiagnosticRequestPayload.terminal(rackPos)
                : ElectricalDiagnosticRequestPayload.cable(rackPos, hitCable.runId());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stardustindustry.electricians_meter.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
