package com.stardustindustry.stardustindustry.energy;

/**
 * How much current a cable's conductor can take continuously.
 *
 * <h2>One of two independent axes</h2>
 * <p>A cable is described by two perpendicular properties (§6.1): how much
 * current it carries (this enum) and what happens when you touch it
 * ({@link CableInsulation}). Either may change without the other.</p>
 *
 * <h2>Why a class also has a material</h2>
 * <p>A class is <b>an amount of current</b>; the conductor that delivers it is a
 * separate decision. 800 A in copper is a thick, heavy, insulated-able cable;
 * 800 A in steel-cored aluminium is a lighter line that can be strung far and
 * sags less, at the cost of higher loss and no insulation at all (§6.1,
 * {@link CableMaterial}).</p>
 *
 * <p>The two ACSR classes sit <b>between</b> the copper classes in current rather
 * than on top of them: they are the long-span product, not the biggest conductor.
 * Nothing about the ladder is a single sequence any more — copper climbs
 * 50 → 5000, and ACSR is a parallel branch for overhead runs.</p>
 *
 * <h2>Why the copper steps are about three times each</h2>
 * <p>The steps are ratio-driven rather than rounded to pretty numbers: about
 * <b>three times</b> the one below, because that is the gap a player can feel. An
 * upgrade changes "this works" to "this works with headroom", not "this works by
 * 10 %". Five steps cover the whole grid: a 380 V lighting circuit at the bottom,
 * a 500 kV transmission line at the top.</p>
 *
 * <table>
 *   <caption>Current classes and what they are for</caption>
 *   <tr><th>class</th><th>material</th><th>ampacity</th><th>typical use</th></tr>
 *   <tr><td>50 A</td><td>copper</td><td>50</td><td>lighting, control, one small machine</td></tr>
 *   <tr><td>150 A</td><td>copper</td><td>150</td><td>a workshop's LV feeder</td></tr>
 *   <tr><td>500 A</td><td>copper</td><td>500</td><td>factory LV bus, MV distribution</td></tr>
 *   <tr><td>800 A</td><td>ACSR</td><td>800</td><td>MV overhead run, long span</td></tr>
 *   <tr><td>1 500 A</td><td>copper</td><td>1 500</td><td>MV / HV substation feeder</td></tr>
 *   <tr><td>1 600 A</td><td>ACSR</td><td>1 600</td><td>HV overhead run, long span</td></tr>
 *   <tr><td>5 000 A</td><td>copper</td><td>5 000</td><td>HV / EHV bulk transmission</td></tr>
 * </table>
 */
public enum CableAmpacity {
    /** 50 A copper — the thinnest class, for lighting and control. */
    A50("50", 50, CableMaterial.COPPER),
    /** 150 A copper — a workshop feeder. */
    A150("150", 150, CableMaterial.COPPER),
    /** 500 A copper — factory LV bus, MV distribution. */
    A500("500", 500, CableMaterial.COPPER),
    /**
     * 800 A steel-cored aluminium — the small long-span overhead line.
     *
     * <p>Sits between the copper 500 and 1500 classes: the product is chosen for
     * span length, not for the biggest number, so it does not need to beat the
     * copper ladder.</p>
     */
    A800_ACSR("800", 800, CableMaterial.ACSR),
    /** 1 500 A copper — MV / HV substation feeder. */
    A1500("1500", 1_500, CableMaterial.COPPER),
    /**
     * 1 600 A steel-cored aluminium — the large long-span overhead line.
     *
     * <p>Deliberately just above the copper 1 500 class: at the same rating the
     * copper version loses slightly less but cannot be strung as far, so the
     * choice is genuinely about distance.</p>
     */
    A1600_ACSR("1600", 1_600, CableMaterial.ACSR),
    /** 5 000 A copper — HV / EHV bulk transmission. */
    A5000("5000", 5_000, CableMaterial.COPPER);

    /**
     * Ohms per block for a copper conductor of the 500 A class, before the
     * material multiplier (§6.9, §6.12).
     *
     * <p>Derived rather than tabulated: resistance falls as copper area grows, and
     * a class carrying three times the current needs about three times the area,
     * so resistance drops by the same factor each copper step. The base is set so
     * a 500 A run of one block costs a noticeable but survivable amount, which is
     * what makes distance a real decision (§7.3).</p>
     */
    private static final float BASE_OHMS_PER_BLOCK = 8.0e-5f;

