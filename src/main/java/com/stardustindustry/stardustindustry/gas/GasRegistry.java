package com.stardustindustry.stardustindustry.gas;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * The registry of known gases.
 *
 * <p>This is the mod's own gas list, not a mirror of any other mod's. It starts
 * with a small set of common industrial gases so a pack using only this mod has
 * something to store, and it accepts further gases from compatibility layers —
 * most importantly the Mekanism bridge, which registers a {@code Gas} for every
 * Mekanism chemical so its whole catalogue (including nuclear materials) can be
 * stored in a gas tank.</p>
 *
 * <h2>Identity and stability</h2>
 * A gas lives and dies by its id. Ids are namespaced, so a bridged chemical uses
 * its originating mod's namespace under a dedicated prefix (see the Mekanism
 * bridge) to avoid colliding with a native gas of the same path. Registration is
 * idempotent: {@link #register} returns the existing gas when the id is already
 * known, so re-loading a compatibility layer never duplicates entries.
 *
 * <h2>Threading</h2>
 * Registration happens during mod construction and compatibility setup on the
 * main thread; lookups happen on the server and client threads. The map is only
 * written during setup, before the world exists, and only read afterwards, so no
 * locking is needed.
 */
public final class GasRegistry {

    private static final Map<ResourceLocation, Gas> GASES = new LinkedHashMap<>();

    // ---- built-in gases for packs without any gas mod ----

    /** Hydrogen, a pale blue. */
    public static final Gas HYDROGEN = registerNative("hydrogen", 0x9FC7F5);
    /** Oxygen, a pale cyan. */
    public static final Gas OXYGEN = registerNative("oxygen", 0xBFE9FF);
    /** Nitrogen, a grey-blue. */
    public static final Gas NITROGEN = registerNative("nitrogen", 0xB8C4D8);
    /** Carbon dioxide, a dull grey. */
    public static final Gas CARBON_DIOXIDE = registerNative("carbon_dioxide", 0x9AA0A0);
    /** Steam, a near-white. */
    public static final Gas STEAM = registerNative("steam", 0xE6F0F5);
    /** Natural gas (methane), a warm amber. */
    public static final Gas NATURAL_GAS = registerNative("natural_gas", 0xE0C079);

    private GasRegistry() {}

    private static Gas registerNative(String path, int color) {
        return register(new Gas(ResourceLocation.fromNamespaceAndPath("stardustindustry", path), color));
    }

    /**
     * Registers a gas, or returns the existing entry with the same id.
     *
     * <p>Idempotent by design: a compatibility layer may be initialised more than
     * once across a session's reloads, and the gas it bridges must stay the same
     * object so identity comparisons keep working.</p>
     */
    public static Gas register(Gas gas) {
        Gas existing = GASES.get(gas.id());
        if (existing != null) {
            return existing;
        }
        GASES.put(gas.id(), gas);
        return gas;
    }

    /** The gas with this id, or {@code null} when none is registered. */
    public static Gas get(ResourceLocation id) {
        return GASES.get(id);
    }

    /** True when a gas of this id is registered. */
    public static boolean contains(ResourceLocation id) {
        return GASES.containsKey(id);
    }

    /** Every registered gas. */
    public static Collection<Gas> all() {
        return GASES.values();
    }
}
