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
        registrar.playToServer(
                ConnectCablePayload.TYPE,
                ConnectCablePayload.STREAM_CODEC,
                ModNetwork::handleConnectCable);
        registrar.playToServer(
                CableEditPayload.TYPE,
                CableEditPayload.STREAM_CODEC,
                ModNetwork::handleCableEdit);
    }

    /**
     * Handles a request to re-colour or cut one cable.
     *
     * <p>Three checks make a forged packet harmless: the player must be within
     * reach of the rack, the cable must actually be stored on that rack, and — for
     * a repaint — the player must be holding the dye whose colour was claimed.
     * The colour is then taken from the <b>dye item</b>, not from the packet, so
     * the packet's {@code dyeIndex} only says "paint with what I am holding".</p>
     */
    private static void handleCableEdit(CableEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!withinReach(player, payload.pos(), player.blockInteractionRange())) {
            return;
        }
        if (!(player.level().getBlockEntity(payload.pos())
                instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity rack)) {
            return;
        }
        if (!payload.isValid()) {
            return;
        }
        // Identify the cable from the run, ignoring colour: colour is what a repaint
        // changes and is irrelevant to a cut.
        com.stardustindustry.stardustindustry.energy.CableConnection target =
                com.stardustindustry.stardustindustry.energy.CableConnection.of(
                        payload.ampacity(), payload.insulation(), payload.start(), payload.end());
        com.stardustindustry.stardustindustry.energy.CableConnection actual = rack.findCable(target);
        if (actual == null) {
            return;
        }
        if (payload.isCut()) {
            var dropped = com.stardustindustry.stardustindustry.energy.CableService.disconnect(player.level(), actual);
            if (!dropped.isEmpty()) {
                if (!player.addItem(dropped)) {
                    player.drop(dropped, false);
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.stardustindustry.cable.cut")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            return;
        }
        com.stardustindustry.stardustindustry.energy.CableColor color = heldDyeColor(player, payload.dyeIndex());
        if (color == null) {
            return;
        }
        if (!com.stardustindustry.stardustindustry.energy.CableColor.isPaintable(actual.insulation())) {
            // Bare conductor has no sheath; painting it would hide the fact that it
            // is bare, which is the one visual cue that matters for safety.
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.stardustindustry.cable.bare_not_paintable")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        if (com.stardustindustry.stardustindustry.energy.CableService.recolor(player.level(), actual, color)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.stardustindustry.cable.dyed")
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
    }

    /**
     * The colour of the dye the player is holding, if it matches the claimed
     * index; {@code null} otherwise.
     *
     * <p>Matching against the claimed index means a stale client (which painted
     * with a dye that has since been put away) simply does nothing instead of
     * painting with whatever happens to be in hand now.</p>
     */
    private static com.stardustindustry.stardustindustry.energy.CableColor heldDyeColor(
            ServerPlayer player, int dyeIndex) {
        if (dyeIndex < 0
                || dyeIndex >= com.stardustindustry.stardustindustry.energy.CableColor.DYE_COLORS.length) {
            return null;
        }
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof net.minecraft.world.item.DyeItem dye) {
                int held = dye.getDyeColor().ordinal();
                // DyeColor ordinals match DYE_COLORS: white first, black last.
                if (held == dyeIndex) {
                    return com.stardustindustry.stardustindustry.energy.CableColor.ofDyeIndex(held);
                }
            }
        }
        return null;
    }

    /**
     * Handles a request to string a cable between two terminals.
     *
     * <h2>Why the reach check is not "both ends in arm's reach"</h2>
     * <p>The first version required <b>both</b> endpoints to be within the
     * player's block-interaction range (4.5 blocks in survival, 5 in creative).
     * That silently broke every cable longer than arm's reach: the start terminal
     * is clicked from one place, but by the time the player walks to the far end
     * and clicks it, the start is many blocks away — so the request was always
     * rejected, and the player saw only "无法连接". Short jumper cables worked
     * (both ends near each other), long spans never did, which read as "it works
     * sometimes".</p>
     *
     * <p>The check is therefore split. The <b>just-clicked</b> end must be in
     * normal interaction range, because that is the click the server can trust.
     * The <b>other</b> end may be across the room, but only up to
     * {@link #MAX_CABLE_SPAN} — the longest run a single cable is allowed to
     * cover. Anything beyond that is either a stale client or a forged packet.</p>
     */
    private static void handleConnectCable(ConnectCablePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // The just-clicked end must still be in interaction range: that is the
        // click the server can trust. A block of slack absorbs the moment of
        // walking between the client's click and the packet's arrival.
        if (!withinReach(player, payload.toPos(), player.blockInteractionRange() + 1.0)) {
            // The player has moved away from the terminal they are trying to
            // attach to; the click is stale. Say so instead of failing silently —
            // a connection that just does nothing is what made this bug so hard to
            // see in the first place.
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.stardustindustry.cable.stale")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        double dx = payload.fromPos().getX() - payload.toPos().getX();
        double dy = payload.fromPos().getY() - payload.toPos().getY();
        double dz = payload.fromPos().getZ() - payload.toPos().getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_CABLE_SPAN * MAX_CABLE_SPAN) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.stardustindustry.cable.too_far", (int) MAX_CABLE_SPAN)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        com.stardustindustry.stardustindustry.energy.PendingCable.complete(
                player, payload.start(), payload.end());
    }

    /**
     * The longest run one cable may span, in blocks.
     *
     * <p>A safety cap rather than a gameplay limit: it keeps a forged packet from
     * wiring two terminals on opposite sides of the world, while leaving plenty of
     * room for the long aerial runs steel-cored aluminium exists for. Raise it when
     * aerial spans need to be longer.</p>
     */
    private static final double MAX_CABLE_SPAN = 64.0;

    /**
     * True when the block centre is within {@code reach} of the player's <b>eyes</b>.
     *
     * <p>Eyes, not feet. The client's block ray starts at the eye position, so a
     * terminal mounted high on a wall can be clicked while the player's feet are
     * further from it than the interaction range. Measuring from the feet refused
     * exactly those clicks on the server while the client had happily sent them,
     * which is another way a connection could "randomly" fail.</p>
     */
    private static boolean withinReach(ServerPlayer player, net.minecraft.core.BlockPos pos, double reach) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        return eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
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
