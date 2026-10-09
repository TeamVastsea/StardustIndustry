package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;
import com.stardustindustry.stardustindustry.multiblock.TierMaterials;
import com.stardustindustry.stardustindustry.multiblock.modifier.FillerModifier;
import com.stardustindustry.stardustindustry.multiblock.modifier.FillerRegistry;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Evaluates an open-shape "dynamic" machine: a rectangular shell whose size the
 * player chooses.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>The twelve edges must be frame blocks, all of one voltage tier.</li>
 *   <li>The six faces (edges excluded) must be shell blocks or ports.</li>
 *   <li>The interior must be air or fillers.</li>
 *   <li>Every port must share the frame's tier.</li>
 *   <li>Each axis is between {@value BoundingBoxScanner#MAX_SIZE} and 2 blocks.</li>
 * </ul>
 *
 * <p>The size is discovered by {@link BoundingBoxScanner} walking out from the
 * controller, so nothing is authored: the provider is one instance shared by
 * every dynamic machine type, and the machine only supplies the anchor.</p>
 *
 * <p>Unlike a static machine, a dynamic one re-scans while formed, because
 * editing a shell in place is normal and expected for a tank or boiler.</p>
 */
public final class DynamicStructureProvider implements StructureProvider {

    /** Default instance; the provider is stateless. */
    public static final DynamicStructureProvider INSTANCE = new DynamicStructureProvider();

    /** The smallest a dynamic machine may be along one axis. */
    public static final int MIN_SIZE = 2;

    @Override
    public boolean revalidateWhileFormed() {
        return true;
    }

    /**
     * The dynamic box is discovered by walk-out, so its real size is unknown
     * before the scan. Reserve everything the scanner may reach — the controller's
     * chunk plus half-extent {@link BoundingBoxScanner#MAX_SIZE} on both horizontal
     * axes — so no unloaded chunk can hide inside the box.
     */
    @Override
    public java.util.Collection<net.minecraft.world.level.ChunkPos> footprint(BlockPos controller, Direction facing) {
        return StructureChunkGuard.reachChunks(controller, BoundingBoxScanner.MAX_SIZE);
    }

    @Override
    public StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing) {
        Map<BlockPos, BlockRole> roles = new HashMap<>();
        List<ScanFailure> failures = new ArrayList<>();
        Map<FillerKind, Integer> fillers = new EnumMap<>(FillerKind.class);
        List<EnergyTier> frameTiers = new ArrayList<>();
        List<EnergyTier> portTiers = new ArrayList<>();

        BoundingBoxScanner.Result scan = BoundingBoxScanner.scan(level, controller);
        if (!scan.ok()) {
            failures.add(new ScanFailure(controller, BlockRole.CONTROLLER, scan.failure().expectation()));
            return StructureEvaluation.failed(null, failures, roles, fillers);
        }

        ScanBounds bounds = scan.bounds();
        for (int axis = 0; axis < 3; axis++) {
            if (bounds.size(Direction.Axis.values()[axis]) < MIN_SIZE) {
                failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                        "structure.stardustindustry.need.min_size", MIN_SIZE));
                return StructureEvaluation.failed(null, failures, roles, fillers);
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos world = pos.immutable();
            BlockState state = level.getBlockState(world);

            // The controller occupies one shell cell — it is the hole where a
            // face block would be. Treat it as that face so the surrounding shape
            // check still sees a closed panel.
            if (world.equals(controller)) {
                roles.put(world, BlockRole.CONTROLLER);
                continue;
            }

            var type = MachinePartTypes.typeOf(state);
            int edges = bounds.edgesTouched(world);

            if (edges >= 2) {
                // An edge or corner: must be a frame block.
                if (type != MachinePartTypes.PartType.FRAME) {
                    failures.add(new ScanFailure(world, BlockRole.FRAME, "structure.stardustindustry.need.frame"));
                    continue;
                }
                roles.put(world, BlockRole.FRAME);
                EnergyTier tier = TierMaterials.tierOf(state);
                if (tier != null) {
                    frameTiers.add(tier);
                }
            } else if (edges == 1) {
                // A face: shell or a port.
                if (type == MachinePartTypes.PartType.PORT) {
                    roles.put(world, BlockRole.PORT);
                    EnergyTier tier = TierMaterials.tierOf(state);
                    if (tier != null) {
                        portTiers.add(tier);
                    }
                } else if (type == MachinePartTypes.PartType.SHELL) {
                    roles.put(world, BlockRole.PANEL);
                } else {
                    failures.add(new ScanFailure(world, BlockRole.PANEL, "structure.stardustindustry.need.panel"));
                }
            } else {
                // Interior: air or a filler.
                if (state.isAir()) {
                    roles.put(world, BlockRole.INTERIOR);
                } else if (type == MachinePartTypes.PartType.FILLER) {
                    roles.put(world, BlockRole.FILLER);
                    FillerKind kind = MachinePartTypes.fillerKindOf(state);
                    if (kind != null) {
                        fillers.merge(kind, 1, Integer::sum);
                    }
                } else {
                    failures.add(new ScanFailure(world, BlockRole.INTERIOR, "structure.stardustindustry.need.interior"));
                }
            }
        }

        // The frame declares the tier; every edge must agree and every port
        // must match it.
        EnergyTier tier = TierMaterials.commonTier(frameTiers);
        if (!frameTiers.isEmpty() && tier == null) {
            failures.add(new ScanFailure(controller, BlockRole.FRAME, "structure.stardustindustry.need.tier"));
        }
        if (tier != null) {
            for (EnergyTier portTier : portTiers) {
                if (portTier != tier) {
                    failures.add(new ScanFailure(controller, BlockRole.PORT,
                            "structure.stardustindustry.need.port_tier"));
                    break;
                }
            }
        }

        ModifierSet modifiers = modifiersOf(fillers, bounds);
        if (failures.isEmpty()) {
            return StructureEvaluation.formed(tier, modifiers, roles, fillers);
        }
        return StructureEvaluation.failed(tier, failures, roles, fillers);
    }

    /**
     * Folds filler counts into a modifier set, scaled so a larger container is
     * naturally better: a dynamic machine's interior volume participates in its
     * buffer baseline, which is the "big containers are big buffers" rule.
     */
    private static ModifierSet modifiersOf(Map<FillerKind, Integer> fillers, ScanBounds bounds) {
        ModifierSet result = ModifierSet.BASE;
        for (Map.Entry<FillerKind, Integer> entry : fillers.entrySet()) {
            FillerModifier modifier = FillerRegistry.modifierOf(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                result = result.plus(modifier);
            }
        }
        // Interior volume adds to the buffer multiplier: an empty 5x5x5 tank
        // still has a larger working volume than a 2x2x2 one.
        int volume = Math.max(0, (bounds.size(Direction.Axis.X) - 2)
                * (bounds.size(Direction.Axis.Y) - 2)
                * (bounds.size(Direction.Axis.Z) - 2));
        if (volume > 0) {
            result = new ModifierSet(result.speedMultiplier(), result.energyMultiplier(),
                    result.parallelBonus(), result.bufferMultiplier() + volume * 0.02f);
        }
        return result;
    }
}
