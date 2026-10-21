package com.stardustindustry.stardustindustry.energy;

/**
 * The colour of one cable's surface, stored on the cable rather than on the item.
 *
 * <h2>Why colour is data, not an item</h2>
 * <p>A cable's electrical properties are what it is <b>made of</b> — a conductor
 * class and an insulation class — and those are fixed by the item. Colour is what
 * the player <b>marks</b> on it, the way real installers wrap coloured tape or
 * order coloured sheath to tell one circuit from another. Making colour an item
 * dimension would multiply the catalogue without changing anything electrical:
 * sixteen coloured variants of seventeen cables is 272 items, all behaving the
 * same. Keeping it as a field on the cable means <b>one item per real product</b>
 * and a painted wire costs nothing extra to register.</p>
 *
 * <p>It also means the colour survives exactly where it matters: it is written
 * into the cable's record on both racks (§6.9), so it saves, syncs with the
 * chunk, and comes back with the item when the cable is cut down.</p>
 *
 * <h2>Unpainted is not a colour</h2>
 * <p>{@link #UNDYED} is the state of a cable nobody has painted, and it is
 * deliberately <b>not one fixed colour</b>. What an unpainted cable looks like
 * follows from what it <b>is</b>:</p>
 * <ul>
 *   <li><b>bare conductor</b> shows the metal itself — copper is copper-coloured,
 *       steel-cored aluminium is bright aluminium. There is no sheath on a bare
 *       wire, so claiming one has a colour would be a lie, and the whole point of
 *       a bare line is that you can see it is bare;</li>
 *   <li><b>insulated cable</b> shows its sheath, which is <b>grey</b> as
 *       manufactured — the usual colour of industrial sheathing, and dark enough
 *       to read as a cable yet light enough to be seen against grass and stone.
 *       Black was tried first and simply disappeared into the world.</li>
 * </ul>
 *
 * <h2>Bare cable is never painted</h2>
 * <p>Dye colours a <b>sheath</b>. A bare conductor has none, so it cannot be
 * painted at all: the server refuses it and the renderer ignores any colour a
 * stale save might carry for it. See {@link #isPaintable(CableInsulation)}.</p>
 *
 * <h2>Colour does not touch the simulation</h2>
 * <p>§6.8 is explicit: colour is identification only. It never affects
 * insulation, ampacity, resistance or which terminal a cable may join.</p>
 */
public record CableColor(int dyeColor) {

    /**
     * The colour of an unwanted cable-time paint: nobody dyed it.
     *
     * <p>Stored as {@code 0xFFFFFF} plus one "unset" flag rather than as a magic
     * absence, so the record is still a plain packed RGB and every call site that
     * just wants "a colour" keeps working. {@link #isPainted()} is the question to
     * ask; the RGB below is only what is drawn if something really insists on one
     * (grey, the insulated default).</p>
     */
    private static final int UNPAINTED = -1;

    /** The unpainted state: no dye, drawn as the cable's own natural appearance. */
    public static final CableColor UNDYED = new CableColor(UNPAINTED);

    /** Sheath grey: what an insulated cable looks like before anybody paints it. */
    public static final int SHEATH_GREY = 0x8C8C8C;

    /** Copper metal: what a bare copper conductor looks like. */
    public static final int BARE_COPPER = 0xB87333;

    /** Bright aluminium: what a bare steel-cored aluminium conductor looks like. */
    public static final int BARE_ALUMINIUM = 0xB8BEC4;

    /**
     * The default paint used by older callers: an unpainted cable.
     *
     * <p>Kept as the named constant {@code BLACK} so nothing silently changes
     * meaning — but it is <b>not</b> "black any more". It is "nobody has painted
     * this", which draws as {@link #SHEATH_GREY} on an insulated cable and as the
     * metal on a bare one. Black itself is still available as a dye.</p>
     */
    public static final CableColor BLACK = UNDYED;

