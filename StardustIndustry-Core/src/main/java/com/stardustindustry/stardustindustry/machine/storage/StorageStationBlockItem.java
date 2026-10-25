package com.stardustindustry.stardustindustry.machine.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Block item tooltip for the electrical ratings shared by station components. */
public final class StorageStationBlockItem extends BlockItem {

    private final StorageTier stationTier;

    public StorageStationBlockItem(Block block, Properties properties, StorageTier stationTier) {
        super(block, properties);
        this.stationTier = stationTier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stardustindustry.storage_station.tip.rating",
                stationTier.energyTier().volts(), stationTier.amperage(),
                stationTier.nominalPowerWatts() / 1_000L).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.stardustindustry.storage_station.tip.cell_capacity",
                stationTier.capacityPerCellKilowattHours()).withStyle(ChatFormatting.GRAY));
    }
}
