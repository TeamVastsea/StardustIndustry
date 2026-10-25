package com.stardustindustry.stardustindustry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

class CableShockSafetyTest {
    @Test
    void pointDistanceUsesTheSameSaggingPolylineAsRendering() {
        Vector3f from = new Vector3f(0.0f, 2.0f, 0.0f);
        Vector3f to = new Vector3f(8.0f, 2.0f, 0.0f);
        Vector3f midpoint = CableGeometry.point(from, to, 0.5f, 1.0f);

        assertEquals(0.0, CableGeometry.distanceSqrToPolyline(from, to,
                new Vec3(midpoint.x, midpoint.y, midpoint.z), 1.0f), 1.0e-12);
        assertTrue(CableGeometry.distanceSqrToPolyline(from, to,
                new Vec3(midpoint.x, midpoint.y + 1.0, midpoint.z), 1.0f) > 0.9);
    }

    @Test
    void bodyDistanceDetectsSideContactWithoutUsingAnOversizedSphere() {
        Vector3f from = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f to = new Vector3f(0.5f, 0.0f, 0.0f);

        assertEquals(0.01, CableGeometry.distanceSqrToPolyline(from, to,
                new AABB(0.2, 0.1, -0.1, 0.3, 0.2, 0.1), 1.0f), 1.0e-9);
        assertEquals(0.0, CableGeometry.distanceSqrToPolyline(from, to,
                new AABB(0.2, -0.1, -0.1, 0.3, 0.1, 0.1), 1.0f), 1.0e-12);
    }

    @Test
    void insulationAndLiveVoltageDetermineShockSeverity() {
        assertEquals(CableInsulation.Shock.NONE, CableInsulation.CLASS_1.shockAt(380));
        assertEquals(CableInsulation.Shock.LIGHT, CableInsulation.CLASS_1.shockAt(10_000));
        assertEquals(CableInsulation.Shock.LETHAL, CableInsulation.CLASS_1.shockAt(110_000));
        assertEquals(CableInsulation.Shock.NONE, CableInsulation.CLASS_2.shockAt(10_000));
        assertEquals(CableInsulation.Shock.LETHAL, CableInsulation.CLASS_2.shockAt(110_000));
        assertEquals(CableInsulation.Shock.LIGHT, CableInsulation.BARE.shockAt(380));
        assertEquals(CableInsulation.Shock.LETHAL, CableInsulation.BARE.shockAt(10_000));
    }

    @Test
    void strongestContactWinsAndRenderedRadiusGrowsWithAmpacity() {
        assertEquals(CableInsulation.Shock.LETHAL, CableShockService.stronger(
                CableInsulation.Shock.LIGHT, CableInsulation.Shock.LETHAL));
        assertEquals(2.0f, CableShockService.survivableDamage(20.0f, 3.0f));
        assertEquals(0.0f, CableShockService.survivableDamage(4.0f, 1.0f));
        assertTrue(CableGeometry.radiusFor(CableAmpacity.A5000)
                > CableGeometry.radiusFor(CableAmpacity.A50));
    }
}
