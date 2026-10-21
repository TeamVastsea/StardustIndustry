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

    /** OBJ units per block. The renderer's {@code UNIT}. */
    public static final float UNIT = 1.0f / 16.0f;

    /**
     * How far the plate's room-facing surface sits from the block centre, in
     * block units, measured <b>against</b> the growth direction.
     *
     * <p>Half a block is 8 units and the plate is 1 unit thick, flush with the
     * {@code FACING} face, so its inner surface is 7 units out from the centre.
     * Terminals stand on that surface; the sign at the call site is negative
     * because the frame's {@code normal} points into the room, away from it.</p>
     */
    public static final float PLATE_OFFSET = 7.0f;

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
     * <p>This is the cell's centre projected onto the plate's room-facing surface:
     * the cell centre in the grid's u/v frame, carried onto the real face by the
     * basis, then pulled {@code PLATE_OFFSET} out along the plate's side.</p>
     */
    public static Vector3f footLocal(Direction facing, TerminalGrid grid, int cell) {
        Vector3f[] basis = faceAxes(facing);
        float u = (grid.columnOf(cell) + 0.5f) / grid.columns();
        float v = (grid.rowOf(cell) + 0.5f) / grid.rows();
        return new Vector3f(0.5f, 0.5f, 0.5f)
                .add(new Vector3f(basis[0]).mul(u - 0.5f))
                .add(new Vector3f(basis[1]).mul(v - 0.5f))
                .fma(-PLATE_OFFSET * UNIT, new Vector3f(basis[2]));
    }

    /**
     * The foot of a terminal in <b>world</b> coordinates: the point a cable is
     * anchored to, and where the post starts.
     */
    public static Vector3f footWorld(net.minecraft.core.BlockPos pos, Direction facing,
                                     TerminalGrid grid, int cell) {
        Vector3f local = footLocal(facing, grid, cell);
        return new Vector3f(local.x + pos.getX(), local.y + pos.getY(), local.z + pos.getZ());
    }

    /**
     * A point just beyond the foot along the growth direction, so a cable does
     * not start buried inside the plate.
     *
     * <p>Kept small on purpose: the terminal post is the visible thing, and the
     * cable should appear to come off its tip rather than out of the wall. The
     * value is a standoff in block units, not the post's length — the artist
     * decides that, and a cable that depended on it would break the moment the
     * OBJ changed.</p>
     */
    public static final float CABLE_STANDOFF = 2.0f / 16.0f;

    /** The world point a cable hangs from, offset clear of the plate. */
    public static Vector3f cableAnchor(net.minecraft.core.BlockPos pos, Direction facing,
                                       TerminalGrid grid, int cell) {
        Vector3f foot = footWorld(pos, facing, grid, cell);
        return foot.fma(CABLE_STANDOFF, growth(facing));
    }

    /** How many straight segments approximate a hanging cable, in renderer and picking alike. */
    public static final int SAG_SEGMENTS = 12;

    /**
     * How many segments this particular span needs.
     *
     * <p>Fixed {@link #SAG_SEGMENTS} is enough for a short run but a 40-block span
     * breaks visibly into straight chords — the wire looks like a bent pipe rather
     * than a hanging cable. Subdividing by length keeps the curve smooth at every
     * span, while the cap stops a very long run from costing thousands of quads.
     * <b>The renderer and the click test must both use this</b>: pick a different
     * count and the wire a player sees stops being the wire they can hit.</p>
     *
     * <p>Short spans need very few segments because they barely bend: a 1-block run
     * is a straight line and two segments are already more than enough. Scaling
     * with the span instead of paying a fixed cost is what keeps a wall of short
     * jumpers cheap.</p>
     */
    public static int sagSegmentsFor(Vector3f from, Vector3f to) {
        float span = new Vector3f(to).sub(from).length();
        if (span < 1.0f) {
            // Straight: one segment would do, but two keep the tube builder's
            // ring loop well-defined without a special case.
            return 2;
        }
        return Math.max(4, Math.min(64, (int) Math.ceil(span * 2.0)));
    }

    /** How far a cable drops at mid-span, as a fraction of the horizontal run. */
    public static final float SAG_RATIO = 0.06f;

    /** A hard cap on the drop, so a long run does not sag into the ground. */
    public static final float MAX_SAG = 1.0f;

    /**
     * Spans shorter than this are strung effectively taut.
     *
     * <p>A real pulled cable barely hangs below the straight line over a couple of
     * metres, and at this scale the difference is what players notice: a 1–2 block
     * run should look like a tight wire, not a hanging rope. The sag ramps in over
     * the threshold with a smoothstep, so the transition is invisible — 3 blocks
     * and above hang normally, below it the wire straightens out.</p>
     */
    public static final float SHORT_SPAN_THRESHOLD = 3.0f;

    /**
     * A point along a hanging cable, at fraction {@code t} of the way from
     * {@code from} to {@code to}, for a copper conductor.
     *
     * <p>Kept for callers that predate materials; the overload below is what the
     * renderer and the click test use, so the drawn wire and the clickable wire
     * stay the same curve for every material.</p>
     */
    public static Vector3f pointOnCable(Vector3f from, Vector3f to, float t) {
        return pointOnCable(from, to, t, 1.0f);
    }

    /**
     * A point along a hanging cable, weighted for its conductor.
     *
     * <p>The sag is a parabola rather than a true catenary: §6.9 asks for the
     * <b>simplest</b> hanging curve and forbids anchor solving, and at these lengths
     * the difference is invisible. It is <b>visual only</b> — the electrical length
     * is the straight-line distance, not the arc length.</p>
     *
     * <p>{@code sagMultiplier} carries the conductor's weight and how hard it may be
     * strung: copper is 1.0, steel-cored aluminium far below it, so a long-span line
     * hangs visibly straighter. The cap is divided by the multiplier as well as the
     * drop being multiplied by it, otherwise a light conductor would flatten out at
     * long spans too and the difference would vanish exactly where it should show.</p>
     *
     * <p>Lives here, not in the renderer, because the click test needs the same
     * curve: what the player aims at must be the wire they can see.</p>
     */
    public static Vector3f pointOnCable(Vector3f from, Vector3f to, float t, float sagMultiplier) {
        Vector3f point = new Vector3f(from).lerp(to, t);
        float span = new Vector3f(to).sub(from).length();
        // Below one block there is no room for a sag to matter and no player would
        // expect one: pull it straight. This is not a compromise but the correct
        // end of the curve — a taut wire's sag tends to zero with its span.
        if (span < 1.0f) {
            return point;
        }
        float weight = Math.max(sagMultiplier, 0.01f);
        float sag = Math.min(span * SAG_RATIO, MAX_SAG / weight) * weight;
        // Short spans are strung tight: the sag ramps in smoothly over the first
        // few blocks instead of appearing suddenly, so a 2-block run reads as a
        // straight wire and a long run hangs normally. This is how real overhead
        // lines look — droop only becomes visible once the span is long enough.
        sag *= shortSpanFactor(span);
        // 4t(1-t) is 0 at both ends and 1 at mid-span: the parabola's shape.
        point.y -= sag * 4.0f * t * (1.0f - t);
        return point;
    }

    /**
     * 0 → 1 ramp that keeps short spans taut.
     *
     * <p>Smoothstep over {@link #SHORT_SPAN_THRESHOLD}: a 3-block run hangs at
     * full droop, a 1-block run is straight, and nothing in between visibly pops.
     * Called with the straight-line span; the sag and the segment subdivision
     * both use it, so the drawn wire and the pickable wire stay identical.</p>
     */
    private static float shortSpanFactor(float span) {
        float t = Math.max(0f, Math.min(1f, (span - 1.0f) / (SHORT_SPAN_THRESHOLD - 1.0f)));
        return t * t * (3f - 2f * t);
    }

    /**
     * True when the point {@code hit} is within {@code radius} of the hanging
     * cable running {@code from} to {@code to}.
     *
     * <p>Tested against the same polyline the renderer draws, so a player who aims
     * at the visible wire is aiming at the pickable one. The radius is generous on
     * purpose: a 2 cm wire is a few pixels wide and impossible to hit exactly.</p>
     *
     * <p>This overload assumes a copper conductor. Callers that know the cable's
     * material must use {@link #isNearCable(Vector3f, Vector3f, Vec3, float, float)},
     * because a straighter wire hangs somewhere else — testing the copper curve
     * would make an ACSR run unclickable in the middle of its span.</p>
     */
    public static boolean isNearCable(Vector3f from, Vector3f to, net.minecraft.world.phys.Vec3 hit, float radius) {
        return isNearCable(from, to, hit, radius, 1.0f);
    }

    /** As above, for a conductor of the given sag weight. */
    public static boolean isNearCable(Vector3f from, Vector3f to, net.minecraft.world.phys.Vec3 hit,
                                      float radius, float sagMultiplier) {
        double radiusSqr = (double) radius * radius;
        int segments = sagSegmentsFor(from, to);
        Vector3f previous = pointOnCable(from, to, 0f, sagMultiplier);
        for (int i = 1; i <= segments; i++) {
            Vector3f point = pointOnCable(from, to, (float) i / segments, sagMultiplier);
            if (distanceSqrToSegment(hit, previous, point) <= radiusSqr) {
                return true;
            }
            previous = point;
        }
        return false;
    }

    /** Squared distance from {@code p} to the segment {@code a}–{@code b}. */
    private static double distanceSqrToSegment(net.minecraft.world.phys.Vec3 p, Vector3f a, Vector3f b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abz = b.z - a.z;
        double apx = p.x - a.x;
        double apy = p.y - a.y;
        double apz = p.z - a.z;
        double lengthSqr = abx * abx + aby * aby + abz * abz;
        double t = lengthSqr < 1.0e-9 ? 0.0 : (apx * abx + apy * aby + apz * abz) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = apx - abx * t;
        double dy = apy - aby * t;
        double dz = apz - abz * t;
        return dx * dx + dy * dy + dz * dz;
    }
}
