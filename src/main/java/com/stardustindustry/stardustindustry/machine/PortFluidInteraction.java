package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Intercepts a right-click on a fluid port so a held bucket pours into the
 * machine instead of the world.
 *
 * <h2>Why an event and not the block</h2>
 * A bucket handles its own right-click in {@code BucketItem.useOn}, which runs
 * <em>before</em> the block's {@code useItemOn} and, on a placeable spot, empties
 * itself onto the ground before the block is ever asked. A block therefore cannot
 * win this race on its own. {@link PlayerInteractEvent.RightClickBlock} fires
 * ahead of both, so the same interaction can be claimed here and the event
 * cancelled, which stops the vanilla placement.
 *
 * <p>The fluid handler is fetched explicitly rather than through the
 * position-based helper: that keeps the code honest about the one case that
 * matters (a port bound to a machine exposes a handler, an unbound one does
 * not), and it means an inert port falls through to normal block behaviour
 * instead of being reported as handled.
 */
public final class PortFluidInteraction {

    private PortFluidInteraction() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // Only fluid ports take part; item and energy ports are left alone.
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MachinePortBlock port)
                || port.role() != com.stardustindustry.stardustindustry.multiblock.PartRole.PORT_FLUID) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();

        // An empty hand has nothing to transfer; leave it for other handlers.
        if (player.getItemInHand(hand).isEmpty()) {
            return;
        }

        if (level.isClientSide()) {
            com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.info(
                    "[port-fluid] right-click cl on fluid port at {} with {}",
                    pos, player.getItemInHand(hand).getItem());
            return;
        }
        com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.info(
                "[port-fluid] right-click srv on fluid port at {} with {}",
                pos, player.getItemInHand(hand).getItem());

        // The handler comes from the block capability, which is null on a port
        // that is not bound to a machine. A null handler means nothing to do.
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, pos, event.getHitVec().getDirection());
        com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.info(
                "[port-fluid] handler = {}", handler);
        if (handler == null) {
            return;
        }

        if (FluidUtil.interactWithFluidHandler(player, hand, handler)) {
            // Stop the bucket from also emptying itself into the world: the item
            // may not act, and the result tells both sides the click was used.
            event.setUseItem(TriState.FALSE);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }
}
