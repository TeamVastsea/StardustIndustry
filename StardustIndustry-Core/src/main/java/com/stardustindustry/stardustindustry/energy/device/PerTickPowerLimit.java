package com.stardustindustry.stardustindustry.energy.device;

import com.stardustindustry.stardustindustry.energy.ElectricalUnits;

/** Accumulates direct capability transfers so repeated calls cannot bypass a watt rating. */
final class PerTickPowerLimit {
    private long gameTick = Long.MIN_VALUE;
    private long usedThisTick;

    int limit(int requestedJoules, long maxPowerWatts, long currentGameTick) {
        long limit = Math.max(0L, maxPowerWatts) / ElectricalUnits.TICKS_PER_SECOND;
        long used = currentGameTick == gameTick ? usedThisTick : 0L;
        long remaining = Math.max(0L, limit - used);
        return (int) Math.min(Math.max(0, requestedJoules), Math.min(Integer.MAX_VALUE, remaining));
    }

    void record(int transferredJoules, long currentGameTick) {
        if (transferredJoules <= 0) return;
        if (currentGameTick != gameTick) {
            gameTick = currentGameTick;
            usedThisTick = 0L;
        }
        usedThisTick = Math.min(Long.MAX_VALUE, usedThisTick + transferredJoules);
    }
}
