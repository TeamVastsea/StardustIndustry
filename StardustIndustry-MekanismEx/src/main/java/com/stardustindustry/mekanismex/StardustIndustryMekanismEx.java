package com.stardustindustry.mekanismex;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(StardustIndustryMekanismEx.MODID)
public final class StardustIndustryMekanismEx {

    public static final String MODID = "stardustindustry_mekanismex";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StardustIndustryMekanismEx(IEventBus modEventBus) {
        modEventBus.addListener(MekanismCompat::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MekanismCompat::registerGases);
    }
}
