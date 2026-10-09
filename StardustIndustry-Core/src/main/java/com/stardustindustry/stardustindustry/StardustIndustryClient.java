package com.stardustindustry.stardustindustry;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = StardustIndustry.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = StardustIndustry.MODID, value = Dist.CLIENT)
public class StardustIndustryClient {
    public StardustIndustryClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        StardustIndustry.LOGGER.info("Stardust Industry client setup complete");
        // Give the crusher a spinning rotor. Animation is registered per machine
        // type from the client layer, so the crusher stays a plain common-side
        // class and a machine with no animator simply stands still.
        event.enqueueWork(() -> com.stardustindustry.stardustindustry.client.MachineAnimators.register(
                com.stardustindustry.stardustindustry.registry.ModBlockEntities.CRUSHER.get(),
                new com.stardustindustry.stardustindustry.client.CrusherAnimator(
                        net.minecraft.client.Minecraft.getInstance().getBlockRenderer())));
    }

    /** Binds the parameter menu to its screen. */
    @SubscribeEvent
    static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.stardustindustry.stardustindustry.registry.ModMenus.MACHINE_PARAMS.get(),
                com.stardustindustry.stardustindustry.client.MachineParamsScreen::new);
    }

    /**
     * Binds the projection renderer to every machine controller.
     *
     * <p>All machines share one renderer: the projection is a property of the
     * block entity base class, not of a machine type, so a new machine gets the
     * overlay for free by being registered here.</p>
     */
    @SubscribeEvent
    static void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider<com.stardustindustry.stardustindustry.machine.MachineBlockEntity> provider =
                com.stardustindustry.stardustindustry.client.MachineRenderer::new;
        event.registerBlockEntityRenderer(
                com.stardustindustry.stardustindustry.registry.ModBlockEntities.CRUSHER.get(), provider);
        event.registerBlockEntityRenderer(
                com.stardustindustry.stardustindustry.registry.ModBlockEntities.FLUID_TANK_SHELL.get(), provider);
        event.registerBlockEntityRenderer(
                com.stardustindustry.stardustindustry.registry.ModBlockEntities.GAS_TANK_SHELL.get(), provider);
    }
}
