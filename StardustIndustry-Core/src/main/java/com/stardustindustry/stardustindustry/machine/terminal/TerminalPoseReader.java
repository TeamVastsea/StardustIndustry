package com.stardustindustry.stardustindustry.machine.terminal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads a terminal's pose file ({@code models/terminal/<style>.json}) for the
 * server's collision code.
 *
 * <h2>Why a second reader instead of {@code TerminalModelConfigs}</h2>
 * <p>{@code TerminalModelConfigs} lives in {@code client/} and is reloaded by the
 * client's resource listener. The collision box is asked for on the <b>server</b>,
 * which has its own resource manager and never runs that listener. Reusing the
 * client class would compile but quietly return defaults on a dedicated server.
 * This reader is deliberately dumb: same keys, same defaults, no client types.</p>
 *
 * <h2>Same defaults as the renderer</h2>
 * <p>A missing field must mean the same thing on both sides, or a model whose
 * file only sets {@code anchor} would draw at scale 1.0 and collide at a
 * different size. Every default here mirrors
 * {@code TerminalModelConfig#defaults}: zero offset, no rotation, scale 1.0,
 * origin zero, no mirror, {@code objUnitsPerBlock} 1000 (millimetres).</p>
 */
public final class TerminalPoseReader {

    private TerminalPoseReader() {}

    /**
     * The server-side view of one pose file.
     *
     * @param offset           blocks, plate frame (right / up / out)
     * @param anchorX/Y/Z      cable attachment point, in the same plate frame
     * @param scale            plain multiplier, 1.0 = as drawn
     * @param originX/Y/Z      pivot, in blocks relative to the OBJ origin
     * @param rotationX/Y/Z    degrees, applied X then Y then Z
     * @param mirrored         whether {@code mirror} names an axis
     * @param mirror           {@code "" / "x" / "y" / "z"}
     * @param mirrorOffset     distance of the mirror plane from the origin
     * @param objUnitsPerBlock how many OBJ units make one block (1000 = mm)
     */
    public record Pose(float offsetX, float offsetY, float offsetZ,
                       float anchorX, float anchorY, float anchorZ,
                       float scale,
                       float originX, float originY, float originZ,
                       float rotationX, float rotationY, float rotationZ,
                       String mirror, float mirrorOffset,
                       float objUnitsPerBlock) {

        /** The renderer's fallback when no file exists. */
        public static Pose defaults() {
            return new Pose(0f, 0f, 0f, 0f, 0f, 0f,
                    1f, 0f, 0f, 0f, 0f, 0f, 0f, "", 0f, 1000f);
        }

        public org.joml.Vector3f offset() {
            return new org.joml.Vector3f(offsetX, offsetY, offsetZ);
        }

        public org.joml.Vector3f origin() {
            return new org.joml.Vector3f(originX, originY, originZ);
        }

        public org.joml.Vector3f anchor() {
            return new org.joml.Vector3f(anchorX, anchorY, anchorZ);
        }

        public boolean mirrored() {
            return mirror != null && !mirror.isEmpty();
        }

        public float usedObjUnitsPerBlock() {
            return objUnitsPerBlock > 0f ? objUnitsPerBlock : 1000f;
        }

        public org.joml.Quaternionf rotation() {
            return new org.joml.Quaternionf()
                    .rotateX((float) Math.toRadians(rotationX))
                    .rotateY((float) Math.toRadians(rotationY))
                    .rotateZ((float) Math.toRadians(rotationZ));
        }
    }

    private record Key(String styleId, int managerId) {}

    private static final Map<Key, Pose> CACHE = new ConcurrentHashMap<>();

    /** The pose of {@code style}, or {@link Pose#defaults()} when unreadable. */
    public static Pose of(ResourceManager manager, TerminalStyle style) {
        Key key = new Key(style.poseFile(), TerminalAssetResources.managerId(manager));
        Pose cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Pose pose = parse(manager, style);
        CACHE.put(key, pose);
        return pose;
    }

    private static Pose parse(ResourceManager manager, TerminalStyle style) {
        // Same path TerminalModelConfigs uses, including the explicit ".json":
        // a ResourceLocation does not append an extension on its own.
        var id = com.stardustindustry.stardustindustry.StardustIndustry.id(
                "models/terminal/" + style.poseFile() + ".json");
        try (InputStream in = TerminalAssetResources.open(manager, id)) {
            if (in == null) {
                return Pose.defaults();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return new Pose(
                        objectField(root, "offset", "x", 0f),
                        objectField(root, "offset", "y", 0f),
                        objectField(root, "offset", "z", 0f),
                        objectField(root, "anchor", "x", 0f),
                        objectField(root, "anchor", "y", 0f),
                        objectField(root, "anchor", "z", 0f),
                        floatField(root, "scale", 1f),
                        objectField(root, "origin", "x", 0f),
                        objectField(root, "origin", "y", 0f),
                        objectField(root, "origin", "z", 0f),
                        objectField(root, "rotation", "x", 0f),
                        objectField(root, "rotation", "y", 0f),
                        objectField(root, "rotation", "z", 0f),
                        stringField(root, "mirror", ""),
                        floatField(root, "mirrorOffset", 0f),
                        floatField(root, "objUnitsPerBlock", 1000f));
            }
        } catch (Exception e) {
            com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.warn(
                    "Could not read terminal pose {} ({}); using defaults for collisions",
                    id, e.getMessage());
            return Pose.defaults();
        }
    }

    private static float floatField(JsonObject root, String name, float fallback) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** A number nested in an object such as {@code "offset": {"x": 0.0}}. */
    private static float objectField(JsonObject root, String objectName, String name, float fallback) {
        JsonElement element = root.get(objectName);
        if (element == null || !element.isJsonObject()) {
            return fallback;
        }
        return floatField(element.getAsJsonObject(), name, fallback);
    }

    private static String stringField(JsonObject root, String name, String fallback) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Drops every cached pose. Called when the server swaps resource managers. */
    public static void clearCache() {
        CACHE.clear();
    }
}
