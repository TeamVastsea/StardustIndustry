package com.stardustindustry.stardustindustry.machine.terminal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

/**
 * One kind of terminal: the fitting a player bolts onto a rack cell.
 *
 * <h2>Why this type exists</h2>
 * <p>Terminals used to be identified by their <b>voltage tier alone</b>: the
 * renderer looked up {@code models/terminal/<tier>.json}, the item was named
 * {@code <tier>_terminal}, and the model file was chosen by the tier. That was
 * enough while one tier had exactly one terminal. It stops working the moment a
 * second terminal exists at the same voltage — a different post type, a busbar
 * clamp, a porcelain vs. resin insulator — because they would all share one
 * model and one anchor, and the last artist to edit the file would overwrite
 * the others' work.</p>
 *
 * <p>So a terminal is identified by its own <b>style id</b> (the item name), and
 * carries everything that varies independently of it:</p>
 *
 * <ul>
 *   <li>{@code id} — the style id, also the registry name stem of the item and
 *       the file name under {@code models/terminal/<id>.json};</li>
 *   <li>{@code tier} — which racks it fits, and its voltage rating. A rack only
 *       accepts terminals whose tier matches it (§6.3); that rule is unchanged;</li>
 *   <li>{@code itemName} — the registry name of the item, kept explicit rather
 *       than derived so a style may be added without renaming anything;</li>
 *   <li>{@code model} — the terminal's OBJ-pose config key (defaults to
 *       {@code id}), so two styles may deliberately share one look.</li>
 * </ul>
 *
 * <h2>Adding a new terminal</h2>
 * <ol>
 *   <li>Add a constant here with a fresh {@code id} and the tier it fits.</li>
 *   <li>Drop {@code assets/.../models/terminal/<id>.json} next to the others;
 *       {@code offset / origin / anchor} are then tuned per style.</li>
 *   <li>Point its item at the style in {@code ModItems}.</li>
 * </ol>
 * <p>Nothing in the renderer or the cable code needs to change: both ask the
 * style for its model key and its anchor, never the tier.</p>
 *
 * <p>The registry is a plain static map rather than a NeoForge registry on
 * purpose: these are code-level constants whose pose files ship in the same jar,
 * and a data-driven terminal would need a resource reload to answer "which
 * model does this item use" while the item is registered far earlier.</p>
 */
public final class TerminalStyle {

    /** Registry, filled by the constants below, in declaration order. */
    private static final Map<String, TerminalStyle> BY_ID = new LinkedHashMap<>();

    /**
     * LV terminal, 4x4 rack.
     *
     * <p>{@code lv_terminal} keeps its original name: this is the style that
     * every existing world already stores under {@code Tier=lv}.</p>
     */
    public static final TerminalStyle LV =
            register("lv_terminal", "lv_terminal", EnergyTier.LV, "lv");

    /** MV terminal, 2x2 rack (10 kV). */
    public static final TerminalStyle MV =
            register("mv_terminal", "mv_terminal", EnergyTier.MV, "mv");

    /** HV terminal, 1x1 rack (110 kV). */
    public static final TerminalStyle HV =
            register("hv_terminal", "hv_terminal", EnergyTier.HV, "hv");

    /** EHV terminal, 1x1 rack (500 kV). */
    public static final TerminalStyle EHV =
            register("ehv_terminal", "ehv_terminal", EnergyTier.EHV, "ehv");

    private final String id;
    private final String itemName;
    private final EnergyTier tier;
    private final String model;

    private TerminalStyle(String id, String itemName, EnergyTier tier, String model) {
        this.id = id;
        this.itemName = itemName;
        this.tier = tier;
        this.model = model;
    }

    private static TerminalStyle register(String id, String itemName, EnergyTier tier, String model) {
        TerminalStyle style = new TerminalStyle(id, itemName, tier, model);
        BY_ID.put(id, style);
        return style;
    }

    /**
     * The style id: registry-name stem of the item and the pose file name.
     *
     * <p>This is what a saved rack stores per cell, so it must stay stable.</p>
     */
    public String id() {
        return id;
    }

    /**
     * The item's registry name, e.g. {@code lv_terminal}.
     *
     * <p><b>This must match the resource file names</b>: the game looks for
     * {@code models/item/<itemName>.json} for the icon and
     * {@code item.stardustindustry.<itemName>} in the language files for the
     * name. A mismatch does not crash — it silently renders a magenta/black
     * missing model and prints the raw translation key — which is exactly what
     * happened when this was left as the bare tier name {@code lv} while the
     * files were renamed to {@code lv_terminal.json}.</p>
     */
    public String itemName() {
        return itemName;
    }

    /** The rack tier this terminal fits, and its voltage rating. */
    public EnergyTier tier() {
        return tier;
    }

    /** Key of the pose file under {@code models/terminal/}, without {@code .json}. */
    public String model() {
        return model;
    }

    /**
     * Name of this style's pose/anchor file under {@code models/terminal/},
     * without {@code .json}.
     *
     * <h2>Why this is the style id and not {@link #model()}</h2>
     * <p>This was the bug behind "editing {@code anchor} does nothing": the file
     * on disk is {@code lv_terminal.json} (named after the style, so a second
     * terminal at the same voltage gets its own file), but the loader asked for
     * {@code <model()>.json} = {@code lv.json}, which no longer exists. It logged
     * {@code No pose file for terminal style lv} and fell back to defaults —
     * silently ignoring every number in the file the artist was editing.</p>
     *
     * <p>So the pose file is named after the <b>style id</b>, which is also what
     * saves store and what the anchor lookup is keyed by. {@link #model()} stays
     * separate because it names the shared OBJ under {@code models/block/}
     * ({@code terminal_lv.obj}).</p>
     */
    public String poseFile() {
        return id;
    }

    /** Every style, in declaration order. */
    public static Collection<TerminalStyle> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Looks a style up by id, or {@code null} when the id is unknown. */
    public static TerminalStyle byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /**
     * The style a saved cell refers to, tolerating the old tier-only format.
     *
     * <h2>Migration</h2>
     * <p>Before styles existed a rack saved {@code Tier=lv} (and the tier was
     * also the model key). Existing worlds must keep working, so a name that is
     * not a style id is tried as a tier name and mapped to that tier's default
     * style. Nothing is written back — the next save stores the style id — but a
     * world loaded and played without saving does not lose its terminals.</p>
     */
    public static TerminalStyle fromSaved(String saved) {
        TerminalStyle direct = byId(saved);
        if (direct != null) {
            return direct;
        }
        EnergyTier tier = null;
        for (EnergyTier candidate : EnergyTier.values()) {
            if (candidate.getSerializedName().equals(saved)) {
                tier = candidate;
                break;
            }
        }
        if (tier == null) {
            return null;
        }
        // Old saves stored the tier name ("lv"), which is also the default
        // style's model key; the id carries the "_terminal" suffix.
        TerminalStyle fallback = byId(tier.getSerializedName() + "_terminal");
        if (fallback != null) {
            return fallback;
        }
        for (TerminalStyle style : BY_ID.values()) {
            if (style.tier == tier) {
                return style;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return id;
    }
}
