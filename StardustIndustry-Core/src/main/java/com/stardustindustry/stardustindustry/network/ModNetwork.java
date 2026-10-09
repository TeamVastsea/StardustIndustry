package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * This mod's network channel.
 *
 * <p>Only one message exists so far: the parameter screen's dismantle request.
 * It is registered as a play-to-server payload and handled on the main thread,
 * so its handler may touch the world freely.</p>
 *
 * <p>The handler treats the packet as a request, not a command: it re-derives
 * everything (ownership, distance, installed state) from the server world, so a
 * tampered or replayed packet can do nothing the player could not do by hand.</p>
 */
public final class ModNetwork {

    private ModNetwork() {}

    /** Registers the channel and its handlers. Called on the mod event bus. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                DismantleRequestPayload.TYPE,
                DismantleRequestPayload.STREAM_CODEC,
                ModNetwork::handleDismantleRequest);
    }

    /**
     * Handles a dismantle request from a parameter screen.
     *
     * <p>The machine is dismantled only when the sender is a real player, the
     * block is a machine, it is installed, the player still has it open, and the
     * player is within reach. Any other case is silently ignored: a rejected
     * packet is a client that has gone stale, not an error worth logging.</p>
     */
    private static void handleDismantleRequest(DismantleRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.controllerPos()) instanceof MachineBlockEntity machine)) {
            return;
        }
        // The player must still have this exact machine open; this makes a
        // replayed packet harmless once the screen has been closed or moved.
        if (!(player.containerMenu instanceof com.stardustindustry.stardustindustry.machine.MachineParamsMenu menu)
                || !menu.data().controllerPos().equals(payload.controllerPos())) {
            return;
        }
        if (!machine.isInstalled()) {
            return;
        }
        double reach = player.blockInteractionRange();
        if (player.distanceToSqr(
                payload.controllerPos().getX() + 0.5,
                payload.controllerPos().getY() + 0.5,
                payload.controllerPos().getZ() + 0.5) > reach * reach) {
            return;
        }
        machine.dismantle(true);
        player.closeContainer();
    }
}
