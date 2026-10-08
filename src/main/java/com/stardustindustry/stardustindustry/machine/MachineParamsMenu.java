package com.stardustindustry.stardustindustry.machine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * The container behind the machine parameter screen.
 *
 * <p>It owns no slots: the parameter screen is informational plus a single
 * dismantle action, so there is no inventory to keep in sync. The menu exists
 * for three reasons: it gives the server a place to validate that the opening
 * player may act on this machine, it carries the {@link MachineParamsData}
 * snapshot the screen reads, and it is the context a future in-menu action would
 * use without another registration.</p>
 *
 * <p>The dismantle button does <em>not</em> go through this menu: it is a
 * separate request packet, so the action is validated server-side exactly like
 * any other tool use rather than trusting a client menu click.</p>
 */
public class MachineParamsMenu extends AbstractContainerMenu {

    private final MachineParamsData data;

    /** Client-side constructor, built from the extra data written when opening. */
    public MachineParamsMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, MachineParamsData.STREAM_CODEC.decode(extraData));
    }

    public MachineParamsMenu(int containerId, Inventory playerInventory, MachineParamsData data) {
        super(com.stardustindustry.stardustindustry.registry.ModMenus.MACHINE_PARAMS.get(), containerId);
        this.data = data;
    }

    /** The snapshot the screen renders. */
    public MachineParamsData data() {
        return data;
    }

    /** A parameter screen takes no items, so nothing may be moved into or out of it. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // The machine must still exist and the player must still be close enough
        // to touch it; this is what stops a stale screen acting at a distance.
        if (!(player.level().getBlockEntity(data.controllerPos()) instanceof MachineBlockEntity machine)) {
            return false;
        }
        return player.distanceToSqr(
                data.controllerPos().getX() + 0.5,
                data.controllerPos().getY() + 0.5,
                data.controllerPos().getZ() + 0.5) <= 64.0;
    }
}
