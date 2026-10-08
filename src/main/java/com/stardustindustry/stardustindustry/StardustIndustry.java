package com.stardustindustry.stardustindustry;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;
import com.stardustindustry.stardustindustry.registry.ModBlocks;
import com.stardustindustry.stardustindustry.registry.ModCapabilities;
import com.stardustindustry.stardustindustry.registry.ModItems;
import com.stardustindustry.stardustindustry.registry.ModMenus;
import com.stardustindustry.stardustindustry.registry.ModRegistries;
import com.stardustindustry.stardustindustry.recipe.ModRecipes;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(StardustIndustry.MODID)
public class StardustIndustry {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "stardustindustry";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public StardustIndustry(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Registries. Order matters only in that block entities read block
        // holders, so blocks must have their entries declared before their
        // block-entity types are constructed.
        ModBlocks.init();
        ModItems.init();
        ModRegistries.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);

        // Network channel: the parameter screen's dismantle request.
        modEventBus.addListener(com.stardustindustry.stardustindustry.network.ModNetwork::register);

        // Capability registration is its own mod-bus event and must be listened
        // for explicitly rather than done in the constructor.
        modEventBus.addListener(ModCapabilities::register);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);
        // Fluid ports swallow a held bucket's right-click before the bucket can
        // empty itself onto the ground.
        NeoForge.EVENT_BUS.register(com.stardustindustry.stardustindustry.machine.PortFluidInteraction.class);

        // Add this mod's content to its creative tab.
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Deferred holders only resolve once the registry events have fired, so
        // the port blocks' links to their block-entity types are wired here
        // rather than in the constructor.
        event.enqueueWork(ModBlockEntities::wirePorts);
        // Classification, balance numbers and the self-check must run in this
        // order: the self-check asserts that the first two completed. Enqueuing
        // them as separate tasks would let NeoForge run them in any order, so
        // they are done in one task instead.
        event.enqueueWork(() -> {
            // Classify structural blocks for the multiblock layer (port/filler/base/casing).
            ModBlocks.registerPartTypes();
            // Balance numbers for the filler kinds; structure evaluation reads these.
            com.stardustindustry.stardustindustry.multiblock.modifier.FillerRegistry.registerDefaults();
            // Fail fast on multiblock authoring mistakes before the world ever loads.
            com.stardustindustry.stardustindustry.multiblock.MultiblockSelfCheck.run();
        });
        // Highlight-mod integrations. Jade and WTHIT are self-discovering; TOP
        // needs the explicit, presence-guarded hook this installs.
        event.enqueueWork(com.stardustindustry.stardustindustry.compat.hud.HighlightCompat::register);
        LOGGER.info("Stardust Industry common setup complete");
    }

    // Add this mod's blocks and items to its own creative tab.
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModRegistries.MAIN_TAB.getKey()) {
            event.accept(ModBlocks.CRUSHER_ITEM);
            event.accept(ModBlocks.STEEL_CASING_ITEM);
            event.accept(ModBlocks.ITEM_PORT_ITEM);
            event.accept(ModBlocks.FLUID_PORT_ITEM);
            event.accept(ModBlocks.ENERGY_PORT_ITEM);
            event.accept(ModBlocks.LV_BASE_ITEM);
            event.accept(ModBlocks.LV_ITEM_PORT_ITEM);
            event.accept(ModBlocks.LV_FLUID_PORT_ITEM);
            event.accept(ModBlocks.LV_ENERGY_PORT_ITEM);
            event.accept(ModBlocks.LV_GRINDING_CORE_ITEM);
            event.accept(ModBlocks.LV_HEAT_EXCHANGER_CORE_ITEM);
            event.accept(ModBlocks.LV_PARALLEL_CORE_ITEM);
            event.accept(ModBlocks.LV_BUFFER_CORE_ITEM);
            event.accept(ModBlocks.TANK_FRAME_ITEM);
            event.accept(ModBlocks.TANK_SHELL_ITEM);
            event.accept(ModBlocks.TANK_GLASS_ITEM);
            event.accept(ModItems.INSTALLATION_TOOL);
            event.accept(ModItems.CRUSHED_IRON);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("Stardust Industry server starting");
    }

    /**
     * Builds a {@link ResourceLocation} in this mod's namespace.
     *
     * <p>Every registry id, translation key and data file for this mod goes
     * through here so the namespace is never spelled out by hand.</p>
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
