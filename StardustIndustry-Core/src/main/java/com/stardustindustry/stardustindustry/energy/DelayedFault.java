package com.stardustindustry.stardustindustry.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * Debounces destructive faults. A condition must stay present for the full
 * confirmation window; one unstable tick resets the countdown.
 */
public final class DelayedFault {
    private final int confirmationTicks;
    private int observedTicks;
    private boolean latched;

    public DelayedFault(int confirmationTicks) {
        if (confirmationTicks < 1) {
            throw new IllegalArgumentException("confirmationTicks must be positive");
        }
        this.confirmationTicks = confirmationTicks;
    }

    public boolean update(boolean faultPresent) {
        if (latched) {
            return false;
        }
        if (!faultPresent) {
            observedTicks = 0;
            return false;
        }
        observedTicks++;
        if (observedTicks < confirmationTicks) {
            return false;
        }
        latched = true;
        return true;
    }

    public int observedTicks() {
        return observedTicks;
    }

    public boolean latched() {
        return latched;
    }

    public void reset() {
        observedTicks = 0;
        latched = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ObservedTicks", observedTicks);
        tag.putBoolean("Latched", latched);
        return tag;
    }

    public void load(CompoundTag tag) {
        observedTicks = Math.max(0, Math.min(confirmationTicks, tag.getInt("ObservedTicks")));
        latched = tag.getBoolean("Latched") || observedTicks >= confirmationTicks;
    }
}
