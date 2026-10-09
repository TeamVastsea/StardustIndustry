package com.stardustindustry.utilsex;

import com.stardustindustry.utilsex.hud.HighlightCompat;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(StardustIndustryUtilsEx.MODID)
public final class StardustIndustryUtilsEx {

    public static final String MODID = "stardustindustry_utilsex";

    public StardustIndustryUtilsEx(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(HighlightCompat::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
