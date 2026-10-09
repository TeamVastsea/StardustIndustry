package com.stardustindustry.stardustindustry.gas;

/**
 * The standard contract for something that holds gas, the gas analogue of
 * NeoForge's {@code IFluidHandler}.
 *
 * <p>Pipes, tanks and machines move gas through this interface so that our own
 * gas pipes and a compatibility layer over another mod's gas network can both
 * speak to a gas tank without the tank knowing which one is calling.</p>
 *
 * <p>The semantics mirror {@code IFluidHandler} exactly — simulate before
 * execute, report what was actually moved — because that is the pattern every
 * mod's automation already expects. A {@code GasStack} returned from a fill or
 * drain is what <em>actually</em> moved, never more.</p>
 */
public interface IGasHandler {

    /** Whether an operation should change anything. */
    enum GasAction {
        /** Only report what would happen. */
        SIMULATE,
        /** Actually move the gas. */
        EXECUTE;

        public boolean simulate() {
            return this == SIMULATE;
        }
    }

    /** The number of separate gas tanks this handler exposes. */
    int getTanks();

    /** The gas held in tank {@code tank}, or {@link GasStack#EMPTY}. */
    GasStack getGasInTank(int tank);

    /** The capacity of tank {@code tank}, in mB. */
    int getTankCapacity(int tank);

    /** True when {@code stack} may be put into tank {@code tank}. */
    boolean isGasValid(int tank, GasStack stack);

    /**
     * Fills the handler with {@code resource}.
     *
     * @return the amount of {@code resource} actually accepted, in mB
     */
    int fill(GasStack resource, GasAction action);

    /**
     * Drains from the handler, honouring the gas and amount of {@code resource}.
     *
     * @return the gas actually drained, or {@link GasStack#EMPTY}
     */
    GasStack drain(GasStack resource, GasAction action);

    /**
     * Drains up to {@code maxDrain} mB of whatever gas the handler holds.
     *
     * @return the gas actually drained, or {@link GasStack#EMPTY}
     */
    GasStack drain(int maxDrain, GasAction action);
}
