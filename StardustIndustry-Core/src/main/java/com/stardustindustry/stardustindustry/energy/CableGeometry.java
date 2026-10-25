package com.stardustindustry.stardustindustry.energy;

import java.util.Arrays;

import org.joml.Vector3f;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared geometry for rendering and picking hanging cables.
 *
 * <p>The renderer and interaction code must sample the same curve. Keeping that
 * curve here prevents a visible cable from drifting away from its clickable
 * area when sag or subdivision rules change.</p>
 */
public final class CableGeometry {

    /** A hard cap on the drop, so a long run does not sag into the ground. */
    public static final float MAX_SAG = 1.0f;

    /** How far a cable drops at mid-span, as a fraction of the run. */
    public static final float SAG_RATIO = 0.06f;

    /** Spans at or above this length use their full calculated sag. */
    public static final float SHORT_SPAN_THRESHOLD = 3.0f;

    /** Rendered diameter of the smallest 50 A conductor, in blocks. */
    private static final float BASE_DIAMETER = 0.05f;

    private CableGeometry() {}

    /**
     * How many straight segments this span needs.
     *
     * <p>Subdivision grows with length to keep long runs smooth, while the cap
     * prevents exceptionally long runs from producing unbounded geometry.</p>
     */
    public static int segmentsFor(Vector3f from, Vector3f to) {
        float span = new Vector3f(to).sub(from).length();
        if (span < 1.0f) {
            return 2;
        }
        return Math.max(4, Math.min(64, (int) Math.ceil(span * 2.0)));
    }

    /**
     * Physical radius shared by rendering, picking and server-side contact checks.
     *
     * <p>The fourth-root curve keeps every class visible without turning high-current
     * conductors into pipes. Keeping the value here is important: a player must be
     * shocked by the tube they can actually see, not by a second invisible width.</p>
     */
    public static float radiusFor(CableAmpacity ampacity) {
        int amps = ampacity == null ? CableAmpacity.A50.amps() : ampacity.amps();
        int baseAmps = CableAmpacity.A50.amps();
        double diameter = BASE_DIAMETER * Math.pow((double) amps / baseAmps, 0.25);
        return (float) (diameter * 0.5);
    }

    /** A point along a copper cable at fraction {@code t} of its span. */
    public static Vector3f point(Vector3f from, Vector3f to, float t) {
        return point(from, to, t, 1.0f);
    }

    /**
     * A point along a hanging cable, weighted for its conductor.
     *
     * <p>The sag is a parabola rather than a true catenary. It is visual only;
     * electrical length remains the straight-line distance.</p>
     */
    public static Vector3f point(Vector3f from, Vector3f to, float t, float sagMultiplier) {
        Vector3f point = new Vector3f(from).lerp(to, t);
        float span = new Vector3f(to).sub(from).length();
        if (span < 1.0f) {
            return point;
        }

        float weight = Math.max(sagMultiplier, 0.01f);
        float sag = Math.min(span * SAG_RATIO, MAX_SAG / weight) * weight;
        sag *= shortSpanFactor(span);
        point.y -= sag * 4.0f * t * (1.0f - t);
        return point;
    }

    /** True when {@code hit} is within {@code radius} of a copper cable. */
    public static boolean isNear(Vector3f from, Vector3f to, Vec3 hit, float radius) {
        return isNear(from, to, hit, radius, 1.0f);
    }

    /**
     * True when {@code hit} is within {@code radius} of the sampled cable.
     *
     * <p>This uses the renderer's subdivision and sag rules so the visible and
     * pickable cable remain identical for every conductor material.</p>
     */
    public static boolean isNear(Vector3f from, Vector3f to, Vec3 hit,
                                 float radius, float sagMultiplier) {
        return distanceSqrToPolyline(from, to, hit, sagMultiplier)
                <= (double) radius * radius;
    }

    /** Squared distance from a point to the sampled cable polyline. */
    public static double distanceSqrToPolyline(Vector3f from, Vector3f to, Vec3 point,
                                               float sagMultiplier) {
        double closest = Double.POSITIVE_INFINITY;
        int segments = segmentsFor(from, to);
        Vector3f previous = point(from, to, 0f, sagMultiplier);
        for (int i = 1; i <= segments; i++) {
            Vector3f current = point(from, to, (float) i / segments, sagMultiplier);
            closest = Math.min(closest, distanceSqrToSegment(point, previous, current));
            previous = current;
        }
        return closest;
    }

