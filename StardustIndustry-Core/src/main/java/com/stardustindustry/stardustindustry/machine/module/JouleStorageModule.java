package com.stardustindustry.stardustindustry.machine.module;

import java.util.function.LongSupplier;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Native machine energy storage. All public amounts are joules and use {@code long}. */
public final class JouleStorageModule implements MachineModule, ModuleHost.ResourceHolder {
    private final EnergyTier tier;
    private final long baseCapacityJoules;
    private final long basePowerWatts;
    private LongSupplier capacityOverride;
    private LongSupplier powerOverride;
    private long storedJoules;
    private MachineBlockEntity machine;

    public JouleStorageModule(EnergyTier tier, long capacityJoules, long maxPowerWatts) {
        this.tier = tier;
        this.baseCapacityJoules = Math.max(1L, capacityJoules);
        this.basePowerWatts = Math.max(0L, maxPowerWatts);
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public EnergyTier tier() {
        return tier;
    }

    @Override
    public long buffered() {
        return storedJoules;
    }

    public long capacityJoules() {
        long base = capacityOverride != null ? capacityOverride.getAsLong() : baseCapacityJoules;
        double multiplier = machine == null ? 1.0 : machine.modifiers().bufferMultiplier();
        double scaled = Math.max(0L, base) * multiplier;
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(capacityOverride == null ? 1L : 0L,
                Math.round(scaled));
    }

    public long maxPowerWatts() {
        return Math.max(0L, powerOverride != null ? powerOverride.getAsLong() : basePowerWatts);
    }

    public void setCapacityOverride(LongSupplier capacityOverride) {
        this.capacityOverride = capacityOverride;
    }

    public void setPowerOverride(LongSupplier powerOverride) {
        this.powerOverride = powerOverride;
    }

    public long storedJoules() {
        return storedJoules;
    }

    public long receiveJoules(long amount, boolean simulate) {
        long space = Math.max(0L, capacityJoules() - storedJoules);
        long accepted = Math.min(Math.max(0L, amount), space);
        if (!simulate && accepted > 0L) {
            storedJoules += accepted;
            changed();
        }
        return accepted;
    }

    public long extractJoules(long amount, boolean simulate) {
        long taken = Math.min(Math.max(0L, amount), storedJoules);
        if (!simulate && taken > 0L) {
            storedJoules -= taken;
            changed();
        }
        return taken;
    }

    /** Trims energy above the current dynamic capacity and returns the amount removed. */
    public long clampToCapacity() {
        long capacity = capacityJoules();
        if (storedJoules <= capacity) {
            return 0L;
        }
        long removed = storedJoules - capacity;
        storedJoules = capacity;
        changed();
        return removed;
    }

    public boolean hasJoules(long amount) {
        return storedJoules >= Math.max(0L, amount);
    }

    private void changed() {
        if (machine != null) {
            machine.setChanged();
        }
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("Joules", storedJoules);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // Worlds written by the FE implementation migrate once at the fixed 1 FE = 1 J anchor.
        storedJoules = Math.max(0L, tag.contains("Joules") ? tag.getLong("Joules") : tag.getInt("Energy"));
    }

    /** Keeps the old child key so existing world data reaches the migration above. */
    @Override
    public String storageKey() {
        return "energyBufferModule";
    }
}
