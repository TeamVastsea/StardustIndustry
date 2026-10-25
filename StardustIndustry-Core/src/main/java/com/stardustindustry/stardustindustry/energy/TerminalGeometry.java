package com.stardustindustry.stardustindustry.energy;

import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * Turns a {@link TerminalEndpoint} into a world-space point.
 *
 * <h2>One source of truth</h2>
 * <p>The point where a cable leaves a terminal is the point a player sees and
 * clicks. Three different places need it — the terminal renderer (to place the
 * post), the cable renderer (to hang the catenary from it) and the interaction
 * code (to pick the nearest cable) — and if any two of them computed it
 * separately the cable would visibly miss its terminal.</p>
 *
 * <p>The formulas here are the terminal renderer's, moved out of the renderer so
 * they can be shared. <b>{@code TerminalMountRenderer} must call this</b> rather
 * than keep its own copy; the two drifting apart is exactly the class of bug the
 * project has already paid for three times (§6.3.3).</p>
 *
 * <h2>The basis table</h2>
 * <p>For each {@code FACING} (the side the backplate is bolted against) the
 * frame is:</p>
 *
 * <pre>
 *   right  — world direction that goes right on the face, seen by the player
 *   up     — world direction that goes up on the face
 *   normal — the direction a terminal GROWS, i.e. out of the block into the room
 *            (always {@code FACING.getOpposite()})
 * </pre>
 *
 * <p>Every row satisfies {@code right × up = normal}, so the basis is a proper
 * rotation and not a mirrored frame. That matters because the renderer builds a
 * {@code Matrix3f} from the same triple.</p>
 *
 * <p>These rows are <b>not</b> derived by sign algebra — they were checked
 * against the running game and against the block models, which place the plate
 * on the {@code FACING} side (north {@code z 0..1}, south {@code z 15..16}, west
 * {@code x 0..1}, east {@code x 15..16}, up {@code y 15..16}, down
 * {@code y 0..1}). See {@code docs/electric-power-system-design.md} §6.3.3.</p>
 */
public final class TerminalGeometry {

    private TerminalGeometry() {}

    // A constant PLATE_OFFSET = 7/16 used to sit here: the distance from the block
    // centre to the room-facing surface of a 1/16-thick backplate. Every terminal
    // was pushed out by it so its foot would "stand on the plate".
    //
    // It was removed on the project owner's call. The plate is the ARTIST's
    // object: a rack may be a thick casting, a busbar frame, a bracket with its
    // own depth, or a plate mounted flush with the block face. Baking one
    // particular plate thickness into the engine meant the terminal was either
    // sunk into the hardware or floating in front of it, and the artist had to
    // compensate for an invisible 7/16 shift whenever they placed vertices.
    //
    // The engine now puts the model origin at the cell centre and adds nothing.
    // Any stand-off is drawn into the model, or written as "offset" in the pose
    // file, where it can be seen and tuned.

