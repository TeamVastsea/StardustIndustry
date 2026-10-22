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
 * <p>All lengths are in <b>blocks</b> — one block is {@code 1.0}. That is the unit
 * the player sees and the unit a modelling tool shows when a block is modelled as
 * one metre, so {@code 0.5} means "half a block" in both files and on screen.
 * (Older versions of this file used 1/16-block units; to convert an old number,
 * divide it by 16 — an old {@code offset.z = 4} is now {@code 0.25}.)</p>
 *
 * <p>{@code scale} is the only field that is not a length: it is a plain
 * multiplier, {@code 1.0} = as drawn. It does not convert OBJ units — the OBJ's
 * 1/16-unit vertices are handled by the renderer, so the artist never has to
 * know about the 16.</p>
 *
 * <ul>
 *   <li>{@code offsetX/Y/Z} — move the model <b>on the face it is bolted to</b>.
 *       They are applied <em>after</em> the face rotation, so they mean the same
 *       thing on all six sides: {@code x} = right along the face as the player
 *       sees it, {@code y} = up along the face, {@code z} = out of the plate into
 *       the room. Positive {@code z} pulls the terminal away from the wall;
 *       negative pushes it into the plate. (Before this, the offset was applied
 *       in the model's own north frame, before the rotation, so on a west plate
 *       "z" moved the terminal sideways and on a floor plate vertically — which
 *       is why editing it seemed to do nothing.)</li>
 *   <li>{@code rotationX/Y/Z} — degrees, applied in that order (X, then Y, then
 *       Z), about the model's origin.</li>
 *   <li>{@code scale} — plain multiplier applied about the origin, default
 *       {@link #DEFAULT_SCALE} (1.0 = as drawn; 0.5 = half size).</li>
 *   <li>{@code originX/Y/Z} — the pivot the {@code rotation} and {@code scale}
 *       act about, in blocks, <em>relative to the OBJ's own origin</em>. The
 *       authored convention is "origin at the foot of the terminal, grows along
 *       +z", so all three default to 0; only move them if the exported OBJ does
 *       not follow that convention.</li>
 * </ul>
 *
 * <p><b>Mirroring.</b> A bare negative {@code scale} would flip the model inside
 * out (every triangle winds backwards and lighting goes wrong). To mirror an OBJ
 * that was authored with its axis pointing the other way, set
 * {@code mirror: "x"} (or {@code "y"} / {@code "z"}), optionally with a
 * {@code mirrorOffset}. The renderer implements it as a negative scale followed
 * by a winding flip, so it is a real mirror and still lights correctly.</p>
 *
 * <h2>The cable anchor is separate — deliberately</h2>
 * <p>{@code anchorX/Y/Z} is where a <b>cable</b> leaves the terminal, expressed
 * in the same block units and the same frame as {@code offset}
 * (right / up / out of the plate), but measured from the <b>model origin</b> —
 * the point the modelling tool shows, where the post meets the plate.
 * {@code (0, 0, 0)} is therefore the model origin itself. It is its own number,
 * never derived from {@code offset}, {@code origin} or {@code scale}.</p>
 *
 * <p>It is <b>not</b> derived from the model, by the project owner's call: a
 * terminal is a piece of art that will be re-exported many times, while the
 * point a wire must meet is a fixed piece of lore about the hardware. Tying one
 * to the other means every art tweak drags the wire with it — and the reverse,
 * moving the anchor to suit a different wire, drags the art. Two knobs, two
 * jobs.</p>
 *
 * @see TerminalModelConfigs
 */
public record TerminalModelConfig(String name,
                                  float offsetX, float offsetY, float offsetZ,
                                  float rotationX, float rotationY, float rotationZ,
                                  float scale,
                                  float originX, float originY, float originZ,
                                  String mirror,
                                  float mirrorOffset,
                                  float anchorX, float anchorY, float anchorZ,
                                  float objUnitsPerBlock) {

    /**
     * The pose the renderer used before configs existed.
     *
     * <p>This is also the fallback for a style whose JSON is missing or broken:
     * a bad file must never make the block invisible, only keep the old look.</p>
     *
     * <p>{@code scale} defaults to {@link #DEFAULT_SCALE} (1.0 = as drawn, because
     * the renderer already converts the OBJ's 1/16-block vertices); the anchor
     * defaults to the plate's inner surface. Both reproduce the old hard-coded
     * geometry, so existing worlds look unchanged.</p>
     */
    public static TerminalModelConfig defaults(String name) {
        return new TerminalModelConfig(name, 0f, 0f, 0f, 0f, 0f, 0f, DEFAULT_SCALE, 0f, 0f, 0f, "", 0f,
                0f, 0f, DEFAULT_ANCHOR_Z, DEFAULT_OBJ_UNITS_PER_BLOCK);
    }

    /**
     * Default OBJ scale: how many OBJ units the file uses per block.
     *
     * <p>{@code 1000} because the project models in <b>millimetres</b>:
     * 1000 mm = 1 m = 1 block. Every model file shipped from now on is authored
     * on that ruler, so millimetres are the default rather than a special case.
     * A legacy file still in the vanilla block-model convention (a block spans
     * {@code 0..16}) declares {@code 16} itself.</p>
     */
    public static final float DEFAULT_OBJ_UNITS_PER_BLOCK = 1000f;

    /**
     * Default {@code scale}: a plain multiplier, so {@code 1.0} means "as drawn".
     *
     * <p>Every length in the pose file is in <b>blocks</b>; the only unit bridge
     * is {@link #objUnitsPerBlock()}, which says what the OBJ vertices are
     * measured in (16 for block units, 1000 for millimetres). {@code scale}
     * deliberately stays a multiplier and does <b>not</b> double as a unit
     * conversion: when it did, putting {@code 1.0} in the file made a terminal
     * 16 times larger instead of leaving it alone.</p>
     */
    public static final float DEFAULT_SCALE = 1.0f;

    /**
     * Default {@code anchor}: the model origin, in <b>blocks</b>.
     *
     * <p>The anchor is measured from the model origin (the post's foot on the
     * plate), so an all-zero anchor means "the cable leaves where the model
     * starts" — exactly the point a modelling tool shows at {@code (0,0,0)}.
     * That is what makes the number copyable straight out of the editor instead
     * of something only reachable by guessing.</p>
     */
    public static final float DEFAULT_ANCHOR_Z = 0f;

    /**
     * The anchor's offset from the <b>model origin</b>, in blocks, authored frame.
     *
     * <p>{@code x} = right along the face, {@code y} = up along the face,
     * {@code z} = out of the plate into the room. {@code (0,0,0)} is the model
     * origin — the same point the modelling tool shows at its own zero.</p>
     */
    public org.joml.Vector3f anchorOffset() {
        return new org.joml.Vector3f(anchorX, anchorY, anchorZ);
    }

    /**
     * How many OBJ units make one block, for <b>this</b> model file.
     *
     * <h2>Why this is per-model and not a constant</h2>
     * <p>The project models in <b>millimetres</b>: 1000 mm = 1 m = 1 block.
     * Depending on the exporter, an OBJ may therefore store {@code 500} for a post
     * half a block tall (millimetre file) or {@code 8} (vanilla block-model file,
     * where a block spans {@code 0..16}). The renderer must know which, or one of
     * the two pipelines comes out 62.5 times wrong.</p>
     *
     * <p>So the factor is data the artist declares once per model, and the config
     * numbers ({@code offset / origin / anchor}) stay in blocks — metres — which is
     * the unit the artist reads off the tool in millimetres and divides by 1000.
     * Default {@link #DEFAULT_OBJ_UNITS_PER_BLOCK} is {@code 1000} (millimetres),
     * because that is the project's modelling unit.</p>
     */
    public float objUnitsPerBlock() {
        return objUnitsPerBlock > 0f ? objUnitsPerBlock : DEFAULT_OBJ_UNITS_PER_BLOCK;
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
