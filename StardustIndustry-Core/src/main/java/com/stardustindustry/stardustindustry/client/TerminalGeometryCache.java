package com.stardustindustry.stardustindustry.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import net.minecraft.resources.ResourceLocation;
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

    /** Scratch buffer used while tessellating; render thread only. */
    private static ByteBufferBuilder scratch = new ByteBufferBuilder(64 * 1024);

    /** Built geometry per style + pose + light. Render thread only. */
    private static final Map<BucketKey, ModelBuffer> BUILT = new HashMap<>();

    /** Reused for stable quad ordering, matching what the block renderer does. */
    private static final RandomSource RANDOM = RandomSource.create();

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
        ModelBuffer built = build(state, model, level, light);
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
     * set up <b>once</b> and then only the model-view matrix changes between
     * draws. {@code drawWithShader} cannot be used for the middle of a batch
     * because it re-applies every uniform and then clears them; instead the
     * matrix uniform is written directly, which is exactly the one value that
     * differs per terminal.</p>
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
            VertexBuffer.unbind();
            renderType.clearRenderState();
            return;
        }
        boolean canUseFastPath = shader.MODEL_VIEW_MATRIX != null;
        if (!canUseFastPath) {
            // Defensive: if the shader is not the one this render type installed,
            // fall back to the full per-model path rather than writing uniforms
            // into whatever happens to be bound.
            for (int i = 0; i < models.size(); i++) {
                ModelBuffer model = models.get(i);
                model.vertexBuffer.bind();
                model.vertexBuffer.drawWithShader(
                        matrices.get(i),
                        com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),
                        com.mojang.blaze3d.systems.RenderSystem.getShader());
            }
            VertexBuffer.unbind();
            renderType.clearRenderState();
            return;
        }

        // Apply the state once for the whole batch. From here on only the
        // model-view matrix changes, which needs an apply but no restore.
        shader.apply();
        for (int i = 0; i < models.size(); i++) {
            ModelBuffer model = models.get(i);
            shader.MODEL_VIEW_MATRIX.set(matrices.get(i));
            shader.apply();
            model.vertexBuffer.bind();
            model.vertexBuffer.draw();
        }
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
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
    private static ModelBuffer build(BlockState state, BakedModel model, ClientLevel level,
                                     int lightValue) {
        List<BakedQuad> quads = collectQuads(state, model);
        if (quads.isEmpty()) {
            return null;
        }
        int needed = quads.size() * 4 * FORMAT.getVertexSize();
        if (needed > 64 * 1024) {
            // ByteBufferBuilder grows by doubling up to a 2 MB ceiling; a model
            // bigger than that would need several result buffers and the upload
            // would be split. Allocate once at the size this model needs.
            scratch.close();
            scratch = new ByteBufferBuilder(needed);
        } else {
            scratch.clear();
        }

        // Every quad is lit by the same light value: the terminal is a few
        // centimetres across and stands at one block position. `light` is already
        // the packed (block << 4 | sky << 20) form that LightTexture expects, so
        // it is used verbatim rather than split and repacked.
        int[] lightmap = {lightValue, lightValue, lightValue, lightValue};
        float[] brightness = new float[4];

        BufferBuilder builder = new BufferBuilder(scratch, VertexFormat.Mode.QUADS, FORMAT);
        for (BakedQuad quad : quads) {
            float shade = level == null ? 1.0f : level.getShade(quad.getDirection(), quad.isShade());
            brightness[0] = shade;
            brightness[1] = shade;
            brightness[2] = shade;
            brightness[3] = shade;
            builder.putBulkData(IDENTITY, quad, brightness, 1f, 1f, 1f, 1f,
                    lightmap, OverlayTexture.NO_OVERLAY, false);
        }
        MeshData data = builder.build();
        if (data == null) {
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        int quadsWritten = data.drawState().vertexCount() / 4;
        try (MeshData mesh = data) {
            vb.upload(mesh);
        }
        return new ModelBuffer(vb, RenderType.entitySolid(textureOf(model)), quadsWritten);
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

    /** The atlas sprite the model draws with, as a render-type texture. */
    private static ResourceLocation textureOf(BakedModel model) {
        return model.getParticleIcon(ModelData.EMPTY).contents().name();
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
