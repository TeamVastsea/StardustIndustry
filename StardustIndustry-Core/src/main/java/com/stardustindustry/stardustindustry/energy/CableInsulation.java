package com.stardustindustry.stardustindustry.energy;

/**
 * How well a cable's sheath protects the player who touches it.
 *
 * <h2>Insulation is about touch, not about the cable surviving</h2>
 * <p>This is the single most important thing to get right about this enum, and
 * the design changed to say it: <b>insulation class does not decide what voltage
 * a cable may join, and an under-rated cable does not "puncture and explode".</b>
 * A wire is a wire; if you string it onto a 10 kV grid it will happily carry the
 * current. What differs is what happens <b>when a player touches it</b> (§6.11).</p>
 *
 * <p>That is why there are only two classes and not four. In reality a cable's
 * voltage rating is a <i>safety</i> rating: below it, touching the sheath is
 * harmless; above it, the sheath is simply not there at grid scale and the
 * player is touching bare metal.</p>
 *
 * <h2>The two classes</h2>
 * <table>
 *   <caption>What touching a cable does, per class and per grid voltage</caption>
 *   <tr><th>class</th><th>holds up to</th><th>touching it</th></tr>
 *   <tr><td>class 1</td><td>LV (380 V)</td><td>harmless at LV; <b>light shock</b> on MV; lethal above</td></tr>
 *   <tr><td>class 2</td><td>MV (10 kV)</td><td>harmless at LV and MV; lethal at HV and above</td></tr>
 *   <tr><td>bare</td><td>—</td><td>light shock on LV; lethal above LV</td></tr>
 * </table>
 *
 * <p>"Lethal" follows §6.11's rule — enough damage per tick that no player
 * survives it even in armour. Above MV the voltage is far past what any sheath a
 * player could carry would hold, so HV and EHV treat every class the same.</p>
 *
 * <p>The class also <b>costs current capacity</b>: the thicker a sheath, the
 * more heat it traps, so the same conductor carries less inside a better
 * insulation. See {@link #ampacityFactorPercent()}.</p>
 */
public enum CableInsulation {
    /** Class 1: holds up to LV (380 V). Harmless at LV, a light shock on MV. */
    CLASS_1("class_1", 380),
    /** Class 2: holds up to MV (10 kV). Harmless at LV and MV. */
    CLASS_2("class_2", 10_000),
    /** No sheath at all: bare conductor, a light shock on LV and lethal above it. */
    BARE("bare", 0);

    private final String id;
    private final int holdsUpToVolts;

    CableInsulation(String id, int holdsUpToVolts) {
        this.id = id;
        this.holdsUpToVolts = holdsUpToVolts;
    }

    /** Id for registry and translation keys. */
    public String id() {
        return id;
    }

    /**
     * The highest grid voltage this sheath holds at, in volts; {@code 0} for bare
     * conductor, which holds nothing.
     */
    public int holdsUpToVolts() {
        return holdsUpToVolts;
    }

    /** True when touching this cable on a {@code volts} grid is harmless. */
    public boolean protectsAt(int volts) {
        return volts <= holdsUpToVolts;
    }

    /**
     * How much current a conductor of a given class carries <b>inside this
     * sheath</b>, as a percentage of the bare conductor's ampacity.
     *
     * <p>Real derating: insulation traps heat, and a thicker wall traps more.
     * This is the trade that keeps the better sheath from being a free upgrade —
     * a class 2 cable on an MV line carries less than a bare one would, which is
     * exactly why overhead MV lines are bare.</p>
     */
    public int ampacityFactorPercent() {
        return switch (this) {
            case BARE -> 100;
            case CLASS_1 -> 90;
            case CLASS_2 -> 80;
        };
    }

    /**
     * The effect of touching a cable of this class on a grid of {@code volts}.
     *
     * <p>The one place the §6.11 table is written down; the damage code and the
     * tooltip both ask this rather than each deciding for themselves.</p>
     */
    public Shock shockAt(int volts) {
        if (protectsAt(volts)) {
            return Shock.NONE;
        }
        if (this == BARE && volts <= EnergyTier.LV.volts()) {
            return Shock.LIGHT;
        }
        if (this == CLASS_1 && volts <= EnergyTier.MV.volts()) {
            // Class 1 is thin insulation: it is rated for LV, and MV leaks through
            // it. Annoying and painful, not fatal — the player is meant to learn
            // "this line needs a better sheath", not to die to find out.
            return Shock.LIGHT;
        }
        // Bare MV and every under-insulated HV/EHV line are immediately lethal.
        return Shock.LETHAL;
    }

    /** Looks a class up by its id, or {@code null}. */
    public static CableInsulation byId(String id) {
        for (CableInsulation insulation : values()) {
            if (insulation.id().equals(id)) {
                return insulation;
            }
        }
        return null;
    }

    /** What touching a cable does, per §6.11. */
    public enum Shock {
        /** The sheath does its job: no damage. */
        NONE,
        /** A painful reminder: some damage, survivable even at full health. */
        LIGHT,
        /** No survival: far more damage per tick than any player has health. */
        LETHAL
    }
}