    /**
     * The sixteen dye colours, in vanilla's own order, so "red wool" and "red
     * cable" are the same red to a player.
     *
     * <p>Kept as ARGB-free packed RGB, which is what both the renderer and the
     * item tooltip need. {@code DYE_COLORS[15]} is the black dye, which is a real
     * paintable colour even though it is also this class's historical name.</p>
     */
    public static final int[] DYE_COLORS = {
            0xF9FFFE, // white
            0xF9801D, // orange
            0xC74EBD, // magenta
            0x3AB3DA, // light blue
            0xFED83D, // yellow
            0x80C71F, // lime
            0xF38BAA, // pink
            0x474F52, // gray
            0x9D9D97, // light gray
            0x169C9C, // cyan
            0x8932B8, // purple
            0x3C44AA, // blue
            0x835432, // brown
            0x5E7C16, // green
            0xB02E26, // red
            0x1D1D21, // black
    };

    public CableColor {
        // Mask to RGB: a cable is never translucent, and a stray alpha byte would
        // silently make the wire disappear or glow. The unpainted sentinel is the
        // one value that must survive untouched.
        if (dyeColor != UNPAINTED) {
            dyeColor &= 0xFFFFFF;
        }
    }

    /** True when nobody has painted this cable. */
    public boolean isPainted() {
        return dyeColor != UNPAINTED;
    }

    /** True for a cable nobody has dyed. Opposite of {@link #isPainted()}. */
    public boolean isDefault() {
        return !isPainted();
    }

    /**
     * The colour to draw, given what the cable is.
     *
     * <p>The single place the "unpainted" rule is resolved, so a bare wire cannot
     * be drawn grey by one call site and copper by another.</p>
     */
    public int renderedColor(CableAmpacity ampacity, CableInsulation insulation) {
        if (isPainted()) {
            return dyeColor;
        }
        return naturalColor(ampacity, insulation);
    }

    /** The colour of an unpainted cable: metal if bare, sheath grey if insulated. */
    public static int naturalColor(CableAmpacity ampacity, CableInsulation insulation) {
        if (insulation == CableInsulation.BARE) {
            return isAluminium(ampacity) ? BARE_ALUMINIUM : BARE_COPPER;
        }
        return SHEATH_GREY;
    }

    /** True for steel-cored aluminium classes, whose bare surface is bright aluminium. */
    private static boolean isAluminium(CableAmpacity ampacity) {
        return ampacity != null && ampacity.material() == CableMaterial.ACSR;
    }

    /**
     * Whether a conductor with this insulation could ever be painted.
     *
     * <p>Only a real sheath can be dyed. A bare conductor shows its metal, and no
     * dye applied to bare metal should change what the player sees, because the
     * metal is the information: it is how a bare line is recognised at a glance.</p>
     */
    public static boolean isPaintable(CableInsulation insulation) {
        return insulation != CableInsulation.BARE;
    }

    /** Red channel, 0..1, for the renderer. */
    public float red() {
        return ((resolvedRaw() >> 16) & 0xFF) / 255.0f;
    }

    /** Green channel, 0..1. */
    public float green() {
        return ((resolvedRaw() >> 8) & 0xFF) / 255.0f;
    }

    /** Blue channel, 0..1. */
    public float blue() {
        return (resolvedRaw() & 0xFF) / 255.0f;
    }

    /**
     * The raw RGB for users that do not know the cable's classes.
     *
     * <p>For an unpainted cable this is the insulated default, grey; callers that
     * have the cable should use {@link #renderedColor(CableAmpacity,
     * CableInsulation)} instead, which also gets bare metal right.</p>
     */
    public int resolvedRaw() {
        return isPainted() ? dyeColor : SHEATH_GREY;
    }

    /** Vanilla's dye index this colour corresponds to, or {@code -1} if none. */
    public int dyeIndex() {
        for (int i = 0; i < DYE_COLORS.length; i++) {
            if (DYE_COLORS[i] == dyeColor) {
                return i;
            }
        }
        return -1;
    }

    /** A colour from a vanilla dye index, clamped to the palette. */
    public static CableColor ofDyeIndex(int index) {
        if (index < 0 || index >= DYE_COLORS.length) {
            return UNDYED;
        }
        return new CableColor(DYE_COLORS[index]);
    }
}
