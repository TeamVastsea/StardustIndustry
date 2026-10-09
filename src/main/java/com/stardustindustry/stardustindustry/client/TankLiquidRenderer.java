package com.stardustindustry.stardustindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the fluid inside a formed tank.
 *
 * <p>The fluid is rendered as a solid box filling the interior cavity from the
 * bottom up to the current level, so the level itself is readable at a glance —
 * the way a glass sight-glass works, and the way a Tinkers' smeltery shows what
 * it holds. The player never has to open a screen to learn what is in the tank
 * or roughly how full it is.</p>
 *
 * <p>The box is drawn in the controller's local space with the cavity's true
 * world offset applied, because the controller is only one face cell of the
 * dynamic tank. Every face is emitted <em>one block cell at a time</em>, each
 * mapped to exactly one copy of the fluid sprite. Stretching a single quad's UVs
 * across a wider face would sample past the sprite's edge into neighbouring
 * atlas pixels, which is what produced the black-and-white streaking the old
 * renderer showed.</p>
 *
 * <p>The fluid is drawn translucent with its own tint colour, at a mostly-opaque
 * alpha: a little transparency reads as liquid rather than a painted block, while
 * staying saturated instead of washing out to the colour of whatever is behind
 * the glass.</p>
 */
public final class TankLiquidRenderer {

    /**
     * The alpha the fluid is drawn at. High enough that the tint stays rich and
     * the fluid reads as a body of liquid rather than a tinted window, low enough
     * that it does not look like flat paint.
     */
    private static final float FLUID_ALPHA = 0.80f;

    /**
     * The lowest block-light the fluid is drawn with, and the lowest sky-light.
     * Together these keep a dark tank's fluid legible instead of pitch black.
     */
    private static final int MIN_BLOCK_BRIGHTNESS = 4;
    private static final int MIN_SKY_BRIGHTNESS = 6;

    private TankLiquidRenderer() {}

    /**
     * Renders the tank's fluid, if it holds any.
     *
     * @param tank        the tank block entity
     * @param pose        the controller's pose stack
     * @param buffers     the buffer source to draw into
     * @param packedLight the combined light for the quads
     */
    public static void render(TankBlockEntity tank, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        FluidStack fluid = tank.fluid().fluid();
        if (fluid.isEmpty()) {
            return;
        }
        BlockPos min = tank.syncedMin();
        BlockPos max = tank.syncedMax();
        if (min == null || max == null) {
            return;
        }

        int capacity = tank.currentCapacityMb();
        if (capacity <= 0) {
            return;
        }
        float fill = Math.min(1.0f, (float) fluid.getAmount() / capacity);

        // The interior cavity: one block in from every face.
        float x0 = min.getX() + 1 - tank.getBlockPos().getX();
        float y0 = min.getY() + 1 - tank.getBlockPos().getY();
        float z0 = min.getZ() + 1 - tank.getBlockPos().getZ();
        int cellsX = max.getX() - min.getX() - 1;
        int cellsZ = max.getZ() - min.getZ() - 1;
        int height = max.getY() - min.getY() - 1;
        if (cellsX <= 0 || cellsZ <= 0 || height <= 0) {
            return;
        }
        float top = y0 + height * fill;
        if (top <= y0) {
            return;
        }

        Fluid fluidType = fluid.getFluid();
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluidType);
        ResourceLocation still = ext.getStillTexture(fluid);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(still);

        // Light the fluid from its own position, not from the controller.
        //
        // The renderer is handed the light at the controller cell, but the
        // controller is a shell panel: once the tank is closed it sees no sky and
        // no block light, so that value is near zero and the fluid rendered
        // black. Sampling the middle of the cavity instead means a tank in
        // daylight is lit like the room around it. A floor keeps the fluid
        // readable in a genuinely dark tank rather than a solid black mass.
        packedLight = fluidLight(tank, min, max, packedLight);

