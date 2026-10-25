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
     * Whether this account represents an in-line device that may receive and extract energy in
     * the same tick. Storage accounts deliberately keep the default one-way behavior.
     */
    default boolean allowsSameTickPassThrough() {
        return false;
    }

    /** Power threshold that trips an in-line protection source; zero disables protection signalling. */
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
