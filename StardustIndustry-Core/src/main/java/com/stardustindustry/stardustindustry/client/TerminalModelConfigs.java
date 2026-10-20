package com.stardustindustry.stardustindustry.client;

import java.io.Reader;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.energy.EnergyTier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

/**
 * Loads the per-tier terminal pose files.
 *
 * <h2>Where the files live</h2>
 * <p>{@code assets/stardustindustry/models/terminal/<name>.json}, one per
 * voltage tier ({@code lv}, {@code mv}, {@code hv}, {@code ehv}). They are
 * <b>not</b> block models: the folder sits outside {@code models/item} and
 * {@code models/block}, so the model bakery never tries to bake them. See
 * {@code docs/obj-model-manual.md} for the schema.</p>
 *
 * <h2>Failure policy</h2>
 * <p>A missing or malformed file logs one line and falls back to
 * {@link TerminalModelConfig#defaults(String)}, so a typo costs the artist a
 * look, never a crash or an invisible block. Reload is {@code F3+T}.</p>
 */
public final class TerminalModelConfigs implements ResourceManagerReloadListener {

    /** Folder, relative to the mod's asset namespace. */
    public static final String FOLDER = "models/terminal";

    /**
     * The live map, replaced wholesale on reload.
     *
     * <p>{@code volatile} so the render thread never sees a half-built map; the
     * whole map is swapped rather than mutated because a reload happens on a
     * worker thread while the render thread may be reading it.</p>
     */
    private static volatile Map<EnergyTier, TerminalModelConfig> current = loadDefaults();

    /** The pose for {@code tier}; never {@code null}. */
    public static TerminalModelConfig get(EnergyTier tier) {
        TerminalModelConfig config = current.get(tier);
        return config != null ? config : TerminalModelConfig.defaults(tier.getSerializedName());
    }

    private static Map<EnergyTier, TerminalModelConfig> loadDefaults() {
        Map<EnergyTier, TerminalModelConfig> map = new EnumMap<>(EnergyTier.class);
        for (EnergyTier tier : EnergyTier.values()) {
            map.put(tier, TerminalModelConfig.defaults(tier.getSerializedName()));
        }
        return map;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        // Start from the CURRENT map, not from the defaults: a tier whose file has
        // a typo must keep the pose it was showing, which is what the manual
        // promises ("the model keeps its previous look"). Rebuilding from defaults
        // meant a single bad edit silently snapped the terminal back to the raw
        // OBJ pose, and the artist saw a mystery resize instead of an error.
        Map<EnergyTier, TerminalModelConfig> loaded = new EnumMap<>(current);
        for (EnergyTier tier : EnergyTier.values()) {
            String name = tier.getSerializedName();
            ResourceLocation id = StardustIndustry.id(FOLDER + "/" + name);
            try {
                Resource resource = manager.getResource(id).orElse(null);
                if (resource == null) {
                    continue;
                }
                try (Reader reader = resource.openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (!root.isJsonObject()) {
                        throw new IllegalArgumentException("root is not a JSON object");
                    }
                    loaded.put(tier, parse(name, root.getAsJsonObject()));
                }
            } catch (Exception e) {
                StardustIndustry.LOGGER.warn(
                        "Model config {} is unusable ({}); keeping the previous pose for {}",
                        id, e.getMessage(), name);
            }
        }
        current = loaded;
    }

    /** Reads one file. Numbers may be written with a decimal point or not. */
    static TerminalModelConfig parse(String name, JsonObject json) {
        TerminalModelConfig defaults = TerminalModelConfig.defaults(name);
        JsonObject offset = objectOrEmpty(json, "offset");
        JsonObject rotation = objectOrEmpty(json, "rotation");

        float scale = GsonHelper.getAsFloat(json, "scale", defaults.scale());
        if (!(scale > 0f)) {
            // A zero or negative scale would silently erase the model.
            throw new IllegalArgumentException("scale must be > 0, got " + scale);
        }

        JsonObject origin = objectOrEmpty(json, "origin");
        String mirror = GsonHelper.getAsString(json, "mirror", defaults.mirror());
        if (!mirror.isEmpty() && !mirror.equals("x") && !mirror.equals("y") && !mirror.equals("z")) {
            throw new IllegalArgumentException("mirror must be \"x\", \"y\", \"z\" or absent, got \"" + mirror + "\"");
        }
        float mirrorOffset = GsonHelper.getAsFloat(json, "mirrorOffset", defaults.mirrorOffset());
        return new TerminalModelConfig(
                name,
                GsonHelper.getAsFloat(offset, "x", defaults.offsetX()),
                GsonHelper.getAsFloat(offset, "y", defaults.offsetY()),
                GsonHelper.getAsFloat(offset, "z", defaults.offsetZ()),
                GsonHelper.getAsFloat(rotation, "x", defaults.rotationX()),
                GsonHelper.getAsFloat(rotation, "y", defaults.rotationY()),
                GsonHelper.getAsFloat(rotation, "z", defaults.rotationZ()),
                scale,
                GsonHelper.getAsFloat(origin, "x", defaults.originX()),
                GsonHelper.getAsFloat(origin, "y", defaults.originY()),
                GsonHelper.getAsFloat(origin, "z", defaults.originZ()),
                mirror,
                mirrorOffset);
    }

    /** A child object, or an empty one when absent (so every key stays optional). */
    private static JsonObject objectOrEmpty(JsonObject parent, String key) {
        JsonElement child = parent.get(key);
        if (child == null || child.isJsonNull()) {
            return new JsonObject();
        }
        if (!child.isJsonObject()) {
            throw new IllegalArgumentException("\"" + key + "\" must be an object");
        }
        return child.getAsJsonObject();
    }
}
