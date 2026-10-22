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
        // Cables are anchored per terminal STYLE, and the anchor numbers live in
        // the same pose files the artist edits. The geometry code cannot read
        // those itself (it also runs on a dedicated server), so the lookup is
        // injected here once.
        event.enqueueWork(() -> com.stardustindustry.stardustindustry.energy.TerminalGeometry.setAnchorSource(
                modelKey -> com.stardustindustry.stardustindustry.client.TerminalModelConfigs
                        .get(modelKey).anchorOffset()));
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
    static void onRegisterClientReloadListeners(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        // Terminal posts are OBJ models baked once; a re-exported OBJ or
        // a texture change only shows up after the cache is dropped.
        event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) resourceManager ->
                        com.stardustindustry.stardustindustry.client.TerminalMountRenderer.clearCache());
        // The pose (offset / rotation / scale) and the cable anchor of each
        // terminal STYLE live in models/terminal/<style>.json, so the artist can
        // iterate without a rebuild. Keyed by style, not tier: two terminals at
        // one voltage must not overwrite each other's numbers.
        event.registerReloadListener(new com.stardustindustry.stardustindustry.client.TerminalModelConfigs());
    }

    /**
     * Drops a half-pulled cable when the player leaves the world.
     *
     * <p>The pending "loose end" is client-local, so nothing on the server ever
     * clears it. Without this hook a player who starts a cable, quits and rejoins
     * would still be holding an end that points at a position from the previous
     * session — and their next right-click on any terminal would try to connect to
     * it. Clearing on logout is what makes the gesture self-contained.</p>
     */
    @SubscribeEvent
    static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.stardustindustry.stardustindustry.energy.PendingCable.clear();
    }

    /** Drops it on a dimension change too: the endpoint's coordinates mean a different place there. */
    @SubscribeEvent
    static void onClone(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        com.stardustindustry.stardustindustry.energy.PendingCable.clear();
    }

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

        // Terminal mounts: the plate model draws the empty rack, and this
        // renderer draws a copper post on every occupied cell. Occupancy cannot
        // be a blockstate (2^16 patterns for a 4x4 rack), so it needs a renderer.
        event.registerBlockEntityRenderer(
                com.stardustindustry.stardustindustry.registry.ModBlockEntities.TERMINAL_MOUNT.get(),
                com.stardustindustry.stardustindustry.client.TerminalMountRenderer::new);
        // Cables are drawn by the SAME renderer, not a second registration: a block
        // entity type maps to exactly one renderer, so registering CableRenderer
        // separately would replace the terminal posts rather than add to them.
    }
}
