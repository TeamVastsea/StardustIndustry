package com.stardustindustry.stardustindustry.client;

import java.util.Collection;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableColor;
import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.CableService;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;
import com.stardustindustry.stardustindustry.energy.TerminalGeometry;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the cables leaving one terminal mount, and the loose end being pulled.
 *
 * <h2>Which mount draws which cable</h2>
 * <p>A cable is stored on <b>both</b> racks it joins, so each rack sees its own
 * half. Drawing from both would paint every wire twice — doubled brightness and
 * artefacts where the two copies overlap. Each rack therefore draws a cable only
 * when it holds the cable's <b>smaller endpoint</b> ({@code a}). The comparison is
 * the connection's own total order, so the owning side is stable across reloads and
 * does not depend on which end the player happened to pull from.</p>
 *
 * <h2>Why a solid tube and not lines</h2>
 * <p>The first version drew each cable as a ring of thin {@code RenderType.lines()}
 * segments, on the theory that a few parallel lines would read as a tube. It does
 * not, for a structural reason rather than a stylistic one: <b>GL line primitives
 * have no thickness model</b>. Vanilla's {@code lines()} state shard asks the
 * driver for a wide line, and a wide line is made by stretching the segment in
 * screen space, so the result is a set of separate streaks whose apparent width
 * depends on the driver and the window size — and whose corners break at every
 * bend. That is exactly what a bundle of fine wires looks like, which is the
 * opposite of what a cable should look like.</p>
 *
 * <p>A cable is a physical object, so it is drawn as one: a closed prism swept
 * along the hanging curve. Thickness is real geometry in block units, so a 50 A
 * and a 5 000 A cable differ by the same ratio at every distance and resolution,
 * and the surface lights and occludes like any other solid (§6.8 asks for
 * "readable at a glance"; only geometry delivers that).</p>
 *
 * <h2>Appearance</h2>
 * <ul>
 *   <li><b>Radius</b> follows the current class, spread evenly over the five
 *       classes.</li>
 *   <li><b>Colour</b> is the colour stored on the cable: black until the player
 *       dyes it (§6.8). It is cosmetic and never affects the simulation.</li>
 * </ul>
 *
 * <h2>Culling</h2>
 * <p>A block-entity renderer is normally culled against its own one-block box, and
 * a cable runs many blocks away. Left alone, a long cable would pop out of
 * existence as soon as its rack left the frustum. The renderer therefore reports
 * {@link #shouldRenderOffScreen()} and grows its culling box to cover every cable
 * that leaves it.</p>
 */
public class CableRenderer implements BlockEntityRenderer<TerminalMountBlockEntity> {

    /**
     * The single instance. A block entity type maps to exactly one renderer
     * (registering a second replaces the first), so cables are drawn from inside
     * {@code TerminalMountRenderer}, which delegates here. The instance holds no
     * per-rack state, so sharing it is safe.
     */
    private static final CableRenderer INSTANCE = new CableRenderer(null);

    /** The shared renderer, for the terminal renderer to call. */
    public static CableRenderer instance() {
        return INSTANCE;
    }

    /**
     * How many faces go around the tube.
     *
     * <p>Twelve reads as a round wire at arm's length. Eight was tried first and
     * showed visible facets along the silhouette — a cable looked like a rolled
     * strip of metal rather than a cylinder. The cost is negligible: a cable is a
     * handful of quads per segment.</p>
     */
    private static final int TUBE_SIDES = 12;

    /**
     * The sheath/strand textures, one per product family.
     *
     * <p>Real cable is not a flat colour: bare conductor shows the twist of its
     * strands, and an insulated sheath carries printing — ratings, maker's marks,
     * metre runs. A single flat tube reads as a plastic pipe, which is exactly what
     * a cable must not look like.</p>
     *
     * <p>The textures are <b>greyscale</b> on purpose. Colour is multiplied in from
     * the vertex (the cable's paint, or its natural conductor/sheath tone), so one
     * texture serves every colour and every ampacity: a red 500 A and a grey 50 A
     * share the same weave.</p>
     */
    private static final ResourceLocation TEX_BARE =
            ResourceLocation.fromNamespaceAndPath("stardustindustry", "textures/block/cable_bare.png");
    private static final ResourceLocation TEX_CLASS_1 =
            ResourceLocation.fromNamespaceAndPath("stardustindustry", "textures/block/cable_class_1.png");
    private static final ResourceLocation TEX_CLASS_2 =
            ResourceLocation.fromNamespaceAndPath("stardustindustry", "textures/block/cable_class_2.png");
    private static final ResourceLocation TEX_ACSR =
            ResourceLocation.fromNamespaceAndPath("stardustindustry", "textures/block/cable_acsr.png");

    /**
     * How many blocks of cable one vertical texture repeat covers.
     *
     * <p>Two blocks is a compromise: a short jumper still shows a repeat, a long
     * aerial run does not turn into moiré, and the printed marks on a sheath recur
     * at a scale a player can read while standing next to the wire.</p>
     */
    private static final float TEXTURE_REPEAT_BLOCKS = 2.0f;

    /**
     * One {@code entitySolid} render type per texture, cached.
     *
     * <p>{@code entitySolid} is vanilla's lit, culled, opaque entity pipeline and
     * already uses the {@code NEW_ENTITY} format this renderer writes. Building our
     * own type would mean re-deriving the shader, lightmap and overlay states for
     * no gain; the cache exists only because the factory allocates a new object on
     * every call.</p>
     */
    private static final java.util.Map<ResourceLocation, RenderType> TYPES = new java.util.HashMap<>();

    private static RenderType typeFor(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, RenderType::entitySolid);
    }

    /** The texture a cable is drawn with: strand twist when bare, sheath print when insulated. */
    private static ResourceLocation textureFor(CableAmpacity ampacity, CableInsulation insulation) {
        if (ampacity != null && ampacity.material()
                == com.stardustindustry.stardustindustry.energy.CableMaterial.ACSR) {
            return TEX_ACSR;
        }
        if (insulation == CableInsulation.CLASS_2) {
            return TEX_CLASS_2;
        }
        if (insulation == CableInsulation.CLASS_1) {
            return TEX_CLASS_1;
        }
        return TEX_BARE;
    }

    public CableRenderer(BlockEntityRendererProvider.Context context) {
        // Nothing to build: the buffer is fetched per frame.
    }

    @Override
    public void render(TerminalMountBlockEntity rack, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = rack.getLevel();
        if (level == null) {
            return;
        }
        pose.pushPose();
        // Anchors are absolute world coordinates while the pose is already at the
        // block origin, so shift back to world zero and draw absolutely.
        pose.translate(-rack.getBlockPos().getX(), -rack.getBlockPos().getY(), -rack.getBlockPos().getZ());
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();

        for (CableConnection cable : rack.cables()) {
            if (!cable.a().pos().equals(rack.getBlockPos())) {
                // The other end owns this cable's geometry.
                continue;
            }
            drawCable(level, matrix, normal, buffers, packedLight, cable);
        }
        drawLooseEnd(level, matrix, normal, buffers, packedLight, rack);
        pose.popPose();
    }

    /**
     * Draws the dangling end the local player is currently pulling.
     *
     * <p>The pending end is client-local ({@code PendingCable}), so it cannot be
     * drawn from block-entity data: whoever holds it draws it. It hangs from the
     * starting terminal to wherever the player is looking, using the same sag
     * curve as a real cable, so the preview shows what will be built.</p>
     */
    private void drawLooseEnd(Level level, Matrix4f matrix, Matrix3f normal, MultiBufferSource buffers,
                              int packedLight, TerminalMountBlockEntity rack) {
        TerminalEndpoint from = com.stardustindustry.stardustindustry.energy.PendingCable.from();
        if (from == null || !from.pos().equals(rack.getBlockPos())) {
            return;
        }
        Vector3f start = CableService.anchorOf(level, from);
        if (start == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // Aim at where the player is looking, two blocks out: close enough to read
        // as "the end is in my hand", far enough to show the sag.
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 eye = minecraft.player.getEyePosition(partialTick);
        Vec3 look = minecraft.player.getViewVector(partialTick);
        Vector3f end = new Vector3f(
                (float) (eye.x + look.x * 2.0),
                (float) (eye.y + look.y * 2.0),
                (float) (eye.z + look.z * 2.0));
        // The loose end takes its look from the cable in hand, so the player can
        // see which cable they are about to lay before committing. A new cable is
        // unpainted: bare conductor shows its metal, insulated shows sheath grey.
        CableAmpacity ampacity = CableAmpacity.A50;
        CableInsulation insulation = CableInsulation.BARE;
        float sagMultiplier = 1.0f;
        var held = com.stardustindustry.stardustindustry.energy.PendingCable.heldCable(minecraft.player);
        if (held != null) {
            ampacity = held.ampacity();
            insulation = held.insulation();
            sagMultiplier = ampacity.material().sagMultiplier();
        }
        drawTube(buffers.getBuffer(typeFor(textureFor(ampacity, insulation))), matrix, normal,
                start, end, radiusOf(ampacity), CableColor.naturalColor(ampacity, insulation),
                sagMultiplier, packedLight);
    }

    private void drawCable(Level level, Matrix4f matrix, Matrix3f normal, MultiBufferSource buffers,
                           int packedLight, CableConnection cable) {
        Vector3f from = CableService.anchorOf(level, cable.a());
        Vector3f to = CableService.anchorOf(level, cable.b());
        if (from == null || to == null) {
            // One end has gone, or its chunk is not loaded on this client. The
            // server decides whether the cable still exists; drawing nothing here
            // is the honest answer for a cable we cannot see both ends of.
            return;
        }
        // The colour to draw is the cable's paint — or, if nobody painted it, its
        // natural appearance: the metal for a bare wire, sheath grey for insulated.
        // A bare wire has no sheath to paint, so even a stale save that carries a
        // dye colour for it is ignored (§6.8).
        int rgb = cable.color().renderedColor(cable.ampacity(), cable.insulation());
        VertexConsumer tube = buffers.getBuffer(typeFor(textureFor(cable.ampacity(), cable.insulation())));
        drawTube(tube, matrix, normal, from, to, radiusOf(cable.ampacity()), rgb,
                cable.sagMultiplier(), packedLight);
    }

    /**
     * Radius of a cable, from its <b>ampacity</b> rather than its position in the
     * enum order.
     *
     * <h2>Why current, not index</h2>
     * <p>The first version spread the radius evenly over the enum's order. That was
     * fine while every class was copper and every step was about 3x, but it breaks
     * as soon as a class exists for a different reason: an 800 A ACSR line would
     * have been drawn <b>thinner than a 500 A copper one</b>, purely because it
     * sits later in the list. A player reads thickness as "how much current", so
     * thickness must follow current.</p>
     *
     * <h2>Why a fourth root, anchored on real diameters</h2>
     * <p>Real figures: a 50 A copper conductor is about 5 mm across, a 500 A one
     * about 16 mm, a 5 000 A busbar about 50 mm (1 block = 1 m, so 0.005–0.05
     * blocks). Drawn to scale those are a small fraction of a pixel and vanish; the
     * game has to magnify them. Magnifying everything equally would make the thin
     * classes vanish anyway while turning the thick ones into pipes, so the
     * magnification <b>decreases with size</b>: the same compression a root gives.</p>
     *
     * <p>The cube root (the first attempt) still pushed the 5 000 A class to 0.232
     * blocks — a pipe, not a wire. A fourth root keeps the 50 A class exactly where
     * it was and compresses the top end instead, which is the only place the eye
     * complained: diameters now run 0.05 → 0.158 blocks, still monotonic and still
     * clearly separable class by class, but no class reads as a barrel.</p>
     *
     * <p>Anchor {@code 0.05 blocks} is deliberate: at 16 px/m that is under a pixel
     * of texture, but a cable is <b>geometry</b>, not a texture, so what matters is
     * its screen size at viewing distance. 0.05 blocks reads at 5–10 blocks'
     * range.</p>
     *
     * <p>A null class can only come from a record built by a stale client; it is
     * drawn as the thinnest rather than throwing in the render loop.</p>
     */
    private static final float BASE_DIAMETER = 0.05f;

    private static float radiusOf(CableAmpacity ampacity) {
        int amps = ampacity == null ? CableAmpacity.A50.amps() : ampacity.amps();
        int baseAmps = CableAmpacity.A50.amps();
        double diameter = BASE_DIAMETER * Math.pow((double) amps / baseAmps, 0.25);
        return (float) (diameter * 0.5);
    }

    /**
     * Sweeps a closed tube along the hanging curve.
     *
     * <p>Each span between two samples of the curve becomes a ring of quads. One
     * shared perpendicular frame is used for the whole cable rather than a
     * per-segment one: a per-segment frame makes the tube twist at every joint and
     * visibly pinch where the sag steepens. A single frame only fails when the run
     * is exactly parallel to the chosen up axis (a perfectly vertical cable), which
     * the fallback handles.</p>
     *
     * <p>Both ends are capped, so a cable seen head-on does not show a hole.</p>
     *
     * <p>{@code sagMultiplier} is the conductor's weight: steel-cored aluminium is
     * far lighter than copper and is strung harder, so it hangs straighter (§6.1).</p>
     */
    private static void drawTube(VertexConsumer tube, Matrix4f matrix, Matrix3f normalMatrix,
                                 Vector3f from, Vector3f to, float radius, int rgb,
                                 float sagMultiplier, int packedLight) {
        float[] channels = {
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f
        };

        Vector3f run = new Vector3f(to).sub(from);
        Vector3f axis = new Vector3f(run).normalize();
        Vector3f side = new Vector3f(0f, 1f, 0f).cross(axis);
        if (side.lengthSquared() < 1.0e-6f) {
            side.set(1f, 0f, 0f).cross(axis);
        }
        side.normalize();
        Vector3f other = new Vector3f(axis).cross(side).normalize();

        // Ring offsets around the axis, in the frame's own coordinates.
        Vector3f[] offsets = new Vector3f[TUBE_SIDES];
        for (int i = 0; i < TUBE_SIDES; i++) {
            float angle = (float) (2.0 * Math.PI * i / TUBE_SIDES);
            offsets[i] = new Vector3f(side).mul((float) Math.cos(angle) * radius)
                    .fma((float) Math.sin(angle) * radius, other);
        }

        Vector3f startCentre = TerminalGeometry.pointOnCable(from, to, 0f, sagMultiplier);
        Vector3f[] previousRing = ring(startCentre, offsets);
        Vector3f previousCentre = startCentre;
        // Segments follow the span: a short jumper costs a couple of rings, a long
        // span subdivides until the curve is smooth, capped so a very long run
        // cannot explode the vertex count. Must match the picker's count.
        int segments = TerminalGeometry.sagSegmentsFor(from, to);
        // v advances with distance along the wire, not with the segment index: a
        // long span and a short one then share the same printed scale, so the
        // sheath marks do not stretch on long runs.
        float vPrevious = 0f;
        for (int s = 1; s <= segments; s++) {
            Vector3f centre = TerminalGeometry.pointOnCable(from, to, (float) s / segments, sagMultiplier);
            Vector3f[] currentRing = ring(centre, offsets);
            float vCurrent = vPrevious + centre.distance(previousCentre) / TEXTURE_REPEAT_BLOCKS;
            for (int i = 0; i < TUBE_SIDES; i++) {
                int j = (i + 1) % TUBE_SIDES;
                // Outward normal of this wall: the ring offset points away from
                // the cable's centre line by construction.
                Vector3f faceNormal = new Vector3f(offsets[i]).add(offsets[j]).normalize();
                quad(tube, matrix, normalMatrix, previousRing[i], previousRing[j], currentRing[j],
                        currentRing[i], faceNormal, channels,
                        u(i), vPrevious, u(j), vPrevious, u(j), vCurrent, u(i), vCurrent,
                        packedLight);
            }
            previousCentre = centre;
            previousRing = currentRing;
            vPrevious = vCurrent;
        }

        // Caps, facing out along the run at each end. The cap is a disc; its UV is
        // the sheet's own centre so it reads as the cut end of the same material.
        cap(tube, matrix, normalMatrix, previousCentre, previousRing, new Vector3f(axis), channels,
                packedLight);
        cap(tube, matrix, normalMatrix, startCentre, ring(startCentre, offsets),
                new Vector3f(axis).negate(), channels, packedLight);
    }

    /** Horizontal texture coordinate at ring index {@code i}, wrapping once per tube. */
    private static float u(int i) {
        return (float) i / TUBE_SIDES;
    }

    /** One ring of points: the centre offset in every tube direction. */
    private static Vector3f[] ring(Vector3f centre, Vector3f[] offsets) {
        Vector3f[] result = new Vector3f[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            result[i] = new Vector3f(centre).add(offsets[i]);
        }
        return result;
    }

    /**
     * A flat disc closing one end of the tube, wound so the visible face points
     * along {@code facing}.
     */
    private static void cap(VertexConsumer tube, Matrix4f matrix, Matrix3f normalMatrix, Vector3f centre,
                            Vector3f[] ring, Vector3f facing, float[] rgb, int packedLight) {
        for (int i = 0; i < ring.length; i++) {
            Vector3f a = ring[i];
            Vector3f b = ring[(i + 1) % ring.length];
            // A fan slice as two triangles sharing an edge, expressed as a quad
            // with a degenerate fourth corner so one writer serves both shapes.
            quad(tube, matrix, normalMatrix, centre, a, b, b, facing, rgb,
                    0.5f, 0.5f, u(i), 0.0f, u(i + 1), 0.0f, u(i + 1), 0.0f, packedLight);
        }
    }

    /**
     * One quad, counter-clockwise seen from outside.
     *
     * <p>If {@code c} and {@code d} coincide the quad collapses to a triangle,
     * which is how the end caps are written.</p>
     *
     * <p>The matrix is applied inside {@link #vertex}: vertices are given in world
     * space because the pose was shifted to the world origin, so no per-quad matrix
     * parameter is needed here.</p>
     */
    private static void quad(VertexConsumer tube, Matrix4f matrix, Matrix3f normalMatrix,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             Vector3f normal, float[] rgb,
                             float ua, float va, float ub, float vb, float uc, float vc,
                             float ud, float vd, int packedLight) {
        Vector3f turned = new Vector3f(normal).mul(normalMatrix).normalize();
        vertex(tube, matrix, a, turned, rgb, ua, va, packedLight);
        vertex(tube, matrix, b, turned, rgb, ub, vb, packedLight);
        vertex(tube, matrix, c, turned, rgb, uc, vc, packedLight);
        vertex(tube, matrix, d, turned, rgb, ud, vd, packedLight);
    }

    /**
     * One vertex: position, colour, UV, lightmap and normal.
     *
     * <p>The format is {@code NEW_ENTITY} (what {@code entitySolid} expects), so the
     * overlay layer must be written too. {@code packedLight} is the <b>rack's</b>
     * light, sampled once per block entity rather than per vertex: a cable is a
     * thin curved object crossing cells, and sampling the lightmap at each vertex
     * makes it shimmer along its length as neighbouring cells differ.</p>
     */
    private static void vertex(VertexConsumer tube, Matrix4f matrix, Vector3f position, Vector3f normal,
                               float[] rgb, float u, float v, int packedLight) {
        tube.addVertex(matrix, position.x, position.y, position.z)
                .setColor(rgb[0], rgb[1], rgb[2], 1.0f)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normal.x, normal.y, normal.z);
    }

    @Override
    public boolean shouldRenderOffScreen(TerminalMountBlockEntity rack) {
        // Cables leave the block; the mount must be drawn even when its own cell is
        // just outside the frustum.
        return true;
    }

    /**
     * The culling box: the rack's own block plus every cable end that leaves it.
     *
     * <p>Without this the box is one block and a cable running 30 blocks away is
     * culled the moment the rack edges off screen — which looks exactly like the
     * cable being deleted.</p>
     */
    @Override
    public AABB getRenderBoundingBox(TerminalMountBlockEntity rack) {
        AABB box = new AABB(rack.getBlockPos()).inflate(1.0);
        Level level = rack.getLevel();
        if (level == null) {
            return box;
        }
        Collection<CableConnection> cables = rack.cables();
        for (CableConnection cable : cables) {
            Vector3f from = CableService.anchorOf(level, cable.a());
            Vector3f to = CableService.anchorOf(level, cable.b());
            if (from != null) {
                box = box.minmax(new AABB(from.x, from.y, from.z, from.x, from.y, from.z));
            }
            if (to != null) {
                box = box.minmax(new AABB(to.x, to.y, to.z, to.x, to.y, to.z));
            }
        }
        // The sag drops below the straight line, so grow downward too. The loose
        // end can also reach a player's eye, which is at most a few blocks out.
        return box.expandTowards(0, -(TerminalGeometry.MAX_SAG + 1.0), 0).inflate(4.0);
    }
}
