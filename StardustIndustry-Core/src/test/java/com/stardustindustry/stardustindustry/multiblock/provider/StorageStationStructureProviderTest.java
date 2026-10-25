package com.stardustindustry.stardustindustry.multiblock.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.multiblock.BlockRole;

import net.minecraft.core.BlockPos;

class StorageStationStructureProviderTest {
    @Test
    void panelDoesNotSatisfyRequiredEnergyPort() {
        assertFalse(StorageStationStructureProvider.hasMatchingEnergyPort(
                Map.of(BlockPos.ZERO, BlockRole.PANEL)));
    }

    @Test
    void matchingEnergyPortSatisfiesRequirement() {
        assertTrue(StorageStationStructureProvider.hasMatchingEnergyPort(
                Map.of(BlockPos.ZERO, BlockRole.PORT)));
    }
}
