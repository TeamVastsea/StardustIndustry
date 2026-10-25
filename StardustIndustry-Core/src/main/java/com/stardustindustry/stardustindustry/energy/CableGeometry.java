package com.stardustindustry.stardustindustry.energy;

import org.joml.Vector3f;

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
        double radiusSqr = (double) radius * radius;
        int segments = segmentsFor(from, to);
        Vector3f previous = point(from, to, 0f, sagMultiplier);
        for (int i = 1; i <= segments; i++) {
            Vector3f current = point(from, to, (float) i / segments, sagMultiplier);
            if (distanceSqrToSegment(hit, previous, current) <= radiusSqr) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    /** Smoothly ramps sag from a taut one-block span to full sag. */
    private static float shortSpanFactor(float span) {
        float t = Math.max(0f, Math.min(1f, (span - 1.0f) / (SHORT_SPAN_THRESHOLD - 1.0f)));
        return t * t * (3f - 2f * t);
    }

    /** Squared distance from {@code point} to segment {@code from}-{@code to}. */
    private static double distanceSqrToSegment(Vec3 point, Vector3f from, Vector3f to) {
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
}
