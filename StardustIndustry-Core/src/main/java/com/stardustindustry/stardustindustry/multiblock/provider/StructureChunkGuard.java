package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Decides whether a structure may be evaluated right now.
 *
 * <p>Reading a block in an unloaded chunk yields air, so a multi-block machine
 * that spans a chunk border appears torn down the moment one of its chunks
 * unloads — for example when a player walks to the edge of view distance while
 * standing next to a tank. Evaluating in that state would unbind the machine's
 * ports and recompute its size and capacity from a half-loaded box.</p>
 *
 * <p>The rule enforced here is deliberately conservative: <b>evaluate only when
 * every chunk the structure can touch is loaded</b>. Otherwise the machine is
 * frozen with whatever state it already had, and it resumes the moment the
 * missing chunks come back.</p>
 *
 * <p>The guard is shared by static and dynamic machines: a provider reports its
 * footprint through {@link StructureProvider#footprint}, and any provider that
 * reports none never blocks.</p>
 */
public final class StructureChunkGuard {

    private StructureChunkGuard() {}

    /**
     * Whether every chunk in {@code footprint} is loaded in {@code level}.
     *
     * @param footprint the required chunks, or {@code null} for "no restriction"
     * @return {@code true} when the structure may be safely evaluated
     */
    public static boolean allLoaded(Level level, Collection<ChunkPos> footprint) {
        if (footprint == null) {
            return true;
        }
        for (ChunkPos chunk : footprint) {
            if (!level.hasChunk(chunk.x, chunk.z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The chunks a box covers, edges inclusive.
     *
     * @param min the lowest corner, inclusive
     * @param max the highest corner, inclusive
     */
    public static Set<ChunkPos> chunksOf(BlockPos min, BlockPos max) {
        Set<ChunkPos> chunks = new HashSet<>();
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    /**
     * The chunks a centred, bounded region can reach: a square of half-extent
     * {@code reach} blocks around {@code controller}, along both horizontal axes.
     *
     * <p>Used by scanners whose box is discovered at evaluation time: rather than
     * guess the real size, they reserve the largest box the rules allow, so no
     * unloaded chunk can ever fall inside the scan.</p>
     *
     * @param reach the maximum distance from the controller the region may extend
     */
    public static Set<ChunkPos> reachChunks(BlockPos controller, int reach) {
        BlockPos min = controller.offset(-reach, 0, -reach);
        BlockPos max = controller.offset(reach, 0, reach);
        Set<ChunkPos> chunks = chunksOf(min, max);
        // The controller's own chunk is always in the set for reach >= 0.
        return chunks;
    }
}
