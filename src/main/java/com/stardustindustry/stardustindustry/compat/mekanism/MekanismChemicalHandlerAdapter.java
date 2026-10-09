package com.stardustindustry.stardustindustry.compat.mekanism;

import com.stardustindustry.stardustindustry.gas.GasStack;
import com.stardustindustry.stardustindustry.gas.IGasHandler;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;

/**
 * Presents one of this mod's {@link IGasHandler}s as a Mekanism
 * {@link IChemicalHandler}.
 *
 * <p>This is the adapter that makes Mekanism's Pressurized Tubes able to fill
 * and drain our gas ports: the tube asks for a Mekanism chemical capability, and
 * this object answers by translating every call into a gas fill/drain on the
 * wrapped handler. The direction of translation is always Mekanism → us; the
 * gas buffer never learns that Mekanism exists.</p>
 *
 * <h2>One tank</h2>
 * Our gas buffers always expose a single tank, so {@link #getChemicalTanks()}
 * reports one and the tank index is ignored. That matches how a tube sees a
 * Mekanism chemical tank.</p>
 *
 * <h2>Amounts</h2>
 * Mekanism passes {@code long} amounts and this mod uses {@code int}; a transfer
 * larger than {@link Integer#MAX_VALUE} mB is clamped before it reaches the
 * buffer. No tank of ours can approach that, so the clamp is invisible in
 * practice and only exists to keep the conversion total.</p>
 */
public final class MekanismChemicalHandlerAdapter implements IChemicalHandler {

    private final IGasHandler gas;

    public MekanismChemicalHandlerAdapter(IGasHandler gas) {
        this.gas = gas;
    }

    private static int clampAmount(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    @Override
    public int getChemicalTanks() {
        return gas.getTanks();
    }

    @Override
    public ChemicalStack getChemicalInTank(int tank) {
        return MekanismGasBridge.toChemicalStack(gas.getGasInTank(tank));
    }

    @Override
    public void setChemicalInTank(int tank, ChemicalStack stack) {
        // The buffer has no "set" operation, and Mekanism only uses this on its
        // own tanks. Draining everything then filling with the new stack keeps
        // the handler honest for any caller that does try it.
        gas.drain(Integer.MAX_VALUE, IGasHandler.GasAction.EXECUTE);
        GasStack replacement = MekanismGasBridge.toGasStack(stack);
        if (!replacement.isEmpty()) {
            gas.fill(replacement, IGasHandler.GasAction.EXECUTE);
        }
    }

    @Override
    public long getChemicalTankCapacity(int tank) {
        return gas.getTankCapacity(tank);
    }

    @Override
    public boolean isValid(int tank, ChemicalStack stack) {
        return gas.isGasValid(tank, MekanismGasBridge.toGasStack(stack));
    }

    @Override
    public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (stack == null || stack.isEmpty()) {
            return ChemicalStack.EMPTY;
        }
        IGasHandler.GasAction gasAction = action.simulate()
                ? IGasHandler.GasAction.SIMULATE
                : IGasHandler.GasAction.EXECUTE;
        int accepted = gas.fill(MekanismGasBridge.toGasStack(stack), gasAction);
        return accepted <= 0 ? ChemicalStack.EMPTY : stack.copyWithAmount(accepted);
    }

    @Override
    public ChemicalStack extractChemical(int tank, long amount, Action action) {
        GasStack drained = gas.drain(clampAmount(amount),
                action.simulate() ? IGasHandler.GasAction.SIMULATE : IGasHandler.GasAction.EXECUTE);
        return MekanismGasBridge.toChemicalStack(drained);
    }
}
