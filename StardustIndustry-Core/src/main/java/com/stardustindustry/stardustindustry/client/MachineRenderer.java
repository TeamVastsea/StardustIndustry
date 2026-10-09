package com.stardustindustry.stardustindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardustindustry.stardustindustry.machine.ClientFailure;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineRoles;
import com.stardustindustry.stardustindustry.machine.PlacedPart;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a machine from its controller: the formed body, the animated parts, and
 * the projection ghosts.
 *
 * <h2>Why the controller draws the machine</h2>
 * Once a static machine is installed its body blocks are replaced by invisible
 * ones, so the world no longer draws them. The controller is the machine's anchor
 * and the only block guaranteed to exist, which makes it the natural place to put
 * the model back on screen. All the renderer needs travels in the block entity's
 * update tag, so no world scan and no per-machine special case is required.
 *
 * <h2>The three states</h2>
 * <ul>
 *   <li><b>Unformed</b>: ghost blocks mark every cell that still needs fixing.</li>
 *   <li><b>Formed</b>: the body is redrawn from the ledger — the original blocks
 *       the player built — so the machine looks exactly as assembled, plus any
 *       animation the machine itself contributes.</li>
 *   <li><b>Formed, no ledger</b> (a dynamic machine): the shell blocks were never
 *       hidden, so there is nothing to redraw and only the animation runs.</li>
 * </ul>
 *
 * <p>Redrawing the original blocks rather than a bespoke model keeps the art
 * honest: what the player sees formed is what they built, pixel for pixel, with
 * no separate model that can drift out of alignment.</p>
 */
public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public MachineRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(MachineBlockEntity machine, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = machine.getLevel();
        if (level == null) {
            return;
        }

        if (!machine.isFormed()) {
            renderProjection(machine, pose, buffers, packedLight, packedOverlay);
            return;
        }

        renderBody(machine, pose, buffers, packedLight, packedOverlay);
        MachineAnimator animator = MachineAnimators.animatorOf(machine);
        if (animator != null) {
            animator.animate(machine, partialTick, pose, buffers, packedLight, packedOverlay);
        }

        // A tank shows its contents: the contents box is drawn from the controller
        // like the body, since the controller is the only cell with an entity. A
        // fluid tank draws its liquid by level, a gas tank its gas by concentration.
        if (machine instanceof com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity tank) {
            TankLiquidRenderer.render(tank, pose, buffers, packedLight);
        } else if (machine instanceof com.stardustindustry.stardustindustry.machine.tank.GasTankBlockEntity gas) {
            TankGasRenderer.render(gas, pose, buffers, packedLight);
        }
    }

    /** Draws every still-missing cell as a coloured ghost, plus a status label. */
    private void renderProjection(MachineBlockEntity machine, PoseStack pose,
                                  MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = machine.getLevel();
        // A dynamic machine has no authored layout, so there is no correct block
        // to ghost; it shows only the status label.
        if (machine.supportsProjection()) {
            for (ClientFailure failure : machine.clientFailures()) {
                // Only draw where there is room to place something. A failure pinned
                // to the controller or to an existing block would be a ghost buried
                // inside opaque geometry, invisible and misleading; the label below
                // covers those cases with words instead.
                if (level != null && !level.getBlockState(failure.worldPos()).isAir()) {
                    continue;
                }
                BlockState ghost = ghostState(failure.colourGroup());
                offsetTo(machine, failure.worldPos(), pose);
                blockRenderer.renderSingleBlock(ghost, pose, buffers, packedLight, packedOverlay);
                pose.popPose();
            }
        }

        renderStatusLabel(machine, pose, buffers, packedLight);
    }

    /**
     * Draws a short status line floating above the controller.
     *
     * <p>The ghost blocks say <em>where</em>; this says <em>what</em>. It matters
     * most for a dynamic machine, whose only failure is often the controller cell
     * itself, leaving nothing visible to place a ghost on.</p>
     */
    private void renderStatusLabel(MachineBlockEntity machine, PoseStack pose,
                                   MultiBufferSource buffers, int packedLight) {
        var failures = machine.clientFailures();
        if (failures.isEmpty()) {
            return;
        }
        Component text = failures.get(0).expectationComponent();

        pose.pushPose();
        pose.translate(0.5, 1.4, 0.5);
        pose.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher()
                .cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float width = font.width(text);
        font.drawInBatch(text, -width / 2.0f, 0.0f, 0xFFE0E0, false,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                0, packedLight);
        pose.popPose();
    }

    /**
     * Redraws the machine's consumed body from the ledger.
     *
     * <p>Only hidable roles are redrawn: ports and the controller were left in
     * the world, so drawing them again would double up.</p>
     */
    private void renderBody(MachineBlockEntity machine, PoseStack pose,
                            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        for (PlacedPart part : machine.ledgerParts()) {
            if (!MachineRoles.isHidable(part.role())) {
                continue;
            }
            offsetTo(machine, part.worldPos(), pose);
            blockRenderer.renderSingleBlock(part.originalState(), pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    /**
     * Pushes a pose positioned at {@code worldPos}, relative to the controller.
     *
     * <p>The inset is a hair less than one block so the redrawn body sits just
     * inside its own cell and cannot z-fight with the invisible block that
     * actually occupies it.</p>
     */
    private static void offsetTo(MachineBlockEntity machine, BlockPos worldPos, PoseStack pose) {
        pose.pushPose();
        Vec3 offset = Vec3.atLowerCornerOf(worldPos.subtract(machine.getBlockPos())).add(0.001, 0.001, 0.001);
        pose.translate(offset.x, offset.y, offset.z);
        pose.scale(0.998f, 0.998f, 0.998f);
    }

    /** The placeholder block shown for a failed cell, chosen by its colour group. */
    private static BlockState ghostState(ClientFailure.ColourGroup group) {
        return switch (group) {
            case CHOICE -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case ERROR -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case STRUCTURE -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        };
    }

    @Override
    public boolean shouldRenderOffScreen(MachineBlockEntity machine) {
        // Machines can be several blocks across, so their ghosts must be drawn
        // even when the controller itself is just outside the frustum.
        return true;
    }

    /**
     * Expands the renderer's bounds beyond its own cell.
     *
     * <p>One cell is the default, but this renderer draws far more than the block
     * it is attached to: a tank's fluid box and a large machine's redrawn body
     * both extend across the structure. With the default bound, all of that is
     * culled the moment the single controller cell leaves the frustum, which is
     * why a tank's fluid could vanish while its glass was still on screen. The
     * bound is grown from the last evaluated box so the extra geometry is kept
     * whenever any part of the machine is visible.</p>
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(MachineBlockEntity machine) {
        BlockPos origin = machine.getBlockPos();
        int[] size = machine.evaluatedSize();
        if (size.length == 3) {
            // Cover the whole machine generously, in both directions, so the
            // controller need not be the corner nearest the camera.
            return new net.minecraft.world.phys.AABB(origin).inflate(
                    Math.max(size[0], 1) + 1.0, Math.max(size[1], 1) + 1.0, Math.max(size[2], 1) + 1.0);
        }
        return new net.minecraft.world.phys.AABB(origin).inflate(2.0);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
