package com.stardustindustry.stardustindustry.energy.device;

import java.util.List;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A consumable replacement link for one voltage tier of fuse box. */
public final class FuseItem extends Item {
    private final EnergyTier tier;

    public FuseItem(Properties properties, EnergyTier tier) {
        super(properties);
        this.tier = tier;
    }

    public EnergyTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.stardustindustry.fuse.tip",
                Component.translatable("tier." + tier.translationKey()))
                .withStyle(ChatFormatting.GRAY));
    }
}
