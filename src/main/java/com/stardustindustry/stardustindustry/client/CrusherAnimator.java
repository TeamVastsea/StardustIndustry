package com.stardustindustry.stardustindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.block.Blocks;

/**
 * Spins the crusher's rotor while the machine is formed.
 *
 * <p>The rotor is drawn as a real block model spun about the vertical axis, one
 * cell above the base, which reads as a turning millstone without needing a
 * bespoke animated model or an extra texture. The rotation is driven purely by
 * the world clock, so it is identical for every player and needs no state.</p>
 *
 * <p>The angle is taken from the world's game time rather than a per-machine
 * counter: two crushers running side by side then turn in lockstep, which is what
 * a machine shop looks like, and a machine reloaded from disk resumes in phase
 * with the world instead of snapping to zero.</p>
 */
public final class CrusherAnimator implements MachineAnimator {

    /** Degrees of rotation per tick: a full turn every two seconds. */
    private static final float DEGREES_PER_TICK = 9.0f;

    /** How far above the controller the rotor floats, in blocks. */
    private static final float ROTOR_HEIGHT = 1.02f;

    private final BlockRenderDispatcher blockRenderer;

    public CrusherAnimator(BlockRenderDispatcher blockRenderer) {
        this.blockRenderer = blockRenderer;
    }

    @Override
    public void animate(MachineBlockEntity machine, float partialTick, PoseStack pose,
                        MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (machine.getLevel() == null) {
            return;
        }

        float time = machine.getLevel().getGameTime() + partialTick;
        float angle = (time * DEGREES_PER_TICK) % 360.0f;

        pose.pushPose();
        // Centre of the machine: the controller sits in the middle of the front
        // wall, so the rotor goes half a block in front of it and half a block
        // over, i.e. at the machine's centre in the horizontal plane.
        pose.translate(0.5f, ROTOR_HEIGHT, 1.0f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        // A rotor is smaller than a full block, so it reads as machinery rather
        // than another structural cube.
        pose.scale(0.6f, 0.25f, 0.6f);
        pose.translate(-0.5f, -0.5f, -0.5f);
        blockRenderer.renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), pose, buffers,
                packedLight, packedOverlay);
        pose.popPose();
    }
}