    /**
     * Squared distance from an axis-aligned body to the sampled cable polyline.
     *
     * <p>Each rendered straight segment is minimized exactly against the box. This
     * catches contact at a player's head, feet and sides without replacing the body
     * with an oversized sphere.</p>
     */
    public static double distanceSqrToPolyline(Vector3f from, Vector3f to, AABB bounds,
                                               float sagMultiplier) {
        double closest = Double.POSITIVE_INFINITY;
        int segments = segmentsFor(from, to);
        Vector3f previous = point(from, to, 0f, sagMultiplier);
        for (int i = 1; i <= segments; i++) {
            Vector3f current = point(from, to, (float) i / segments, sagMultiplier);
            closest = Math.min(closest, distanceSqrToSegment(bounds, previous, current));
            if (closest == 0.0) {
                return 0.0;
            }
            previous = current;
        }
        return closest;
    }

    /** Smoothly ramps sag from a taut one-block span to full sag. */
    private static float shortSpanFactor(float span) {
        float t = Math.max(0f, Math.min(1f, (span - 1.0f) / (SHORT_SPAN_THRESHOLD - 1.0f)));
        return t * t * (3f - 2f * t);
    }

    /** Squared distance from {@code point} to segment {@code from}-{@code to}. */
    static double distanceSqrToSegment(Vec3 point, Vector3f from, Vector3f to) {
        double abx = to.x - from.x;
        double aby = to.y - from.y;
        double abz = to.z - from.z;
        double apx = point.x - from.x;
        double apy = point.y - from.y;
        double apz = point.z - from.z;
        double lengthSqr = abx * abx + aby * aby + abz * abz;
        double t = lengthSqr < 1.0e-9 ? 0.0 : (apx * abx + apy * aby + apz * abz) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = apx - abx * t;
        double dy = apy - aby * t;
        double dz = apz - abz * t;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Exact squared distance from a line segment to an axis-aligned box. */
    static double distanceSqrToSegment(AABB bounds, Vector3f from, Vector3f to) {
        double[] start = {from.x, from.y, from.z};
        double[] delta = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] minimum = {bounds.minX, bounds.minY, bounds.minZ};
        double[] maximum = {bounds.maxX, bounds.maxY, bounds.maxZ};

        // Distance from P(t) to an AABB is a convex piecewise quadratic. The only
        // places its formula changes are where an axis crosses a box face.
        double[] cuts = new double[8];
        int cutCount = 2;
        cuts[0] = 0.0;
        cuts[1] = 1.0;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0e-12) {
                continue;
            }
            double enter = (minimum[axis] - start[axis]) / delta[axis];
            double exit = (maximum[axis] - start[axis]) / delta[axis];
            if (enter > 0.0 && enter < 1.0) cuts[cutCount++] = enter;
            if (exit > 0.0 && exit < 1.0) cuts[cutCount++] = exit;
        }
        Arrays.sort(cuts, 0, cutCount);

        double closest = Double.POSITIVE_INFINITY;
        for (int index = 0; index < cutCount - 1; index++) {
            double low = cuts[index];
            double high = cuts[index + 1];
            double midpoint = (low + high) * 0.5;
            double quadratic = 0.0;
            double linear = 0.0;
            for (int axis = 0; axis < 3; axis++) {
                double coordinate = start[axis] + delta[axis] * midpoint;
                double face;
                if (coordinate < minimum[axis]) {
                    face = minimum[axis];
                } else if (coordinate > maximum[axis]) {
                    face = maximum[axis];
                } else {
                    continue;
                }
                quadratic += delta[axis] * delta[axis];
                linear += delta[axis] * (start[axis] - face);
            }
            double optimum = quadratic == 0.0 ? midpoint : -linear / quadratic;
            optimum = Math.max(low, Math.min(high, optimum));
            closest = Math.min(closest, distanceSqrToAabb(start, delta, bounds, low));
            closest = Math.min(closest, distanceSqrToAabb(start, delta, bounds, high));
            closest = Math.min(closest, distanceSqrToAabb(start, delta, bounds, optimum));
        }
        return closest;
    }

    private static double distanceSqrToAabb(double[] start, double[] delta,
                                            AABB bounds, double t) {
        double x = start[0] + delta[0] * t;
        double y = start[1] + delta[1] * t;
        double z = start[2] + delta[2] * t;
        double dx = axisDistance(x, bounds.minX, bounds.maxX);
        double dy = axisDistance(y, bounds.minY, bounds.maxY);
        double dz = axisDistance(z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) return minimum - value;
        if (value > maximum) return value - maximum;
        return 0.0;
    }
}
