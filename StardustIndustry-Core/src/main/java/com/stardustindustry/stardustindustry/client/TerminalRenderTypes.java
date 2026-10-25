package com.stardustindustry.stardustindustry.client;

import java.io.IOException;
import java.util.Objects;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.stardustindustry.stardustindustry.StardustIndustry;

/** Render state for terminal geometry kept in reusable local-space VBOs. */
public final class TerminalRenderTypes {
    private static ShaderInstance terminalCutoutShader;

    private static final RenderStateShard.ShaderStateShard TERMINAL_CUTOUT_SHADER =
            new RenderStateShard.ShaderStateShard(TerminalRenderTypes::terminalCutoutShader);

    private static final RenderType TERMINAL_CUTOUT = RenderType.create(
            "stardustindustry_terminal_cutout",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            RenderType.TRANSIENT_BUFFER_SIZE,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(TERMINAL_CUTOUT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true));

    private TerminalRenderTypes() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                StardustIndustry.MODID, "terminal_cutout");
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), location, DefaultVertexFormat.NEW_ENTITY),
                shader -> terminalCutoutShader = shader);
    }

    public static RenderType terminalCutout() {
        return TERMINAL_CUTOUT;
    }

    private static ShaderInstance terminalCutoutShader() {
        return Objects.requireNonNull(terminalCutoutShader,
                "Terminal cutout shader was requested before shader registration completed");
    }
}
