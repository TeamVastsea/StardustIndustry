package com.stardustindustry.stardustindustry.energy;

/**
 * What a cable's conductor is <b>made of</b>.
 *
 * <h2>Two metals, two jobs</h2>
 * <p>Copper is the best room-temperature conductor, but it is heavy. Aluminium
 * conducts worse yet weighs less, and that trade is the whole reason
 * <b>steel-cored aluminium</b> (ACSR, 钢芯铝绞线) exists in the real world: the
 * steel core carries the tension across a long span, the aluminium sleeve carries
 * the current, and the whole thing weighs about half what an equivalent copper
 * conductor would. Power companies use it for almost every overhead line on the
 * planet for exactly that reason.</p>
 *
 * <table>
 *   <caption>What the material decides</caption>
 *   <tr><th>material</th><th>resistance</th><th>sag</th><th>insulated variants</th></tr>
 *   <tr><td>copper</td><td>baseline</td><td>normal</td><td>bare, class 1, class 2</td></tr>
 *   <tr><td>ACSR</td><td><b>higher</b> (worse conductor)</td><td><b>less</b> (lighter, stiffer)</td>
 *       <td><b>none</b> — always bare</td></tr>
 * </table>
 *
 * <h2>Why ACSR has no insulated version</h2>
 * <p>Not an oversight and not a balance decision: insulating an aerial conductor
 * makes it heavy and stiff enough that you may as well use copper, and no real
 * utility insulates a 100 kV span. So an ACSR line is <b>always bare</b>, which
 * means it is always lethal to touch on anything above LV (§6.11). The player who
 * buys the long-span cable is buying a line that must be routed where nobody
 * walks.</p>
 *
 * <h2>Why the resistance is higher</h2>
 * <p>Aluminium has about 1.6x the resistivity of copper and an ACSR conductor of
 * the same current rating carries less metal, because the steel core is doing
 * mechanical work rather than conducting. Rather than model two effects, the
 * material carries one plain multiplier against the copper figure of the same
 * ampacity class: {@link #resistanceMultiplier()}.</p>
 *
 * <p>The consequence is the point: an ACSR run loses more per block than a copper
 * run of the same rating, so it is only worth using when the span is long enough
 * that <b>being able to string it at all</b> matters more than the loss.</p>
 */
public enum CableMaterial {
    /**
     * Copper: the default conductor, and the only one that can be insulated.
     * Resistance and sag are the baseline every other material is measured
     * against.
     */
    COPPER("copper", 1.0f, 1.0f, true),

    /**
     * Steel-cored aluminium (ACSR): lighter, stiffer, higher resistance, bare
     * only. The overhead long-span conductor.
     */
    ACSR("acsr", 1.3f, 0.35f, false);

    private final String id;
    private final float resistanceMultiplier;
    private final float sagMultiplier;
    private final boolean canBeInsulated;

    CableMaterial(String id, float resistanceMultiplier, float sagMultiplier, boolean canBeInsulated) {
        this.id = id;
        this.resistanceMultiplier = resistanceMultiplier;
        this.sagMultiplier = sagMultiplier;
        this.canBeInsulated = canBeInsulated;
    }

    /** Id for registry and translation keys. */
    public String id() {
        return id;
    }

    /**
     * Ohms per block are multiplied by this, relative to copper of the same
     * ampacity class.
     *
     * <p>1.3 for ACSR: worse than the best conductor, better than pretending the
     * steel core conducts. A run of it costs 30 % more loss than copper of the
     * same rating.</p>
     */
    public float resistanceMultiplier() {
        return resistanceMultiplier;
    }

    /**
     * How far the cable sags, as a multiplier on the standard curve (1.0 =
     * copper, the reference).
     *
     * <p>0.35 for ACSR: it weighs roughly half as much for the same span and the
     * steel core lets it be strung under more tension, so the overhead line hangs
     * much straighter. That is the second half of "why use it for long spans" —
     * it both can span and <b>looks</b> like it spans.</p>
     */
    public float sagMultiplier() {
        return sagMultiplier;
    }

    /**
     * Whether this conductor has insulated variants at all.
     *
     * <p>Used by {@link CableAmpacity} to decide which products exist, so the
     * catalogue cannot drift into "ACSR class 2" by accident.</p>
     */
    public boolean canBeInsulated() {
        return canBeInsulated;
    }

    /** Looks a material up by its id, or {@code null}. */
    public static CableMaterial byId(String id) {
        for (CableMaterial material : values()) {
            if (material.id().equals(id)) {
                return material;
            }
        }
        return null;
    }
}
