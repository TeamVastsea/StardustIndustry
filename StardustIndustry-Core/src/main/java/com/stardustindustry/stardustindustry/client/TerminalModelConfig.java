package com.stardustindustry.stardustindustry.client;

import org.joml.Quaternionf;

/**
 * One OBJ model's placement, as written by the artist in
 * {@code assets/stardustindustry/models/terminal/<name>.json}.
 *
 * <h2>Why this file exists</h2>
 * <p>An OBJ is just triangles: it has no idea where in the block it should sit,
 * which way it should point, or how big it should be. Previously those numbers
 * were hard-coded in {@link TerminalMountRenderer}, so every art iteration meant
 * a Java rebuild. Now they are data: the artist edits the JSON, presses
 * {@code F3+T}, and sees the new pose.</p>
 *
 * <h2>What the numbers mean</h2>
 * <p>All lengths are in <b>block model units</b>, {@code 1.0} meaning 1/16 of a
 * block, i.e. the same units an OBJ exported from Blockbench uses (a block is
 * 0..16, its centre is 8). That keeps the two files in the same language.</p>
 *
 * <ul>
 *   <li>{@code offsetX/Y/Z} — move the model's origin from where the host puts
 *       it. Positive X is to the model's right in its own frame, positive Y is
 *       up, positive Z is <b>towards the viewer</b> for a north-facing host.</li>
 *   <li>{@code rotationX/Y/Z} — degrees, applied in that order (X, then Y, then
 *       Z), about the model's origin.</li>
 *   <li>{@code scale} — uniform multiplier applied last, about the origin.</li>
 *   <li>{@code originX/Y/Z} — the pivot the {@code rotation} and {@code scale}
 *       act about, expressed in model units <em>relative to the OBJ's own
 *       origin</em>. The authored convention is "origin at the foot of the
 *       terminal, grows along +z", so all three default to 0; only move them if
 *       the exported OBJ does not follow that convention.</li>
 * </ul>
 *
 * <p><b>Mirroring.</b> A bare negative {@code scale} would flip the model inside
 * out (every triangle winds backwards and lighting goes wrong). To mirror an OBJ
 * that was authored with its axis pointing the other way, set
 * {@code mirror: "x"} (or {@code "y"} / {@code "z"}), optionally with a
 * {@code mirrorOffset}. The renderer implements it as a negative scale followed
 * by a winding flip, so it is a real mirror and still lights correctly.</p>
 *
 * @see TerminalModelConfigs
 */
public record TerminalModelConfig(String name,
                                  float offsetX, float offsetY, float offsetZ,
                                  float rotationX, float rotationY, float rotationZ,
                                  float scale,
                                  float originX, float originY, float originZ,
                                  String mirror,
                                  float mirrorOffset) {

    /**
     * The pose the renderer used before configs existed, in block units.
     *
     * <p>This is also the fallback for a tier whose JSON is missing or broken:
     * a bad file must never make the block invisible, only keep the old look.</p>
     */
    public static TerminalModelConfig defaults(String name) {
        return new TerminalModelConfig(name, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, "", 0f);
    }

    /** The config's rotation as a quaternion, in the documented X → Y → Z order. */
    public Quaternionf rotation() {
        return new Quaternionf()
                .rotateX((float) Math.toRadians(rotationX))
                .rotateY((float) Math.toRadians(rotationY))
                .rotateZ((float) Math.toRadians(rotationZ));
    }

    /** True when this config mirrors the model along one axis. */
    public boolean mirrored() {
        return mirror != null && !mirror.isEmpty();
    }
}
