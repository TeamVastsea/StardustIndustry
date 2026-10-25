package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Crusher inventory and live process telemetry. */
public final class CrusherMenu extends TelemetryMenu {
    public CrusherMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, ElectricalTelemetrySnapshot.STREAM_CODEC.decode(data), null);
    }

    public CrusherMenu(int id, Inventory inventory, CrusherBlockEntity crusher,
                       ElectricalTelemetrySnapshot snapshot) {
        this(id, inventory, snapshot, crusher == null ? null : crusher.inventory().handler());
    }

    private CrusherMenu(int id, Inventory inventory, ElectricalTelemetrySnapshot snapshot,
                        IItemHandler serverHandler) {
        super(ModMenus.CRUSHER.get(), id, inventory, snapshot);
        IItemHandler handler = serverHandler == null ? new ItemStackHandler(4) : serverHandler;
        addSlot(new SlotItemHandler(handler, 0, 43, 82));
        addSlot(output(handler, 1, 116, 82));
        addSlot(output(handler, 2, 136, 82));
        addSlot(new SlotItemHandler(handler, 3, 63, 82));
        machineSlotCount = 4;
        addPlayerInventory(inventory, 35, 158);
    }

    private static SlotItemHandler output(IItemHandler handler, int index, int x, int y) {
        return new SlotItemHandler(handler, index, x, y) {
            @Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack) { return false; }
        };
    }

    @Override
    protected boolean isExpectedTarget(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof CrusherBlockEntity;
    }
}
