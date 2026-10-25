package com.stardustindustry.stardustindustry.machine.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stardustindustry.stardustindustry.energy.TerminalGrid;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

class TerminalCollisionGeometryTest {

    private static final double EPSILON = 1.0e-4;

    @BeforeEach
    void clearCaches() {
        TerminalPoseReader.clearCache();
        TerminalModelBounds.clearCache();
        TerminalPlateShapes.clearCache();
    }

    @Test
    void commonSideReadsBundledPoseAndObjWithoutAClientResourceManager() {
        TerminalPoseReader.Pose lvPose = TerminalPoseReader.of(null, TerminalStyle.LV);
        TerminalModelBounds.Box lvBounds = TerminalModelBounds.of(null, TerminalStyle.LV);

        assertEquals(-0.4375, lvPose.offsetZ(), EPSILON);
        assertNotNull(lvBounds);
        assertEquals(0.0, lvBounds.min().z, EPSILON);
        assertEquals(400.0, lvBounds.max().z, EPSILON);

        TerminalPoseReader.Pose hvPose = TerminalPoseReader.of(null, TerminalStyle.HV);
        assertEquals(-0.375, hvPose.offsetZ(), EPSILON);
        assertEquals(1000.0, hvPose.objUnitsPerBlock(), EPSILON);
    }

    @Test
    void lvCollisionFollowsThePoseOnEveryMountingFace() {
        TerminalGrid grid = TerminalGrid.of(TerminalStyle.LV.tier());

        assertAxisRange(TerminalPoser.blockLocalBox(Direction.NORTH, grid, 0, TerminalStyle.LV), 2,
                0.0625, 0.4625);
        assertAxisRange(TerminalPoser.blockLocalBox(Direction.SOUTH, grid, 0, TerminalStyle.LV), 2,
                0.5375, 0.9375);
        assertAxisRange(TerminalPoser.blockLocalBox(Direction.WEST, grid, 0, TerminalStyle.LV), 0,
                0.0625, 0.4625);
        assertAxisRange(TerminalPoser.blockLocalBox(Direction.EAST, grid, 0, TerminalStyle.LV), 0,
                0.5375, 0.9375);
        assertAxisRange(TerminalPoser.blockLocalBox(Direction.UP, grid, 0, TerminalStyle.LV), 1,
                0.5375, 0.9375);
        assertAxisRange(TerminalPoser.blockLocalBox(Direction.DOWN, grid, 0, TerminalStyle.LV), 1,
                0.0625, 0.4625);
    }

    @Test
    void hvCollisionStartsAtItsTwoSixteenthBackplate() {
        float[] box = TerminalPoser.blockLocalBox(
                Direction.NORTH, TerminalGrid.of(TerminalStyle.HV.tier()), 0, TerminalStyle.HV);

        assertAxisRange(box, 2, 0.125, 0.75);
    }

    @Test
    void terminalCollisionIsNotClippedToTheOwningBlock() {
        float[] box = TerminalPoser.blockLocalBox(
                Direction.NORTH, TerminalGrid.of(TerminalStyle.EHV.tier()), 0, TerminalStyle.EHV);

        assertEquals(1.0625, box[5], EPSILON);
    }

