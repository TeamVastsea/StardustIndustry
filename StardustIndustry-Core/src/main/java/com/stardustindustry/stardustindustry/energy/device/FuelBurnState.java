package com.stardustindustry.stardustindustry.energy.device;

/** Fuel queue state with a side-effect-free extraction preflight. */
final class FuelBurnState {
    private int queuedTicks;
    private int burningTicks;
    private long lastConsumedGameTick = Long.MIN_VALUE;

    boolean addTicks(int ticks) {
        if (ticks <= 0) return false;
        queuedTicks = (int) Math.min(Integer.MAX_VALUE, (long) queuedTicks + ticks);
        return true;
    }

    boolean hasFuel() {
        return burningTicks > 0 || queuedTicks > 0;
    }

    boolean extract(boolean simulate, Long gameTick) {
        if (!hasFuel()) return false;
        if (simulate) return true;
        if (burningTicks <= 0) {
            burningTicks = queuedTicks;
            queuedTicks = 0;
        }
        if (gameTick != null && gameTick != lastConsumedGameTick) {
            burningTicks--;
            lastConsumedGameTick = gameTick;
        }
        return true;
    }

    int queuedTicks() {
        return queuedTicks;
    }

    int burningTicks() {
        return burningTicks;
    }

    void load(int queuedTicks, int burningTicks) {
        this.queuedTicks = Math.max(0, queuedTicks);
        this.burningTicks = Math.max(0, burningTicks);
        lastConsumedGameTick = Long.MIN_VALUE;
    }
}