        // Translucent, with the fluid's own tint. A fully transparent tint means
        // "untinted", and the render alpha floors at FLUID_ALPHA so the colour
        // stays rich rather than washing out to whatever is behind the glass.
        int tint = ext.getTintColor(fluid);
        int tintAlpha = (tint >> 24) & 0xFF;
        float alpha = tintAlpha == 0 ? FLUID_ALPHA : Math.max(FLUID_ALPHA, tintAlpha / 255.0f);
        float red = ((tint >> 16) & 0xFF) / 255.0f;
        float green = ((tint >> 8) & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;

        VertexConsumer builder = buffers.getBuffer(RenderType.translucent());
        Matrix4f matrix = pose.last().pose();

        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();

        // Top surface: one quad per interior cell.
        for (int ix = 0; ix < cellsX; ix++) {
            for (int iz = 0; iz < cellsZ; iz++) {
                float ax = x0 + ix;
                float az = z0 + iz;
                quad(builder, matrix,
                        ax, top, az,
                        ax, top, az + 1,
                        ax + 1, top, az + 1,
                        ax + 1, top, az,
                        u0, v0, u1, v1,
                        red, green, blue, alpha, packedLight, 0, 1, 0);
            }
        }

        // Side walls: only the outer skin of the fluid body.
        //
        // Every interior cell drawn its own four walls produced coincident quads
        // at every cell boundary inside the fluid, and the blend lines at those
        // contacts read as a grid of frames floating inside the liquid. The fluid
        // is one connected body, so a column only needs a wall where it faces the
        // cavity's outer edge — its neighbours inside the body are already covered
        // by the adjacent column's body.
        for (int ix = 0; ix < cellsX; ix++) {
            for (int iz = 0; iz < cellsZ; iz++) {
                float ax = x0 + ix;
                float az = z0 + iz;
                boolean westEdge = ix == 0;
                boolean eastEdge = ix == cellsX - 1;
                boolean northEdge = iz == 0;
                boolean southEdge = iz == cellsZ - 1;

                if (northEdge) {
                    // North wall (z = az), facing -Z.
                    quad(builder, matrix,
                            ax, y0, az,
                            ax, top, az,
                            ax + 1, top, az,
                            ax + 1, y0, az,
                            u0, v0, u1, v1,
                            red, green, blue, alpha, packedLight, 0, 0, -1);
                }
                if (southEdge) {
                    // South wall (z = az + 1), facing +Z.
                    quad(builder, matrix,
                            ax + 1, y0, az + 1,
                            ax + 1, top, az + 1,
                            ax, top, az + 1,
                            ax, y0, az + 1,
                            u0, v0, u1, v1,
                            red, green, blue, alpha, packedLight, 0, 0, 1);
                }
                if (westEdge) {
                    // West wall (x = ax), facing -X.
                    quad(builder, matrix,
                            ax, y0, az + 1,
                            ax, top, az + 1,
                            ax, top, az,
                            ax, y0, az,
                            u0, v0, u1, v1,
                            red, green, blue, alpha, packedLight, -1, 0, 0);
                }
                if (eastEdge) {
                    // East wall (x = ax + 1), facing +X.
                    quad(builder, matrix,
                            ax + 1, y0, az,
                            ax + 1, top, az,
                            ax + 1, top, az + 1,
                            ax + 1, y0, az + 1,
                            u0, v0, u1, v1,
                            red, green, blue, alpha, packedLight, 1, 0, 0);
                }
            }
        }
    }

    /**
     * Emits one quad from four corners in order, mapping the sprite so its
     * bottom-left corner is at the first vertex.
     */
    private static void quad(VertexConsumer builder, Matrix4f matrix,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float u0, float v0, float u1, float v1,
                             float red, float green, float blue, float alpha,
                             int light, float nx, float ny, float nz) {
        vertex(builder, matrix, x1, y1, z1, u0, v1, red, green, blue, alpha, light, nx, ny, nz);
        vertex(builder, matrix, x2, y2, z2, u0, v0, red, green, blue, alpha, light, nx, ny, nz);
        vertex(builder, matrix, x3, y3, z3, u1, v0, red, green, blue, alpha, light, nx, ny, nz);
        vertex(builder, matrix, x4, y4, z4, u1, v1, red, green, blue, alpha, light, nx, ny, nz);
    }

    /** Emits one vertex, transforming its local position through the pose matrix. */
    private static void vertex(VertexConsumer builder, Matrix4f matrix,
                               float x, float y, float z, float u, float v,
                               float red, float green, float blue, float alpha,
                               int packedLight, float normalX, float normalY, float normalZ) {
        Vector3f pos = matrix.transformPosition(x, y, z, new Vector3f());
        builder.addVertex(pos.x(), pos.y(), pos.z())
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(0)
                .setLight(packedLight)
                .setNormal(normalX, normalY, normalZ);
    }

    /**
     * The light the fluid should be lit by.
     *
     * <p>The light handed to the renderer is sampled at the controller cell. A
     * controller is one shell panel of the tank, so once the structure is closed
     * it receives neither sky nor block light and the fluid renders black — the
     * black liquid that only looked right with Night Vision, which brightens the
     * fragment regardless. Sampling the cavity's own middle instead lights the
     * fluid the way the room around the tank is lit.</p>
     *
     * <p>A floor is applied so a tank in a genuinely unlit room still shows its
     * fluid as a dim body of liquid rather than an indistinguishable black
     * mass.</p>
     */
    private static int fluidLight(TankBlockEntity tank, BlockPos min, BlockPos max, int fallback) {
        var level = tank.getLevel();
        if (level == null) {
            return fallback;
        }
        BlockPos centre = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
        int sampled = net.minecraft.client.renderer.LightTexture.pack(
                Math.max(MIN_BLOCK_BRIGHTNESS, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, centre)),
                Math.max(MIN_SKY_BRIGHTNESS, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, centre)));
        return Math.max(sampled, fallback);
    }
}
