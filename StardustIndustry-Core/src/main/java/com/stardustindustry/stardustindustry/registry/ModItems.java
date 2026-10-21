package com.stardustindustry.stardustindustry.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableInsulation;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * All plain items this mod adds.
 *
 * <p>Intermediate products of the processing chain (crushed ore, dust, ...) are
 * registered here. Their registry names stay flat ({@code crushed_iron}, never
 * {@code crushed/iron}) to match vanilla conventions and keep translation keys
 * working; the chain they belong to is expressed by their tags, not their id.</p>
 *
 * <p>Only the crushing stage's first intermediate is present so far. Further
 * intermediates are added as their machines land, one per stage of the chain.</p>
 */
public final class ModItems {

    /** Raw ore reduced to a crushable concentrate. */
    public static final DeferredItem<Item> CRUSHED_IRON = ModRegistries.ITEMS.registerSimpleItem("crushed_iron",
            new Item.Properties());

    /** The engineer's installation tool: installs and dismantles multiblocks. */
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.tool.InstallationToolItem> INSTALLATION_TOOL =
            ModRegistries.ITEMS.register("installation_tool",
                    () -> new com.stardustindustry.stardustindustry.machine.tool.InstallationToolItem(
                            new Item.Properties().stacksTo(1)));

    // ---- terminals ----
    //
    // One item per voltage tier. A terminal is bolted onto a terminal mount's
    // grid and becomes one power-grid node there; see
    // docs/electric-power-system-design.md §6.3.

    /** A low-voltage terminal (220 V / 380 V): bolts onto an LV mount. */
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem> LV_TERMINAL =
            terminal("lv_terminal", com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
    /** A medium-voltage terminal (10 kV): bolts onto an MV mount. */
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem> MV_TERMINAL =
            terminal("mv_terminal", com.stardustindustry.stardustindustry.energy.EnergyTier.MV);
    /** A high-voltage terminal (110 kV): bolts onto an HV mount. */
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem> HV_TERMINAL =
            terminal("hv_terminal", com.stardustindustry.stardustindustry.energy.EnergyTier.HV);
    /** An extra-high-voltage terminal (500 kV): bolts onto an EHV mount. */
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem> EHV_TERMINAL =
            terminal("ehv_terminal", com.stardustindustry.stardustindustry.energy.EnergyTier.EHV);

    // ---- cables ----
    //
    // Cable is not a block: it is pulled between two terminals and stored on
    // them. A cable is not rated by voltage tier either. It has two independent
    // properties (§6.1): a current class (how much copper) and an insulation
    // class (what a player feels when touching it, §6.11). Every combination is a
    // real product, so every combination gets an item: 5 x 3 = 15.
    // See docs/electric-power-system-design.md §6.9.

    // 钢芯铝绞线（ACSR）只有裸线一种，因为它本来就只用于架空长跨距，
    // 现实中高压架空线不会加绝缘。
    // See docs/electric-power-system-design.md §6.9.

    /**
     * Every cable item, keyed by {@code ampacity/insulation}.
     *
     * <p>Built from the enums rather than written out, so adding a class adds its
     * items automatically and the id scheme cannot drift between the two axes.
     * Registration order is stable (enum order, ampacity outer), which keeps ids
     * and the creative tab consistent between runs.</p>
     */
    private static final Map<String, DeferredItem<com.stardustindustry.stardustindustry.energy.CableItem>> CABLES =
            cableItems();

    private ModItems() {}

    /** The cable item for one combination, never {@code null} once registered. */
    public static com.stardustindustry.stardustindustry.energy.CableItem cableFor(
            CableAmpacity ampacity, CableInsulation insulation) {
        return CABLES.get(keyOf(ampacity, insulation)).get();
    }

    /** Registry name of the cable for one combination, e.g. {@code cable_500a_class_1}. */
    public static String cableName(CableAmpacity ampacity, CableInsulation insulation) {
        return "cable_" + ampacity.id() + "a_" + insulation.id();
    }

    /** The key used by {@link #CABLES}. */
    private static String keyOf(CableAmpacity ampacity, CableInsulation insulation) {
        return ampacity.id() + "/" + insulation.id();
    }

    /** Registers every combination and returns the map. */
    private static Map<String, DeferredItem<com.stardustindustry.stardustindustry.energy.CableItem>> cableItems() {
        Map<String, DeferredItem<com.stardustindustry.stardustindustry.energy.CableItem>> map = new LinkedHashMap<>();
        for (CableAmpacity ampacity : CableAmpacity.values()) {
            for (CableInsulation insulation : CableInsulation.values()) {
                // A bare-only class (steel-cored aluminium) gets ONE item, its bare
                // form. Registering "800 A class 2 ACSR" would advertise a product
                // nobody makes and no real line uses.
                if (ampacity.isBareOnly() && insulation != CableInsulation.BARE) {
                    continue;
                }
                map.put(keyOf(ampacity, insulation), ModRegistries.ITEMS.register(
                        cableName(ampacity, insulation),
                        () -> new com.stardustindustry.stardustindustry.energy.CableItem(
                                new Item.Properties().stacksTo(64), ampacity, insulation)));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** Every cable item, for the creative tab and for iteration. */
    public static java.util.Collection<DeferredItem<com.stardustindustry.stardustindustry.energy.CableItem>> cables() {
        return CABLES.values();
    }

    /** Terminal items stack high: they are small, cheap-to-make fittings. */
    private static DeferredItem<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem> terminal(
            String name, com.stardustindustry.stardustindustry.energy.EnergyTier tier) {
        return ModRegistries.ITEMS.register(name,
                () -> new com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock.TerminalItem(
                        new Item.Properties().stacksTo(64), tier));
    }

    /** Forces class initialisation so the deferred entries above are created. */
    public static void init() {
        // Referencing the class is enough.
    }
}
