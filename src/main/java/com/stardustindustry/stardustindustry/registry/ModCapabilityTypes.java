package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.gas.IGasHandler;

import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * The mod's own capability types, for resources the game has no built-in one for.
 *
 * <p>Items, fluids and energy each have a NeoForge capability; gases do not,
 * because gases are this mod's abstraction. A mod that wants a gas to move between
 * its own blocks and another mod's therefore needs its own capability, and this
 * is where it is declared. Exposing it as a {@link BlockCapability} means the
 * normal {@code level.getCapability(...)} path works and a compatibility layer
 * can bridge it in either direction.</p>
 *
 * <p>The capability is named in this mod's namespace so another mod may depend on
 * it without colliding with anything.</p>
 */
public final class ModCapabilityTypes {

    /** Gas moved in and out of a block, keyed on the accessed side ({@code Void} = side-agnostic). */
    public static final BlockCapability<IGasHandler, Void> GAS_HANDLER =
            BlockCapability.createVoid(
                    StardustIndustry.id("gas_handler"),
                    IGasHandler.class);

    private ModCapabilityTypes() {}
}
