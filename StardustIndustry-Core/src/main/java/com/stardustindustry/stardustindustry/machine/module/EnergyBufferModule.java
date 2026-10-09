package com.stardustindustry.stardustindustry.machine.module;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * An FE energy buffer with a fixed tier.
 *
 * <p>The module exposes a standard {@link IEnergyStorage} so it connects to
 * every other mod's FE machines, cables and batteries without translation. It
 * enforces the tier's voltage on external cables: a connection may only push or
 * pull at the tier's maximum transfer, which is what makes an under-rated cable
 * a real (and fixable) problem rather than an invisible one.</p>
 *
 * <p>Only a machine-side port reaches this storage; external access goes
 * through the machine's port blocks, which query the module through
 * {@link #capability()}.</p>
 */
public final class EnergyBufferModule implements MachineModule, ModuleHost.ResourceHolder {

    private final EnergyTier tier;
    private int stored;
    private MachineBlockEntity machine;

    public EnergyBufferModule(EnergyTier tier) {
        this.tier = tier;
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public EnergyTier tier() {
        return tier;
    }

    @Override
    public int buffered() {
        return stored;
    }

    public int capacity() {
        // The buffer ceiling is the tier's base multiplied by the filler set's
        // buffer multiplier. Reading it live means adding or removing a buffer
        // core takes effect immediately, at the cost of a cheap multiply.
        float multiplier = machine == null ? 1.0f : machine.modifiers().bufferMultiplier();
        return Math.max(1, Math.round(tier.maxBuffer() * multiplier));
    }

    public int stored() {
        return stored;
    }

    /** Adds energy, clamped to capacity. Returns the amount actually accepted. */
    public int receive(int amount, boolean simulate) {
        int accepted = Math.min(amount, capacity() - stored);
        if (!simulate && accepted > 0) {
            stored += accepted;
            if (machine != null) {
                machine.setChanged();
            }
        }
        return accepted;
    }

    /** Removes energy if available. Returns the amount actually extracted. */
    public int extract(int amount, boolean simulate) {
        int taken = Math.min(amount, stored);
        if (!simulate && taken > 0) {
            stored -= taken;
            if (machine != null) {
                machine.setChanged();
            }
        }
        return taken;
    }

    public boolean hasEnergy(int amount) {
        return stored >= amount;
    }

    /** The FE capability this module exposes to ports and cables. */
    public IEnergyStorage capability() {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                // A connection may never exceed the tier's rated transfer.
                return EnergyBufferModule.this.receive(Math.min(toReceive, tier.maxTransfer()), simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return EnergyBufferModule.this.extract(Math.min(toExtract, tier.maxTransfer()), simulate);
            }

            @Override
            public int getEnergyStored() {
                return stored;
            }

            @Override
            public int getMaxEnergyStored() {
                return capacity();
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", stored);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        stored = tag.getInt("Energy");
    }
}
