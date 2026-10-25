package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Interactive one-block electrical equipment menu, including generator fuel slots. */
public final class ElectricalDeviceMenu extends TelemetryMenu {
    private static final String GENERATOR_KEY = "block.stardustindustry.fuel_generator";

    public ElectricalDeviceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, ElectricalTelemetrySnapshot.STREAM_CODEC.decode(data), null);
    }

    public ElectricalDeviceMenu(int id, Inventory inventory, ElectricalDeviceBlockEntity device,
                                ElectricalTelemetrySnapshot snapshot) {
        this(id, inventory, snapshot, device == null ? null : device.generatorMenuHandler());
    }

    private ElectricalDeviceMenu(int id, Inventory inventory, ElectricalTelemetrySnapshot snapshot,
                                 IItemHandler serverHandler) {
        super(ModMenus.ELECTRICAL_DEVICE.get(), id, inventory, snapshot);
        if (GENERATOR_KEY.equals(snapshot.nameKey())) {
            IItemHandler handler = serverHandler == null ? new ItemStackHandler(2) : serverHandler;
            addSlot(new SlotItemHandler(handler, 0, 44, 100));
            addSlot(new SlotItemHandler(handler, 1, 116, 100) {
                @Override public boolean mayPlace(net.minecraft.world.item.ItemStack stack) { return false; }
            });
            machineSlotCount = 2;
        }
        addPlayerInventory(inventory, 35, 158);
    }

    @Override
    protected boolean isExpectedTarget(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof ElectricalDeviceBlockEntity;
    }
}
