package com.stardustindustry.stardustindustry.registry;

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

    private ModItems() {}

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
