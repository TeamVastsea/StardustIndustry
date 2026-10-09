package com.stardustindustry.utilsex.hud;

import net.neoforged.fml.ModList;

/**
 * Installs the highlight-mod integrations that need an explicit hook.
 *
 * <p>Jade and WTHIT find their plugins by themselves — the annotations on
 * {@code TankJadePlugin} and {@code TankWthitPlugin} are scanned by the owning
 * mod, and the plugin classes are never loaded when that mod is absent. The One
 * Probe has no such scan: a mod must send it an inter-mod message, and the
 * message is only delivered when TOP is installed. That send has to happen from
 * a class that is <em>not</em> the plugin, or merely reaching the call site
 * would drag in TOP's types on a TOP-less client.</p>
 *
 * <p>This class is the indirection: it checks the mod list first and only then
 * touches {@code TankTopPlugin}. Everything here is deliberately class-name
 * strings and a boolean check until the guard passes.</p>
 */
public final class HighlightCompat {

    private HighlightCompat() {}

    /** Called once during common setup. */
    public static void register() {
        if (ModList.get().isLoaded("theoneprobe")) {
            com.stardustindustry.utilsex.top.TankTopPlugin.register();
        }
    }
}
