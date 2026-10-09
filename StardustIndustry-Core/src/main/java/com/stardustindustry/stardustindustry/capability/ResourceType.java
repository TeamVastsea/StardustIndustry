package com.stardustindustry.stardustindustry.capability;

import net.minecraft.util.StringRepresentable;

/**
 * The kinds of resource that can move between machines.
 *
 * <p>The recipe system is written against this abstraction rather than against
 * concrete item or fluid stacks, so a single recipe engine can consume items,
 * fluids, gases, energy and heat without a separate code path per kind. Each
 * concrete resource carries a payload (see {@link ResourceStack}) whose meaning
 * depends on the type:</p>
 *
 * <ul>
 *   <li>{@link #ITEM} - a registered item + count</li>
 *   <li>{@link #FLUID} - a fluid type + millibuckets</li>
 *   <li>{@link #GAS} - a gas type + millibuckets (same units, different network)</li>
 *   <li>{@link #ENERGY} - FE</li>
 *   <li>{@link #HEAT} - thermal units</li>
 * </ul>
 *
 * <p>Solids, liquids and gases share the item/fluid registries they are drawn
 * from; {@link #GAS} exists as a distinct type because gases live on their own
 * pipe network and obey different pressure rules, not because they are stored
 * differently.</p>
 */
public enum ResourceType implements StringRepresentable {
    ITEM("item"),
    FLUID("fluid"),
    GAS("gas"),
    ENERGY("energy"),
    HEAT("heat");

    private final String name;

    ResourceType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** True for resources measured in millibuckets. */
    public boolean isMeasuredInMillibuckets() {
        return this == FLUID || this == GAS;
    }

    /** True for resources that are transported by a pipe network. */
    public boolean isPiped() {
        return this == FLUID || this == GAS;
    }
}
