package com.stardustindustry.stardustindustry.multiblock.provider;

import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the box a dynamic machine occupies, starting from its controller.
 *
 * <p>The controller occupies one cell <em>of the shell</em>: it is a hole in the
 * wall where a shell block would otherwise be. The scanner therefore treats the
 * controller's own coordinate as that face's boundary, and walks out from it
 * along each axis to find the rest of the box.</p>
 *
 * <p>The subtlety is that walking along an axis may either cross the interior
 * (air/fillers) to the far wall, or travel <em>through</em> the shell layer the
 * controller sits in. Both must be handled: a cell that is shell but has more
 * shell beyond it along the walk is wall material being travelled along, not the
 * far boundary; only the last shell cell before open space is the boundary. A
 * thin outer wall (nothing but air beyond) ends the walk.</p>
 *
 * <p>The scan is a pure read: it mutates nothing and may be run on the client.
 * It returns a box or a reason, never both.</p>
 */
public final class BoundingBoxScanner {

    /** The largest a dynamic machine may be along one axis. */
    public static final int MAX_SIZE = 9;

    /** Encloses the outcome of a scan: either a box, or why none was found. */
    public record Result(ScanBounds bounds, Failure failure) {
        public boolean ok() {
            return bounds != null;
        }

        public static Result of(ScanBounds bounds) {
            return new Result(bounds, null);
        }

        public static Result failed(String expectation) {
            return new Result(null, new Failure(expectation));
        }
    }

    /** The reason a scan failed, resolved to a world position by the caller. */
    public record Failure(String expectation) {}

    private BoundingBoxScanner() {}

    /**
     * Scans the box around {@code controller}.
     *
     * @return the box on success, or a failure describing the first obstruction
     */
    public static Result scan(Level level, BlockPos controller) {
        return scan(controller, level::getBlockState);
    }

    /**
     * The scanner core, over any state lookup.
     *
     * <p>Splitting the core out keeps the geometry testable without a live level:
     * the startup self-check feeds it a hand-built box and pins the rule that a
     * controller sitting on a face is itself that face's boundary.</p>
     */
    public static Result scan(BlockPos controller, java.util.function.Function<BlockPos, BlockState> states) {
        // Content-based "get" so a null-returning lookup is treated as air.
        java.util.function.Function<BlockPos, BlockState> get = pos -> {
            BlockState state = states.apply(pos);
            return state == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
        };

        int[] min = new int[3];
        int[] max = new int[3];
        int[] origin = {controller.getX(), controller.getY(), controller.getZ()};

        for (int axis = 0; axis < 3; axis++) {
            Step lower = walk(get, origin, axis, -1);
            Step upper = walk(get, origin, axis, +1);

            if (lower.obstructed() || upper.obstructed()) {
                return Result.failed("structure.stardustindustry.need.panel");
            }
            if (lower.wall() == null && !lower.boundaryAtOrigin()) {
                return Result.failed("structure.stardustindustry.need.panel");
            }
            if (upper.wall() == null && !upper.boundaryAtOrigin()) {
                return Result.failed("structure.stardustindustry.need.panel");
            }

            min[axis] = lower.wall() != null ? lower.wall() : origin[axis];
            max[axis] = upper.wall() != null ? upper.wall() : origin[axis];
        }

        ScanBounds bounds = new ScanBounds(new BlockPos(min[0], min[1], min[2]),
                new BlockPos(max[0], max[1], max[2]));

        for (int axis = 0; axis < 3; axis++) {
            if (bounds.size(Direction.Axis.values()[axis]) > MAX_SIZE) {
                return Result.failed("structure.stardustindustry.need.min_size");
            }
        }
        return Result.of(bounds);
    }

    /** What a single directional walk concluded. */
    private record Step(boolean boundaryAtOrigin, Integer wall, boolean obstructed) {}

    /**
     * Walks from the controller along {@code axis} in {@code step}.
     *
     * <p>The walk passes through interior air and fillers. On reaching shell it
     * keeps travelling while more shell lies beyond (it is running along a wall),
     * and the last shell cell before open space becomes the boundary. If the walk
     * never meets a wall — only open air, or terrain right at the controller's
     * face — then the controller is itself the boundary on this side. An
     * unclassified block met after machine material is an obstruction.</p>
     */
    private static Step walk(java.util.function.Function<BlockPos, BlockState> get,
                             int[] origin, int axis, int step) {
        int[] cursor = origin.clone();
        cursor[axis] += step;
        boolean seenMachine = false;

        for (int i = 0; i <= MAX_SIZE + 1; i++) {
            BlockState state = get.apply(new BlockPos(cursor[0], cursor[1], cursor[2]));
            var type = MachinePartTypes.typeOf(state);

            boolean shell = type == MachinePartTypes.PartType.FRAME
                    || type == MachinePartTypes.PartType.SHELL
                    || type == MachinePartTypes.PartType.PORT;
            boolean interior = state.isAir() || type == MachinePartTypes.PartType.FILLER;

            if (shell) {
                seenMachine = true;
                int[] beyond = cursor.clone();
                beyond[axis] += step;
                BlockState beyondState = get.apply(new BlockPos(beyond[0], beyond[1], beyond[2]));
                var beyondType = MachinePartTypes.typeOf(beyondState);
                boolean beyondShell = beyondType == MachinePartTypes.PartType.FRAME
                        || beyondType == MachinePartTypes.PartType.SHELL
                        || beyondType == MachinePartTypes.PartType.PORT;
                if (!beyondShell) {
                    // The outer skin of the wall: the box boundary.
                    return new Step(false, cursor[axis], false);
                }
                cursor[axis] += step;
                continue;
            }

            if (interior) {
                cursor[axis] += step;
                continue;
            }

            // Unclassified: world terrain or another machine. If it is the very
            // first cell and nothing of this machine has been seen, it is simply
            // what the controller's face is butted against, so the controller is
            // the boundary. Otherwise it is garbage inside the container.
            if (!seenMachine && i == 0) {
                return new Step(true, null, false);
            }
            return new Step(false, null, true);
        }

        // Ran the full length through open air without ever meeting a wall: the
        // controller is the outer boundary on this side.
        return new Step(true, null, false);
    }
}
