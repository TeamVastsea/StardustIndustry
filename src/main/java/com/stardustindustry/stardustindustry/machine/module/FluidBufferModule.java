package com.stardustindustry.stardustindustry.machine.module;

import java.util.function.IntSupplier;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A fluid tank held by a machine.
 *
 * <p>The module holds one {@link FluidStack} (a tank stores a single fluid at a
 * time, which is what makes "what is in it" a single readable answer) and
 * exposes a standard {@link IFluidHandler} so pipes, buckets and other mods'
 * tanks can fill and drain it without translation.</p>
 *
 * <h2>Capacity</h2>
 * There are two ways a module learns how much it can hold:
 * <ul>
 *   <li>A fixed tier, where the capacity is derived from the tier's voltage (the
 *       generic machine case).</li>
 *   <li>A {@link #setCapacityOverride(IntSupplier) capacity override}, where the
 *       owning machine computes the capacity from its own structure. A tank uses
 *       this, because its capacity comes from the interior volume it was built
 *       with rather than from any voltage.</li>
 * </ul>
 * The live capacity is still scaled by the structure's buffer multiplier, so the
 * filler system keeps working unchanged.
 *
 * <h2>Rate</h2>
 * {@link #transferLimit()} caps a single fill or drain. A tiered module uses its
 * tier's maximum transfer; a tank leaves it unlimited and lets the port that
 * asked do the limiting, since each port may be a different tier.
 *
 * <p>Fill and drain amounts are accumulated into two rolling counters so the
 * parameter screen can show an input and output rate. They decay once per
 * second, so the number is a rough throughput rather than a lifetime total.</p>
 */
public final class FluidBufferModule implements MachineModule, ModuleHost.ResourceHolder {

    /** The tier whose numbers seed the module, or {@code null} when overridden. */
    private final EnergyTier tier;
    /** The capacity used when no override is supplied, in mB. */
    private final int baseCapacity;
    /** Supplied by the owning machine; when non-null it wins over {@link #baseCapacity}. */
    private IntSupplier capacityOverride;
    /** The largest amount one fill or drain may move, or {@link Integer#MAX_VALUE}. */
    private int transferLimit;
    private final FluidTank tank;
    private MachineBlockEntity machine;

    /** Fluid received since the last rate decay, in mB. */
    private int recentIn;
    /** Fluid drained since the last rate decay, in mB. */
    private int recentOut;
    /** Ticks until the rolling counters are halved. */
    private int decayTimer;

    public FluidBufferModule(EnergyTier tier) {
        this.tier = tier;
        this.baseCapacity = Math.max(1000, tier.voltage() * 1000);
        // A tiered machine's transfer is fixed by its tier.
        this.transferLimit = tier.maxTransfer();
        this.tank = newTank(this.baseCapacity);
    }

    /**
     * A module whose capacity and transfer are supplied by the owning machine.
     *
     * @param initialCapacity the capacity before the override is installed, in mB
     */
    public FluidBufferModule(int initialCapacity) {
        this.tier = null;
        this.baseCapacity = Math.max(1, initialCapacity);
        this.transferLimit = Integer.MAX_VALUE;
        this.tank = newTank(this.baseCapacity);
    }

    private FluidTank newTank(int capacity) {
        return new FluidTank(capacity) {
            @Override
            protected void onContentsChanged() {
                if (machine != null) {
                    machine.setChanged();
                }
            }
        };
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public EnergyTier tier() {
        return tier;
    }

    /**
     * Installs a dynamic capacity, read on every access.
     *
     * <p>A container's capacity is a property of its structure, which the machine
     * evaluates separately, so the machine hands the module a supplier rather than
     * a number it would have to remember to update.</p>
     */
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
        return tank.getFluidAmount();
    }

    /** The live capacity: the base scaled by the structure's buffer multiplier. */
    public int capacity() {
        int base = capacityOverride != null ? capacityOverride.getAsInt() : baseCapacity;
        float multiplier = machine == null ? 1.0f : machine.modifiers().bufferMultiplier();
        return Math.max(1, Math.round(Math.max(1, base) * multiplier));
    }

    /** The fluid currently held, or {@link FluidStack#EMPTY}. */
    public FluidStack fluid() {
        return tank.getFluid();
    }

    public int amount() {
        return tank.getFluidAmount();
    }

    /** mB received per second, as last measured. */
    public int inputRate() {
        return recentIn;
    }

    /** mB drained per second, as last measured. */
    public int outputRate() {
        return recentOut;
    }

    /** Fills the tank, recording the accepted amount for the rate display. */
    public int fill(FluidStack resource, boolean simulate) {
        return fill(resource, transferLimit, simulate);
    }

    /**
     * Fills the tank, accepting at most {@code maxTransfer} mB.
     *
     * @param maxTransfer the port's rated throughput, or {@link Integer#MAX_VALUE}
     */
    public int fill(FluidStack resource, int maxTransfer, boolean simulate) {
        // Capacity is dynamic, so the tank's own ceiling is re-synced before a
        // fill: a structural change since construction takes effect immediately.
        tank.setCapacity(capacity());
        if (resource.isEmpty()) {
            return 0;
        }
        FluidStack offered = resource.getAmount() > maxTransfer
                ? resource.copyWithAmount(maxTransfer)
                : resource;
        int accepted = tank.fill(offered,
                simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (!simulate && accepted > 0) {
            recentIn += accepted;
            if (machine != null) {
                machine.setChanged();
            }
        }
        return accepted;
    }

    /** Drains up to {@code amount} mB, recording the drained amount for the rate display. */
    public FluidStack drain(int amount, boolean simulate) {
        return drain(amount, transferLimit, simulate);
    }

    /**
     * Drains up to {@code amount} mB, moving at most {@code maxTransfer}.
     *
     * @param maxTransfer the port's rated throughput, or {@link Integer#MAX_VALUE}
     */
    public FluidStack drain(int amount, int maxTransfer, boolean simulate) {
        FluidStack drained = tank.drain(Math.min(amount, maxTransfer),
                simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        if (!simulate && !drained.isEmpty()) {
            recentOut += drained.getAmount();
            if (machine != null) {
                machine.setChanged();
            }
        }
        return drained;
    }

    @Override
    public void serverTick() {
        if (++decayTimer < 20) {
            return;
        }
        decayTimer = 0;
        // Halve rather than clear, so a steady flow settles on a stable reading
        // instead of flickering between full and zero every second.
        recentIn /= 2;
        recentOut /= 2;
    }

    /** The fluid capability this module exposes to ports and pipes. */
    public IFluidHandler capability() {
        return capability(transferLimit);
    }

    /**
     * The fluid capability, capped at {@code maxTransfer} per operation.
     *
     * <p>A port of a given tier asks for its own cap, so two ports on one tank
     * move fluid at their own rates rather than at the tank's.</p>
     */
    public IFluidHandler capability(int maxTransfer) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return FluidBufferModule.this.fluid();
            }

            @Override
            public int getTankCapacity(int tank) {
                return capacity();
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return true;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return FluidBufferModule.this.fill(resource, maxTransfer, action.simulate());
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, fluid())) {
                    return FluidStack.EMPTY;
                }
                return FluidBufferModule.this.drain(resource.getAmount(), maxTransfer, action.simulate());
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidBufferModule.this.drain(maxDrain, maxTransfer, action.simulate());
            }
        };
    }

    @Override
    public String storageKey() {
        return "fluidBuffer";
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag fluid = new CompoundTag();
        tank.getFluid().save(registries, fluid);
        tag.put("Fluid", fluid);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        FluidStack stack = tag.contains("Fluid")
                ? FluidStack.parseOptional(registries, tag.getCompound("Fluid"))
                : FluidStack.EMPTY;
        tank.setFluid(stack);
    }
}
