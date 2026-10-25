package com.stardustindustry.stardustindustry.machine.terminal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision shape of a rack's <b>backplate</b>, read from the block model
 * that draws it.
 *
 * <h2>Why the model and not a table</h2>
 * <p>The plate is art: each voltage tier has its own thickness (LV 1/16, MV
 * 1.5/16, HV 2/16, EHV 2.5/16) and each facing is a different model file. The
 * collision box used to be a hard-coded {@code 0.125} slab for all four tiers
 * and all six facings, so on three tiers the player's hitbox and the visible
 * plate did not line up — you could stand inside a thick plate, or float in
 * front of it. Reading {@code elements} means an artist who changes the plate
 * depth fixes the collision by saving the model.</p>
 *
 * <h2>Only {@code elements}, never {@code textures}</h2>
 * <p>Block models allow {@code elements} to be defined in a parent. The rack
 * models are generated with their own {@code elements}, so this reads the file
 * named by the blockstate's variant directly; if it ever gains a parent, the
 * fallback below keeps the collision sane rather than zeroing it.</p>
 */
public final class TerminalPlateShapes {

    private TerminalPlateShapes() {}

    /** A shape cached per (tier, facing, resource manager). */
    private record Key(String tier, Direction facing, int managerId) {}

    private static final Map<Key, VoxelShape> CACHE = new ConcurrentHashMap<>();

    /**
     * The plate shape for {@code mount}'s tier and {@code facing}, in block-local
     * 0..1 coordinates.
     *
     * <p>Falls back to a 2/16 slab on the {@code facing} side when the model
     * cannot be read: that is thick enough for every tier but EHV (which is
     * 2.5/16), and a slightly thin collision is far less disruptive than none.</p>
     */
    public static VoxelShape forFacing(TerminalMountBlock mount, Direction facing) {
        return shape(mount, facing, serverResourceManager());
    }

    private static VoxelShape shape(TerminalMountBlock mount, Direction facing, ResourceManager manager) {
        String tier = mount.tier().getSerializedName();
        String facingName = facing.getSerializedName();
        Key key = new Key(tier, facing, TerminalAssetResources.managerId(manager));
        VoxelShape cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        VoxelShape shape = parse(manager, tier, facingName);
        if (shape == null) {
            shape = fallback(facing);
        }
        CACHE.put(key, shape);
        return shape;
    }

    /** Reads {@code models/block/<tier>_terminal_mount_<facing>.json}'s elements. */
    static VoxelShape parse(ResourceManager manager, String tier, String facingName) {
        var id = com.stardustindustry.stardustindustry.StardustIndustry.id(
                "models/block/" + tier + "_terminal_mount_" + facingName + ".json");
        Direction facing = Direction.byName(facingName);
        if (facing == null) {
            return null;
        }
        try (InputStream in = TerminalAssetResources.open(manager, id)) {
            if (in == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonElement elements = root.get("elements");
                if (elements == null || !elements.isJsonArray()) {
                    return null;
                }
                VoxelShape shape = Shapes.empty();
                for (JsonElement element : elements.getAsJsonArray()) {
                    VoxelShape part = fromElement(element.getAsJsonObject());
                    if (part != null) {
                        shape = Shapes.or(shape, part);
                    }
                }
                return shape.isEmpty() ? null : shape;
            }
        } catch (Exception e) {
            com.stardustindustry.stardustindustry.StardustIndustry.LOGGER.warn(
                    "Could not read the plate shape from {} ({}); the rack gets a default "
                            + "2/16-thick collision box",
                    id, e.getMessage());
            return null;
        }
    }

    /**
     * One {@code elements} entry as a block-local box.
     *
     * <p><b>No rotation here.</b> Each facing has its own file and the generator
     * already baked the turn into the {@code from/to} numbers — {@code
     * lv_terminal_mount_south} says {@code from [0,0,15] to [16,16,16]}, not the
     * north box plus a hint. Re-rotating would put the collision on a different
     * side of the block from the plate that is drawn, which is exactly the class
     * of bug the hard-coded table had (four of the six facings disagreed).</p>
     */
    private static VoxelShape fromElement(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        if (from == null || to == null) {
            return null;
        }
        // Model coordinates are in 1/16 of a block; boxes are 0..1.
        double x0 = Math.min(from.get(0).getAsDouble(), to.get(0).getAsDouble()) / 16.0;
        double y0 = Math.min(from.get(1).getAsDouble(), to.get(1).getAsDouble()) / 16.0;
        double z0 = Math.min(from.get(2).getAsDouble(), to.get(2).getAsDouble()) / 16.0;
        double x1 = Math.max(from.get(0).getAsDouble(), to.get(0).getAsDouble()) / 16.0;
        double y1 = Math.max(from.get(1).getAsDouble(), to.get(1).getAsDouble()) / 16.0;
        double z1 = Math.max(from.get(2).getAsDouble(), to.get(2).getAsDouble()) / 16.0;
        return Shapes.box(x0, y0, z0, x1, y1, z1);
    }

    /** The 2/16 slab on the {@code facing} side, used when the model is unreadable. */
    private static VoxelShape fallback(Direction facing) {
        return switch (facing) {
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, 0.125);
            case SOUTH -> Shapes.box(0, 0, 0.875, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, 0.125, 1, 1);
            case EAST -> Shapes.box(0.875, 0, 0, 1, 1, 1);
            case UP -> Shapes.box(0, 0.875, 0, 1, 1, 1);
            case DOWN -> Shapes.box(0, 0, 0, 1, 0.125, 1);
        };
    }

    /**
     * The server's resource manager, or {@code null} when no server is running.
     *
     * <p>{@code BlockGetter} in {@code getShape} is not necessarily a real level
     * — the block engine also asks with a dummy getter — so this never depends on
     * the caller's level. The cache makes the lookup cheap after the first query
     * per world load.</p>
     *
     * <p><b>Server only on purpose.</b> A collision shape must be identical on
     * both sides, so it is always derived from the <b>server's</b> resource pack.
     * In single player that is the same jar the client loaded; on a dedicated
     * server the client's local pack may differ (a resource pack can change block
     * models), and letting the client's copy decide would put the hitbox and the
     * server's head on different boxes.</p>
     */
    public static ResourceManager serverResourceManager() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getResourceManager();
    }

    /** Drops every cached shape. Called when the server swaps resource managers. */
    public static void clearCache() {
        CACHE.clear();
    }
}
