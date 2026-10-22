package com.stardustindustry.stardustindustry.machine.terminal;

import org.joml.Vector3f;

import com.stardustindustry.stardustindustry.energy.TerminalGeometry;
import com.stardustindustry.stardustindustry.energy.TerminalGrid;

import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Gives one bolted terminal its <b>collision box</b> in block-local space.
 *
 * <h2>The two inputs</h2>
 * <ol>
 *   <li><b>How big the art is</b> — {@link TerminalModelBounds} reads the OBJ's
 *       vertex extent on the server.</li>
 *   <li><b>How it is posed</b> — {@link TerminalPoseReader} reads the same
 *       {@code models/terminal/<style>.json} the renderer uses (offset, origin,
 *       rotation, scale, mirror, {@code objUnitsPerBlock}).</li>
 * </ol>
 *
 * <p>{@link TerminalGeometry#placeBox} then applies the same transform the
 * renderer applies to a {@code PoseStack}. The result is the axis-aligned box
 * that encloses the terminal as it is actually drawn: turning a model makes its
 * box grow to cover the turned shape, it never clips into it.</p>
 *
 * <h2>Why this lives beside the block, not inside the renderer</h2>
 * <p>A collision box is asked for by the <b>server</b> as well — movement, line of
 * sight, "did my click land on the post". Anything client-only is unavailable
 * there, so the box is re-derived from the same plain data the client uses.</p>
 */
public final class TerminalPoser {

    private TerminalPoser() {}

    /**
     * The fallback post when a style's OBJ cannot be read: a 0.2-block square
     * column standing 0.5 block out of the cell centre.
     *
     * <p>Roughly the placeholder terminal. Small enough that the player can still
     * click the plate around it, tall enough that a missing model is not a
     * walk-through terminal.</p>
     */
    private static final float FALLBACK_RADIUS = 0.1f;
    private static final float FALLBACK_DEPTH = 0.5f;

    /**
     * The terminal's block-local box as {@code [x0, y0, z0, x1, y1, z1]}, in
     * block coordinates (0..1 per axis, may go slightly outside on a model that
     * pokes out of the block; the caller clamps).
     */
    public static float[] blockLocalBox(Direction facing, TerminalGrid grid, int cell, TerminalStyle style) {
        ResourceManager manager = TerminalPlateShapes.serverResourceManager();
        TerminalPoseReader.Pose pose = TerminalPoseReader.of(manager, style);
        TerminalModelBounds.Box bounds = TerminalModelBounds.of(manager, style);

        Vector3f min;
        Vector3f max;
        if (bounds == null) {
            min = new Vector3f(-FALLBACK_RADIUS, -FALLBACK_RADIUS, 0f);
            max = new Vector3f(FALLBACK_RADIUS, FALLBACK_RADIUS, FALLBACK_DEPTH);
        } else {
            // OBJ units -> blocks. This is the renderer's own factor:
            // 1 / objUnitsPerBlock * scale. Scaling here means placeBox works
            // entirely in blocks and is handed scale 1 — multiplying twice would
            // shrink a scaled model twice.
            float toBlocks = 1.0f / pose.usedObjUnitsPerBlock() * pose.scale();
            min = new Vector3f(bounds.min()).mul(toBlocks);
            max = new Vector3f(bounds.max()).mul(toBlocks);
        }

        Vector3f[] placed = TerminalGeometry.placeBox(
                facing, grid, cell,
                min, max,
                pose.offset(), pose.origin(),
                pose.rotation(), 1.0f, pose.mirrored(), pose.mirror(), pose.mirrorOffset());
        return new float[] {
                placed[0].x, placed[0].y, placed[0].z,
                placed[1].x, placed[1].y, placed[1].z
        };
    }
}
