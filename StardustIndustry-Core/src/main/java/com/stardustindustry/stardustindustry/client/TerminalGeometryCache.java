package com.stardustindustry.stardustindustry.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * GPU-side geometry cache for terminal models.
 *
 * <h2>The problem this exists for</h2>
 * <p>A block entity renderer runs <b>every frame</b>, and everything it pushes
 * through {@code MultiBufferSource} is re-tessellated, re-packed and re-uploaded
 * every time. That is fine for one chest. It is not fine for a factory.</p>
 *
 * <p>At 300 triangles per terminal and 1000 terminals:</p>
 * <pre>
 *   300 000 triangles -&gt; 1 200 000 vertices -&gt; ~36 MB of vertex data
 * </pre>
 * <p>per frame, pushed through the 768 KB shared {@code RenderBuffers} buffer:
 * roughly 49 flush-and-draw cycles every frame, on the main thread, to produce
 * the same bytes as the frame before. Terminals do not animate.</p>
 *
 * <h2>What is cached, and in what space</h2>
 * <p>The unit is one <b>terminal style, in one pose, at one light level</b>,
 * tessellated in <b>local space</b>: the frame the old code built its
 * {@link PoseStack} in for a single cell — origin at the foot, {@code +z} out of
 * the plate. Keying on local space rather than world position is what makes the
 * cache shareable:</p>
 * <ul>
 *   <li>all six rack facings draw the same local geometry — one buffer, not six;</li>
 *   <li>every rack with the same style shares the same buffer;</li>
 *   <li>a 4×4 rack shares one buffer across all sixteen cells.</li>
 * </ul>
 *
 * <h2>Why light is part of the key</h2>
 * <p>A packed lightmap has to end up in the vertices, and vanilla gives no way
 * to swap one vertex attribute between draws without re-uploading the buffer (the
 * format's attribute bindings are fixed when the VBO is first uploaded). The
 * first sketch of this class tried to overwrite UV2 per draw and could not.</p>
 *
 * <p>So light is baked in, and the light value is part of the cache key. That
 * sounds expensive and is not, because of how Minecraft light actually
 * behaves:</p>
 * <ul>
 *   <li>{@code packedLight} carries <b>block light and sky light</b>. Sky light is
 *       the propagation of daylight through the world, and it does <b>not</b>
 *       change as the sun moves — the day/night swing is a shader uniform, not a
 *       vertex term. So nightfall does not invalidate anything.</li>
 *   <li>It changes only when <b>block light</b> changes: a torch placed, a lamp
 *       broken, a door opened. Those are rare events, and they invalidate only
 *       the bucket that shared that light value.</li>
 *   <li>Terminals on the same wall in the same room share a light value, so a
 *       factory wall of 200 terminals is typically <b>one</b> bucket, not 200.</li>
 * </ul>
 *
 * <p>Expected steady-state behaviour: zero rebuilds per frame, and a handful of
 * rebuilds when someone flips a light switch nearby.</p>
 *
 * <h2>Directional shading is baked in</h2>
 * <p>Vanilla's per-facing brightness (up {@code 1.0}, north/south {@code 0.8},
 * east/west {@code 0.6}, down {@code 0.5}) depends only on the quad's facing,
 * which never changes for a static model. It is folded into the vertex colour at
 * build time — which also replaces the reason the old code walked every quad by
 * hand: {@code ModelBlockRenderer} applies no shade at all.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Buffers are OpenGL objects and must be closed on the render thread.
 * {@link #clear()} runs on resource reload and client shutdown. A bounded cache
 * protects against pathological scenes (a terminal lit differently at every
 * position); see {@link #MAX_BUCKETS}.</p>
 */
public final class TerminalGeometryCache {

    /**
     * The vertex format uploaded to the GPU.
     *
     * <p>This <b>is</b> {@link DefaultVertexFormat#NEW_ENTITY}, referenced rather
     * than rebuilt: {@code BufferBuilder} has a fast path gated on
     * {@code format == DefaultVertexFormat.NEW_ENTITY} (an identity comparison,
     * not {@code equals}), so a structurally identical hand-built format would
     * silently take the slow reflective path when tessellating.</p>
     */
    private static final VertexFormat FORMAT = DefaultVertexFormat.NEW_ENTITY;

    /**
     * Upper bound on cached buckets, to keep a pathological scene from eating
     * VRAM: worst case each bucket is one model, and a model is capped by what an
     * artist will actually ship (hundreds of KB). When the cap is hit the cache is
     * cleared wholesale rather than evicted one by one — a light flicker that
     * changes a thousand keys at once would otherwise thrash an LRU, and a full
     * clear costs one rebuild each, same as an eviction storm.
     */
    private static final int MAX_BUCKETS = 512;

    /** Built geometry per style + pose + light. Render thread only. */
    private static final Map<BucketKey, ModelBuffer> BUILT = new HashMap<>();

    /** Reused for stable quad ordering, matching what the block renderer does. */
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * Temporary switch for the "terminals are invisible" investigation.
     *
     * <p>Every branch in this class that can silently draw nothing — an empty
     * quad list, a null shader, a GL error on the draw call — logs once and then
     * stays quiet, so the log stays readable on a factory wall. Remove this
     * block, and its call sites, once the cause is found.</p>
     */
    private static final Set<String> DIAGNOSED = ConcurrentHashMap.newKeySet();

    private static final org.slf4j.Logger DIAG_LOG =
            org.slf4j.LoggerFactory.getLogger(TerminalGeometryCache.class);

    /** Logs {@code message} once per {@code tag}. */
    private static void once(String tag, String message, Object... args) {
        if (DIAGNOSED.add(tag)) {
            DIAG_LOG.info("[terminal-diag] " + message, args);
        }
    }


    /** A neutral pose: vertices are written in the model's own local space. */
    private static final PoseStack.Pose IDENTITY = new PoseStack().last();

    private TerminalGeometryCache() {
    }

    /**
     * Identifies a chunk of cached geometry.
     *
     * @param styleId  which terminal OBJ
     * @param poseHash every pose input that changes the drawn shape (offset,
     *                 rotation, scale, origin, mirror, {@code objUnitsPerBlock}),
     *                 so an edited JSON file invalidates instead of lying
     * @param light    the packed lightmap baked into the vertices; see the class
     *                 note on why this is safe to key on
     */
    public record BucketKey(String styleId, int poseHash, int light) {
    }

    /**
     * One uploaded model plus the texture it was built against.
     *
     * <p>The texture is kept because the render type must match the model's own
     * sprite; storing it here makes a mismatched bind impossible rather than
     * merely unlikely.</p>
     */
    public static final class ModelBuffer {
        private final VertexBuffer vertexBuffer;
        private final RenderType renderType;
        private final int quadCount;

        private ModelBuffer(VertexBuffer vertexBuffer, RenderType renderType, int quadCount) {
            this.vertexBuffer = vertexBuffer;
            this.renderType = renderType;
            this.quadCount = quadCount;
        }

        /**
         * The render type to draw with: {@code entitySolid} for this model's
         * texture.
         *
         * <p>Resolved once at build time rather than per frame. Although
         * {@code RenderType.entitySolid} memoises per texture, it still costs a map
         * lookup and a lambda call per terminal per frame, and a factory has
         * thousands of terminal-frames.</p>
         */
        public RenderType renderType() {
            return this.renderType;
        }

        /** Quads in the buffer; used by tests and the debug overlay. */
        public int quadCount() {
            return this.quadCount;
        }
    }

    /**
     * Returns the buffer for a style+pose+light, building it on first use.
     *
     * <p>Must run on the render thread: {@link VertexBuffer#upload} asserts it. A
     * model that produced no quads returns {@code null} and the caller draws
     * nothing.</p>
     */
    public static ModelBuffer get(String styleId, BlockState state, BakedModel model,
                                  TerminalModelConfig cfg, ClientLevel level, int light) {
        BucketKey key = new BucketKey(styleId, poseHash(cfg), light);
        ModelBuffer existing = BUILT.get(key);
        if (existing != null) {
            return existing;
        }
        if (BUILT.size() >= MAX_BUCKETS) {
            // See MAX_BUCKETS: a wholesale clear beats an eviction storm here.
            clear();
        }
        ModelBuffer built = build(styleId, state, model, level, light);
        if (built != null) {
            BUILT.put(key, built);
        }
        return built;
    }

    /**
     * Draws a run of cached models that share one render type.
     *
     * <h2>Why a batch and not one call per model</h2>
     * <p>The naive version called {@code setupRenderState} and
     * {@code drawWithShader} once per terminal. Both do real work beyond the
     * draw itself — shader, texture, blend, depth, cull, and a full
     * {@code setDefaultUniforms} upload — so a factory wall of a thousand
     * terminals paid for roughly ten thousand GL calls a frame to issue a thousand
     * draws.</p>
     *
     * <p>Within one rack every terminal shares the render type, so the state is
     * set up <b>once</b>. Per model the only values that differ are the
     * model-view matrix and, because {@code setDefaultUniforms} writes them
     * together, the sampler bindings — but the samplers resolve to the same
     * texture for every terminal in the batch, so the upload is unchanged.</p>
     *
     * <h2>Why {@code setDefaultUniforms} is still called per model</h2>
     * <p>It sets <b>well over a dozen</b> uniforms, not just the model-view
     * matrix: {@code ProjMat}, {@code ColorModulator}, fog start/end/colour/shape,
     * glint alpha and the twelve samplers. An earlier version of this method wrote
     * only {@code MODEL_VIEW_MATRIX} and called {@code apply()} directly, which
     * left {@code ProjMat} at whatever the previous draw left behind — the
     * projection matrix is data the shader cannot do without. The uniforms are
     * cheap to set (they are cached struct writes, not GL calls); what is
     * expensive is {@code apply()} and the state setup, and those
     * <b>are</b> hoisted out of the loop.</p>
     *
     * @param models    buffers to draw, all sharing {@code renderType}
     * @param matrices  matching model-view matrices, index for index with
     *                  {@code models}; each already contains the full placement
     */
    public static void drawBatch(List<ModelBuffer> models, List<org.joml.Matrix4f> matrices,
                                 RenderType renderType) {
        if (models.isEmpty()) {
            return;
        }
        renderType.setupRenderState();
        // setupRenderState installs this render type's shader; getShader then
        // returns it resolved. A shader that failed to compile comes back null,
        // in which case there is nothing sensible to draw.
        net.minecraft.client.renderer.ShaderInstance shader =
                com.mojang.blaze3d.systems.RenderSystem.getShader();
        if (shader == null) {
            once("shader-null",
                    "RENDER TYPE HAS NO SHADER for {} - {} models skipped",
                    renderType, models.size());
            VertexBuffer.unbind();
            renderType.clearRenderState();
            return;
        }
        com.mojang.blaze3d.shaders.Uniform fogModelMatrix = shader.getUniform("FogModelMat");
        if (fogModelMatrix == null) {
            once("fog-model-matrix-null",
                    "shader {} has no FogModelMat uniform; terminal fog distance will be incorrect",
                    shader.getName());
        }
        once("draw:" + renderType,
                "drawBatch type={} models={} firstQuads={} shader={}",
                renderType, models.size(), models.get(0).quadCount(), shader.getName());

        org.joml.Matrix4f projection = com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
        // The camera rotation lives HERE, not in the block entity's PoseStack.
        //
        // A BER's pose only carries the block's offset from the camera; the view
        // rotation is RenderSystem's model-view matrix. Passing the pose straight
        // in as ModelViewMat therefore leaves the geometry un-rotated: it is drawn
        // correctly, with no GL error, in a frame that has nothing to do with where
        // the player is looking. That is exactly "the terminals are invisible" —
        // they are somewhere off screen.
        //
        // The old per-frame path did not have this problem because it handed the
        // pose to the MultiBufferSource consumer, baking it into the vertices;
        // BufferUploader then multiplied by this same model-view matrix at flush
        // time. Copy the reference too: getModelViewMatrix returns the live object.
        org.joml.Matrix4f view = new org.joml.Matrix4f(
                com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        once("matrix",
                "view=[m00={} m02={} m20={} m22={} t=({},{},{})] poseT=({},{},{}) projM00={} projM11={}",
                view.m00(), view.m02(), view.m20(), view.m22(),
                view.m30(), view.m31(), view.m32(),
                matrices.get(0).m30(), matrices.get(0).m31(), matrices.get(0).m32(),
                projection.m00(), projection.m11());
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        for (int i = 0; i < models.size(); i++) {
            ModelBuffer model = models.get(i);
            // view * pose, in that order: convert the model's own placement into
            // camera space. See the note on `view` above.
            org.joml.Matrix4f modelView = new org.joml.Matrix4f(view).mul(matrices.get(i));
            // Sets ModelViewMat, ProjMat, ColorModulator, the fog uniforms and the
            // samplers. All of them have to be written: this is not a convenience
            // wrapper around the matrix.
            shader.setDefaultUniforms(
                    VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
            // The stock entity shader computes fog from the raw VBO Position. That
            // is wrong for this cache: Position is model-local (and often in mm),
            // while the per-instance pose carries its scale and camera-relative
            // translation. Keep fog in the pre-view space used by vanilla's
            // per-frame block path so cylindrical fog retains the world's Y axis.
            if (fogModelMatrix != null) {
                fogModelMatrix.set(matrices.get(i));
            }
            shader.apply();
            model.vertexBuffer.bind();
            model.vertexBuffer.draw();
        }
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    /**
     * Temporary: reports one rack's draw inputs, once per rack.
     *
     * <p>Distinguishes the three ways a terminal can fail to appear: the loop
     * found no style to draw ({@code styled} 0), the style's model never baked
     * ({@code missingModel} &gt; 0), or everything resolved and the problem is
     * downstream in the GL path ({@code batch} &gt; 0 but nothing on screen).</p>
     */
    static void reportRack(net.minecraft.core.BlockPos pos, Direction facing, int cells,
                           int styled, int missing, int batch) {
        once("rack:" + pos.asLong(),
                "rack {} facing={} cells={} styled={} missingModel={} batch={}",
                pos, facing, cells, styled, missing, batch);
    }

    /** Hashes everything from the pose file that changes the drawn shape. */
    private static int poseHash(TerminalModelConfig cfg) {
        int h = Float.floatToIntBits(cfg.objUnitsPerBlock());
        h = 31 * h + Float.floatToIntBits(cfg.scale());
        h = 31 * h + Float.floatToIntBits(cfg.offsetX());
        h = 31 * h + Float.floatToIntBits(cfg.offsetY());
        h = 31 * h + Float.floatToIntBits(cfg.offsetZ());
        h = 31 * h + Float.floatToIntBits(cfg.originX());
        h = 31 * h + Float.floatToIntBits(cfg.originY());
        h = 31 * h + Float.floatToIntBits(cfg.originZ());
        h = 31 * h + cfg.rotation().hashCode();
        h = 31 * h + Float.floatToIntBits(cfg.mirrorOffset());
        h = 31 * h + cfg.mirror().hashCode();
        return h;
    }

    /** Tessellates a model once and uploads it to a static VBO. */
    private static ModelBuffer build(String styleId, BlockState state, BakedModel model,
                                     ClientLevel level, int lightValue) {
        List<BakedQuad> quads = collectQuads(state, model);
        if (quads.isEmpty()) {
            once("empty:" + styleId,
                    "build produced NO QUADS for style={} (model={}) - nothing will be drawn",
                    styleId, model);
            return null;
        }
        once("built:" + styleId,
                "build style={} quads={} bounds={} model={}",
                styleId, quads.size(), boundsOf(quads), model.getClass().getSimpleName());
        int needed = quads.size() * 4 * FORMAT.getVertexSize();
        // A fresh scratch buffer per build. It is a handful of KB to a few MB and
        // is allocated once per style, not per frame — sharing one across builds
        // saves nothing measurable but creates a real hazard: ByteBufferBuilder
        // recycles memory only once every Result from it has been closed, and the
        // MeshData handed to VertexBuffer#upload owns its Result until upload
        // returns. A mistimed clear() there would hand already-released memory to
        // the next model.
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(Math.max(needed, 1024))) {
            // Every quad is lit by the same light value: the terminal is a few
            // centimetres across and stands at one block position. `light` is
            // already the packed (block << 4 | sky << 20) form that LightTexture
            // expects, so it is used verbatim rather than split and repacked.
            int[] lightmap = {lightValue, lightValue, lightValue, lightValue};
            float[] brightness = new float[4];

            BufferBuilder builder = new BufferBuilder(scratch, VertexFormat.Mode.QUADS, FORMAT);
            for (BakedQuad quad : quads) {
                float shade = level == null ? 1.0f : level.getShade(quad.getDirection(), quad.isShade());
                brightness[0] = shade;
                brightness[1] = shade;
                brightness[2] = shade;
                brightness[3] = shade;
                // OBJ materials bake Kd into the quad's vertex colour. Keep that
                // tint, then multiply in the directional shade.
                builder.putBulkData(IDENTITY, quad, brightness, 1f, 1f, 1f, 1f,
                        lightmap, OverlayTexture.NO_OVERLAY, true);
            }
            MeshData data = builder.build();
            if (data == null) {
                return null;
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            int quadsWritten = data.drawState().vertexCount() / 4;
            // Bind THIS buffer's vertex array before uploading.
            //
            // VertexBuffer#upload does not bind its own VAO: it writes this
            // model's attribute pointers and index binding into whatever array
            // object happens to be bound. Uploading without binding first puts
            // them into the PREVIOUS buffer's VAO, and the draw then reads
            // garbage — which surfaces as an access violation inside
            // nvoglv64.dll on glDrawElements rather than as a Java exception.
            vb.bind();
            // upload() closes the MeshData itself in its own try/finally, so it is
            // passed straight in; wrapping it here would split ownership.
            vb.upload(data);
            VertexBuffer.unbind();
            once("uploaded:" + styleId,
                    "uploaded style={} quads={} indexCount={} -> buffer {}",
                    styleId, quadsWritten, quadsWritten * 6, vb);
            return new ModelBuffer(vb, renderTypeFor(), quadsWritten);
        }
    }

    /** The quads' extent in model space, to tell "not drawn" from "drawn tiny". */
    private static String boundsOf(List<BakedQuad> quads) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (BakedQuad quad : quads) {
            int[] v = quad.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(v[i * stride]);
                float y = Float.intBitsToFloat(v[i * stride + 1]);
                float z = Float.intBitsToFloat(v[i * stride + 2]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }
        return String.format("[%.3f %.3f %.3f]..[%.3f %.3f %.3f]",
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Every quad of a model, across the six directions and the unculled bucket. */
    private static List<BakedQuad> collectQuads(BlockState state, BakedModel model) {
        List<BakedQuad> out = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            RANDOM.setSeed(42L);
            out.addAll(model.getQuads(state, direction, RANDOM, ModelData.EMPTY, null));
        }
        RANDOM.setSeed(42L);
        out.addAll(model.getQuads(state, null, RANDOM, ModelData.EMPTY, null));
        return out;
    }

    /**
     * The render type this cache draws with.
     *
     * <h2>Why the block atlas, and not the model's own sprite</h2>
     * <p>An OBJ terminal is baked <b>into the block atlas</b>: its {@code map_Kd}
     * texture is stitched into that one big sheet, and its vertex UVs are
     * <b>atlas coordinates</b> — {@code 0..1} spans the whole atlas, not the
     * model's little patch of it.</p>
     *
     * <p>An earlier version bound {@code entitySolid(model.getParticleIcon())},
     * i.e. the model's own sprite as a standalone texture. The geometry was there
     * and correct, but every UV then pointed outside that one sprite and sampled
     * the empty part of it: the terminals were drawn fully transparent. That is
     * what "I can wire them up but I cannot see them" looked like.</p>
     *
     * <p>The old per-frame path used {@code RenderType.cutout()}, which binds the
     * block atlas. This cache also needs the {@code NEW_ENTITY} layout, so its
     * custom render type keeps that same atlas, cutout rule and depth/cull state.
     * Its only semantic difference from vanilla entity cutout is that fog uses the
     * per-instance transformed position and lighting uses the directional shade
     * already baked into {@code Color}.</p>
     */
    private static RenderType renderTypeFor() {
        return TerminalRenderTypes.terminalCutout();
    }

    /**
     * Drops every cached buffer.
     *
     * <p>Closing a {@link VertexBuffer} frees GL objects and asserts it is on the
     * render thread. A reload listener can be invoked from the resource thread, so
     * the close is marshalled when needed rather than allowed to throw: a failed
     * cache drop would leave the old geometry on screen forever.</p>
     *
     * <p>Called from the resource-reload listener and on shutdown. A re-exported
     * OBJ or a re-painted texture must not keep drawing the old geometry.</p>
     */
    public static void clear() {
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            // Marshal to the render thread. The map is cleared first so the very
            // next lookup cannot hand out a buffer that is about to be deleted.
            java.util.Map<BucketKey, ModelBuffer> stale = new java.util.HashMap<>(BUILT);
            BUILT.clear();
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
                for (ModelBuffer buffer : stale.values()) {
                    buffer.vertexBuffer.close();
                }
            });
            return;
        }
        for (ModelBuffer buffer : BUILT.values()) {
            buffer.vertexBuffer.close();
        }
        BUILT.clear();
    }

    /** Number of cached buckets, for the debug overlay and tests. */
    public static int size() {
        return BUILT.size();
    }
}
