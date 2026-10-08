package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.machine.module.FluidBufferModule;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Handles a right-click on a fluid port, moving fluid between the held container
 * and the machine the port serves.
 *
 * <h2>Why an event and not the block</h2>
 * A bucket handles its own right-click in {@code BucketItem.useOn}, which runs
 * <em>before</em> the block's {@code useItemOn} and, on a placeable spot, empties
 * itself onto the ground before the block is ever asked. A block therefore cannot
 * win this race on its own. {@link PlayerInteractEvent.RightClickBlock} fires
 * ahead of both, so the interaction is claimed here and the event cancelled,
 * which stops the vanilla placement.
 *
 * <p>This is the <b>only</b> hand-transfer path. The port block deliberately does
 * no fluid work of its own, so there is no second handler to race or to disagree
 * with this one.
 *
 * <h2>Why the machine's own handler, not the port's</h2>
 * The port's capability caps every operation at its rated throughput — 5000 mB
 * for an LV port. That is right for a pipe, which moves a little each tick, but
 * wrong for a hand: a container is emptied and filled in one atomic click. A
 * manual transfer is therefore done against the machine's fluid buffer directly,
 * with no per-call cap. Pipes are unaffected: they still connect to the port and
 * still see the port's cap.
 *
 * <h2>Containers of any size</h2>
 * The transfer asks the held item for its own fluid handler and moves whatever
 * that handler will give or take, so it works for a vanilla bucket (1000 mB), a
 * large drum (many buckets) and a sub-bucket vial (500 mB) without special cases.
 *
 * <h2>One fluid only</h2>
 * A tank holds a single fluid. A container of a different fluid is refused and
 * the click is still consumed, so a right-click on a port with the wrong fluid
 * does nothing at all rather than pouring it into the world.
 *
 * <h2>Creative debug transfer</h2>
 * Sneaking turns the click into a debug action: the machine gains or loses one
 * full container's worth of fluid while the held item is left untouched. This is
 * for building and testing, and is only honoured for creative players.
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
        ItemStack held = player.getItemInHand(hand);

        // An empty hand has nothing to transfer; leave it for other handlers.
        if (held.isEmpty()) {
            return;
        }

        // Only a fluid container (bucket, drum, vial, …) takes part. A copy with
        // count one lets a stacked container be handled one item at a time.
        IFluidHandlerItem container = FluidUtil.getFluidHandler(held.copyWithCount(1)).orElse(null);
        if (container == null) {
            return;
        }

        // The machine the port serves. An unbound port has no machine, so there
        // is nothing to transfer into and the click is left to the block.
        if (!(level.getBlockEntity(pos) instanceof MachinePortBlockEntity portEntity)) {
            return;
        }
        MachineBlockEntity machine = portEntity.controller();
        if (machine == null) {
            return;
        }
        FluidBufferModule fluid = machine.modules().get(FluidBufferModule.class);
        if (fluid == null) {
            return;
        }
        IFluidHandler handler = fluid.capability();

        // The click belongs to the port from here on, whatever it does: a
        // recognised fluid container on a bound fluid port is this handler's
        // business. That is what keeps a refused transfer (a fluid the tank does
        // not hold) from falling through to the bucket and emptying into the
        // world, and what keeps the port's own block from acting twice.
        boolean creativeDebug = player.isCreative() && player.isShiftKeyDown();
        if (!level.isClientSide()) {
            if (creativeDebug) {
                debugTransfer(container, handler);
            } else {
                ItemStack result = containerTransfer(container, handler);
                if (result != null) {
                    // The item is consumed one at a time and replaced by whatever
                    // the container becomes (full bucket -> empty bucket and vice
                    // versa), so the remainder of a stack is preserved.
                    held.shrink(1);
                    if (held.isEmpty()) {
                        player.setItemInHand(hand, result);
                    } else if (!player.getInventory().add(result)) {
                        player.drop(result, false);
                    }
                }
                // result == null means nothing moved (a different fluid, or the
                // tank already full of this one): fall through to cancelling
                // below, so the click is swallowed and the container is untouched.
            }
            machine.setChanged();
        }

        // Stop the bucket from also emptying itself into the world. Cancelling
        // the event stops the block's own use, and the two TriState flags stop
        // the item's use as well, since a bucket acts from useOn.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /**
     * Moves fluid between {@code container} and {@code handler} in whichever
     * direction the container allows.
     *
     * <p>A simulate-first drain decides the direction: a container either holds
     * fluid or it does not, so exactly one branch can apply. Amounts are bounded
     * by what each side will actually accept, so a container larger or smaller
     * than a bucket moves its true capacity.</p>
     *
     * @return the container's resulting item, or {@code null} when nothing moved
     */
    private static ItemStack containerTransfer(IFluidHandlerItem container, IFluidHandler handler) {
        FluidStack contained = container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!contained.isEmpty()) {
            // Container -> machine; the machine has no per-call cap.
            int accepted = handler.fill(contained, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) {
                return null;
            }
            FluidStack drained = container.drain(
                    contained.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                return null;
            }
            int filled = handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return filled > 0 ? container.getContainer() : null;
        }

        // Machine -> container, bounded by what the container will take.
        FluidStack available = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty()) {
            return null;
        }
        int accepted = container.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return null;
        }
        FluidStack drained = handler.drain(
                available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return null;
        }
        int filled = container.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        return filled > 0 ? container.getContainer() : null;
    }

    /**
     * Creative-only debug transfer: moves one container's worth of fluid between
     * the machine and the held item without consuming or producing an item.
     *
     * <p>The amount is the container's own capacity when empty (a whole bucket,
     * a whole drum), or its current contents when full, so the number matches
     * whatever the player is holding.</p>
     */
    private static void debugTransfer(IFluidHandlerItem container, IFluidHandler handler) {
        FluidStack contained = container.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!contained.isEmpty()) {
            // Held container is full: add one container's worth to the machine.
            handler.fill(contained, IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        // Held container is empty: remove one container's worth from the machine.
        // Its capacity is read by simulating a fill of a large stack.
        FluidStack available = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty()) {
            return;
        }
        int wanted = container.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (wanted <= 0) {
            // A container that cannot be filled from what the machine holds
            // still has a nominal size; fall back to a single bucket so the
            // debug action is not silently a no-op.
            wanted = 1000;
        }
        handler.drain(Math.min(wanted, available.getAmount()), IFluidHandler.FluidAction.EXECUTE);
    }
}
