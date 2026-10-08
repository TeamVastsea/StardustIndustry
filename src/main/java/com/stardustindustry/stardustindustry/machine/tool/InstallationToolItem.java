package com.stardustindustry.stardustindustry.machine.tool;

import java.util.List;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.machine.InvisibleStructureBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.provider.ScanFailure;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The engineer's installation tool: the one item that turns a pile of blocks
 * into a working machine.
 *
 * <h2>Install</h2>
 * Right-click a controller to validate its structure. On success the machine is
 * locked, its body hidden behind invisible blocks and its contents preserved. On
 * failure, nothing changes and the player is told the first thing to fix.
 *
 * <h2>Parameters</h2>
 * Sneak-right-click <em>any</em> block of a machine — the controller, a port, a
 * hidden structure cell — to open its parameter screen, which shows the
 * structure's tier, buffers, fluid contents and offers a dismantle button. The
 * screen resolves the owning controller from whichever part was clicked, so the
 * player never has to find the exact controller block. Breaking a structure
 * block with a pickaxe instead destroys the machine's contents, so the screen is
 * the safe way to move a machine.
 */
public class InstallationToolItem extends Item {

    public InstallationToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        // The clicked block may be the controller itself, a port, or a hidden
        // structure cell. Only the first is a machine block entity; the other two
        // resolve to their controller, so sneak-right-click works anywhere on the
        // machine.
        MachineBlockEntity machine = resolveController(level, pos);
        if (machine == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            // The server decides; the client just plays the swing animation.
            return InteractionResult.SUCCESS;
        }

        if (player != null && player.isShiftKeyDown()) {
            openParams(machine, player);
            return InteractionResult.CONSUME;
        }

        // Installing is only meaningful on the controller, and only for a
        // machine that opts into installation. Dynamic machines form on their
        // own, so a right-click on them is a no-op with a hint.
        if (level.getBlockEntity(pos) != machine) {
            return InteractionResult.PASS;
        }

        install(level, pos, machine, player);
        return InteractionResult.CONSUME;
    }

    /**
     * Finds the machine a clicked block belongs to, or {@code null}.
     *
     * <p>A controller resolves to itself; a port resolves through its binding; a
     * hidden structure cell resolves through its recorded controller. Anything
     * else belongs to no machine and is ignored.</p>
     */
    private static MachineBlockEntity resolveController(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineBlockEntity machine) {
            return machine;
        }
        if (be instanceof MachinePortBlockEntity port) {
            return port.controller();
        }
        if (be instanceof InvisibleStructureBlockEntity invisible) {
            return invisible.controller();
        }
        // A tank's frame and glass have no block entity of their own, so their
        // controller is resolved through the tank membership table.
        if (com.stardustindustry.stardustindustry.multiblock.provider.TankStructureProvider
                .isTankMaterial(level.getBlockState(pos))) {
            return com.stardustindustry.stardustindustry.machine.tank.TankHudAccess.findTank(level, pos);
        }
        return null;
    }

    /**
     * Opens the machine's parameter screen for {@code player}.
     *
     * <p>The screen is informational and owns the dismantle button, so the
     * gesture that used to dismantle directly now shows the numbers first and
     * lets the player decide. Opening happens on the server, which also builds
     * the parameter snapshot; the client only renders it.</p>
     */
    private void openParams(MachineBlockEntity machine, Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new net.minecraft.world.SimpleMenuProvider(
                            (containerId, inventory, ignored) ->
                                    new com.stardustindustry.stardustindustry.machine.MachineParamsMenu(
                                            containerId, inventory, machine.paramsData()),
                            Component.translatable("screen.stardustindustry.machine_params")),
                    buffer -> com.stardustindustry.stardustindustry.machine.MachineParamsData.STREAM_CODEC
                            .encode(buffer, machine.paramsData()));
        }
    }

    private void install(Level level, BlockPos pos, MachineBlockEntity machine, Player player) {
        if (machine.isInstalled()) {
            message(player, ChatFormatting.YELLOW, "message.stardustindustry.install.already");
            return;
        }
        if (machine.provider() == null) {
            message(player, ChatFormatting.YELLOW, "message.stardustindustry.install.no_provider");
            return;
        }
        // A dynamic machine forms on its own, so it must not be locked: locking
        // would freeze a shape the player is meant to keep editing, and there is
        // no hidden body to restore on dismantle.
        if (machine.provider().revalidateWhileFormed()) {
            message(player, ChatFormatting.YELLOW, "message.stardustindustry.install.auto_forming");
            return;
        }

        boolean ok = machine.install();
        if (ok) {
            message(player, ChatFormatting.GREEN, "message.stardustindustry.install.success");
            return;
        }

        message(player, ChatFormatting.RED, "message.stardustindustry.install.incomplete");
        List<ScanFailure> failures = machine.evaluation() == null ? List.of() : machine.evaluation().failures();
        int shown = 0;
        for (ScanFailure failure : failures) {
            BlockPos at = failure.worldPos();
            message(player, ChatFormatting.GRAY,
                    Component.translatable("message.stardustindustry.install.needs",
                            at.getX(), at.getY(), at.getZ(), failure.expectationComponent()));
            if (++shown >= 5) {
                message(player, ChatFormatting.GRAY,
                        Component.translatable("message.stardustindustry.install.more", failures.size() - shown));
                break;
            }
        }
    }

    private static void message(Player player, ChatFormatting color, String key) {
        message(player, color, Component.translatable(key));
    }

    private static void message(Player player, ChatFormatting color, Component text) {
        if (player != null) {
            player.displayClientMessage(text.copy().withStyle(color), false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stardustindustry.installation_tool.tip.install")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.stardustindustry.installation_tool.tip.dismantle")
                .withStyle(ChatFormatting.GRAY));
    }

    /** True when the block at {@code state} is a machine controller this tool can act on. */
    public static boolean isController(BlockState state) {
        return state.getBlock() instanceof MachineBlock;
    }
}
