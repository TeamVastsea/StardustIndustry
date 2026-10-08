package com.stardustindustry.stardustindustry.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tests a {@link StructureDefinition} against a world position.
 *
 * <p>The matcher is stateless and side-agnostic, so the same code validates a
 * structure on the logical server (the authoritative check) and on the client
 * (to decide whether to render the formed overlay). It never mutates the world.
 *
 * <p>Every authored part must be satisfied for a match. Ports are checked
 * against their declared role; a {@link PartRole#PORT_ANY} position accepts any
 * registered port block. Positions the definition does not mention are ignored,
 * so a machine may be embedded in a larger build without disturbing it.</p>
 */
public final class StructureMatcher {

    private StructureMatcher() {}

    /** Tests the definition against the world with the controller facing its authored direction. */
    public static StructureMatchResult match(Level level, BlockPos controller, StructureDefinition definition) {
        return match(level, controller, definition, Direction.NORTH);
    }

    /**
     * Tests the definition against the world.
     *
     * @param level      the level to read block states from
     * @param controller world position of the controller block
     * @param definition the structure to check
     * @param facing     the controller's current horizontal facing
     */
    public static StructureMatchResult match(Level level, BlockPos controller, StructureDefinition definition,
                                             Direction facing) {
        Rotation rotation = StructureRotation.forFacing(facing);
        Map<BlockPos, BlockPos> placement = new HashMap<>(definition.size());
        List<StructureMatchResult.StructureFailure> failures = new ArrayList<>();

        for (StructurePart part : definition.parts()) {
            BlockPos worldPos = controller.offset(StructureRotation.rotate(part.offset(), rotation));
            placement.put(part.offset(), worldPos);

            BlockState state = level.getBlockState(worldPos);
            if (!part.matches(state)) {
                failures.add(new StructureMatchResult.StructureFailure(
                        part.offset(), worldPos, part.role(), describe(part.role())));
            }
        }

        return new StructureMatchResult(failures.isEmpty(), controller, placement, failures);
    }

    /** Human-readable description of what a role expects, shown by the projector. */
    public static String describe(PartRole role) {
        return switch (role) {
            case CASING, OPTIONAL_CASING -> "structural casing";
            case CONTROLLER -> "controller";
            case PORT_ITEM -> "item port";
            case PORT_FLUID -> "fluid port";
            case PORT_ENERGY -> "energy port";
            case PORT_ANY -> "any port";
            case BASE_SLOT -> "base block, port or filler (slot must not be empty)";
        };
    }

    /** True when any authored position's block state changed relative to the given baseline. */
    public static boolean needsRecheck(Map<BlockPos, BlockState> baseline, Level level) {
        for (Map.Entry<BlockPos, BlockState> entry : baseline.entrySet()) {
            if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