    @Test
    void bundledPlateShapesKeepTheirTierSpecificThicknessOnEveryFace() {
        Map<String, Double> thicknesses = Map.of(
                "lv", 1.0 / 16.0,
                "mv", 1.5 / 16.0,
                "hv", 2.0 / 16.0,
                "ehv", 2.5 / 16.0);

        for (Map.Entry<String, Double> entry : thicknesses.entrySet()) {
            for (Direction facing : Direction.values()) {
                VoxelShape shape = TerminalPlateShapes.parse(null, entry.getKey(), facing.getSerializedName());
                assertNotNull(shape, entry.getKey() + "/" + facing);
                AABB bounds = shape.bounds();
                double actual = switch (facing.getAxis()) {
                    case X -> bounds.getXsize();
                    case Y -> bounds.getYsize();
                    case Z -> bounds.getZsize();
                };
                assertEquals(entry.getValue(), actual, EPSILON, entry.getKey() + "/" + facing);

                double min = axisMin(bounds, facing.getAxis());
                double max = axisMax(bounds, facing.getAxis());
                if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                    assertEquals(0.0, min, EPSILON, entry.getKey() + "/" + facing);
                    assertEquals(entry.getValue(), max, EPSILON, entry.getKey() + "/" + facing);
                } else {
                    assertEquals(1.0 - entry.getValue(), min, EPSILON, entry.getKey() + "/" + facing);
                    assertEquals(1.0, max, EPSILON, entry.getKey() + "/" + facing);
                }
            }
        }
    }

    @Test
    void terminalObjsContainNoDuplicateGeometricTriangles() throws Exception {
        for (TerminalStyle style : TerminalStyle.all()) {
            String path = "models/block/terminal_" + style.model() + ".obj";
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("stardustindustry", path);
            try (InputStream in = TerminalAssetResources.open(null, id)) {
                assertNotNull(in, path);
                assertNoDuplicateTriangles(path, in);
            }
        }
    }

    @Test
    void hvObjLeavesItsBackplateContactPlaneOpen() throws Exception {
        String path = "models/block/terminal_hv.obj";
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("stardustindustry", path);
        try (InputStream in = TerminalAssetResources.open(null, id)) {
            assertNotNull(in, path);
            assertNoFaceOnZPlane(path, in, 0.0);
        }
    }

    private static void assertAxisRange(float[] box, int axis, double expectedMin, double expectedMax) {
        assertEquals(expectedMin, box[axis], EPSILON);
        assertEquals(expectedMax, box[axis + 3], EPSILON);
    }

    private static double axisMin(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double axisMax(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static void assertNoDuplicateTriangles(String path, InputStream in) throws Exception {
        List<Point> vertices = new ArrayList<>();
        Map<Triangle, String> triangles = new HashMap<>();
        String material = "<none>";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    vertices.add(new Point(number(parts[1]), number(parts[2]), number(parts[3])));
                } else if (line.startsWith("usemtl ")) {
                    material = line.substring("usemtl ".length()).trim();
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    int first = vertexIndex(parts[1], vertices.size());
                    for (int i = 2; i + 1 < parts.length; i++) {
                        Triangle triangle = Triangle.of(
                                vertices.get(first),
                                vertices.get(vertexIndex(parts[i], vertices.size())),
                                vertices.get(vertexIndex(parts[i + 1], vertices.size())));
                        String previous = triangles.putIfAbsent(triangle, material);
                        assertTrue(previous == null,
                                path + " repeats one geometric triangle in materials "
                                        + previous + " and " + material);
                    }
                }
            }
        }
    }

    private static void assertNoFaceOnZPlane(String path, InputStream in, double planeZ) throws Exception {
        List<Point> vertices = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    vertices.add(new Point(number(parts[1]), number(parts[2]), number(parts[3])));
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    boolean onPlane = true;
                    for (int i = 1; i < parts.length; i++) {
                        Point vertex = vertices.get(vertexIndex(parts[i], vertices.size()));
                        onPlane &= Math.abs(vertex.z() - planeZ) <= EPSILON;
                    }
                    assertTrue(!onPlane, path + " renders a face on the backplate contact plane z=" + planeZ);
                }
            }
        }
    }

    private static double number(String text) {
        double value = Double.parseDouble(text);
        return value == 0.0 ? 0.0 : value;
    }

    private static int vertexIndex(String token, int vertexCount) {
        int raw = Integer.parseInt(token.split("/", -1)[0]);
        return raw > 0 ? raw - 1 : vertexCount + raw;
    }

    private record Point(double x, double y, double z) implements Comparable<Point> {
        @Override
        public int compareTo(Point other) {
            int xOrder = Double.compare(x, other.x);
            if (xOrder != 0) {
                return xOrder;
            }
            int yOrder = Double.compare(y, other.y);
            return yOrder != 0 ? yOrder : Double.compare(z, other.z);
        }
    }

    private record Triangle(Point first, Point second, Point third) {
        static Triangle of(Point a, Point b, Point c) {
            List<Point> points = new ArrayList<>(List.of(a, b, c));
            points.sort(Point::compareTo);
            return new Triangle(points.get(0), points.get(1), points.get(2));
        }
    }
}
