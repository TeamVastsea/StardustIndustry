package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
 * <p>The actual transfer still goes through NeoForge's fluid-handler helper, so
 * every container behaves the same and the block keeps its own {@code useItemOn}
 * for the cases that do reach it.
 */
public final class PortFluidInteraction {

    private PortFluidInteraction() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // Only fluid ports with a bound machine have a fluid handler to move into.
        if (!(level.getBlockState(pos).getBlock() instanceof MachinePortBlock port)
                || port.role() != com.stardustindustry.stardustindustry.multiblock.PartRole.PORT_FLUID) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();

        // An empty hand has nothing to transfer; leave it for other handlers.
        if (player.getItemInHand(hand).isEmpty()) {
            return;
        }

        if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(
                player, hand, level, pos, event.getHitVec().getDirection())) {
            // Stop the bucket from also emptying itself into the world: the item
            // may not act, and the result tells both sides the click was used.
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }
}
