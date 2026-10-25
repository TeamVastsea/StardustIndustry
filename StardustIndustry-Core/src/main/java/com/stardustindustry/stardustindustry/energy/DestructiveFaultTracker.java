package com.stardustindustry.stardustindustry.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * Shared confirmation gate for energized faults that destroy an unprotected
 * electrical input. A healthy, offline, or de-energized update resets the
 * full confirmation window.
 */
public final class DestructiveFaultTracker {
    public static final int CONFIRMATION_TICKS = 40;

    private final DelayedFault missingPhase = new DelayedFault(CONFIRMATION_TICKS);
    private final DelayedFault overvoltage = new DelayedFault(CONFIRMATION_TICKS);

    public Confirmation update(GridState state, EnergyTier ratedTier) {
        boolean missingConfirmed = missingPhase.update(
                ElectricalFaultPolicy.shouldConfirm(state, FaultKind.MISSING_PHASE));
        boolean overvoltageConfirmed = overvoltage.update(
                ElectricalFaultPolicy.shouldConfirm(state, FaultKind.OVERVOLTAGE));
        if (missingConfirmed) {
            return new Confirmation(FaultKind.MISSING_PHASE, 2.0f);
        }
        if (overvoltageConfirmed && state.supplyTier() != null) {
            int steps = ratedTier.overvoltageSteps(state.supplyTier());
            float strength = (float) (2 * Math.pow(3, Math.max(0, steps - 1)));
            return new Confirmation(FaultKind.OVERVOLTAGE, strength);
        }
        return null;
    }

    public void reset() {
        missingPhase.reset();
        overvoltage.reset();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("MissingPhase", missingPhase.save());
        tag.put("Overvoltage", overvoltage.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        missingPhase.load(tag.getCompound("MissingPhase"));
        overvoltage.load(tag.getCompound("Overvoltage"));
    }

    public record Confirmation(FaultKind fault, float explosionStrength) {}
}
