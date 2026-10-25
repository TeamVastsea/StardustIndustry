package com.stardustindustry.stardustindustry.energy;

/**
 * One flexible cable strung between two terminals.
 *
 * <h2>What a cable is, and is not</h2>
 * <p>A cable is <b>not a block</b>. It occupies no grid cell, has no collision box
 * and no blockstate; it is a piece of data joining two terminals. The design calls
 * this a 柔性导线 (soft wire): the player pulls it from terminal to terminal, it
 * hangs in a catenary, and the terminals at either end are the real electrical
 * nodes (§6.9).</p>
 *
 * <h2>Three properties, of which one is cosmetic</h2>
 * <ul>
 *   <li>{@link CableAmpacity} — how much current the copper carries (electrical);</li>
 *   <li>{@link CableInsulation} — what the player feels when touching it and how
 *       much current the sheath costs (electrical);</li>
 *   <li>{@link CableColor} — what it looks like (<b>cosmetic only</b>, §6.8).</li>
 * </ul>
 * <p>Colour lives here rather than in the item so sixteen colours do not become
 * sixteen items. It is per-cable, so the same spool can lay a red wire and a blue
 * one.</p>
 *
 * <h2>A cable has no voltage tier</h2>
 * <p>It has a current class and a sheath class instead. Cables do <b>not</b>
 * restrict which terminals they join. A 380 V sheath on a 10 kV line does not
 * refuse the connection or blow up: it simply stops protecting the player who
 * touches it (§6.11). Refusing would hide the rule; letting the player get
 * shocked teaches it.</p>
 *
 * <h2>Endpoints are ordered</h2>
 * <p>The pair is stored with {@link TerminalEndpoint#compareTo the smaller endpoint
 * first}. That makes a cable symmetric: {@code (A, B)} and {@code (B, A)} are the
 * same cable, so pulling it from the other end cannot create a duplicate. Use
 * {@link #a()} and {@link #b()} for "the two ends" and never rely on which one the
 * player started from.</p>
 *
 * <h2>Electrical length (§6.9)</h2>
 * <p>The sag is <b>visual only</b>. The length used for resistance is the
 * straight-line distance between the two anchors, rounded to whole blocks: loss is
 * charged per block, not per metre of hanging curve.</p>
 *
 * @param ampacity   the conductor class
 * @param insulation the sheath class
 * @param color      the cosmetic paint, {@link CableColor#UNDYED} when unpainted
 * @param a          one end, always the smaller of the two
 * @param b          the other end, always the larger
 */
public record CableConnection(CableAmpacity ampacity, CableInsulation insulation, CableColor color,
                              TerminalEndpoint a, TerminalEndpoint b) {

    public CableConnection {
        if (a.compareTo(b) > 0) {
            TerminalEndpoint swap = a;
            a = b;
            b = swap;
        }
    }

    /** An unpainted cable between two endpoints, in whatever order they came in. */
    public static CableConnection of(CableAmpacity ampacity, CableInsulation insulation,
                                     TerminalEndpoint first, TerminalEndpoint second) {
        return new CableConnection(ampacity, insulation, CableColor.UNDYED, first, second);
    }

    /** True when this cable has {@code endpoint} as one of its ends. */
    public boolean touches(TerminalEndpoint endpoint) {
        return a.equals(endpoint) || b.equals(endpoint);
    }

    /**
     * The same cable with a different colour.
     *
     * <p>Colour is the only mutable-looking property, and it is not mutable in
     * place: a cable lives in two racks, so a repaint has to replace it in both.
     * Returning a new record makes that explicit instead of leaving two halves of
     * one cable disagreeing.</p>
     */
    public CableConnection withColor(CableColor newColor) {
        return new CableConnection(ampacity, insulation, newColor, a, b);
    }

    /** Stable identity of this run, independent of product, paint and endpoint order. */
    public CableRunId runId() {
        return new CableRunId(a, b);
    }

    /** The other end from {@code endpoint}, or {@code null} when it is not on this cable. */
    public TerminalEndpoint other(TerminalEndpoint endpoint) {
        if (a.equals(endpoint)) {
            return b;
        }
        if (b.equals(endpoint)) {
            return a;
        }
        return null;
    }

    /**
     * Current this cable carries before it starts to overheat: the conductor's
     * ampacity derated by its sheath.
     */
    public int effectiveAmpacity() {
        return ampacity.amps() * insulation.ampacityFactorPercent() / 100;
    }

    /** Ohms for a run of {@code blocks} whole blocks. */
    public float resistance(int blocks) {
        return ohmsPerBlock() * blocks;
    }

    /** Ohms per block for this conductor. Insulation derates ampacity, not resistance. */
    public float ohmsPerBlock() {
        return ampacity.ohmsPerBlock();
    }

    /** How far this cable sags, relative to copper (1.0). */
    public float sagMultiplier() {
        return ampacity.material().sagMultiplier();
    }

    /** What touching this cable on a {@code volts} grid does to a player (§6.11). */
    public CableInsulation.Shock shockAt(int volts) {
        return insulation.shockAt(volts);
    }

    /** True when two cable records describe the same physical endpoint pair. */
    public boolean sameRun(CableConnection other) {
        return other != null && runId().equals(other.runId());
    }

    /**
     * The electrical length in whole blocks: straight-line distance between the
     * two anchors, rounded to nearest (§6.9).
     *
     * <p>Never reports zero. Two neighbouring cells of one rack are close, but they
     * are still a real run and must not be free; floor-clamped to 1 so per-block
     * resistance can never vanish silently.</p>
     */
    public static int electricalLengthBlocks(org.joml.Vector3f fromAnchor, org.joml.Vector3f toAnchor) {
        return Math.max(1, Math.round(fromAnchor.distance(toAnchor)));
    }
}
