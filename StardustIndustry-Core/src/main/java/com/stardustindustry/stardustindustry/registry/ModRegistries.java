package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.StardustIndustry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central place for every {@code DeferredRegister} this mod owns.
 *
 * <p>Registries are declared here once and registered together in
 * {@link #register(IEventBus)} so the mod class only has to make a single call.
 * Feature packages add their deferred entries into these registers rather than
 * creating their own, which keeps load order deterministic and makes it obvious
 * what the mod actually adds.</p>
 */
public final class ModRegistries {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StardustIndustry.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StardustIndustry.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StardustIndustry.MODID);

    private ModRegistries() {}

    /**
     * The single creative tab for this mod. Content is added through the
     * {@code BuildCreativeModeTabContentsEvent} so that every feature package
     * can contribute without knowing about the others.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + StardustIndustry.MODID + ".main"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new net.minecraft.world.item.ItemStack(ModBlocks.FLUID_TANK_SHELL_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        // Populated by ModItems and the feature packages.
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
