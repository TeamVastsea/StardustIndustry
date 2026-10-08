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

    private ModItems() {}

    /** Forces class initialisation so the deferred entries above are created. */
    public static void init() {
        // Referencing the class is enough.
    }
}
