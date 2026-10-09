package com.stardustindustry.stardustindustry.gas;

import net.minecraft.resources.ResourceLocation;

/**
 * A gas type: one kind of gaseous substance a gas tank can hold.
 *
 * <p>Gases are the mod's own abstraction, not any particular mod's chemicals.
 * The game has no vanilla gas registry, and the only common source on modern
 * versions is Mekanism's chemicals, so the mod defines its own {@code Gas}
 * handle and lets compatibility layers map foreign chemicals onto it. That keeps
 * the core free of a hard Mekanism dependency: a pack without Mekanism still
 * builds, runs, and can register its own gases, while a pack with Mekanism gets
 * its whole chemical catalogue bridged in.</p>
 *
 * <h2>Identity</h2>
 * A gas is identified by its registry id. Two gases are the same gas when their
 * ids match, matching how items, fluids and the mod's other resources are
 * compared.
 *
 * <h2>Appearance</h2>
 * A gas carries a tint colour so the tank renderer can draw it. The tint is used
 * with the concentration model (see the renderer): a gas in a partially filled
 * tank is drawn fainter, up to {@value com.stardustindustry.stardustindustry.client.TankGasRenderer#MAX_ALPHA}
 * opacity when full.
 *
 * @param id      the registry id
 * @param color   the RGB tint, as 0xRRGGBB
 * @param nameKey the translation key for this gas's display name, or {@code null}
 *                to fall back to a key derived from the id. A bridge sets this to
 *                the foreign chemical's own translation key so its real localised
 *                name is used instead of our synthetic one.
 */
public record Gas(ResourceLocation id, int color, String nameKey) {

    public Gas {
        if (id == null) {
            throw new IllegalArgumentException("A gas must have an id");
        }
    }

    /** Creates a gas with no foreign name key, deriving one from its id. */
    public Gas(ResourceLocation id, int color) {
        this(id, color, null);
    }

    /** The gas's registry id. */
    public ResourceLocation getId() {
        return id;
    }

    /**
     * The gas's tint colour, as 0xRRGGBB.
     *
     * <p>This is the colour the tank renderer draws a full tank of it in;
     * partial fills scale that colour's opacity, not its hue.</p>
     */
    public int getColor() {
        return color;
    }

    /** The translation key for this gas's display name. */
    public String getDescriptionId() {
        if (nameKey != null) {
            return nameKey;
        }
        return "gas." + id.getNamespace() + "." + id.getPath();
    }
}
