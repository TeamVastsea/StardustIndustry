package com.stardustindustry.stardustindustry.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation;

import net.minecraft.core.BlockPos;

class MachinePortLifecycleTest {
    @Test
    void firstProviderEvaluationAfterReloadDropsPersistedPortsItNoLongerContains() {
        BlockPos removed = new BlockPos(1, 2, 3);
        BlockPos retained = new BlockPos(4, 5, 6);
        BlockPos added = new BlockPos(7, 8, 9);
        Set<BlockPos> persisted = Set.of(removed, retained);
        StructureEvaluation current = StructureEvaluation.formed(
                EnergyTier.LV,
                ModifierSet.BASE,
                Map.of(retained, BlockRole.PORT, added, BlockRole.PORT),
                Map.of());

        Set<BlockPos> currentPorts = MachineBlockEntity.providerPortPositions(current);

        assertEquals(Set.of(retained, added), currentPorts);
        assertEquals(Set.of(removed), MachineBlockEntity.removedPortPositions(persisted, currentPorts));
    }

    @Test
    void nonPortRolesNeverBecomeTrackedBindings() {
        BlockPos controller = BlockPos.ZERO;
        BlockPos panel = new BlockPos(1, 0, 0);
        StructureEvaluation current = StructureEvaluation.formed(
                EnergyTier.LV,
                ModifierSet.BASE,
                Map.of(controller, BlockRole.CONTROLLER, panel, BlockRole.PANEL),
                Map.of());

        assertEquals(Set.of(), MachineBlockEntity.providerPortPositions(current));
    }
}
