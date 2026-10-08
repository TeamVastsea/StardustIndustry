package com.stardustindustry.stardustindustry.multiblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Maps every block of a formed tank back to the shell cell that owns it.
 *
 * <p>A tank's frame and glass blocks have no block entity of their own — only the
 * shell anchor does. Without a side table there is no way to answer "which tank
 * is this frame part of?" when a player points at it, and both the installation
 * tool and the highlight-mod integration need exactly that answer for every part
 * of the tank, not just the anchor.</p>
 *
 * <p>The table is a cache, not state: it is rebuilt from the structure evaluation
 * whenever a tank forms or re-forms, and cleared when the structure breaks. A
 * lookup that misses simply means the caller has to fall back to reading the
 * world (see {@code TankHudAccess}), never that the tank is invalid.</p>
 *
 * <p>Each side keeps its own instance of the static table, which is what makes it
 * safe on both the server (for the tool) and the client (for the HUD).</p>
 */
public final class TankMembershipRegistry {

    /** Level -> (member cell -> anchor cell). Weak keys so a closed level is collected. */
    private static final Map<Level, Map<BlockPos, BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private TankMembershipRegistry() {}

    /** Records that every cell in {@code members} belongs to the tank anchored at {@code anchor}. */
    public static synchronized void register(Level level, BlockPos anchor, Collection<BlockPos> members) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        removeAnchor(table, anchor);
        BlockPos immutableAnchor = anchor.immutable();
        for (BlockPos member : members) {
            table.put(member.immutable(), immutableAnchor);
        }
    }

    /** Forgets every cell that pointed at {@code anchor}. */
    public static synchronized void clear(Level level, BlockPos anchor) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.get(level);
        if (table != null) {
            removeAnchor(table, anchor);
        }
    }

    /** The anchor of the tank containing {@code pos}, or {@code null} when it is not a tank cell. */
    public static synchronized BlockPos anchorOf(Level level, BlockPos pos) {
        Map<BlockPos, BlockPos> table = BY_LEVEL.get(level);
        return table == null ? null : table.get(pos);
    }

    private static void removeAnchor(Map<BlockPos, BlockPos> table, BlockPos anchor) {
        // The table is small (at most a 9x9x9 tank), so a scan is cheaper and less
        // error-prone than keeping a reverse index in step.
        Collection<BlockPos> toRemove = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockPos> entry : table.entrySet()) {
            if (entry.getValue().equals(anchor)) {
                toRemove.add(entry.getKey());
            }
        }
        for (BlockPos pos : toRemove) {
            table.remove(pos);
        }
    }
}
