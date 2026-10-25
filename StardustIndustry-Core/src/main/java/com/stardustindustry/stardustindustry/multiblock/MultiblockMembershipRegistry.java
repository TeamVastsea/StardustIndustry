package com.stardustindustry.stardustindustry.multiblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Runtime index from an inert dynamic-multiblock part to its controller.
 *
 * <p>Frames, glass and internal cells have no block entity, so interaction code
 * cannot otherwise discover which controller owns a clicked block. This table
 * is a cache rebuilt by structure providers, not persistent world state.</p>
 */
public final class MultiblockMembershipRegistry {

    private static final Map<Level, Map<BlockPos, BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private MultiblockMembershipRegistry() {}

    /** Replaces all cached members belonging to {@code controller}. */
    public static synchronized void register(Level level, BlockPos controller, Collection<BlockPos> members) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        removeController(table, controller);
        BlockPos immutableController = controller.immutable();
        for (BlockPos member : members) {
            table.put(member.immutable(), immutableController);
        }
    }

    /** Removes every cached member belonging to {@code controller}. */
    public static synchronized void clear(Level level, BlockPos controller) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.get(level);
        if (table != null) {
            removeController(table, controller);
        }
    }

    /** Returns the owning controller, or {@code null} when the cache has no entry. */
    public static synchronized BlockPos controllerOf(Level level, BlockPos member) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.get(level);
        return table == null ? null : table.get(member);
    }

    private static void removeController(Map<BlockPos, BlockPos> table, BlockPos controller) {
        Collection<BlockPos> toRemove = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockPos> entry : table.entrySet()) {
            if (entry.getValue().equals(controller)) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(table::remove);
    }
}
