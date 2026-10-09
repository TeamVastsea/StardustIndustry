package com.stardustindustry.stardustindustry.compat.mekanism;

import com.stardustindustry.stardustindustry.gas.Gas;
import com.stardustindustry.stardustindustry.gas.GasRegistry;
import com.stardustindustry.stardustindustry.gas.GasStack;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;

import net.minecraft.resources.ResourceLocation;

/**
 * Translates between Mekanism's chemicals and this mod's {@link Gas}s.
 *
 * <p>Mekanism has no separate "gas" type any more: gases, slurries, infusions
 * and pigments are all {@link Chemical}s in one registry. To let a gas tank hold
 * any of them, every chemical is mapped onto a {@link Gas} by its registry id.
 * The mapping is one-to-one and stable, so a chemical put into a tank by a
 * pressurized tube is the same gas when it is read back out by another
 * Mekanism machine.</p>
 *
 * <h2>Id mapping</h2>
 * A chemical's registry id is used verbatim, keeping Mekanism's namespace, so
 * Mekanism's {@code hydrogen} becomes the gas {@code mekanism:hydrogen}. This
 * deliberately does <em>not</em> reuse this mod's own {@code stardustindustry:hydrogen}:
 * the two are different registrations and merging them would silently claim a
 * foreign id. A gas tank built around Mekanism therefore holds
 * {@code mekanism:hydrogen}, which is what the tube hands it.</p>
 *
 * <h2>Appearance</h2>
 * The tint is Mekanism's own chemical colour, so a tank of its hydrogen looks
 * the same hue in our glass as it does in its own tank.</p>
 *
 * <h2>Amounts</h2>
 * Mekanism measures chemicals in {@code long}; this mod measures gas in
 * {@code int} mB. A single stack larger than {@link Integer#MAX_VALUE} mB
 * (~2.1 billion, far past any built tank) is clamped, which is safe because no
 * tank of ours can hold that much anyway.</p>
 */
public final class MekanismGasBridge {

    private MekanismGasBridge() {}

    /**
     * The {@link Gas} that stands in for a Mekanism chemical, registering one on
     * first use.
     *
     * <p>Null-safe: an empty chemical maps to {@code null}, which is how
     * {@link #toGasStack} reports "nothing".</p>
     */
    public static Gas gasFor(Chemical chemical) {
        if (chemical == null) {
            return null;
        }
        ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
        if (id == null) {
            // An unregistered chemical cannot be addressed in a tank; refusing is
            // better than minting an id the next load could not resolve.
            return null;
        }
        // getColor() on a Chemical is the tint the tank renders, and its
        // translation key gives the real localised name. Both are copied so the
        // core Gas abstraction stays Mekanism-free. Registration is idempotent,
        // so resolving the same chemical twice returns one Gas object.
        return GasRegistry.register(
                new Gas(id, chemical.getColorRepresentation(), chemical.getTranslationKey()));
    }

    /** The {@link Gas} for a chemical id, or {@code null} when it is unknown. */
    public static Gas gasForId(ResourceLocation id) {
        Gas known = GasRegistry.get(id);
        if (known != null) {
            return known;
        }
        // Lazily resolve an id that was not seen during the initial sweep: this
        // covers a chemical added by a datapack or another add-on after our
        // bridge ran, so an id read from NBT still resolves to a live Gas.
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        return chemical == null ? null : gasFor(chemical);
    }

    /** Converts a Mekanism stack to this mod's gas stack. */
    public static GasStack toGasStack(ChemicalStack stack) {
        if (stack == null || stack.isEmpty()) {
            return GasStack.EMPTY;
        }
        Gas gas = gasFor(stack.getChemical());
        if (gas == null) {
            return GasStack.EMPTY;
        }
        long amount = stack.getAmount();
        return GasStack.of(gas, amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
    }

    /** Converts this mod's gas stack to a Mekanism chemical stack. */
    public static ChemicalStack toChemicalStack(GasStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ChemicalStack.EMPTY;
        }
        ResourceLocation id = stack.gas().getId();
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(id);
        if (chemical == null) {
            return ChemicalStack.EMPTY;
        }
        return new ChemicalStack(chemical, stack.amount());
    }
}
