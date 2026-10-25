package com.stardustindustry.stardustindustry.machine.terminal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads a terminal OBJ's vertex extent, so the engine can give terminals a
 * collision box.
 *
 * <h2>Why this parses the file instead of asking the baked model</h2>
 * <p>A collision box is needed on the <b>server</b> (movement, line of sight,
 * "did my click land on the post"), while a baked OBJ only ever exists on the
 * client. Using the baked model would work in single player — where both halves
 * run in one process — and silently collapse to a fallback box on a dedicated
 * server, which is exactly the kind of bug that only shows up after release.</p>
 *
 * <p>So the extent is re-derived from the text: an OBJ is a list of {@code v x y z}
 * lines plus index-only {@code f} lines, and the <b>extent of the vertices a face
 * actually references</b> is the box that hugs the model. Vertices no face uses
 * (a stray vertex left behind by an exporter) are ignored, so a single leftover
 * point cannot blow the box up to the whole cell.</p>
 *
 * <h2>Frames</h2>
 * <p>The result is in the <b>model's own frame</b>: millimetres for this project,
 * the same frame the pose file's {@code offset / origin / anchor} live in, with
 * {@code (0,0,0)} at the model origin and {@code +z} out of the plate. Converting
 * to blocks and placing it on a cell is {@link TerminalGeometry}'s job.</p>
 *
 * <h2>Caching</h2>
 * <p>Parsing runs once per (style, resource-manager) pair and the result is kept
 * until a reload swaps the resource manager. The server reads the file on the
 * first collision query for a style, which happens once per world load.</p>
 */
public final class TerminalModelBounds {

    private TerminalModelBounds() {}

    /**
     * An axis-aligned box in the model's own frame, in the OBJ's units.
     *
     * @param min  the lowest corner
     * @param max  the highest corner
     */
    public record Box(Vector3f min, Vector3f max) {

        /** Width along x (the model's cross-section). */
        public float sizeX() {
            return max.x - min.x;
        }

        /** Height along y (the model's cross-section). */
        public float sizeY() {
            return max.y - min.y;
        }

        /** Depth along z (how far the post reaches out of the plate). */
        public float sizeZ() {
            return max.z - min.z;
        }

        /** A box centred on the origin, {@code radius} wide, {@code depth} deep. */
        public static Box centred(float radius, float depth) {
            return new Box(new Vector3f(-radius, -radius, 0f), new Vector3f(radius, radius, depth));
        }
    }

    /**
     * Cache key: the style's model key plus the resource-manager identity.
     *
     * <p>The manager is part of the key so a resource reload (F3+T, or the server
     * swapping in a new datapack) cannot keep serving a box parsed from a file
     * that has since changed: a new manager is a different key.</p>
     */
    private record Key(String modelKey, int managerId) {}

    /** Cached boxes, keyed by (style model, resource manager identity). */
    private static final Map<Key, Box> CACHE = new ConcurrentHashMap<>();

    /**
     * The extent of the OBJ belonging to {@code style}, or {@code null} when the
     * file is missing or has no usable faces.
     *
     * <p>The caller decides what to do with {@code null}; the block falls back to
     * a conservative box rather than losing its collision entirely.</p>
     */
    public static Box of(ResourceManager manager, TerminalStyle style) {
        // Identity, not equals: two reloads can hand back managers that compare
        // equal while serving different files. A new manager therefore has to be
        // a new key, and old managers are dropped when clearCache runs on reload.
        Key key = new Key(style.model(), TerminalAssetResources.managerId(manager));
        Box cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Box parsed = parse(manager, style);
        if (parsed != null) {
            CACHE.put(key, parsed);
        }
        return parsed;
    }

    /** Reads {@code models/block/terminal_<model>.obj} and returns its extent. */
    private static Box parse(ResourceManager manager, TerminalStyle style) {
        var id = com.stardustindustry.stardustindustry.StardustIndustry.id(
                "models/block/terminal_" + style.model() + ".obj");
        try (InputStream in = TerminalAssetResources.open(manager, id)) {
            if (in == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseVertices(reader);
            }
        } catch (Exception e) {
            com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.warn(
                    "Could not read the vertex extent of {} ({}); terminals of style {} get a "
                            + "conservative collision box",
                    id, e.getMessage(), style.id());
            return null;
        }
    }

    /**
     * The extent of the vertices {@code reader}'s faces reference.
     *
     * <p>Two passes would need the file twice, so vertices are collected as they
     * are read and only those named by a later {@code f} line are counted. OBJ
     * requires every {@code v} to precede the faces that use it, so one pass is
     * enough.</p>
     */
    private static Box parseVertices(BufferedReader reader) throws Exception {
        java.util.List<Vector3f> vertices = new java.util.ArrayList<>();
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        boolean used = false;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("v ")) {
                String[] parts = line.split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                try {
                    vertices.add(new Vector3f(
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])));
                } catch (NumberFormatException ignored) {
                    // A malformed vertex line is the artist's file problem; skip it
                    // rather than failing the whole model's collision.
                }
            } else if (line.startsWith("f ")) {
                String[] parts = line.split("\\s+");
                for (int i = 1; i < parts.length; i++) {
                    // "v/vt/vn", "v//vn" or plain "v".
                    String indexText = parts[i].split("/", -1)[0];
                    int index;
                    try {
                        index = Integer.parseInt(indexText);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    // OBJ indices are 1-based; negative ones are relative to the end.
                    int resolved = index > 0 ? index - 1 : vertices.size() + index;
                    if (resolved < 0 || resolved >= vertices.size()) {
                        continue;
                    }
                    Vector3f v = vertices.get(resolved);
                    min[0] = Math.min(min[0], v.x);
                    min[1] = Math.min(min[1], v.y);
                    min[2] = Math.min(min[2], v.z);
                    max[0] = Math.max(max[0], v.x);
                    max[1] = Math.max(max[1], v.y);
                    max[2] = Math.max(max[2], v.z);
                    used = true;
                }
            }
        }
        if (!used) {
            return null;
        }
        return new Box(new Vector3f(min[0], min[1], min[2]), new Vector3f(max[0], max[1], max[2]));
    }

    /** Drops every cached box. Called when the server swaps resource managers. */
    public static void clearCache() {
        CACHE.clear();
    }
}
