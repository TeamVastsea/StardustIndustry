package com.stardustindustry.stardustindustry.energy;

/**
 * Stable electrical identity of one cable run.
 *
 * <p>Paint is deliberately absent: recolouring changes only a cable's appearance,
 * so electrical state such as accumulated heat must remain attached to the same
 * conductor. Endpoints are canonicalized to make the identity independent of the
 * direction in which the cable was placed.</p>
 */
public record CableRunKey(CableAmpacity ampacity, CableInsulation insulation,
                          TerminalEndpoint a, TerminalEndpoint b) {

    public CableRunKey {
        if (a.compareTo(b) > 0) {
            TerminalEndpoint swap = a;
            a = b;
            b = swap;
        }
    }
}
