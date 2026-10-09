package com.stardustindustry.stardustindustry.compat.mekanism;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.gas.Gas;
import com.stardustindustry.stardustindustry.gas.GasRegistry;
import com.stardustindustry.stardustindustry.gas.IGasHandler;
import com.stardustindustry.stardustindustry.registry.ModBlocks;
import com.stardustindustry.stardustindustry.registry.ModCapabilities;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.common.capabilities.Capabilities;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Wires this mod's gas system into Mekanism when Mekanism is present.
 *
 * <p>Everything here is optional. The mod is compiled against Mekanism's API but
 * must run without it, so this class is only ever loaded — and only ever touches
 * a Mekanism class — after {@link #isPresent()} has confirmed the mod is on the
 * classpath. That is why the capability hook is a small guarded call from
 * {@link com.stardustindustry.stardustindustry.registry.ModCapabilities} into a
 * method here rather than a direct registration in that class: linking against
 * Mekanism types happens lazily, only on a pack that has Mekanism.</p>
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li><b>Registers every chemical as a gas</b>, so the whole Mekanism catalogue
 *       — gases, slurries, infusions, pigments, including nuclear materials — can
 *       be stored in a gas tank. The sweep runs once, after registries freeze, so
 *       it sees the final list.</li>
 *   <li><b>Exposes our gas ports as Mekanism chemical handlers</b>, so a
 *       Pressurized Tube connects to a gas port and moves chemicals in and out.
 *       Tubes reach the port the same way any Mekanism pipe does: by asking that
 *       side for the chemical capability, which now answers.</li>
 * </ol>
 *
 * <h2>What it does not do</h2>
 * Only the port is bridged. The gas tank's shell is not, so a tube cannot connect
 * to the vessel wall and be rate-treated differently from a port — a rule that
 * holds for fluids too.
 */
public final class MekanismCompat {

    private static final String MEKANISM_MODID = "mekanism";

    private MekanismCompat() {}

    /** True when Mekanism is loaded in this instance. */
    public static boolean isPresent() {
        return ModList.get().isLoaded(MEKANISM_MODID);
    }

    /**
     * Hooks the Mekanism chemical capability onto our gas ports.
     *
     * <p>Called from the capability-registration event. A no-op when Mekanism is
     * absent, so the mod registers its own gas capability alone and a pack
     * without Mekanism never loads any Mekanism type.</p>
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!isPresent()) {
            return;
        }
        event.registerBlock(Capabilities.CHEMICAL.block(),
                (level, pos, state, blockEntity, side) -> {
                    IGasHandler gas = ModCapabilities.portGasCapability(blockEntity, state);
                    return gas == null ? null : new MekanismChemicalHandlerAdapter(gas);
                },
                ModBlocks.LV_GAS_PORT.get());
        StardustIndustry.LOGGER.info(
                "Mekanism detected: gas ports now also accept Mekanism chemical pipes");
    }

    /**
     * Registers a {@link Gas} for every chemical Mekanism knows about.
     *
     * <p>Runs after registries freeze so the catalogue is final. It is called on
     * the mod-loading thread during common setup, before any world exists, and it
     * only writes to {@link GasRegistry}, so no synchronisation is needed.</p>
     */
    public static void registerGases() {
        if (!isPresent()) {
            return;
        }
        int count = 0;
        for (Chemical chemical : MekanismAPI.CHEMICAL_REGISTRY) {
            ResourceLocation id = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            if (id == null) {
                continue;
            }
            GasRegistry.register(new Gas(id, chemical.getColorRepresentation(),
                    chemical.getTranslationKey()));
            count++;
        }
        StardustIndustry.LOGGER.info(
                "Mekanism bridge: registered {} chemicals as storable gases", count);
    }
}