    /** How much copper resistance falls per step up the copper ladder. */
    private static final float OHMS_PER_STEP = 3.0f;

    /** The copper class whose resistance is {@link #BASE_OHMS_PER_BLOCK}. */
    private static final CableAmpacity BASE_COPPER_CLASS = A500;

    private final String label;
    private final int amps;
    private final CableMaterial material;

    CableAmpacity(String label, int amps, CableMaterial material) {
        this.label = label;
        this.amps = amps;
        this.material = material;
    }

    /** Class label as written on the cable, e.g. {@code "1500"}. */
    public String label() {
        return label;
    }

    /** What the conductor is made of. */
    public CableMaterial material() {
        return material;
    }

    /**
     * Continuous current this conductor carries before it overheats, for a
     * <b>bare</b> conductor. Insulation may derate it — see
     * {@link CableInsulation#ampacityFactorPercent()}.
     *
     * <p>Crossing it does not sever the cable instantly: it starts the heat-up in
     * §6.10, which turns into smoke and finally failure.</p>
     */
    public int amps() {
        return amps;
    }

    /**
     * Ohms per block of run, material included.
     *
     * <p>The copper ladder is <b>anchored on the 500 A class</b> rather than on
     * enum order, because ACSR classes now sit in the middle of the order and an
     * ordinal-based formula would silently give them the wrong copper baseline.
     * The steps on either side are counted on the copper ladder only.</p>
     */
    public float ohmsPerBlock(float insulationFactorPercent) {
        return copperBaselineOhmsPerBlock() * material.resistanceMultiplier()
                * insulationFactorPercent / 100.0f;
    }

    /**
     * Ohms per block for a bare conductor of this class, ignoring any sheath.
     *
     * <p>Kept for callers that want to show "conductor only" figures; the value
     * the simulation uses is {@link #ohmsPerBlock(float)}.</p>
     */
    public float ohmsPerBlock() {
        return copperBaselineOhmsPerBlock() * material.resistanceMultiplier();
    }

    /**
     * Ohms per block this class would have in copper, before the material
     * multiplier.
     *
     * <p>Computed by distance from the 500 A anchor on the <b>copper</b> ladder.
     * A non-copper class borrows the copper class with the nearest rating, so the
     * multiplier applies to a sensible base instead of to an enum position.</p>
     */
    private float copperBaselineOhmsPerBlock() {
        int steps = copperStepsFrom500();
        return steps >= 0
                ? BASE_OHMS_PER_BLOCK / pow(OHMS_PER_STEP, steps)
                : BASE_OHMS_PER_BLOCK * pow(OHMS_PER_STEP, -steps);
    }

    /**
     * How many copper steps above (+) or below (−) the 500 A class this rating
     * is.
     *
     * <p>Counted by <b>rating</b>, not by enum position, so ACSR classes land on
     * the copper step their current corresponds to. 800 A sits between 500 and
     * 1500 and is treated as the 500 step plus a fraction, which is exactly how
     * resistance behaves in reality: it tracks copper cross-section, not a label.</p>
     */
    private int copperStepsFrom500() {
        double ratio = (double) amps / BASE_COPPER_CLASS.amps;
        // Each step up multiplies current by three, so log base 3 gives the step.
        return (int) Math.round(Math.log(ratio) / Math.log(OHMS_PER_STEP));
    }

    /** {@code base^n} for the small integer exponents used here. */
    private static float pow(float base, int exponent) {
        float result = 1.0f;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    /** Numeric id for registry and translation keys, e.g. {@code "1500"}. */
    public String id() {
        return label;
    }

    /**
     * True when this class exists only as bare conductor.
     *
     * <p>ACSR is the case: nobody insulates a long-span aerial conductor.</p>
     */
    public boolean isBareOnly() {
        return !material.canBeInsulated();
    }

    /** Looks a class up by its id, or {@code null}. */
    public static CableAmpacity byId(String id) {
        for (CableAmpacity ampacity : values()) {
            if (ampacity.id().equals(id)) {
                return ampacity;
            }
        }
        return null;
    }
}
