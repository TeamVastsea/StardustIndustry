package com.stardustindustry.stardustindustry.client;

import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client-only access to the camera used for cable interaction. */
public final class ClientCablePicker {

    /** How close the aim ray must pass to a cable for it to be clickable. */
    private static final float PICK_RADIUS = 0.18f;

    /** Extra reach beyond normal block interaction for cables above eye level. */
    private static final double PICK_REACH = 4.0;

    private ClientCablePicker() {}

    /** Returns the cable under the local player's view ray, if any. */
    public static CableConnection pick(TerminalMountBlockEntity mount) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return null;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        return mount.cableAtRay(
                player.getEyePosition(partialTick),
                player.getViewVector(partialTick),
                PICK_RADIUS,
                player.blockInteractionRange() + PICK_REACH);
    }
}
