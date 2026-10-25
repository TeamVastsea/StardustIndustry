package com.stardustindustry.stardustindustry.energy;

/** Native electrical boundary used by terminals and the grid solver. */
public interface IElectricalPort {
    EnergyTier tier();

    WiringMode wiring();

    ElectricalPortRole electricalRole();

    /** Sources and isolating equipment must be explicitly closed before this is true. */
    boolean enabled();

    long maxPowerWatts();

    /** Identity of the backing energy account; several physical ports may share one budget. */
    default Object energyAccount() {
        return this;
    }

    /**
     * Identity of the logical port represented by this physical capability. Ports with the same
     * key contribute their conductors to one connection and request power only once.
     */
    default Object portGroup() {
        return this;
    }

    /**
     * Whether this account represents an in-line device that may receive and extract energy in
     * the same tick. Storage accounts deliberately keep the default one-way behavior.
     */
    default boolean allowsSameTickPassThrough() {
        return false;
    }

    /**
     * Supplies this tick's downstream demand to an in-line source before its input circuit is
     * solved. The value is the output-side source draw, including downstream line loss when known.
     * Ordinary sources, loads, and storage ignore this preparation step.
     */
    default void preparePassThroughDemandWatts(long outputWatts) {}

    /** Nameplate power used to derive the measured-current trip point; zero disables it. */
    default long overcurrentTripWatts() {
        return 0L;
    }

    default long requestedPowerWatts() {
        return 0L;
    }

    default long availablePowerWatts() {
        return 0L;
    }

    default long receiveJoules(long amount, boolean simulate) {
        return 0L;
    }

    default long extractJoules(long amount, boolean simulate) {
        return 0L;
    }

    default void updateGridState(GridState state) {}

    /** Updates a non-primary member of a logical port without repeating destructive side effects. */
    default void updateGridStateReplica(GridState state) {
        updateGridState(state);
    }

    /**
     * Reports a fault on the circuit fed by this source. In-line protection
     * uses this separate signal to isolate its downstream segment without
     * making ordinary generators or transformers own the downstream fault.
     *
     * @return {@code true} only when the fault was not handled here and must
     *         continue through this in-line device to its upstream circuit
     */
    default boolean updateDownstreamFault(GridState state) {
        return false;
    }
}
