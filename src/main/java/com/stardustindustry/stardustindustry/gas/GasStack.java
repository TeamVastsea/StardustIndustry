package com.stardustindustry.stardustindustry.gas;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * An amount of a gas, measured in millibuckets.
 *
 * <p>The gas analogue of {@code FluidStack}: a gas type plus an amount. The unit
 * is mB, deliberately the same as fluids, so a tank's capacity, a port's rate and
 * a recipe's costs read the same whether the medium is liquid or gas.</p>
 *
 * <h2>Empty</h2>
 * {@link #EMPTY} is the canonical "nothing" stack. An empty stack has a
 * {@code null} gas and an amount of zero; it is what a handler returns when it
 * has nothing to give, and it must never be stored in a filled tank.
 *
 * @param gas    the gas, or {@code null} when empty
 * @param amount the amount in mB, zero when empty
 */
public record GasStack(Gas gas, int amount) {

    /** The empty stack: no gas, no amount. */
    public static final GasStack EMPTY = new GasStack(null, 0);

    public GasStack {
        if (amount < 0) {
            throw new IllegalArgumentException("Gas amount may not be negative: " + amount);
        }
        if (gas == null && amount != 0) {
            throw new IllegalArgumentException("An empty gas stack must have amount zero");
        }
    }

    /** A stack of {@code amount} mB of {@code gas}, or empty when the amount is zero. */
    public static GasStack of(Gas gas, int amount) {
        return gas == null || amount <= 0 ? EMPTY : new GasStack(gas, amount);
    }

    /** True when this stack carries no gas. */
    public boolean isEmpty() {
        return gas == null || amount <= 0;
    }

    /** A copy with the amount replaced, collapsing to empty at zero. */
    public GasStack withAmount(int newAmount) {
        return of(gas, newAmount);
    }

    /** A copy grown by {@code extra} mB. */
    public GasStack grow(int extra) {
        return withAmount(amount + extra);
    }

    /** True when both stacks hold the same gas (both empty counts as the same). */
    public boolean isSameGas(GasStack other) {
        if (isEmpty() || other.isEmpty()) {
            return isEmpty() && other.isEmpty();
        }
        return gas.id().equals(other.gas.id());
    }

    // ---- persistence ----

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!isEmpty()) {
            tag.putString("Gas", gas.id().toString());
            tag.putInt("Amount", amount);
        }
        return tag;
    }

    public static GasStack parse(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("Gas")) {
            return EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Gas"));
        if (id == null) {
            return EMPTY;
        }
        Gas gas = GasRegistry.get(id);
        return gas == null ? EMPTY : of(gas, tag.getInt("Amount"));
    }
}
