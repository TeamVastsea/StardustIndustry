package com.stardustindustry.stardustindustry.energy;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Remembers the terminal a player has started a cable from, and completes one.
 *
 * <h2>The two-click gesture</h2>
 * <p>Pulling a cable from terminal to terminal takes two right-clicks (§6.3.5);
 * moving a loose end from one terminal to another takes a third. Between them the
 * player is "holding a loose end". That state is <b>client-local</b> — it only
 * decides what the next click means and what the renderer previews — while the
 * cable itself is created by the server through {@link CableService}, which
 * re-checks everything.</p>
 *
 * <p>Keeping the pending end on the client rather than the server is deliberate:
 * a server-side pending end would need syncing, would survive disconnects it
 * should not, and would be one more piece of state a desynced client could
 * corrupt. The client merely proposes; the server decides.</p>
 *
 * <h2>Which cable is being pulled is decided by the item</h2>
 * <p>The player's held cable item fixes the conductor and sheath class, on the
 * <b>server</b>, at the moment the cable is made. The client never tells the
 * server which cable to use: it would be one more thing to forge, and the server
 * can see the player's hand anyway.</p>
 */
public final class PendingCable {

    private PendingCable() {}

    /** The endpoint the local player is holding a loose end from, or {@code null}. */
    private static TerminalEndpoint from;

    /** The player who started it, so a switch of identity clears it. */
    private static UUID owner;

    /** Starts (or restarts) a pull from {@code endpoint}. */
    public static void begin(Player player, TerminalEndpoint endpoint) {
        from = endpoint;
        owner = player.getUUID();
    }

    /** Clears any pending end. Safe to call when there is none. */
    public static void clear() {
        from = null;
        owner = null;
    }

    /** The pending start, or {@code null} when the player is not pulling. */
    public static TerminalEndpoint from() {
        return from;
    }

    /**
     * Drops the pending end if it belongs to someone else.
     *
     * <p>The client-local state would otherwise follow a player across a server
     * switch or a dimension change and point at a terminal that no longer means
     * anything.</p>
     */
    public static void validateOwner(Player player) {
        if (owner != null && !owner.equals(player.getUUID())) {
            clear();
        }
    }

    /**
     * Tries to string a cable from {@code from} to {@code to}, on the
     * <b>server</b>.
     *
     * <p>The server keeps no pending end, so every request carries both endpoints.
     * Each is treated as a hint and re-checked from the world: the checks that a
     * legitimate click would pass are the same ones a forged packet must pass.</p>
     *
     * <p>The cable's class comes from what the player is actually holding. Pulling
     * with an empty hand or a non-cable item is refused: the cable is a physical
     * thing, and letting an empty hand lay one made the item meaningless.</p>
     */
    public static void complete(ServerPlayer player, TerminalEndpoint from, TerminalEndpoint to) {
        if (from.equals(to)) {
            // Clicking the same terminal twice cancels the pull rather than
            // making a zero-length cable.
            message(player, "message.stardustindustry.cable.cancelled", ChatFormatting.GRAY);
            return;
        }
        CableItem cable = heldCable(player);
        if (cable == null) {
            message(player, "message.stardustindustry.cable.need_cable", ChatFormatting.YELLOW);
            return;
        }
        if (!CableService.connect(player.level(), from, to, cable.ampacity(), cable.insulation())) {
            message(player, "message.stardustindustry.cable.rejected", ChatFormatting.YELLOW);
            return;
        }
        consume(player, cable);
        message(player, "message.stardustindustry.cable.connected", ChatFormatting.GREEN);
    }

    /**
     * The cable item in either hand, or {@code null}.
     *
     * <p>Both hands, because which one the player uses is a held-item detail the
     * gesture should not care about.</p>
     */
    public static CableItem heldCable(Player player) {
        if (player.getMainHandItem().getItem() instanceof CableItem main) {
            return main;
        }
        if (player.getOffhandItem().getItem() instanceof CableItem off) {
            return off;
        }
        return null;
    }

    /**
     * Charges one cable from the held stack.
     *
     * <p>Creative mode pays nothing. Otherwise exactly one comes out of the stack
     * the player used, so a 150 A cable costs a 150 A cable and not merely "some
     * cable" from elsewhere in the inventory.</p>
     */
    private static void consume(ServerPlayer player, CableItem cable) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        if (main.getItem() == cable) {
            main.shrink(1);
        } else {
            player.getOffhandItem().shrink(1);
        }
        player.getInventory().setChanged();
    }

    private static void message(ServerPlayer player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
    }
}
