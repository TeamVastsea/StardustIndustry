package com.stardustindustry.ae2ex;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.fml.common.Mod;

@Mod(StardustIndustryAE2Ex.MODID)
public final class StardustIndustryAE2Ex {

    public static final String MODID = "stardustindustry_ae2ex";
    private static final Logger LOGGER = LogUtils.getLogger();

    public StardustIndustryAE2Ex() {
        LOGGER.info("Stardust Industry AE2 extension loaded");
    }
}
