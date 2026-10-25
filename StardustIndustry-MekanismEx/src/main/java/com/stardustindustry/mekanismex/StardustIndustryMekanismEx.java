package com.stardustindustry.mekanismex;

import com.mojang.logging.LogUtils;
import com.stardustindustry.stardustindustry.demo.PowerDemoCompat;
import org.slf4j.Logger;

import net.minecraft.resources.ResourceLocation;
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
        PowerDemoCompat.registerFeStorage(
                ResourceLocation.fromNamespaceAndPath("mekanism", "basic_energy_cube"),
                MekanismCompat::configureEnergyCube);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MekanismCompat::registerGases);
    }
}
