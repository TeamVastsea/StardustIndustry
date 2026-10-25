package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock;
import com.stardustindustry.stardustindustry.machine.storage.StorageTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.MultiblockMembershipRegistry;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Validates the variable rectangular shell and tiered cells of a storage station. */
public final class StorageStationStructureProvider implements StructureProvider {

    public static final StorageStationStructureProvider INSTANCE = new StorageStationStructureProvider();
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 9;

    private StorageStationStructureProvider() {}

    @Override
    public boolean revalidateWhileFormed() {
        return true;
    }

    @Override
    public java.util.Collection<net.minecraft.world.level.ChunkPos> footprint(BlockPos controller, Direction facing) {
        return StructureChunkGuard.reachChunks(controller, MAX_SIZE);
    }

    @Override
    public StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing) {
        Map<BlockPos, BlockRole> roles = new HashMap<>();
        List<ScanFailure> failures = new ArrayList<>();
        StorageTier stationTier = controllerTier(level.getBlockState(controller));
        if (stationTier == null) {
            return failed(level, controller, stationTier,
                    List.of(new ScanFailure(controller, BlockRole.CONTROLLER,
                            "structure.stardustindustry.need.storage_controller")), roles);
        }

        Set<BlockPos> shell = new HashSet<>();
        if (!floodShell(level, controller, shell)) {
            return failed(level, controller, stationTier,
                    List.of(new ScanFailure(controller, BlockRole.CONTROLLER,
                            "structure.stardustindustry.need.max_size", MAX_SIZE)), roles);
        }
        if (shell.isEmpty()) {
            return failed(level, controller, stationTier,
                    List.of(new ScanFailure(controller, BlockRole.CONTROLLER,
                            "structure.stardustindustry.need.storage_casing")), roles);
        }

        ScanBounds bounds = boundsOf(shell);
        for (Direction.Axis axis : Direction.Axis.values()) {
            int size = bounds.size(axis);
            if (size < MIN_SIZE) {
                return failed(level, controller, stationTier,
                        List.of(new ScanFailure(controller, BlockRole.CONTROLLER,
                                "structure.stardustindustry.need.min_size", MIN_SIZE)), roles);
            }
            if (size > MAX_SIZE) {
                return failed(level, controller, stationTier,
                        List.of(new ScanFailure(controller, BlockRole.CONTROLLER,
                                "structure.stardustindustry.need.max_size", MAX_SIZE)), roles);
            }
        }

        if (bounds.edgesTouched(controller) != 1 || controller.getY() == bounds.min().getY()) {
            failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                    "structure.stardustindustry.need.storage_controller_position"));
        }

        long controllers = shell.stream()
                .filter(pos -> level.getBlockState(pos).getBlock() instanceof StorageStationControllerBlock)
                .count();
        if (controllers != 1) {
            failures.add(new ScanFailure(controller, BlockRole.CONTROLLER,
                    "structure.stardustindustry.need.single_storage_controller"));
        }

        int cells = 0;
        for (BlockPos mutable : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos pos = mutable.immutable();
            BlockState state = level.getBlockState(pos);
            int edges = bounds.edgesTouched(pos);

            if (pos.equals(controller)) {
                roles.put(pos, BlockRole.CONTROLLER);
                continue;
            }
            if (edges >= 2) {
                if (state.is(ModBlocks.STORAGE_STATION_FRAME.get())) {
                    roles.put(pos, BlockRole.FRAME);
                } else {
                    failures.add(new ScanFailure(pos, BlockRole.FRAME,
                            "structure.stardustindustry.need.storage_frame"));
                }
                continue;
            }
            if (edges == 1) {
                boolean bottom = pos.getY() == bounds.min().getY();
                if (bottom) {
                    if (state.is(ModBlocks.STORAGE_STATION_CASING.get())) {
                        roles.put(pos, BlockRole.PANEL);
                    } else {
                        failures.add(new ScanFailure(pos, BlockRole.PANEL,
                                "structure.stardustindustry.need.storage_floor"));
                    }
                } else if (state.is(ModBlocks.STORAGE_STATION_CASING.get())
                        || state.is(ModBlocks.INDUSTRIAL_GLASS.get())) {
                    roles.put(pos, BlockRole.PANEL);
                } else if (isEnergyPort(state)) {
                    MachinePortBlock port = (MachinePortBlock) state.getBlock();
                    if (port.tier() == stationTier.energyTier()) {
                        roles.put(pos, BlockRole.PORT);
                    } else {
                        failures.add(new ScanFailure(pos, BlockRole.PORT,
                                "structure.stardustindustry.need.storage_port_tier"));
                    }
                } else if (state.getBlock() instanceof StorageStationControllerBlock) {
                    failures.add(new ScanFailure(pos, BlockRole.CONTROLLER,
                            "structure.stardustindustry.need.single_storage_controller"));
                } else {
                    failures.add(new ScanFailure(pos, BlockRole.PANEL,
                            "structure.stardustindustry.need.storage_casing"));
                }
                continue;
            }

            if (state.isAir()) {
                roles.put(pos, BlockRole.INTERIOR);
            } else if (state.getBlock() instanceof StorageCellBlock cell) {
                if (cell.stationTier() == stationTier) {
                    roles.put(pos, BlockRole.FILLER);
                    cells++;
                } else {
                    failures.add(new ScanFailure(pos, BlockRole.FILLER,
                            "structure.stardustindustry.need.storage_cell_tier"));
                }
            } else {
                failures.add(new ScanFailure(pos, BlockRole.INTERIOR,
                        "structure.stardustindustry.need.storage_interior"));
            }
        }

        if (cells == 0) {
            failures.add(new ScanFailure(controller, BlockRole.FILLER,
                    "structure.stardustindustry.need.storage_cell"));
        }
        if (!hasMatchingEnergyPort(roles)) {
            failures.add(new ScanFailure(controller, BlockRole.PORT,
                    "structure.stardustindustry.need.storage_port"));
        }

        if (!failures.isEmpty()) {
            return failed(level, controller, stationTier, failures, roles);
        }

        Set<BlockPos> members = new HashSet<>();
        for (BlockPos pos : roles.keySet()) {
            if (!level.getBlockState(pos).isAir()) {
                members.add(pos);
            }
        }
        MultiblockMembershipRegistry.register(level, controller, members);
        return StructureEvaluation.formed(stationTier.energyTier(), ModifierSet.BASE, roles, Map.of());
    }

    private static StructureEvaluation failed(Level level, BlockPos controller, StorageTier tier,
                                              List<ScanFailure> failures, Map<BlockPos, BlockRole> roles) {
        MultiblockMembershipRegistry.clear(level, controller);
        return StructureEvaluation.failed(tier == null ? null : tier.energyTier(), failures, roles, Map.of());
    }

    /**
     * Floods only the finite cube a legal station can occupy. A connected shell
     * that crosses that boundary is rejected immediately instead of allowing an
     * accidental casing network to turn one validation into an unbounded scan.
     */
    private static boolean floodShell(Level level, BlockPos start, Set<BlockPos> out) {
        if (!isStationShellMaterial(level.getBlockState(start))) {
            return true;
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
                if (!withinMaximumReach(start, next)) {
                    if (isStationShellMaterial(level.getBlockState(next))) {
                        return false;
                    }
                    continue;
                }
                if (isStationShellMaterial(level.getBlockState(next))) {
                    out.add(next);
                    queue.add(next);
                }
            }
        }
        return true;
    }

    private static boolean withinMaximumReach(BlockPos controller, BlockPos candidate) {
        return Math.abs((long) candidate.getX() - controller.getX()) < MAX_SIZE
                && Math.abs((long) candidate.getY() - controller.getY()) < MAX_SIZE
                && Math.abs((long) candidate.getZ() - controller.getZ()) < MAX_SIZE;
    }

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

    public static boolean isStationShellMaterial(BlockState state) {
        return state.is(ModBlocks.STORAGE_STATION_FRAME.get())
                || state.is(ModBlocks.STORAGE_STATION_CASING.get())
                || state.is(ModBlocks.INDUSTRIAL_GLASS.get())
                || state.getBlock() instanceof StorageStationControllerBlock
                || isEnergyPort(state);
    }

    public static boolean isEnergyPort(BlockState state) {
        return state.getBlock() instanceof MachinePortBlock port
                && port.role() == PartRole.PORT_ENERGY
                && port.tier() != null;
    }

    static boolean hasMatchingEnergyPort(Map<BlockPos, BlockRole> roles) {
        return roles.containsValue(BlockRole.PORT);
    }

    private static StorageTier controllerTier(BlockState state) {
        return state.getBlock() instanceof StorageStationControllerBlock controller
                ? controller.stationTier()
                : null;
    }

    public static int cellCount(StructureEvaluation evaluation) {
        if (evaluation == null || evaluation.failed()) {
            return 0;
        }
        int cells = 0;
        for (BlockRole role : evaluation.roles().values()) {
            if (role == BlockRole.FILLER) {
                cells++;
            }
        }
        return cells;
    }

    public static long capacityJoules(StructureEvaluation evaluation, StorageTier tier) {
        return tier.capacityJoulesForCells(cellCount(evaluation));
    }
}
