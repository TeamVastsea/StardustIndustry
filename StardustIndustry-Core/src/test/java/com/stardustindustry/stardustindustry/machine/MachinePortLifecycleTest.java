package com.stardustindustry.stardustindustry.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.multiblock.StructureDefinition;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;
import com.stardustindustry.stardustindustry.multiblock.provider.FillerKind;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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

    @Test
    void legacyDefinitionPortsAreValidatedInWorldSpace() {
        StructureDefinition definition = StructureDefinition.builder("port_binding_test")
                .part(0, 0, 0, PartRole.CONTROLLER, state -> true)
                .part(2, 1, -1, PartRole.PORT_ENERGY, state -> true)
                .part(0, 1, 1, PartRole.CASING, state -> true)
                .build();
        BlockPos controller = new BlockPos(10, 20, 30);

        assertEquals(Set.of(new BlockPos(11, 21, 32)),
                MachineBlockEntity.definitionPortPositions(controller, Direction.EAST, definition));
    }

    @Test
    void installedEvaluationRoundTripsModifiersRolesAndFillers() {
        BlockPos controller = new BlockPos(12, 34, 56);
        BlockPos port = controller.east();
        StructureEvaluation original = StructureEvaluation.formed(
                EnergyTier.MV,
                new ModifierSet(2.0f, 2.2f, 1, 1.5f),
                Map.of(controller, BlockRole.CONTROLLER, port, BlockRole.PORT),
                Map.of(FillerKind.GRINDING_CORE, 1, FillerKind.BUFFER_CORE, 1));

        assertEquals(original, StructureEvaluation.load(original.save()));
    }
}
