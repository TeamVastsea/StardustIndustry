package com.stardustindustry.utilsex.hud;

import java.util.ArrayList;
import java.util.List;

import com.stardustindustry.stardustindustry.machine.tank.TankMedium;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turns a {@link TankHudData} snapshot into the lines every highlight mod shows.
 *
 * <p>Jade, WTHIT and TOP each have their own tooltip type, but a player should
 * not be able to tell which one they installed by reading a tank tooltip. The
 * wording and the formatting therefore live here, once, and the three plugins
 * only differ in how they hand the finished components to their own API.</p>
 *
 * <p>A fluid tank and a gas tank say "fluid" and "gas" respectively; the medium
 * is spliced into the translation keys, so the same builder serves both without
 * a branch per line.</p>
 *
 * <p>The lines are returned as a list rather than written into a tooltip so the
 * text can also be reused by GUI code and unit tests without a running client.</p>
 */
public final class TankHudLines {

    private TankHudLines() {}

    /**
     * Builds the display lines for {@code data}.
     *
     * <p>An unformed tank still shows its state and, when known, its held
     * contents, so a player pointing at a half-built shell learns why nothing is
     * happening rather than seeing a blank tooltip.</p>
     */
    public static List<Component> build(TankHudData data) {
        List<Component> lines = new ArrayList<>(4);
        TankMedium medium = data.medium();

        if (!data.formed()) {
            lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.unformed"));
            // The contents are still worth showing: the medium is not lost just
            // because a wall was knocked out, and hiding it would look like a
            // glitch.
            if (data.hasContents()) {
                lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.contents",
                        data.contentsName(), formatAmount(data.contentsAmount())));
            }
            return lines;
        }

        lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.size",
                data.sizeX(), data.sizeY(), data.sizeZ()));

        if (data.hasContents()) {
            lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.contents",
                    data.contentsName(), formatAmount(data.contentsAmount())));
        } else {
            lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.empty"));
        }

        MutableComponent capacity = Component.translatable("hud.stardustindustry_utilsex.tank.capacity",
                formatAmount(data.capacityMb()));
        if (data.hasContents() && data.capacityMb() > 0) {
            capacity.append(" ").append(Component.translatable(
                    "hud.stardustindustry_utilsex.tank.percent", Math.round(data.fraction() * 100.0f)));
        }
        lines.add(capacity);

        // Which vessel this is: a one-word line so a glance at a wall of steel
        // tells fluid from gas even before the contents are read.
        lines.add(Component.translatable("hud.stardustindustry_utilsex.tank.medium." + medium.id()));

        return lines;
    }

    /**
     * Formats an mB amount the way a machine mod conventionally shows fluids:
     * buckets once the value is large enough that mB would be noise, and whole
     * mB below that.
     */
    public static String formatAmount(int mb) {
        if (mb >= 1000) {
            return String.format("%,.1f B", mb / 1000.0);
        }
        return mb + " mB";
    }
}
