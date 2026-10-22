package com.stardustindustry.stardustindustry.client;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;

/**
 * Loads the per-style terminal pose and anchor files.
 *
 * <h2>Where the files live</h2>
 * <p>{@code assets/stardustindustry/models/terminal/<style>.json}, one per
 * {@link TerminalStyle} ({@code lv_terminal}, {@code mv_terminal}, ...). They are
 * <b>not</b> block models: {@code terminal_lv.json} next to them is the OBJ
 * loader file, and the pose files are read by name here, outside the model
 * bakery. See {@code docs/obj-model-manual.md} for the schema.</p>
 *
 * <h2>Why keyed by style and not by tier</h2>
 * <p>The file used to be named after the voltage tier, which meant one tier had
 * exactly one terminal look. A pack author who adds a second terminal at the
 * same voltage would have had to overwrite the first one's numbers. Keying by
 * the style's {@link TerminalStyle#model() model key} gives every terminal its
 * own pose <b>and</b> its own cable anchor, and two styles may still share a file
 * by pointing at the same model key.</p>
 *
 * <h2>Failure policy</h2>
 * <p>A missing or malformed file logs one line and falls back to
 * {@link TerminalModelConfig#defaults(String)}, so a typo costs the artist a
 * look, never a crash or an invisible block.</p>
 *
 * <h2>Reloading</h2>
 * <p>{@code F3+T} re-reads these files. In a dev run the game loads resources
 * from Gradle's <b>build output</b>, not from {@code src/main/resources}, so an
 * edit to the source file only becomes visible after
 * {@code processResources --rerun-tasks} and a client restart. That is a build
 * setup detail, not a property of this loader.</p>
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
    private static volatile Map<String, TerminalModelConfig> current = loadDefaults();

    /** The pose for {@code style}; never {@code null}. */
    public static TerminalModelConfig get(TerminalStyle style) {
        return get(style.model());
    }

    /**
     * The pose for a model key (a style's {@code model} field); never
     * {@code null}.
     *
     * <p>Taking a plain string lets non-client code — the cable anchor lookup on
     * the server — ask for the same numbers without dragging a client class into
     * a dedicated server.</p>
     */
    public static TerminalModelConfig get(String modelKey) {
        TerminalModelConfig config = current.get(modelKey);
        return config != null ? config : TerminalModelConfig.defaults(modelKey);
    }

    private static Map<String, TerminalModelConfig> loadDefaults() {
        Map<String, TerminalModelConfig> map = new HashMap<>();
        for (TerminalStyle style : TerminalStyle.all()) {
            map.put(style.model(), TerminalModelConfig.defaults(style.model()));
        }
        return map;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        // Start from the DEFAULTS, not from the previous map: a file the artist
        // just edited must produce exactly what the file says. Carrying the old
        // pose forward on any error is what made "I changed scale to 1.0 and
        // nothing happened" possible — the edit was ignored in silence. A broken
        // file now reverts to the default pose (visible, and logged) instead of
        // pretending the previous numbers still apply.
        Map<String, TerminalModelConfig> loaded = loadDefaults();
        for (TerminalStyle style : TerminalStyle.all()) {
            String name = style.model();
            // The FILE is named after the style id (lv_terminal.json); the MAP is
            // keyed by the model key (lv), which is what the renderer and the anchor
            // lookup ask for.
            //
            // The full path INCLUDING ".json" is required: ResourceLocation is an
            // opaque key and does not add an extension. Leaving it off made every
            // pose file look missing, so the defaults won every time and editing a
            // number silently did nothing.
            ResourceLocation id = StardustIndustry.id(FOLDER + "/" + style.poseFile() + ".json");
            try {
                Resource resource = manager.getResource(id).orElse(null);
                if (resource == null) {
                    StardustIndustry.LOGGER.warn(
                            "No pose file for terminal style {} — expected {}; the defaults are "
                                    + "used instead",
                            style.id(), id);
                    continue;
                }
                try (Reader reader = resource.openAsReader()) {
                    JsonElement root = JsonParser.parseReader(reader);
                    if (!root.isJsonObject()) {
                        throw new IllegalArgumentException("root is not a JSON object");
                    }
                    loaded.put(name, parse(name, root.getAsJsonObject()));
                }
            } catch (Exception e) {
                StardustIndustry.LOGGER.warn(
                        "Model config {} is unusable ({}); falling back to the default pose for {}",
                        id, e.getMessage(), name);
            }
        }
        current = loaded;
        // One line per reload with the anchors spelled out. They are the numbers
        // that get edited most, and the full record dump buries them.
        StringBuilder anchors = new StringBuilder();
        for (TerminalStyle style : TerminalStyle.all()) {
            TerminalModelConfig cfg = loaded.get(style.model());
            if (cfg == null) {
                continue;
            }
            anchors.append(' ').append(style.id()).append("=(")
                    .append(cfg.anchorX()).append(", ")
                    .append(cfg.anchorY()).append(", ")
                    .append(cfg.anchorZ()).append(')');
        }
        StardustIndustry.LOGGER.info("Loaded {} terminal pose file(s) from {}; anchor(blocks):{}",
                loaded.size(), FOLDER, anchors);
        StardustIndustry.LOGGER.debug("Terminal pose values: {}", loaded);
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
        JsonObject anchor = objectOrEmpty(json, "anchor");
        String mirror = GsonHelper.getAsString(json, "mirror", defaults.mirror());
        if (!mirror.isEmpty() && !mirror.equals("x") && !mirror.equals("y") && !mirror.equals("z")) {
            throw new IllegalArgumentException("mirror must be \"x\", \"y\", \"z\" or absent, got \"" + mirror + "\"");
        }
        float mirrorOffset = GsonHelper.getAsFloat(json, "mirrorOffset", defaults.mirrorOffset());
        // How many OBJ units make one block IN THIS FILE. Declared by the artist
        // because the export unit differs per pipeline: 16 for a vanilla-style
        // block model, 1000 when the tool works in millimetres (this project's
        // convention). Without it the renderer would have to assume one of them,
        // and the other pipeline's models would come out 62.5x off.
        float objUnits = GsonHelper.getAsFloat(json, "objUnitsPerBlock", defaults.objUnitsPerBlock());
        if (!(objUnits > 0f)) {
            throw new IllegalArgumentException("objUnitsPerBlock must be > 0, got " + objUnits);
        }
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
                mirrorOffset,
                GsonHelper.getAsFloat(anchor, "x", defaults.anchorX()),
                GsonHelper.getAsFloat(anchor, "y", defaults.anchorY()),
                GsonHelper.getAsFloat(anchor, "z", defaults.anchorZ()),
                objUnits);
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
