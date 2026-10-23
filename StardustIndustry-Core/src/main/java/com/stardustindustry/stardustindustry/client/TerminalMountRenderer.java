package com.stardustindustry.stardustindustry.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.TerminalGrid;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws one terminal per occupied cell of a {@link TerminalMountBlockEntity}.
 *
 * <h2>Why a renderer and not a blockstate</h2>
 * <p>Occupancy is a set of cells: a 4x4 rack has 2^16 patterns, far past what
 * blockstates can express. The plate model therefore draws the <em>empty</em>
 * rack, and this renderer draws the terminals on top from block entity data.</p>
 *
 * <h2>The model</h2>
 * <p>A terminal is an OBJ stand-off insulator (see
 * {@code models/block/terminal_<tier>.obj}), one per voltage tier, so the ceramic
 * body and metal cap differ per tier through the material library.</p>
 *
 * <h2>Where its pose comes from</h2>
 * <p>The OBJ is authored for a north-facing rack in block units (16 per block),
 * with its <b>own origin at the centre of its base</b>: local {@code (0,0,0)} is
 * the foot, the post grows along {@code +z} (out of the plate, towards the
 * player), and the cross-section is symmetric about {@code x = y = 0}. It does
 * <b>not</b> bake the 7/16 standoff into its vertices - this renderer supplies
 * that, together with the cell position and the facing rotation.</p>
 *
 * <p>Offset, rotation, scale and origin are <b>not</b> constants here: they are
 * read per tier from {@code models/terminal/<tier>.json} through
 * {@link TerminalModelConfigs}, so the artist can re-pose a model with a file
 * edit and {@code F3+T}. See {@code docs/obj-model-manual.md}.</p>
 *
 * <h2>Size: the OBJ decides</h2>
 * <p>This renderer applies no fitting at all. The OBJ's vertices are converted
 * to blocks by the model's own {@code objUnitsPerBlock} (16 for vanilla-style
 * block units, 1000 for the millimetres this project models in), then multiplied
 * by the config's {@code scale} ({@code 1.0} = as drawn, {@code 0.5} = half). A
 * terminal is therefore exactly as large as the artist drew it. Keeping a
 * terminal inside its cell is the model's job, and the manual gives the cell
 * size per tier so it can be done.</p>
 *
 * <p>Earlier versions fitted the OBJ to the cell (scale = cell / model width,
 * clamped by the available depth). That made the result depend on the model's
 * <em>proportions</em> in a way nobody could predict: the placeholder's 5.6-wide
 * 10-long post got shrunk until its length fit, then looked like a speck in a
 * 4x4 rack. Removing the fit is both simpler and what the project wants - the
 * artist authors the real size.</p>
 *
 * <p>Parsed models are cached per tier: baking on every frame would hammer the
 * model manager for no benefit. The cache is dropped on resource reload, since
 * a re-exported OBJ would otherwise keep showing the old geometry.</p>
 */
