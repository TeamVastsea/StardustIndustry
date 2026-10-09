package com.stardustindustry.stardustindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws the moving parts of a formed machine.
 *
 * <p>Animation is kept out of the machine classes entirely: a machine is a
 * server-side and common-side concept, while spinning, pumping and glowing are
 * pure client concerns. The renderer looks up an animator by block entity type,
 * so a machine opts into animation from the client layer, and a machine with no
 * animator simply stands still.</p>
 *
 * <p>An animator is called with the pose already positioned at the controller,
 * in the same space the body is drawn in, so it can address parts by the same
 * offsets the structure uses.</p>
 */
@FunctionalInterface
public interface MachineAnimator {

    /**
     * Draws this machine's moving parts.
     *
     * @param machine     the formed machine
     * @param partialTick the frame interpolation, for smooth rotation
     * @param pose        the pose stack, anchored at the controller's block origin
     * @param buffers     the vertex buffers to draw into
     * @param packedLight the packed light at the controller
     * @param packedOverlay the packed overlay at the controller
     */
    void animate(MachineBlockEntity machine, float partialTick, PoseStack pose,
                 MultiBufferSource buffers, int packedLight, int packedOverlay);
}
