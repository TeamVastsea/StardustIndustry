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
import com.stardustindustry.stardustindustry.machine.tank.TankMedium;
import com.stardustindustry.stardustindustry.machine.tank.TankShellBlock;
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
 *   <li>Every face cell (one edge) must be this tank's medium shell
 *       ({@code fluid_tank_shell} or {@code gas_tank_shell}),
 *       {@code industrial_glass}, or a tiered port of this tank's medium. Ports
 *       never sit on an edge. The floor (the lowest face layer) may not be glass:
 *       it must be a solid panel or a port.</li>
 *   <li>Every interior cell must be air. A tank is a hollow container; a block
 *       inside it is a build error, reported with its position.</li>
 *   <li>Each axis is between {@value Config#TANK_MIN_SIZE} and
 *       {@value Config#TANK_MAX_SIZE} blocks.</li>
 * </ul>
 *
 * <h2>Medium</h2>
 * A tank's medium — fluid or gas — is decided by the shell block that owns it.
 * The shared frame and glass belong to either, but a fluid shell cannot be part
 * of a gas tank and vice versa: a box that mixes the two shells is a build error.
 * The port that fits is likewise the medium's own port.
 *
 * <h2>Anchor</h2>
 * The anchor (the shell block that owns the block entity) is one face cell like
 * any other. The scanner floods out from it through connected tank material to
 * find the whole box, so the anchor does not need to be a special hole — a
 * finished wall forms normally. The anchor's shell block also fixes the medium.
 *
 * <h2>Capacity</h2>
 * Capacity is {@code interiorAirCells * bucketsPerAirBlock * 1000} mB, the same
 * for either medium. The machine reads it back through
 * {@link #capacityMb(StructureEvaluation)}.
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

    /**
     * The tank's box is discovered by flood-fill, so its real size is unknown
     * before the scan. Reserve the largest box the rules allow — the controller's
     * chunk plus everything within {@link Config#TANK_MAX_SIZE} along each
     * horizontal axis — so an unloaded chunk can never hide inside the scan.
     */
    @Override
    public java.util.Collection<net.minecraft.world.level.ChunkPos> footprint(BlockPos controller, Direction facing) {
        return StructureChunkGuard.reachChunks(controller, Config.TANK_MAX_SIZE.get());
    }

    @Override
    public StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing) {
        Map<BlockPos, BlockRole> roles = new HashMap<>();
        List<ScanFailure> failures = new ArrayList<>();

        // The medium is fixed by the anchor's own shell block, before any scan.
        TankMedium medium = mediumOf(level.getBlockState(controller));
        if (medium == null) {
            // The controller is not a tank shell. This should not happen for a
            // real tank, but a stray call reports the generic "needs a panel".
            failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                    "structure.stardustindustry.need.panel"));
            return StructureEvaluation.failed(null, failures, roles, Map.of());
        }

        // 1. Flood-fill the connected tank material containing the anchor.
        Set<BlockPos> shell = new HashSet<>();
        floodShell(level, controller, medium, shell);
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

        // 1b. Exactly one shell cell owns the tank.
        //
        // Every tank shell block creates a block entity, so a wall of them would
        // otherwise produce a controller per panel: each one evaluates the same
        // box, forms independently, announces itself, and renders its own copy of
        // the contents. That is the "one full layer plus one draining layer" the
        // renderer showed and the burst of formation messages, whose count scaled
        // with the surface area. The anchor is therefore chosen deterministically
        // — the anchor-candidate cell with the smallest coordinates in the
        // connected shell — and any other shell block's evaluation reports the
        // tank as unformed.
        BlockPos canonicalAnchor = canonicalAnchor(level, shell, medium);
        if (canonicalAnchor != null && !canonicalAnchor.equals(controller)) {
            return StructureEvaluation.failed(null, List.of(), roles, Map.of());
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
                // Face: this medium's shell, glass or a tiered port of the medium.
                if (isTieredPortFor(state, medium)) {
                    roles.put(world, BlockRole.PORT);
                } else if (isShellOf(state, medium)) {
                    roles.put(world, BlockRole.PANEL);
                } else if (state.is(ModBlocks.INDUSTRIAL_GLASS.get())) {
                    // Glass is allowed on the sides and the lid, but not on the
                    // floor: the bottom of a container carries the weight and
                    // must be a solid panel or a drain port. A glass floor is a
                    // build error, reported like any other wrong panel.
                    if (world.getY() == bounds.min().getY()) {
                        failures.add(new ScanFailure(world, BlockRole.PANEL,
                                "structure.stardustindustry.need.panel"));
                    } else {
                        roles.put(world, BlockRole.PANEL);
                    }
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
        // rib hanging off the box is a build error, not a second tank. Material of
        // the other medium does not count as this tank's material (so a stray gas
        // shell on a fluid box is simply not material), but a same-medium rib that
        // did not connect is caught here.
        for (BlockPos pos : shell) {
            roles.put(pos, roles.getOrDefault(pos, BlockRole.PANEL));
        }
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos world = pos.immutable();
            BlockState state = level.getBlockState(world);
            if (isMaterialOf(state, medium) && !shell.contains(world)) {
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
     * The one shell cell that owns the tank: the smallest coordinate among this
     * medium's shell blocks in the connected shell.
     *
     * <p>Deterministic and independent of build order, so every shell block in
     * the box agrees on the same anchor and only that one forms. Returns
     * {@code null} when the shell has no shell block of this medium, which cannot
     * happen for a real tank but keeps the caller safe.</p>
     */
    private static BlockPos canonicalAnchor(Level level, Set<BlockPos> shell, TankMedium medium) {
        BlockPos best = null;
        for (BlockPos pos : shell) {
            if (!isShellOf(level.getBlockState(pos), medium)) {
                continue;
            }
            if (best == null || compare(pos, best) < 0) {
                best = pos;
            }
        }
        return best;
    }

    /** Lexicographic X, then Y, then Z comparison. */
    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) {
            return Integer.compare(a.getX(), b.getX());
        }
        if (a.getY() != b.getY()) {
            return Integer.compare(a.getY(), b.getY());
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    /**
     * Floods from {@code start} through connected tank material of {@code medium},
     * collecting every cell into {@code out}. Tank material is frame, this
     * medium's shell, glass and this medium's tiered ports; everything else —
     * including the other medium's shell and ports — stops the flood.
     */
    private static void floodShell(Level level, BlockPos start, TankMedium medium, Set<BlockPos> out) {
        if (!isMaterialOf(level.getBlockState(start), medium)) {
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
                if (isMaterialOf(level.getBlockState(next), medium)) {
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

    /** The medium of a shell block, or {@code null} when it is not a tank shell. */
    public static TankMedium mediumOf(BlockState state) {
        if (state.getBlock() instanceof TankShellBlock shell) {
            return shell.medium();
        }
        return null;
    }

    /** True when {@code state} is the shell block of {@code medium}. */
    public static boolean isShellOf(BlockState state, TankMedium medium) {
        return mediumOf(state) == medium;
    }

    /** True when {@code state} is one of {@code medium}'s blocks: frame, shell, glass or port. */
    public static boolean isMaterialOf(BlockState state, TankMedium medium) {
        return state.is(ModBlocks.TANK_FRAME.get())
                || isShellOf(state, medium)
                || state.is(ModBlocks.INDUSTRIAL_GLASS.get())
                || isTieredPortFor(state, medium);
    }

    /** True when {@code state} is any block either tank may be built from. */
    public static boolean isTankMaterial(BlockState state) {
        return state.is(ModBlocks.TANK_FRAME.get())
                || state.is(ModBlocks.FLUID_TANK_SHELL.get())
                || state.is(ModBlocks.GAS_TANK_SHELL.get())
                || state.is(ModBlocks.INDUSTRIAL_GLASS.get())
                || isTieredFluidPort(state)
                || isTieredGasPort(state);
    }

    /** True when {@code state} is a tiered fluid or gas port. */
    public static boolean isTieredPort(BlockState state) {
        return isTieredFluidPort(state) || isTieredGasPort(state);
    }

    /** True when {@code state} is a tiered fluid port. */
    public static boolean isTieredFluidPort(BlockState state) {
        return isTieredPortFor(state, TankMedium.FLUID);
    }

    /** True when {@code state} is a tiered gas port. */
    public static boolean isTieredGasPort(BlockState state) {
        return isTieredPortFor(state, TankMedium.GAS);
    }

    /** True when {@code state} is a tiered port belonging to {@code medium}. */
    public static boolean isTieredPortFor(BlockState state, TankMedium medium) {
        if (!(state.getBlock() instanceof MachinePortBlock port)) {
            return false;
        }
        PartRole expected = medium == TankMedium.GAS ? PartRole.PORT_GAS : PartRole.PORT_FLUID;
        return port.role() == expected && TierMaterials.isLevelBearing(port);
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
