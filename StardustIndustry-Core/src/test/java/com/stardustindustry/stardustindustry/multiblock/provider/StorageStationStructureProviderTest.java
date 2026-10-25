package com.stardustindustry.stardustindustry.multiblock.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.machine.storage.StorageTier;

import net.minecraft.core.BlockPos;

class StorageStationStructureProviderTest {
    @Test
    void panelDoesNotSatisfyRequiredEnergyPort() {
        assertFalse(StorageStationStructureProvider.hasSufficientEnergyPorts(
                Map.of(BlockPos.ZERO, BlockRole.PANEL), StorageTier.LV));
    }

    @Test
    void matchingEnergyPortSatisfiesRequirement() {
        assertTrue(StorageStationStructureProvider.hasSufficientEnergyPorts(
                Map.of(BlockPos.ZERO, BlockRole.PORT), StorageTier.LV));
        assertTrue(StorageStationStructureProvider.hasSufficientEnergyPorts(
                Map.of(BlockPos.ZERO, BlockRole.PORT), StorageTier.MV));
    }

    @Test
    void highVoltageSinglePostPortsMustCoverAllThreePhases() {
        Map<BlockPos, BlockRole> twoPorts = new LinkedHashMap<>();
        twoPorts.put(BlockPos.ZERO, BlockRole.PORT);
        twoPorts.put(BlockPos.ZERO.east(), BlockRole.PORT);
        assertFalse(StorageStationStructureProvider.hasSufficientEnergyPorts(
                twoPorts, StorageTier.HV));

        Map<BlockPos, BlockRole> threePorts = new LinkedHashMap<>(twoPorts);
        threePorts.put(BlockPos.ZERO.west(), BlockRole.PORT);
        assertTrue(StorageStationStructureProvider.hasSufficientEnergyPorts(
                threePorts, StorageTier.HV));
        assertTrue(StorageStationStructureProvider.hasSufficientEnergyPorts(
                threePorts, StorageTier.EHV));

        assertTrue(StorageStationStructureProvider.requiredEnergyPortCount(StorageTier.LV) == 1);
        assertTrue(StorageStationStructureProvider.requiredEnergyPortCount(StorageTier.MV) == 1);
        assertTrue(StorageStationStructureProvider.requiredEnergyPortCount(StorageTier.HV) == 3);
        assertTrue(StorageStationStructureProvider.requiredEnergyPortCount(StorageTier.EHV) == 3);
    }
}
