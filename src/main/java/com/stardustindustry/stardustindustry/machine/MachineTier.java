package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

/**
 * A machine's construction tier.
 *
 * <p>The tier bundles the three numbers that scale together in every machine:
 * the voltage it can connect to, how fast it processes, and how many recipes it
 * may run in parallel. Keeping them in one enum means a machine only declares
 * "I am MV" rather than four separate numbers that could drift apart.</p>
 *
 * <p>Tier also gates upgrades: a machine can only install an upgrade rated at
 * or below its own tier, which is how progression is kept honest without
 * special-casing individual items.</p>
 */
public enum MachineTier {
    LV(EnergyTier.LV, 1.0f, 1, 1),
    MV(EnergyTier.MV, 2.0f, 2, 2),
    HV(EnergyTier.HV, 4.0f, 4, 4),
    EHV(EnergyTier.EHV, 8.0f, 8, 8);

    private final EnergyTier energyTier;
    private final float speedMultiplier;
    private final int parallelSlots;
    private final int upgradeSlots;

    MachineTier(EnergyTier energyTier, float speedMultiplier, int parallelSlots, int upgradeSlots) {
        this.energyTier = energyTier;
        this.speedMultiplier = speedMultiplier;
        this.parallelSlots = parallelSlots;
        this.upgradeSlots = upgradeSlots;
    }

    /** The voltage tier this machine connects to. */
    public EnergyTier energyTier() {
        return energyTier;
    }

    /** Progress per tick multiplier relative to the baseline recipe time. */
    public float speedMultiplier() {
        return speedMultiplier;
    }

    /** How many copies of a recipe the machine may run at once. */
    public int parallelSlots() {
        return parallelSlots;
    }

    /** How many upgrades the machine can hold. */
    public int upgradeSlots() {
        return upgradeSlots;
    }

    /** True when an upgrade of {@code upgradeTier} may be installed here. */
    public boolean acceptsUpgrade(MachineTier upgradeTier) {
        return upgradeTier.ordinal() <= this.ordinal();
    }
}
