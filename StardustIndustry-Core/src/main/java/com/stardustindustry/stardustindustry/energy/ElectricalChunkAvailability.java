package com.stardustindustry.stardustindustry.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Non-loading chunk readiness checks shared by cable mutation and grid solving. */
final class ElectricalChunkAvailability {
    private ElectricalChunkAvailability() {}

    static boolean isActiveNow(ServerLevel level, BlockPos pos) {
        var chunks = level.getChunkSource();
        return isActiveNow(
                chunks.isPositionTicking(ChunkPos.asLong(pos)),
                chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null);
    }

    static boolean isActiveNow(boolean positionTicking, boolean chunkPresent) {
        return positionTicking && chunkPresent;
    }
}
