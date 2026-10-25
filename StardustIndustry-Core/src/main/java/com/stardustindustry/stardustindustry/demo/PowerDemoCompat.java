package com.stardustindustry.stardustindustry.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Optional extension hooks used while building the cross-mod power demo. */
public final class PowerDemoCompat {
    private static final Map<ResourceLocation, FeStorageConfigurator> FE_STORAGE =
            new ConcurrentHashMap<>();

    private PowerDemoCompat() {}

    public static void registerFeStorage(
            ResourceLocation blockId, FeStorageConfigurator configurator) {
        FE_STORAGE.put(blockId, configurator);
    }

    public static boolean configureFeStorage(
            ResourceLocation blockId, ServerLevel level, BlockPos pos, Direction connectingFace) {
        FeStorageConfigurator configurator = FE_STORAGE.get(blockId);
        return configurator != null && configurator.configure(level, pos, connectingFace);
    }

    @FunctionalInterface
    public interface FeStorageConfigurator {
        boolean configure(ServerLevel level, BlockPos pos, Direction connectingFace);
    }
}