// Models must be registered on the MOD event bus, which is what
// @EventBusSubscriber gives us. The class was previously missing this
// annotation, so registerModels() below was never called: the OBJs stayed
// unbaked and every terminal was skipped at render time even though the block
// entity recorded it. That is this renderer's only event, hence the annotation
// lives here rather than in StardustIndustryClient.
@net.neoforged.fml.common.EventBusSubscriber(
        modid = com.stardustindustry.stardustindustry.StardustIndustry.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public class TerminalMountRenderer implements BlockEntityRenderer<TerminalMountBlockEntity> {

    /** Baked OBJ per terminal style, filled lazily on the render thread. */
    private static final Map<TerminalStyle, BakedModel> CACHE = new ConcurrentHashMap<>();

    /** Styles already reported as having no baked model, so each warns once. */
    private static final java.util.Set<TerminalStyle> MISSING_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public TerminalMountRenderer(BlockEntityRendererProvider.Context context) {
        // Models are looked up lazily on the first frame.
    }

    @Override
    public void render(TerminalMountBlockEntity mount, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        TerminalGrid grid = mount.grid();
        int cellCount = grid.cellCount();
        if (cellCount <= 0) {
            return;
        }

        BlockState state = mount.getBlockState();
        Direction facing = state.getValue(TerminalMountBlock.FACING);

        // The rack's block models are all one shape, authored for the north face
        // and pre-rotated about the block centre. The terminals must take the
        // same trip or they end up on the wrong wall, so each cell point is
        // turned here rather than by a pose rotation: that keeps the scale about
        // the cell, not the block.
        //
        // Geometry and pose come from the model config
        // (assets/stardustindustry/models/terminal/<style>.json), keyed by the
        // TERMINAL STYLE on the cell, not by the rack's tier: several terminals can
        // exist at one voltage, and each owns its own offset / origin / scale.
        //
        // The renderer does NOT fit the OBJ to the cell. The size of a terminal is
        // whatever the artist drew, in the file's own unit (declared by
        // objUnitsPerBlock: 16 for block units, 1000 for millimetres); the vertices
        // are converted by that once, and the config's "scale" multiplies it
        // (1.0 = as drawn).
        //
        // Automatic fitting was tried and removed on the project owner's call:
        // "keep the model at its authored size, let the artist decide". It is the
        // more direct contract, and it also fixes a real bug. Fitting multiplied
        // by cellWidth / objWidth, so a model whose cross-section was small next
        // to its length (the placeholder: 5.6 across, 10 long) got shrunk until
        // its DEPTH also fit the cell, and a 4x4 rack turned each post into a
        // sub-pixel speck. Nothing in the renderer can know how tall an artist
        // wants a 0.2-block-wide terminal to be - only the OBJ can say that.
        //
        // Consequence for artists: "does it fit the cell" is now their job. The
        // manual lists the cell size per tier and the depth available from the
        // plate (1/16 block) so a post can be authored to sit inside it.
        // No scale-to-fit; a missing model only means nothing is drawn.

        VertexConsumer cutout = buffers.getBuffer(RenderType.cutout());
        for (int cell = 0; cell < cellCount; cell++) {
            TerminalStyle style = mount.terminalStyle(cell);
            if (style == null) {
                continue;
            }
            BakedModel model = modelFor(style);
            if (model == null) {
                // A rack can hold a bolted terminal that never appears if its OBJ
                // failed to bake. Say so once per style rather than every frame,
                // otherwise the log turns into a wall of the same line.
                if (MISSING_LOGGED.add(style)) {
                    org.slf4j.LoggerFactory.getLogger(TerminalMountRenderer.class)
                            .warn("Terminal model stardustindustry:block/terminal_{} did not bake; "
                                    + "terminals of this style will be invisible.", style.id());
                }
                continue;
            }

            TerminalModelConfig cfg = TerminalModelConfigs.get(style.model());
            // Pose numbers are in BLOCKS (metres), which is already the unit the
            // PoseStack works in, so they are used as-is.
            //
            // The OBJ's vertices are in whatever unit the exporter writes, and the
            // artist declares that unit in objUnitsPerBlock (16 = vanilla block
            // model convention, 1000 = millimetres). Converting by that number is
            // what lets a post modelled 500 mm up be written as anchor y: 0.5
            // straight from the tool, with no 1/16 factor for the artist to know
            // about. "scale" stays a plain multiplier on top of it: a value of 1.0
            // must mean "no change", never a unit conversion.
            float scale = (1.0f / cfg.objUnitsPerBlock()) * cfg.scale();
            float originX = cfg.originX();
            float originY = cfg.originY();
            float originZ = cfg.originZ();
            float[] offset = new float[] {cfg.offsetX(), cfg.offsetY(), cfg.offsetZ()};

            // Cell centre in grid fractions, left to right and bottom up as seen by
            // a player looking at the face. The geometry itself lives in
            // TerminalGeometry so this renderer, the cable renderer and the
            // interaction code all agree on where a terminal actually is; a second
            // copy here is how the drawn post and the cable foot drift apart.
            Vector3f at = com.stardustindustry.stardustindustry.energy.TerminalGeometry
                    .footLocal(facing, grid, cell);

            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            // The OBJ is authored for the north face: its own origin is the BASE
            // CENTRE (x = y = 0, z = 0), its axis grows along +z, and its
            // cross-section is measured from that axis. NeoForge's OBJ loader
            // treats an OBJ as a full block and centres it, but it does that
            // identically for the icon and for this renderer, so the contract
            // here is only "local (0,0,0) is the foot".
            //
            // The OBJ must therefore NOT bake the 7/16 standoff into its own
            // vertices: the cell centre already sits 7/16 of a block out from the
            // block centre, so a vertex-level z = 7 would be added twice and push
            // the terminal almost a full block off the plate. See
            // GenTerminalObjPlaceholder for the authored shape.
            pose.mulPose(faceRotation(facing));
            // The offset is applied AFTER the face rotation, so its three numbers
            // always mean the same thing to the artist: x = right along the face,
            // y = up along the face, z = out of the plate into the room. Applying
            // it before the rotation (in the north-authored frame, then carried by
            // the basis) was what made "offset.z += 1" push west and east plates
            // sideways and floors/ceilings vertically — the offset appeared not to
            // work at all, and the artist had to guess a different axis per face.
            pose.translate(offset[0], offset[1], offset[2]);
            // The config origin is a pivot INSIDE the model, expressed in model
            // units around the model's own origin (0,0 = the base centre, +z = up
            // the axis), so it is applied BEFORE the config scale and rotation -
            // that is what lets the artist spin a piece about its foot.
            pose.translate(-originX, -originY, -originZ);
            pose.mulPose(cfg.rotation());
            pose.scale(scale, scale, scale);
            if (cfg.mirrored()) {
                // A negative scale alone would turn the model inside out. Flipping
                // the winding back gives a real mirror that still lights correctly,
                // which is what the artist needs when the OBJ's axis points the
                // other way. The mirror plane sits at the model origin plus
                // mirrorOffset, so "mirror inside the block" stays tunable.
                float m = cfg.mirrorOffset();
                switch (cfg.mirror()) {
                    case "x" -> {
                        pose.translate(m, 0f, 0f);
                        pose.scale(-1f, 1f, 1f);
                        flipNormals(pose, new Vector3f(-1f, 1f, 1f));
                    }
                    case "y" -> {
                        pose.translate(0f, m, 0f);
                        pose.scale(1f, -1f, 1f);
                        flipNormals(pose, new Vector3f(1f, -1f, 1f));
                    }
                    case "z" -> {
                        pose.translate(0f, 0f, m);
                        pose.scale(1f, 1f, -1f);
                        flipNormals(pose, new Vector3f(1f, 1f, -1f));
                    }
                    default -> { }
                }
            }
            renderShaded(pose, cutout, state, model, packedLight, packedOverlay, mount.getLevel());
            pose.popPose();        }

        // Cables last, so they are drawn over the posts rather than through their
        // outlines. The rack holds its own half of every cable, and the cable
        // renderer decides which half owns the geometry.
        CableRenderer.instance().render(mount, partialTick, pose, buffers, packedLight, packedOverlay);
    }

    /**
     * Draws an OBJ with vanilla-style <b>directional shading</b>.
     *
     * <h2>Why not {@code ModelBlockRenderer#renderModel}</h2>
     * <p>That method looks like "draw this model", so it is the obvious call for a
     * block entity renderer — and it is what this renderer used. But it applies
     * <b>no face shading at all</b>: it walks the quads and hands each one to
     * {@code consumer.putBulkData(..., 1.0F, 1.0F, 1.0F, ...)}. The brightness
     * that makes a block's top brighter than its sides lives in
     * {@code ClientLevel#getShade(direction, isShade)} and is multiplied in by
     * {@code putQuadData} / {@code renderQuadList}, which {@code renderModel}
     * bypasses.</p>
     *
     * <p>So every terminal came out as one flat colour — not "the texture is
     * wrong", as it first looks, and not a lighting bug in the chunk mesher. A
     * BER that uses {@code renderModel} simply renders unshaded.</p>
     *
     * <h2>What this does instead</h2>
     * <p>Walks the model's quads itself, asks the level for the same shade factor
     * the chunk mesher would use for that quad's facing, and passes it as the
     * per-vertex brightness array of {@code putBulkData}. The result is
     * indistinguishable from a real block: {@code up} 1.0, north/south 0.8,
     * east/west 0.6, {@code down} 0.5.</p>
     *
     * <p>Only directional shading is reproduced, not ambient occlusion. AO is a
     * per-vertex term that depends on neighbouring block geometry; a terminal
     * standing in open air has nothing to occlude it, and computing AO from the
     * rack's own block would darken the plate it is bolted to.</p>
     */
    private static void renderShaded(PoseStack pose, VertexConsumer consumer, BlockState state,
                                     BakedModel model, int packedLight, int packedOverlay,
                                     net.minecraft.world.level.Level level) {
        var clientLevel = level instanceof net.minecraft.client.multiplayer.ClientLevel c ? c : null;
        PoseStack.Pose entry = pose.last();
        // One seeded RandomSource for the whole model, exactly as renderModel does,
        // so the quad order matches what the same model would give elsewhere.
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            drawQuads(entry, consumer, model.getQuads(state, direction, random, ModelData.EMPTY, null),
                    clientLevel, packedLight, packedOverlay);
        }
        // Unculled quads. An OBJ usually lands here, because its faces are not
        // axis-aligned block faces; each quad carries its own direction, so the
        // shade is read per quad rather than per bucket.
        random.setSeed(42L);
        drawQuads(entry, consumer, model.getQuads(state, null, random, ModelData.EMPTY, null),
                clientLevel, packedLight, packedOverlay);
    }

    /**
     * Writes one bucket of quads, each shaded by its own facing.
     *
     * <p>Shade is a plain multiplier on the vertex colour, which is where vanilla
     * puts it. Tint stays white: a terminal OBJ is not a biome-tinted block.</p>
     */
    private static void drawQuads(PoseStack.Pose entry, VertexConsumer consumer,
                                  java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
                                  net.minecraft.client.multiplayer.ClientLevel level,
                                  int packedLight, int packedOverlay) {
        for (net.minecraft.client.renderer.block.model.BakedQuad quad : quads) {
            float shade = level == null ? 1.0f : level.getShade(quad.getDirection(), quad.isShade());
            float[] brightness = {shade, shade, shade, shade};
            int[] lightmap = {packedLight, packedLight, packedLight, packedLight};
            consumer.putBulkData(entry, quad, brightness, 1.0f, 1.0f, 1.0f, 1.0f,
                    lightmap, packedOverlay, true);
        }
    }

    /**
     * Flips the normal matrix of a mirrored pose.
     *
     * <p>A mirror has determinant -1, so the transform's normal matrix would flip
     * the winding of every quad and the lighting of the whole model. Negating the
     * mirrored axis of the normal matrix cancels exactly that.</p>
     */
    private static void flipNormals(PoseStack pose, Vector3f axis) {
        org.joml.Matrix3f normal = pose.last().normal();
        normal.set(
                normal.m00() * axis.x, normal.m01() * axis.x, normal.m02() * axis.x,
                normal.m10() * axis.y, normal.m11() * axis.y, normal.m12() * axis.y,
                normal.m20() * axis.z, normal.m21() * axis.z, normal.m22() * axis.z);
    }

    /**
     * Cables leave the block, so the rack's culling box must cover them.
     *
     * <p>One block would cull a long cable the moment the rack edges off screen,
     * which looks exactly like the cable being deleted. The cable renderer's box
     * already grows to the far ends; this delegates to it.</p>
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TerminalMountBlockEntity mount) {
        return CableRenderer.instance().getRenderBoundingBox(mount);
    }

    @Override
    public boolean shouldRenderOffScreen(TerminalMountBlockEntity mount) {
        // The rack itself is one block, but its cables are not.
        return true;
    }

    /**
     * The basis of the grid for {@code facing}, in world space.
     *
     * <p><b>{@code facing} is the side the backplate is bolted to</b> (see
     * {@link TerminalMountBlock#FACING}). The terminals stand <b>out</b> of the
     * plate into open air, the way a post insulator stands off a panel towards the
     * operator, so {@code normal} here is the direction a terminal GROWS, i.e.
     * {@code facing.getOpposite()}. The plate itself occupies the first 1/16 of
     * the block on the {@code facing} side, and the terminal's foot sits on its
     * outer surface, so the post pokes out of the block on the open side.</p>
     *
     * <p>Row 0: world direction that goes <b>right</b> on the face as the player
     * sees it when standing in front of the terminals.<br>
     * Row 1: world direction that goes <b>up</b> on the face.<br>
     * Row 2: the direction the terminal grows.</p>
     *
     * <p>Read off the block from the terminal's side:</p>
     * <ul>
     *   <li>north plate (slab z 0..1): the player stands north looking south, right
     *       is <b>+x</b>, up is +y, terminals grow out towards <b>north (-z)</b>;</li>
     *   <li>south plate (z 15..16): right -x, up +y, grow south (+z);</li>
     *   <li>west plate (x 0..1): right -z, up +y, grow west (-x);</li>
     *   <li>east plate (x 15..16): right +z, up +y, grow east (+x);</li>
     *   <li>up plate (ceiling, y 15..16): seen from below with the player south,
     *       right +x, up +z, grow up (+y);</li>
     *   <li>down plate (floor, y 0..1): right +x, up -z, grow down (-y).</li>
     * </ul>
     *
     * <p>Every row satisfies {@code up × right = normal}, so the basis is a proper
     * rotation for all six facings - which {@link #faceRotation} asserts.</p>
     *
     * <p><b>Note on the right vector.</b> With the terminals growing <em>out</em>
     * of the block, a player facing the plate is looking in the <em>same</em>
     * direction as {@code normal}, not against it. For a viewer looking along
     * {@code +n}, their right hand is {@code up × n}, which is <b>minus</b> the
     * standard {@code n × up} used for the inward-facing case. Flipping only
     * {@code normal} (leaving {@code right} alone) makes the triple
     * left-handed: {@code det = -1}, which {@link #faceRotation} rejects. Flipping
     * {@code right} together with {@code normal} keeps {@code det = +1} and keeps
     * the grid un-mirrored, so column order on the plate stays as the player sees
     * it.</p>
     *
     * <p>The grid's {@code u} axis (column index) runs left to right, which is the
     * <b>opposite</b> of row 0, so a cell sits at {@code + right * (u - 0.5)}.</p>
     */
    // Generated by SolveBasis: for each normal (= growth direction) it picks the
    // axis-aligned up closest to world +y (or -z for floor/ceiling plates) and
    // derives right = up x normal, so right x up == normal holds everywhere and
    // faceRotation's determinant is +1.
    //
    // The plate is the 1/16 slab on the side OPPOSITE the growth direction. The
    // foot is placed at centre + normal*7/16, which lands it 1/16 from that edge,
    // i.e. exactly on the slab's inner surface. Growing onwards along +normal
    // therefore leaves the block through the open face.
    private static final java.util.Map<Direction, Vector3f[]> FACES = java.util.Map.of(
            // Generated by SolveBasis3 from the user's own observation of the running
            // game, NOT from sign algebra. Ground truth: the block models place the
            // plate on the FACING side (north z 0..1, south z 15..16, west x 0..1,
            // east x 15..16, up y 15..16, down y 0..1), so the terminals must leave
            // the plate towards -FACING.getVector() for all six facings.
            //
            // north: plate on -z, room on +z  -> grows +z
            Direction.NORTH, axes(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)),
            // south: plate on +z, room on -z  -> grows -z
            Direction.SOUTH, axes(new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1)),
            // west: plate on -x (model x 0..1), room on +x  -> grows +x
            Direction.WEST, axes(new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0)),
            // east: plate on +x (model x 15..16), room on -x -> grows -x
            Direction.EAST, axes(new Vector3f(0, 0, 1), new Vector3f(0, 1, 0), new Vector3f(-1, 0, 0)),
            // ceiling plate on +y, room below -> grows -y (this row was already right)
            Direction.UP, axes(new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, -1, 0)),
            // floor plate on -y, room above   -> grows +y (this row was already right)
            Direction.DOWN, axes(new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0)));

    private static Vector3f[] axes(Vector3f right, Vector3f up, Vector3f normal) {
        return new Vector3f[] {right, up, normal};
    }

    /**
     * The rotation that takes a model authored facing north and lays it on
     * {@code facing}.
     *
     * <p>It is built from the {@link #FACES} frame, not from hard-coded angles:
     * the authored north frame has right {@code +x}, up {@code +y}, out {@code +z}
     * (the identity), and the target frame is the face's row of the table, so the
     * matrix mapping one onto the other simply has the target triple as its
     * columns:</p>
     *
     * <pre>
     *   columns = (right, up, normal)      i.e.  M * e1 = right
     *                                            M * e2 = up
     *                                            M * e3 = normal
     * </pre>
     *
     * <p>Because {@code right × up = normal} holds for every row, this is a
     * proper rotation (determinant +1) and carries the model's outward axis onto
     * the face while keeping its own right/up on the player's right/up. A model
     * is therefore never drawn mirrored, which is what made the earlier
     * point-flip formulas exchange east and west.</p>
     */
    private static Quaternionf faceRotation(Direction facing) {
        Vector3f[] f = FACES.getOrDefault(facing, FACES.get(Direction.NORTH));
        // Build the rotation that maps the authored north frame onto this face's
        // frame. Both frames are right-handed with (right, up, out) triples, so
        // putting the target triple into the matrix COLUMNS is the rotation:
        //
        //   set(col0 = right, col1 = up, col2 = normal)
        //
        // Every row of FACES satisfies right x up = normal, which makes this a
        // proper rotation (det +1) for all six facings. See the long note on the
        // set() call below for why the earlier 9-argument constructor silently
        // built the transpose instead.
        //
        // The "right" column used to be negated here. Negating one column makes
        // the determinant -1, i.e. a MIRROR - and a quaternion cannot encode a
        // mirror, so setFromNormalized() silently produced some unrelated
        // rotation. That is why the terminals' orientation never matched the
        // table no matter how the table was adjusted.
        Vector3f right = new Vector3f(f[0]);
        Vector3f up = new Vector3f(f[1]);
        Vector3f out = new Vector3f(f[2]);

        // Build the matrix by COLUMNS, via set(col0, col1, col2).
        //
        // This used to call the 9-float constructor with the columns written
        // side by side as if they were rows:
        //
        //     new Matrix3f(right.x, up.x, out.x,
        //                  right.y, up.y, out.y,
        //                  right.z, up.z, out.z)
        //
        // JOML reads those nine floats in ROW-major order (m00, m01, m02 is the
        // first row), and a Matrix3f in JOML transforms column vectors, so the
        // arguments above actually filled the ROWS with (right, up, out). The
        // resulting matrix was therefore the TRANSPOSE of the intended one.
        //
        // Transposing sends M*(0,0,1) to the third ROW instead of the third
        // COLUMN. For north and south the intended matrix is symmetric, so the
        // transpose is identical and everything looked fine; for west, east, up
        // and down it is not, and the OBJ's +z axis came out pointing the wrong
        // way - the posts drilled into the block. Verified by running real JOML
        // (JomlProbe): north/south ok, the other four reversed.
        //
        // set(col0, col1, col2) takes actual column vectors, so the intent is
        // unambiguous and cannot silently transpose.
        org.joml.Matrix3f m = new org.joml.Matrix3f().set(right, up, out);
        if (m.determinant() < 0f) {
            // Fail loudly: a mirrored basis cannot become a rotation, and silently
            // drawing something else is how this stayed wrong for so long.
            throw new IllegalStateException("Terminal facing basis is a mirror for " + facing);
        }
        return new Quaternionf().setFromNormalized(m);
    }

    /**
     * Registers the four terminal OBJs as standalone models to bake.
     *
     * <p>This is what makes the terminals visible at all. NeoForge only bakes
     * models that something references: a blockstate variant or an item model.
     * The terminal OBJs are referenced by <b>neither</b> - the rack's blockstates
     * point at the plate models, and the terminal <em>items</em> are flat 16x16
     * icons - so without this hook {@code models/block/terminal_lv.json} is never
     * baked, {@link #modelFor} gets the missing model back, and every bolted
     * terminal is silently skipped. That is exactly the "it's installed but
     * nothing shows" report.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (TerminalStyle style : TerminalStyle.all()) {
            event.register(ModelResourceLocation.standalone(terminalModelId(style)));
        }
    }

    /** Location of the baked OBJ for {@code style}. */
    private static ResourceLocation terminalModelId(TerminalStyle style) {
        return ResourceLocation.fromNamespaceAndPath("stardustindustry",
                "block/terminal_" + style.model());
    }

    /**
     * Looks the OBJ for {@code style} up, or {@code null} when it failed to load. */
    private static BakedModel modelFor(TerminalStyle style) {
        BakedModel cached = CACHE.get(style);
        if (cached != null) {
            return cached;
        }
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.standalone(terminalModelId(style)));
        if (model == Minecraft.getInstance().getModelManager().getMissingModel()) {
            // Do not cache the miss: a resource reload may bring the model back.
            return null;
        }
        CACHE.put(style, model);
        return model;
    }

    /** Drops the model cache. Called on resource reload so re-exported OBJs are picked up. */
    public static void clearCache() {
        CACHE.clear();
        MISSING_LOGGED.clear();
    }
}
