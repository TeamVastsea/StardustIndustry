package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes.PartType;
import com.stardustindustry.stardustindustry.multiblock.StructureRotation;
import com.stardustindustry.stardustindustry.multiblock.TierMaterials;
import com.stardustindustry.stardustindustry.multiblock.model.StructureModel;
import com.stardustindustry.stardustindustry.multiblock.model.StructureSlot;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Evaluates a fixed-shape {@link StructureModel} against the world.
 *
 * <h2>What is checked</h2>
 * <ol>
 *   <li>Every fixed body cell matches its matcher.</li>
 *   <li>Every base slot holds a port, a filler or a level-bearing base block,
 *       and is never left empty.</li>
 *   <li>All level-bearing blocks on the base layer share one voltage tier, and
 *       every port is that same tier.</li>
 * </ol>
 *
 * <h2>What is collected</h2>
 * Fillers are counted by {@link FillerKind} and folded into a {@link ModifierSet}.
 * The observed role of every position is recorded so the machine layer and the
 * projector can act on it without re-deriving anything.
 *
 * <p>The provider is stateless and world-read-only, so the same code serves the
 * authoritative server check and any client-side preview.</p>
 */
public final class StaticStructureProvider implements StructureProvider {

    private final StructureModel model;

    public StaticStructureProvider(StructureModel model) {
        this.model = model;
    }

    /** The model this provider evaluates. */
    public StructureModel model() {
        return model;
    }

    @Override
    public StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing) {
        Rotation rotation = StructureRotation.forFacing(facing);

        Map<BlockPos, BlockRole> roles = new HashMap<>();
        List<ScanFailure> failures = new ArrayList<>();
        Map<FillerKind, Integer> fillers = new EnumMap<>(FillerKind.class);
        List<EnergyTier> tiers = new ArrayList<>();

        // Fixed body cells.
        for (StructureSlot slot : model.body()) {
            BlockPos world = worldPos(controller, slot, rotation);
            BlockState state = level.getBlockState(world);
            if (slot.matches(state)) {
                roles.put(world, BlockRole.FRAME);
            } else {
                failures.add(new ScanFailure(world, BlockRole.FRAME, "structure.stardustindustry.need.body"));
            }
        }

        // Controller itself.
        BlockPos controllerWorld = worldPos(controller, model.controllerOffset(), rotation);
        if (model.matchesController(level.getBlockState(controllerWorld))) {
            roles.put(controllerWorld, BlockRole.CONTROLLER);
        } else {
            failures.add(new ScanFailure(controllerWorld, BlockRole.CONTROLLER, "structure.stardustindustry.need.controller"));
        }

        // Base layer slots: port, filler or base block, and never empty.
        for (StructureSlot slot : model.baseSlots()) {
            BlockPos world = worldPos(controller, slot, rotation);
            BlockState state = level.getBlockState(world);
            PartType type = MachinePartTypes.typeOf(state);

            if (type == null) {
                failures.add(new ScanFailure(world, BlockRole.BASE_SLOT,
                        "structure.stardustindustry.need.base_slot"));
                continue;
            }

            switch (type) {
                case PORT -> {
                    roles.put(world, BlockRole.PORT);
                    EnergyTier portTier = TierMaterials.tierOf(state);
                    if (portTier != null) {
                        tiers.add(portTier);
                    }
                }
                case FILLER -> {
                    roles.put(world, BlockRole.FILLER);
                    FillerKind kind = MachinePartTypes.fillerKindOf(state);
                    if (kind != null) {
                        fillers.merge(kind, 1, Integer::sum);
                    }
                }
                case BASE -> {
                    roles.put(world, BlockRole.BASE);
                    EnergyTier baseTier = TierMaterials.tierOf(state);
                    if (baseTier != null) {
                        tiers.add(baseTier);
                    }
                }
                case CASING -> failures.add(new ScanFailure(world, BlockRole.BASE_SLOT,
                        "structure.stardustindustry.need.base_slot_casing"));
            }
        }

        // Tier consistency: every level-bearing block on the base layer must agree.
        EnergyTier tier = TierMaterials.commonTier(tiers);
        if (!tiers.isEmpty() && tier == null) {
            failures.add(new ScanFailure(controllerWorld, BlockRole.BASE_SLOT,
                    "structure.stardustindustry.need.base_tier"));
        }

        ModifierSet modifiers = modifiersOf(fillers);

        if (failures.isEmpty()) {
            return StructureEvaluation.formed(tier, modifiers, roles, fillers);
        }
        return StructureEvaluation.failed(tier, failures, roles, fillers);
    }

    /** Folds filler counts into a single modifier set. */
    private static ModifierSet modifiersOf(Map<FillerKind, Integer> fillers) {
        ModifierSet result = ModifierSet.BASE;
        for (Map.Entry<FillerKind, Integer> entry : fillers.entrySet()) {
            com.stardustindustry.stardustindustry.multiblock.modifier.FillerModifier modifier =
                    com.stardustindustry.stardustindustry.multiblock.modifier.FillerRegistry.modifierOf(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                result = result.plus(modifier);
            }
        }
        return result;
    }

    /** Maps an authored offset to its world position under the given rotation. */
    private static BlockPos worldPos(BlockPos controller, StructureSlot slot, Rotation rotation) {
        return controller.offset(StructureRotation.rotate(slot.offset(), rotation));
    }

    private static BlockPos worldPos(BlockPos controller, BlockPos offset, Rotation rotation) {
        return controller.offset(StructureRotation.rotate(offset, rotation));
    }
}
