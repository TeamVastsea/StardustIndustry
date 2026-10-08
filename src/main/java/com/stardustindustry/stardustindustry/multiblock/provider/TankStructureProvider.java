package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.stardustindustry.stardustindustry.Config;
import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.multiblock.TierMaterials;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Evaluates a tank: a closed rectangular box built from tank blocks.
 *
 * <h2>Shape</h2>
 * A tank is a box of edges and faces:
 * <ul>
 *   <li>Every cell that touches two or three edges must be a {@code tank_frame}.</li>
 *   <li>Every face cell (one edge) must be a {@code tank_shell}, {@code tank_glass}
 *       or a tiered fluid port. Ports never sit on an edge.</li>
 *   <li>Every interior cell must be air. A tank is a hollow container; a block
 *       inside it is a build error, reported with its position.</li>
 *   <li>Each axis is between {@value Config#TANK_MIN_SIZE} and
 *       {@value Config#TANK_MAX_SIZE} blocks.</li>
 * </ul>
 *
 * <h2>Anchor</h2>
 * The anchor (the shell block that owns the block entity) is one face cell like
 * any other. The scanner floods out from it through connected tank material to
 * find the whole box, so the anchor does not need to be a special hole — a
 * finished wall forms normally.
 *
 * <h2>Capacity</h2>
 * Capacity is {@code interiorAirCells * bucketsPerAirBlock * 1000} mB. The
 * machine reads it back through {@link #capacityMb(StructureEvaluation)}.
 *
 * <h2>Read-only</h2>
 * The scan is a pure read of block states and never mutates the world, so the
 * same code serves the server's authoritative check and a client preview.
 */
public final class TankStructureProvider implements StructureProvider {

    /** Default instance; the provider is stateless. */
    public static final TankStructureProvider INSTANCE = new TankStructureProvider();

    /** The tank's minimum size along one axis. */
    public static final int MIN_SIZE = 3;

    private TankStructureProvider() {}

    @Override
    public boolean revalidateWhileFormed() {
        return true;
    }

    @Override
    public StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing) {
        Map<BlockPos, BlockRole> roles = new HashMap<>();
        List<ScanFailure> failures = new ArrayList<>();

        // 1. Flood-fill the connected tank material containing the anchor.
        Set<BlockPos> shell = new HashSet<>();
        floodShell(level, controller, shell);
        if (shell.isEmpty()) {
            failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                    "structure.stardustindustry.need.panel"));
            return StructureEvaluation.failed(null, failures, roles, Map.of());
        }

        ScanBounds bounds = boundsOf(shell);
        int maxSize = Config.TANK_MAX_SIZE.get();
        for (Direction.Axis axis : Direction.Axis.values()) {
            int size = bounds.size(axis);
            if (size < MIN_SIZE) {
                failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                        "structure.stardustindustry.need.min_size", MIN_SIZE));
                return StructureEvaluation.failed(null, failures, roles, Map.of());
            }
            if (size > maxSize) {
                failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                        "structure.stardustindustry.need.max_size", maxSize));
                return StructureEvaluation.failed(null, failures, roles, Map.of());
            }
        }

        // 2. Validate every cell of the box.
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos world = pos.immutable();
            if (world.equals(controller)) {
                roles.put(world, BlockRole.CONTROLLER);
                continue;
            }

            BlockState state = level.getBlockState(world);
            int edges = bounds.edgesTouched(world);

            if (edges >= 2) {
                // Edge or corner: must be a tank frame.
                if (!state.is(ModBlocks.TANK_FRAME.get())) {
                    failures.add(new ScanFailure(world, BlockRole.FRAME, "structure.stardustindustry.need.frame"));
                    continue;
                }
                roles.put(world, BlockRole.FRAME);
            } else if (edges == 1) {
                // Face: shell, glass or a tiered fluid port.
                if (isTieredFluidPort(state)) {
                    roles.put(world, BlockRole.PORT);
                } else if (state.is(ModBlocks.TANK_SHELL.get()) || state.is(ModBlocks.TANK_GLASS.get())) {
                    roles.put(world, BlockRole.PANEL);
                } else {
                    failures.add(new ScanFailure(world, BlockRole.PANEL, "structure.stardustindustry.need.panel"));
                }
            } else {
                // Interior: must be air.
                if (state.isAir()) {
                    roles.put(world, BlockRole.INTERIOR);
                } else {
                    failures.add(new ScanFailure(world, BlockRole.INTERIOR, "structure.stardustindustry.need.air"));
                }
            }
        }

        // 3. All tank material must belong to the same connected shell: a detached
        // rib hanging off the box is a build error, not a second tank.
        for (BlockPos pos : shell) {
            roles.put(pos, roles.getOrDefault(pos, BlockRole.PANEL));
        }
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos world = pos.immutable();
            BlockState state = level.getBlockState(world);
            if (isTankMaterial(state) && !shell.contains(world)) {
                failures.add(new ScanFailure(world, BlockRole.PANEL,
                        "structure.stardustindustry.need.connected"));
            }
        }

        if (failures.isEmpty()) {
            com.stardustindustry.stardustindustry.multiblock.TankMembershipRegistry
                    .register(level, controller, shell);
            return StructureEvaluation.formed(null, ModifierSet.BASE, roles, Map.of());
        }
        com.stardustindustry.stardustindustry.multiblock.TankMembershipRegistry.clear(level, controller);
        return StructureEvaluation.failed(null, failures, roles, Map.of());
    }

    /**
     * Floods from {@code start} through connected tank material, collecting every
     * cell into {@code out}. Tank material is frame, shell, glass and tiered fluid
     * ports; everything else stops the flood.
     */
    private static void floodShell(Level level, BlockPos start, Set<BlockPos> out) {
        if (!isTankMaterial(level.getBlockState(start))) {
            return;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        out.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (out.contains(next)) {
                    continue;
                }
                if (isTankMaterial(level.getBlockState(next))) {
                    out.add(next);
                    queue.add(next);
                }
            }
        }
    }

    /** The inclusive bounding box of a set of cells. */
    private static ScanBounds boundsOf(Set<BlockPos> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new ScanBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    /** True when {@code state} is one of the blocks a tank may be built from. */
    public static boolean isTankMaterial(BlockState state) {
        return state.is(ModBlocks.TANK_FRAME.get())
                || state.is(ModBlocks.TANK_SHELL.get())
                || state.is(ModBlocks.TANK_GLASS.get())
                || isTieredFluidPort(state);
    }

    /** True when {@code state} is a fluid port that carries a tier. */
    public static boolean isTieredFluidPort(BlockState state) {
        if (state.getBlock() instanceof MachinePortBlock port
                && port.role() == PartRole.PORT_FLUID) {
            return TierMaterials.isLevelBearing(port);
        }
        return false;
    }

    /** The number of interior air cells of a formed evaluation. */
    public static int interiorCells(StructureEvaluation evaluation) {
        if (evaluation == null || evaluation.failed()) {
            return 0;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : evaluation.roles().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int sx = Math.max(0, maxX - minX - 1);
        int sy = Math.max(0, maxY - minY - 1);
        int sz = Math.max(0, maxZ - minZ - 1);
        return sx * sy * sz;
    }

    /** The capacity in mB of a formed evaluation. */
    public static int capacityMb(StructureEvaluation evaluation) {
        int cells = interiorCells(evaluation);
        return cells * Config.TANK_BUCKETS_PER_AIR_BLOCK.get() * 1000;
    }
}