    /**
     * The face frame: {@code {right, up, normal}} per {@code FACING}.
     *
     * <p>Same table the terminal renderer uses, moved here so both read one
     * copy. Editing a row without re-checking the renderer's rotation makes the
     * drawn post and the cable foot disagree.</p>
     */
    private static final java.util.Map<Direction, Vector3f[]> FACES = java.util.Map.of(
            // north: plate on -z, room on +z  -> grows +z
            Direction.NORTH, axes(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)),
            // south: plate on +z, room on -z  -> grows -z
            Direction.SOUTH, axes(new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1)),
            // west: plate on -x, room on +x   -> grows +x
            Direction.WEST, axes(new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0)),
            // east: plate on +x, room on -x   -> grows -x
            Direction.EAST, axes(new Vector3f(0, 0, 1), new Vector3f(0, 1, 0), new Vector3f(-1, 0, 0)),
            // ceiling plate on +y, room below -> grows -y
            Direction.UP, axes(new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, -1, 0)),
            // floor plate on -y, room above   -> grows +y
            Direction.DOWN, axes(new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0)));

    private static Vector3f[] axes(Vector3f right, Vector3f up, Vector3f normal) {
        return new Vector3f[] {right, up, normal};
    }

    /** The {@code {right, up, normal}} frame for a facing, never null. */
    public static Vector3f[] faceAxes(Direction facing) {
        return FACES.getOrDefault(facing, FACES.get(Direction.NORTH));
    }

    /** The direction a terminal of this facing grows (out of the block). */
    public static Vector3f growth(Direction facing) {
        return new Vector3f(faceAxes(facing)[2]);
    }

    /**
     * The foot of a terminal in <b>block-local</b> coordinates (0..1 per axis).
     *
     * <p>This is <b>the cell's centre on the plate's face</b>: the cell centre in
     * the grid's {@code u/v} frame, carried onto the real face by the basis, and
     * nothing else. In particular the renderer adds <b>no stand-off</b> of its
     * own towards the room.</p>
     *
     * <h2>Why there is no automatic plate offset</h2>
     * <p>This used to pull the foot {@code 7/16} of a block out of the plane, on the
     * assumption that every rack is a 1/16-thick backplate whose room-facing
     * surface the terminal must stand on. That assumption belongs to the
     * <em>art</em>, not to the engine: a rack may be a thick casting, a busbar
     * frame, a bracket with its own depth, or a plate mounted flush with the block
     * face — and then the automatic offset either sinks the terminal into the
     * hardware or leaves it floating in front of it. It also made every model
     * harder to author, because the artist had to compensate for an invisible
     * 7/16 shift when placing vertices.</p>
     *
     * <p>So the engine places the model origin exactly at the cell centre and
     * stops there: {@code (0,0,0)} in the artist's file is the middle of the
     * cell, level with the block face. If a terminal must stand off a thick
     * plate, that stand-off is drawn into the model (or written as {@code offset}
     * in the pose file), which is where it can be seen and tuned.</p>
     *
     * <p>This is the <b>art</b> origin: where the terminal renderer starts from,
     * and the point an {@code anchor} is measured from. See {@link #anchorLocal}.</p>
     */
    public static Vector3f footLocal(Direction facing, TerminalGrid grid, int cell) {
        Vector3f[] basis = faceAxes(facing);
        float u = (grid.columnOf(cell) + 0.5f) / grid.columns();
        float v = (grid.rowOf(cell) + 0.5f) / grid.rows();
        return new Vector3f(0.5f, 0.5f, 0.5f)
                .add(new Vector3f(basis[0]).mul(u - 0.5f))
                .add(new Vector3f(basis[1]).mul(v - 0.5f));
    }

    /**
     * The cell centre, before any plate stand-off.
     *
     * <p>Kept as its own name because callers may ask for "the middle of the
     * cell" by intent. Since the engine no longer applies a stand-off, this and
     * {@link #footLocal} return the same point; keeping both makes the intent
     * readable at the call site.</p>
     */
    public static Vector3f cellCentreLocal(Direction facing, TerminalGrid grid, int cell) {
        return footLocal(facing, grid, cell);
    }

    /**
     * The foot of a terminal in <b>world</b> coordinates: where the post starts.
     */
    public static Vector3f footWorld(net.minecraft.core.BlockPos pos, Direction facing,
                                     TerminalGrid grid, int cell) {
        Vector3f local = footLocal(facing, grid, cell);
        return new Vector3f(local.x + pos.getX(), local.y + pos.getY(), local.z + pos.getZ());
    }

    /**
     * The world point a cable hangs from, for this cell.
     *
     * <h2>The origin is the MODEL ORIGIN — the same point the artist sees in their editor</h2>
     * <p>{@code anchor} is measured <b>from the model's own origin</b>, i.e. the
     * foot of the terminal on the plate ({@link #footLocal}). The model is authored
     * with {@code (0,0,0)} at the centre of its base, growing along {@code +z}, so
     * that is exactly what a modelling tool shows when the artist puts the cursor
     * on the spot where the wire should leave. To wire the top of a post drawn
     * {@code z = 0.625} long, the artist writes {@code anchor.z = 0.625} and gets
     * that point — no trial and error.</p>
     *
     * <h2>Why not the block centre</h2>
     * <p>Two earlier attempts measured the anchor from the block centre (and, in
     * one version, from the cell centre). That is <b>not</b> a point any modelling
     * tool shows: the model origin sits on the plate's room-facing surface, half a
     * block away from the block centre, and the gap depends on how thick the
     * backplate is for that tier. The result was an anchor frame the artist could
     * only reach by guessing, and all-zero did not mean "start of the model"
     * either — which is precisely the "anchor numbers do nothing" complaint.</p>
     *
     * <h2>Units are blocks (metres)</h2>
     * <p>One number is one block, the same ruler as {@code offset / origin} and as
     * the modelling software when one block is modelled as one metre: {@code 0.625}
     * means {@code 0.625} of a block, i.e. one OBJ unit {@code 10} in a file where
     * a block spans {@code 0..16}. The axis meaning is the {@code offset} one —
     * {@code x} right along the face, {@code y} up along the face, {@code z} out of
     * the plate into the room — carried by the face basis so all six facings agree.</p>
     *
     * <p>An all-zero anchor therefore lands exactly on the model's origin, on the
     * plate surface, at the centre of that cell.</p>
     */
    public static Vector3f anchorLocal(Direction facing, TerminalGrid grid, int cell, Vector3f anchorOffset) {
        Vector3f[] basis = faceAxes(facing);
        Vector3f offset = new Vector3f(anchorOffset);
        // Start at the MODEL ORIGIN = the foot the renderer also starts from. The
        // per-cell placement is already inside footLocal, so a multi-cell rack gets
        // one anchor per cell for free and no extra spread term is needed.
        //
        // The offset's x/y/z mean right/up/out-of-the-plate, so they are carried by
        // the basis rather than added raw: on a floor plate "up" is +y but "out"
        // is +y too, and on a ceiling they swap sign.
        return footLocal(facing, grid, cell)
                .add(new Vector3f(basis[0]).mul(offset.x))
                .add(new Vector3f(basis[1]).mul(offset.y))
                .add(new Vector3f(basis[2]).mul(offset.z));
    }

    /** World-space form of {@link #anchorLocal}. */
    public static Vector3f anchorWorld(net.minecraft.core.BlockPos pos, Direction facing,
                                       TerminalGrid grid, int cell, Vector3f anchorOffset) {
        Vector3f local = anchorLocal(facing, grid, cell, anchorOffset);
        return new Vector3f(local.x + pos.getX(), local.y + pos.getY(), local.z + pos.getZ());
    }

    /**
     * Carries a model-frame box onto a cell, giving a <b>block-local</b> box
     * (0..1 per axis) that can be turned into a {@code VoxelShape}.
     *
     * <h2>The transform must match the renderer exactly</h2>
     * <p>This applies the same sequence {@code TerminalMountRenderer} applies to a
     * {@code PoseStack}: cell centre, then the face basis, then
     * {@code offset}, {@code origin}, rotation, scale and mirror. If the two ever
     * disagree the collision box sits somewhere other than the visible model —
     * players walk through a terminal or bump into empty air, which is confusing
     * in a way the eye cannot resolve. So the renderer and this method must be
     * changed together; the pose file is the shared truth.</p>
     *
     * <h2>Boxes transform as unions of their corners</h2>
     * <p>Rotating an axis-aligned box does not give a box, so the eight corners
     * are pushed through the transform and a new axis-aligned box is wrapped
     * around them. That is what "the smallest rectangle that encloses the model"
     * means once the model is turned: the box grows to cover the turned shape,
     * it never clips into it.</p>
     *
     * @param min      the lowest corner in the model frame, in blocks
     * @param max      the highest corner in the model frame, in blocks
     * @param rotation the pose rotation as a quaternion (degrees already applied)
     * @param mirrored true when the pose mirrors the model
     * @param mirror   the mirror axis, {@code "x" / "y" / "z"}, ignored otherwise
     * @param mirrorOffset the mirror plane's distance from the origin, in blocks
     */
    public static org.joml.Vector3f[] placeBox(
            Direction facing, TerminalGrid grid, int cell,
            Vector3f min, Vector3f max,
            Vector3f offset, Vector3f origin,
            org.joml.Quaternionf rotation, float scale,
            boolean mirrored, String mirror, float mirrorOffset) {

        Vector3f foot = footLocal(facing, grid, cell);
        Vector3f[] basis = faceAxes(facing);
        Vector3f[] corners = {
                new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, min.y, min.z),
                new Vector3f(min.x, max.y, min.z), new Vector3f(max.x, max.y, min.z),
                new Vector3f(min.x, min.y, max.z), new Vector3f(max.x, min.y, max.z),
                new Vector3f(min.x, max.y, max.z), new Vector3f(max.x, max.y, max.z)
        };

        Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f hi = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (Vector3f corner : corners) {
            // The renderer's order: model space -> pivot -> rotation -> scale ->
            // mirror -> offset -> face basis -> cell centre.
            Vector3f p = new Vector3f(corner).sub(origin);
            p.rotate(rotation);
            p.mul(scale);
            if (mirrored) {
                switch (mirror) {
                    case "x" -> { p.x = -p.x + mirrorOffset; }
                    case "y" -> { p.y = -p.y + mirrorOffset; }
                    case "z" -> { p.z = -p.z + mirrorOffset; }
                    default -> { }
                }
            }
            // offset is in blocks on the plate's own frame (right / up / out).
            p.add(offset);
            // The model frame's axes are the plate's: x right, y up, z out.
            Vector3f world = new Vector3f(
                    basis[0].x * p.x + basis[1].x * p.y + basis[2].x * p.z,
                    basis[0].y * p.x + basis[1].y * p.y + basis[2].y * p.z,
                    basis[0].z * p.x + basis[1].z * p.y + basis[2].z * p.z);
            world.add(foot);

            lo.min(world);
            hi.max(world);
        }
        return new Vector3f[] {lo, hi};
    }

    /**
     * Where the anchor offset for a model key comes from.
     *
     * <h2>Why a hook instead of a direct call</h2>
     * <p>The client installs a supplier for its reloadable resource-pack poses.
     * Authoritative server code reads the same JSON through
     * {@code TerminalPoseReader}; it cannot use this hook because loading a client
     * class on a dedicated server is invalid. Keeping this hook typed only in
     * common classes preserves that side boundary.</p>
     */
    private static volatile java.util.function.Function<String, Vector3f> anchorSource;

    /** Installs the anchor lookup. Called once from the client, on setup. */
    public static void setAnchorSource(java.util.function.Function<String, Vector3f> source) {
        anchorSource = source;
    }

    /**
     * The configured anchor offset for a terminal style's model key.
     *
     * <p>Falls back to {@code (0, 0, 0)} — the model origin — when no client source
     * is installed or the key is unknown.</p>
     */
    public static Vector3f anchorOffsetFor(String modelKey) {
        java.util.function.Function<String, Vector3f> source = anchorSource;
        if (source != null) {
            try {
                Vector3f value = source.apply(modelKey);
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException ignored) {
                // A broken client-side table must not take the cable code down;
                // the default anchor is a valid position.
            }
        }
        return new Vector3f(0f, 0f, 0f);
    }

    /** @deprecated Cable curve geometry now belongs to {@link CableGeometry}. */
    @Deprecated
    public static final int SAG_SEGMENTS = 12;

    /** @deprecated Use {@link CableGeometry#SAG_RATIO}. */
    @Deprecated
    public static final float SAG_RATIO = CableGeometry.SAG_RATIO;

    /** @deprecated Use {@link CableGeometry#MAX_SAG}. */
    @Deprecated
    public static final float MAX_SAG = CableGeometry.MAX_SAG;

    /** @deprecated Use {@link CableGeometry#SHORT_SPAN_THRESHOLD}. */
    @Deprecated
    public static final float SHORT_SPAN_THRESHOLD = CableGeometry.SHORT_SPAN_THRESHOLD;

    /** @deprecated Use {@link CableGeometry#segmentsFor}. */
    @Deprecated
    public static int sagSegmentsFor(Vector3f from, Vector3f to) {
        return CableGeometry.segmentsFor(from, to);
    }

    /** @deprecated Use {@link CableGeometry#point(Vector3f, Vector3f, float)}. */
    @Deprecated
    public static Vector3f pointOnCable(Vector3f from, Vector3f to, float t) {
        return CableGeometry.point(from, to, t);
    }

    /** @deprecated Use {@link CableGeometry#point(Vector3f, Vector3f, float, float)}. */
    @Deprecated
    public static Vector3f pointOnCable(Vector3f from, Vector3f to, float t, float sagMultiplier) {
        return CableGeometry.point(from, to, t, sagMultiplier);
    }

    /** @deprecated Use {@link CableGeometry#isNear(Vector3f, Vector3f, net.minecraft.world.phys.Vec3, float)}. */
    @Deprecated
    public static boolean isNearCable(
            Vector3f from, Vector3f to, net.minecraft.world.phys.Vec3 hit, float radius) {
        return CableGeometry.isNear(from, to, hit, radius);
    }

    /** @deprecated Use {@link CableGeometry#isNear(Vector3f, Vector3f, net.minecraft.world.phys.Vec3, float, float)}. */
    @Deprecated
    public static boolean isNearCable(
            Vector3f from, Vector3f to, net.minecraft.world.phys.Vec3 hit,
            float radius, float sagMultiplier) {
        return CableGeometry.isNear(from, to, hit, radius, sagMultiplier);
    }

}
