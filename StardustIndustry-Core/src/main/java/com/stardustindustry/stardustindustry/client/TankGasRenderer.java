package com.stardustindustry.stardustindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.stardustindustry.stardustindustry.gas.GasStack;
import com.stardustindustry.stardustindustry.machine.tank.GasTankBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the gas inside a formed gas tank.
 *
 * <p>Gas is rendered by <b>concentration</b>, not by level. A liquid shows how
 * much there is by how high it stands; a gas fills whatever vessel it is in, so
 * height says nothing. Instead the cavity is always drawn full, and the amount
 * is expressed as colour intensity: an empty tank draws nothing, and as gas is
 * added the fill becomes steadily more opaque until it reaches {@link #MAX_ALPHA}
 * at full capacity. The tint hue stays fixed; only the alpha moves, which reads
 * as "thin haze" versus "thick cloud".</p>
 *
 * <p>Unlike the liquid renderer, which skins only the outer edge of the body,
 * the gas box is a closed shell drawn cell by cell, because a gas fills the whole
 * volume and its surface is not a single flat top but a translucent volume.</p>
 */
public final class TankGasRenderer {

    /**
     * The alpha a completely full gas tank is drawn at. A gas never reaches full
     * opacity: at 70% the colour is rich and clearly present, while the window and
     * any structure behind it stay readable through it.
     */
    public static final float MAX_ALPHA = 0.70f;

    /**
     * The lowest alpha any visible gas is drawn at. A tank holding a trace of gas
     * should still faintly show that it is not empty rather than snapping from
     * nothing to a noticeable cloud.
     */
    private static final float MIN_VISIBLE_ALPHA = 0.06f;

    /**
     * The lowest block-light the gas is drawn with, and the lowest sky-light,
     * mirroring the liquid renderer so a dark tank's gas stays legible.
     */
    private static final int MIN_BLOCK_BRIGHTNESS = 4;
    private static final int MIN_SKY_BRIGHTNESS = 6;

    /**
     * The mod's 1x1 white block texture, sampled so the translucent shader's
     * texture multiply leaves the gas colour alone instead of tinting it by an
     * arbitrary atlas pixel.
     */
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("stardustindustry", "block/gas_white");

    /** Cached sprite for {@link #WHITE_TEXTURE}, resolved on first render. */
    private static TextureAtlasSprite WHITE_SPRITE;

    private TankGasRenderer() {}

    /**
     * Renders the tank's gas, if it holds any.
     *
     * @param tank        the gas tank block entity
     * @param pose        the controller's pose stack
     * @param buffers     the buffer source to draw into
     * @param packedLight the combined light for the quads
     */
    public static void render(GasTankBlockEntity tank, PoseStack pose,
                              MultiBufferSource buffers, int packedLight) {
        GasStack gas = tank.gas().gas();
        if (gas.isEmpty()) {
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
        float fill = Math.min(1.0f, (float) gas.amount() / capacity);
        // Every gas is a concentration: translucent, capped at MAX_ALPHA (70% when
        // full) so the tank never looks like a solid block of liquid. Only the
        // opacity changes with fill; the hue does not.
        float alpha = Math.max(MIN_VISIBLE_ALPHA, fill * MAX_ALPHA);

        // The interior cavity: one block in from every face.
        float x0 = min.getX() + 1 - tank.getBlockPos().getX();
        float y0 = min.getY() + 1 - tank.getBlockPos().getY();
        float z0 = min.getZ() + 1 - tank.getBlockPos().getZ();
        int cellsX = max.getX() - min.getX() - 1;
        int cellsY = max.getY() - min.getY() - 1;
        int cellsZ = max.getZ() - min.getZ() - 1;
        if (cellsX <= 0 || cellsY <= 0 || cellsZ <= 0) {
            return;
        }
        float x1 = x0 + cellsX;
        float y1 = y0 + cellsY;
        float z1 = z0 + cellsZ;

        int tint = gas.gas().getColor();
        float red = ((tint >> 16) & 0xFF) / 255.0f;
        float green = ((tint >> 8) & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;
        float[] rgb = normalizeTint(red, green, blue);
        red = rgb[0];
        green = rgb[1];
        blue = rgb[2];

        packedLight = gasLight(tank, min, max, packedLight);

        VertexConsumer builder = buffers.getBuffer(RenderType.translucent());
        Matrix4f matrix = pose.last().pose();

        // The translucent shader multiplies vertex colour by the texture sampled
        // at the vertex UV. A gas has no texture — it is a flat colour — so the UV
        // must point at a pure-white texel or the shader tints the gas by whatever
        // arbitrary atlas pixel UV (0,0) happens to land on. We ship a 1x1 white
        // block texture for exactly this and sample its centre.
        TextureAtlasSprite white = whiteSprite();
        float u0 = white.getU0();
        float v0 = white.getV0();
        float u1 = white.getU1();
        float v1 = white.getV1();

        // The gas fills the whole cavity, so the box is drawn as its six outer
        // faces only (each one cell tall), never as interior cell boundaries,
        // which would show as a grid inside the haze.
        //
        // Bottom face (-Y).
        quad(builder, matrix,
                x0, y0, z0,
                x1, y0, z0,
                x1, y0, z1,
                x0, y0, z1,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, 0, -1, 0);
        // Top face (+Y).
        quad(builder, matrix,
                x0, y1, z1,
                x1, y1, z1,
                x1, y1, z0,
                x0, y1, z0,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, 0, 1, 0);
        // North face (-Z).
        quad(builder, matrix,
                x0, y0, z0,
                x0, y1, z0,
                x1, y1, z0,
                x1, y0, z0,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, 0, 0, -1);
        // South face (+Z).
        quad(builder, matrix,
                x1, y0, z1,
                x1, y1, z1,
                x0, y1, z1,
                x0, y0, z1,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, 0, 0, 1);
        // West face (-X).
        quad(builder, matrix,
                x0, y0, z1,
                x0, y1, z1,
                x0, y1, z0,
                x0, y0, z0,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, -1, 0, 0);
        // East face (+X).
        quad(builder, matrix,
                x1, y0, z0,
                x1, y1, z0,
                x1, y1, z1,
                x1, y0, z1,
                u0, v0, u1, v1,
                red, green, blue, alpha, packedLight, 1, 0, 0);
    }

    /**
     * The pure-white sprite the gas box is textured with.
     *
     * <p>The translucent block shader samples a texture and multiplies it by the
     * vertex colour, so a flat colour needs a white texel; anything else bleeds
     * its hue into the gas. The mod ships a 1x1 white block texture and this
     * resolves its sprite, cached because the atlas is stable for the session.</p>
     */
    private static TextureAtlasSprite whiteSprite() {
        if (WHITE_SPRITE == null) {
            WHITE_SPRITE = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE_TEXTURE);
        }
        return WHITE_SPRITE;
    }

    /**
     * Normalises a gas tint so it always reads against the dark tank interior.
     *
     * <p>Mekanism reports a chemical's raw tint, but two extremes are unreadable
     * in a tank:</p>
     * <ul>
     *   <li>a <b>dark</b> tint (redstone {@code 0xB30505} = RGB(179,5,5), carbon
     *       {@code 0x2C2C2C}) has a low <em>perceived</em> brightness even when a
     *       single channel is fairly high, so it blends into the shadowed cavity
     *       and looks like an empty tank;</li>
     *   <li>a <b>near-white</b> tint (hydrogen, steam {@code 0xFFFFFF}) has almost
     *       no saturation, so there is no hue and it shows only as a faint grey
     *       haze.</li>
     * </ul>
     *
     * <p>The colour is therefore lifted in HSL space: hue is kept exactly, the
     * lightness is raised to a legible floor, and a near-achromatic colour is
     * given a cool grey so a colourless gas still reads as present rather than
     * invisible.</p>
     *
     * @param red   the raw red channel, 0..1
     * @param green the raw green channel, 0..1
     * @param blue  the raw blue channel, 0..1
     * @return the adjusted {@code [red, green, blue]} channels, 0..1
     */
    private static float[] normalizeTint(float red, float green, float blue) {
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));

        // A near-achromatic colour has no hue to show, whatever its brightness:
        // a white gas would be a grey haze and a black one a void. Show a legible
        // cool grey instead, matching how a colourless gas actually looks.
        if (max - min < 0.06f) {
            return new float[] {0.66f, 0.72f, 0.78f};
        }

        // Convert RGB to HSL so the hue survives the lift untouched.
        float l = (max + min) / 2.0f;
        float delta = max - min;
        float s = l > 0.5f ? delta / (2.0f - max - min) : delta / (max + min);
        float h;
        if (max == red) {
            h = ((green - blue) / delta) % 6.0f;
        } else if (max == green) {
            h = (blue - red) / delta + 2.0f;
        } else {
            h = (red - green) / delta + 4.0f;
        }
        h /= 6.0f;
        if (h < 0.0f) {
            h += 1.0f;
        }

        // Lift the lightness to a legible floor, and keep the colour reasonably
        // saturated so it does not wash out to pastel on the way up.
        final float minLightness = 0.62f;
        final float minSaturation = 0.55f;
        float lifted = Math.max(l, minLightness);
        float saturated = Math.max(s, minSaturation);
        return hslToRgb(h, saturated, lifted);
    }

    /** Converts an HSL colour (each channel 0..1) back to RGB, each channel 0..1. */
    private static float[] hslToRgb(float h, float s, float l) {
        if (s <= 0.0f) {
            return new float[] {l, l, l};
        }
        float q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
        float p = 2.0f * l - q;
        return new float[] {hueToRgb(p, q, h + 1.0f / 3.0f), hueToRgb(p, q, h), hueToRgb(p, q, h - 1.0f / 3.0f)};
    }

    /** One hue channel of the HSL-to-RGB conversion. */
    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) {
            t += 1.0f;
        }
        if (t > 1.0f) {
            t -= 1.0f;
        }
        if (t < 1.0f / 6.0f) {
            return p + (q - p) * 6.0f * t;
        }
        if (t < 1.0f / 2.0f) {
            return q;
        }
        if (t < 2.0f / 3.0f) {
            return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        }
        return p;
    }

    /** Emits one quad from four corners, in the given winding, with a flat colour. */
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

    /** The light the gas should be lit by, sampled at the cavity centre. */
    private static int gasLight(GasTankBlockEntity tank, BlockPos min, BlockPos max, int fallback) {
        var level = tank.getLevel();
        if (level == null) {
            return fallback;
        }
        BlockPos centre = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
        int sampled = LightTexture.pack(
                Math.max(MIN_BLOCK_BRIGHTNESS, level.getBrightness(LightLayer.BLOCK, centre)),
                Math.max(MIN_SKY_BRIGHTNESS, level.getBrightness(LightLayer.SKY, centre)));
        return Math.max(sampled, fallback);
    }
}
