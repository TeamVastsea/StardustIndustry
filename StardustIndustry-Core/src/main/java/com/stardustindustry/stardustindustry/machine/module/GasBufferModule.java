package com.stardustindustry.stardustindustry.machine.module;

import java.util.function.IntSupplier;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.gas.GasStack;
import com.stardustindustry.stardustindustry.gas.IGasHandler;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * A gas buffer held by a machine: the gas analogue of
 * {@link FluidBufferModule}.
 *
 * <p>It holds one {@link GasStack} at a time (a gas tank stores a single gas,
 * which is what makes "what is in it" a single readable answer) and exposes an
 * {@link IGasHandler} so pipes, canisters and compatibility layers can fill and
 * drain it without translation.</p>
 *
 * <h2>Capacity</h2>
 * Like the fluid module, capacity comes either from a fixed tier or from a
 * {@link #setCapacityOverride(IntSupplier) supplier} the owning machine installs.
 * A gas tank uses the supplier, because its capacity comes from the interior
 * volume it was built with rather than from any voltage, and the live value is
 * still scaled by the structure's buffer multiplier.</p>
 *
 * <h2>Rate</h2>
 * {@link #transferLimit()} caps a single fill or drain, mirroring the fluid
 * module, so a tiered gas port moves gas at its own rated throughput.
 */
public final class GasBufferModule implements MachineModule, ModuleHost.ResourceHolder {

    /** The tier whose numbers seed the module, or {@code null} when overridden. */
    private final EnergyTier tier;
    /** The capacity used when no override is supplied, in mB. */
    private final int baseCapacity;
    /** Supplied by the owning machine; when non-null it wins over {@link #baseCapacity}. */
    private IntSupplier capacityOverride;
    /** The largest amount one fill or drain may move, or {@link Integer#MAX_VALUE}. */
    private int transferLimit;

    /** The gas currently held, or {@link GasStack#EMPTY}. */
    private GasStack gas = GasStack.EMPTY;

    private MachineBlockEntity machine;

    /** Gas received since the last rate decay, in mB. */
    private int recentIn;
    /** Gas drained since the last rate decay, in mB. */
    private int recentOut;
    /** Ticks until the rolling counters are halved. */
    private int decayTimer;

    public GasBufferModule(EnergyTier tier) {
        this.tier = tier;
        this.baseCapacity = Math.max(1000, tier.voltage() * 1000);
        this.transferLimit = tier.maxTransfer();
    }

    /**
     * A module whose capacity and transfer are supplied by the owning machine.
     *
     * @param initialCapacity the capacity before the override is installed, in mB
     */
    public GasBufferModule(int initialCapacity) {
        this.tier = null;
        this.baseCapacity = Math.max(1, initialCapacity);
        this.transferLimit = Integer.MAX_VALUE;
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public EnergyTier tier() {
        return tier;
    }

    /** Installs a dynamic capacity, read on every access. */
    public void setCapacityOverride(IntSupplier capacityOverride) {
        this.capacityOverride = capacityOverride;
    }

    /** The largest amount one operation may move, in mB. */
    public int transferLimit() {
        return transferLimit;
    }

    /** Overrides the per-operation transfer limit; used by a tiered port. */
    public void setTransferLimit(int transferLimit) {
        this.transferLimit = Math.max(1, transferLimit);
    }

    @Override
    public int buffered() {
        return gas.amount();
    }

    /** The live capacity, in mB. */
    public int capacity() {
        int base = capacityOverride != null ? capacityOverride.getAsInt() : baseCapacity;
        float multiplier = machine == null ? 1.0f : machine.modifiers().bufferMultiplier();
        return Math.max(1, Math.round(Math.max(1, base) * multiplier));
    }

    /** The gas currently held, or {@link GasStack#EMPTY}. */
    public GasStack gas() {
        return gas;
    }

    public int amount() {
        return gas.amount();
    }

    /** mB received per second, as last measured. */
    public int inputRate() {
        return recentIn;
    }

    /** mB drained per second, as last measured. */
    public int outputRate() {
        return recentOut;
    }

    /** Fills the buffer, recording the accepted amount for the rate display. */
    public int fill(GasStack resource, boolean simulate) {
        return fill(resource, transferLimit, simulate);
    }

    /**
     * Fills the buffer, accepting at most {@code maxTransfer} mB.
     *
     * <p>A gas tank holds one gas at a time: anything other than what is already
     * held is refused outright, the same rule the fluid module enforces, so every
     * entry point behaves identically.</p>
     */
    public int fill(GasStack resource, int maxTransfer, boolean simulate) {
        if (resource.isEmpty()) {
            return 0;
        }
        if (!gas.isEmpty() && !gas.isSameGas(resource)) {
            return 0;
        }
        int room = capacity() - gas.amount();
        if (room <= 0) {
            return 0;
        }
        int accepted = Math.min(Math.min(resource.amount(), maxTransfer), room);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            gas = GasStack.of(resource.gas(), gas.amount() + accepted);
            recentIn += accepted;
            changed();
        }
        return accepted;
    }

    /** Drains up to {@code amount} mB, recording the drained amount. */
    public GasStack drain(int amount, boolean simulate) {
        return drain(amount, transferLimit, simulate);
    }

    /**
     * Drains up to {@code amount} mB, moving at most {@code maxTransfer}.
     */
    public GasStack drain(int amount, int maxTransfer, boolean simulate) {
        if (gas.isEmpty()) {
            return GasStack.EMPTY;
        }
        int moved = Math.min(Math.min(amount, maxTransfer), gas.amount());
        if (moved <= 0) {
            return GasStack.EMPTY;
        }
        GasStack drained = GasStack.of(gas.gas(), moved);
        if (!simulate) {
            gas = gas.withAmount(gas.amount() - moved);
            recentOut += moved;
            changed();
        }
        return drained;
    }

    private void changed() {
        if (machine != null) {
            machine.markContentsChanged();
        }
    }

    @Override
    public void serverTick() {
        if (++decayTimer < 20) {
            return;
        }
        decayTimer = 0;
        recentIn /= 2;
        recentOut /= 2;
    }

    /** The gas capability this module exposes to ports and pipes. */
    public IGasHandler capability() {
        return capability(transferLimit);
    }

    /** The gas capability, capped at {@code maxTransfer} per operation. */
    public IGasHandler capability(int maxTransfer) {
        return new IGasHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public GasStack getGasInTank(int tank) {
                return GasBufferModule.this.gas();
            }

            @Override
            public int getTankCapacity(int tank) {
                return capacity();
            }

            @Override
            public boolean isGasValid(int tank, GasStack stack) {
                // One gas per tank: anything other than what is already held is
                // not valid, which lets a pipe see the rule before it tries.
                return GasBufferModule.this.gas().isEmpty()
                        || GasBufferModule.this.gas().isSameGas(stack);
            }

            @Override
            public int fill(GasStack resource, GasAction action) {
                return GasBufferModule.this.fill(resource, maxTransfer, action.simulate());
            }

            @Override
            public GasStack drain(GasStack resource, GasAction action) {
                if (resource.isEmpty() || !resource.isSameGas(gas())) {
                    return GasStack.EMPTY;
                }
                return GasBufferModule.this.drain(resource.amount(), maxTransfer, action.simulate());
            }

            @Override
            public GasStack drain(int maxDrain, GasAction action) {
                return GasBufferModule.this.drain(maxDrain, maxTransfer, action.simulate());
            }
        };
    }

    @Override
    public String storageKey() {
        return "gasBuffer";
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        // An empty buffer writes no gas key at all, mirroring the fluid module:
        // encoding an empty stack on a freshly placed tank would be wasteful and
        // the parse path already treats a missing key as empty.
        if (!gas.isEmpty()) {
            tag.put("Gas", gas.save(registries));
        }
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        gas = tag.contains("Gas")
                ? GasStack.parse(registries, tag.getCompound("Gas"))
                : GasStack.EMPTY;
    }
}
