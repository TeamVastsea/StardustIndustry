package com.stardustindustry.stardustindustry.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.stardustindustry.stardustindustry.energy.CableColor;
import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.CableItem;
import com.stardustindustry.stardustindustry.registry.ModItems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tints the cable item icons by conductor and insulation.
 *
 * <h2>Why tint instead of one texture per cable</h2>
 * <p>Seventeen cable items share four greyscale textures
 * ({@code item/cable_bare}, {@code cable_acsr}, {@code cable_class_1},
 * {@code cable_class_2}). The distinctions a player needs at a glance are the
 * <b>metal</b> (copper vs. aluminium) and the <b>sheath class</b>, and both are
 * a colour, not a shape. Painting them in costs seventeen PNGs to keep in sync
 * with four; tinting costs this one class and keeps the art greyscale, which is
 * also what the in-world tube renderer uses
 * ({@link CableColor#naturalColor(CableAmpacity, CableInsulation)}).</p>
 *
 * <p>The consequence, and the reason it is done this way rather than with
 * per-item colour data: a resource pack that retextures the cables keeps
 * working, and a pack that wants different marking colours can override
 * {@link CableColor#naturalColor} without touching any texture.</p>
 *
 * <h2>Bare conductors are not paintable, so this never fights §6.8</h2>
 * <p>§6.8 says dye cannot be applied to bare metal: the metal <i>is</i> the
 * information. The tint is therefore the <b>natural</b> colour of the cable
 * (copper, aluminium, sheath grey), never a paint colour. Painted cables get
 * their colour when laid, from the {@code CableColor} stored in the connection,
 * and the item itself stays the product it always was.</p>
 *
 * <h2>Two paths, because item rendering has two</h2>
 * <p>{@link RegisterColorHandlersEvent.Item} covers the GUI, held items and the
 * dropped-item entity. The <b>item model</b> is covered by the
 * {@code tintindex} in {@code models/item/cable_*.json}, which is what vanilla
 * reads for both paths on 1.21. This handler supplies the integer for that
 * index and nothing else.</p>
 */
@EventBusSubscriber(modid = "stardustindustry", value = Dist.CLIENT)
public final class CableItemColors {

    /**
     * The one tint index the cable models declare.
     *
     * <p>Named here so the JSON and this class cannot drift: the models all say
     * {@code "tintindex": 0}, and a second layer would need a second constant.</p>
     */
    public static final int TINT_LAYER = 0;

    private CableItemColors() {}

    /** Cache of resolved ARGB per cable item: the colour never changes per stack. */
    private static final Map<CableItem, Integer> CACHE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        for (var deferred : ModItems.cables()) {
            CableItem cable = deferred.get();
            event.register((stack, layer) -> layer == TINT_LAYER ? colorOf(cable) : 0xFFFFFFFF, cable);
        }
    }

    /**
     * The RGB packed into an {@code 0xRRGGBB} int for a cable item, opaque.
     *
     * <p>Opaque on purpose: vanilla multiplies the tint into the texture and
     * treats the alpha channel of an item tint as an opacity multiplier in some
     * places, so returning {@code 0xFF000000 | rgb} is the safe form.</p>
     */
    public static int colorOf(CableItem cable) {
        Integer cached = CACHE.get(cable);
        if (cached != null) {
            return cached;
        }
        int rgb = CableColor.naturalColor(cable.ampacity(), cable.insulation()) & 0xFFFFFF;
        int argb = 0xFF000000 | rgb;
        CACHE.put(cable, argb);
        return argb;
    }
}
