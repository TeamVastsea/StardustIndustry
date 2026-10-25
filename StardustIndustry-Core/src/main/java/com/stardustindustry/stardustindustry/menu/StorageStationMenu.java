package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Storage station controls and live state-of-charge telemetry. */
public final class StorageStationMenu extends TelemetryMenu {
    public StorageStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, ElectricalTelemetrySnapshot.STREAM_CODEC.decode(data));
    }

    public StorageStationMenu(int id, Inventory inventory, ElectricalTelemetrySnapshot snapshot) {
        super(ModMenus.STORAGE_STATION.get(), id, inventory, snapshot);
        addPlayerInventory(inventory, 35, 158);
    }

    @Override
    protected boolean isExpectedTarget(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof StorageStationBlockEntity;
    }
}
