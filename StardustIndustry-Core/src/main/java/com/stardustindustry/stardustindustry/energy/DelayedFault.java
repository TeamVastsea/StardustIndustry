package com.stardustindustry.stardustindustry.energy;

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
}
