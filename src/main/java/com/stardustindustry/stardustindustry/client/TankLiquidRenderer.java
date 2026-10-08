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
 * <p>The fluid is rendered as a single translucent box filling the interior
 * cavity from its bottom up to the current level. The level is what the player
 * reads: a tank answers "how full is it?" by looking at it, the way a glass
 * sight-glass does, rather than only through a screen.</p>
 *
 * <p>The box is drawn in the controller's local space with the cavity's true
 * world offset applied, because the controller is only one face cell of the
 * dynamic tank. The still texture of the fluid is tiled once per block across
 * each face, so the surface scale does not change with the tank's size.</p>
 */
public final class TankLiquidRenderer {

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
        float sizeX = max.getX() - min.getX() - 1;
        float sizeZ = max.getZ() - min.getZ() - 1;
        float interiorHeight = max.getY() - min.getY() - 1;
        float top = y0 + interiorHeight * fill;
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

        int tint = ext.getTintColor(fluid);
        float alpha = ((tint >> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.02f) {
            alpha = 0.7f;
        }
        float red = ((tint >> 16) & 0xFF) / 255.0f;
        float green = ((tint >> 8) & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;

        VertexConsumer builder = buffers.getBuffer(RenderType.translucent());
        Matrix4f matrix = pose.last().pose();

        float uMin = sprite.getU0();
        float vMin = sprite.getV0();
        float uSpan = sprite.getU1() - uMin;
        float vSpan = sprite.getV1() - vMin;

        // Top face (always visible).
        float uX = uMin + uSpan * sizeX;
        float vZ = vMin + vSpan * sizeZ;
        vertex(builder, matrix, x0, top, z0, uMin, vMin, red, green, blue, alpha, packedLight, 0, 1, 0);
        vertex(builder, matrix, x0 + sizeX, top, z0, uX, vMin, red, green, blue, alpha, packedLight, 0, 1, 0);
        vertex(builder, matrix, x0 + sizeX, top, z0 + sizeZ, uX, vZ, red, green, blue, alpha, packedLight, 0, 1, 0);
        vertex(builder, matrix, x0, top, z0 + sizeZ, uMin, vZ, red, green, blue, alpha, packedLight, 0, 1, 0);

        float vSide = vMin + vSpan * (top - y0);

        // North face (z = z0), normal -Z.
        vertex(builder, matrix, x0, top, z0, uMin, vMin, red, green, blue, alpha, packedLight, 0, 0, -1);
        vertex(builder, matrix, x0, y0, z0, uMin, vSide, red, green, blue, alpha, packedLight, 0, 0, -1);
        vertex(builder, matrix, x0 + sizeX, y0, z0, uX, vSide, red, green, blue, alpha, packedLight, 0, 0, -1);
        vertex(builder, matrix, x0 + sizeX, top, z0, uX, vMin, red, green, blue, alpha, packedLight, 0, 0, -1);

        // South face (z = z0 + sizeZ), normal +Z.
        vertex(builder, matrix, x0, top, z0 + sizeZ, uMin, vMin, red, green, blue, alpha, packedLight, 0, 0, 1);
        vertex(builder, matrix, x0 + sizeX, top, z0 + sizeZ, uX, vMin, red, green, blue, alpha, packedLight, 0, 0, 1);
        vertex(builder, matrix, x0 + sizeX, y0, z0 + sizeZ, uX, vSide, red, green, blue, alpha, packedLight, 0, 0, 1);
        vertex(builder, matrix, x0, y0, z0 + sizeZ, uMin, vSide, red, green, blue, alpha, packedLight, 0, 0, 1);

        // West face (x = x0), normal -X.
        vertex(builder, matrix, x0, top, z0, uMin, vMin, red, green, blue, alpha, packedLight, -1, 0, 0);
        vertex(builder, matrix, x0, top, z0 + sizeZ, uX, vMin, red, green, blue, alpha, packedLight, -1, 0, 0);
        vertex(builder, matrix, x0, y0, z0 + sizeZ, uX, vSide, red, green, blue, alpha, packedLight, -1, 0, 0);
        vertex(builder, matrix, x0, y0, z0, uMin, vSide, red, green, blue, alpha, packedLight, -1, 0, 0);

        // East face (x = x0 + sizeX), normal +X.
        vertex(builder, matrix, x0 + sizeX, top, z0, uMin, vMin, red, green, blue, alpha, packedLight, 1, 0, 0);
        vertex(builder, matrix, x0 + sizeX, y0, z0, uMin, vSide, red, green, blue, alpha, packedLight, 1, 0, 0);
        vertex(builder, matrix, x0 + sizeX, y0, z0 + sizeZ, uX, vSide, red, green, blue, alpha, packedLight, 1, 0, 0);
        vertex(builder, matrix, x0 + sizeX, top, z0 + sizeZ, uX, vMin, red, green, blue, alpha, packedLight, 1, 0, 0);
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
}
