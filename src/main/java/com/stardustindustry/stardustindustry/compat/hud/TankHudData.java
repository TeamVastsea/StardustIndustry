package com.stardustindustry.stardustindustry.compat.hud;

import com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A read-only snapshot of what a tank shows in a highlight tooltip.
 *
 * <p>Every highlight mod needs the same four answers — is this a tank, what is
 * in it, how full is it, and how big is it — but each has its own tooltip API.
 * Building the snapshot once, here, keeps the three plugins from each
 * re-deriving the numbers and drifting apart, and keeps them all correct when
 * the tank's internals change.</p>
 *
 * <p>The snapshot is a plain value object with no world reference, so it is safe
 * to build on the render thread from a client-side lookup and to hand to any
 * tooltip implementation.</p>
 *
 * @param formed        whether the tank is a valid, formed structure
 * @param sizeX         the tank's size along X, or {@code 0} when unformed
 * @param sizeY         the tank's size along Y, or {@code 0} when unformed
 * @param sizeZ         the tank's size along Z, or {@code 0} when unformed
 * @param fluidName     the held fluid's display name, or an empty component when empty
 * @param fluidAmount   mB currently held
 * @param capacityMb    mB the tank can hold
 * @param interiorCells the number of interior air cells
 */
public record TankHudData(
        boolean formed,
        int sizeX,
        int sizeY,
        int sizeZ,
        Component fluidName,
        int fluidAmount,
        int capacityMb,
        int interiorCells) {

    /** True when the tank holds any fluid. */
    public boolean hasFluid() {
        return fluidAmount > 0 && !fluidName.getString().isEmpty();
    }

    /** The fullness as a 0..1 fraction, or 0 when the capacity is unknown. */
    public float fraction() {
        return capacityMb <= 0 ? 0.0f : Math.min(1.0f, (float) fluidAmount / capacityMb);
    }

    /**
     * Builds the snapshot for {@code tank}, as seen from the clicked cell.
     *
     * <p>The clicked position is not used to read the fluid — the tank holds one
     * fluid regardless of which wall was pointed at — but it is accepted so a
     * future per-face reading (a port's own throughput, say) does not have to
     * change every call site.</p>
     */
    public static TankHudData of(TankBlockEntity tank, BlockPos clickedPos) {
        Level level = tank.getLevel();
        FluidStack fluid = tank.fluid().fluid();

        StructureEvaluation evaluation = tank.evaluation();
        int sizeX = 0;
        int sizeY = 0;
        int sizeZ = 0;
        int interior = 0;
        if (evaluation != null && evaluation.formed()) {
            int[] size = tank.evaluatedSize();
            if (size.length == 3) {
                sizeX = size[0];
                sizeY = size[1];
                sizeZ = size[2];
                interior = Math.max(0, sizeX - 2) * Math.max(0, sizeY - 2) * Math.max(0, sizeZ - 2);
            }
        } else if (tank.syncedMin() != null && tank.syncedMax() != null) {
            // Client side: rebuild the size from the box the server shipped.
            BlockPos min = tank.syncedMin();
            BlockPos max = tank.syncedMax();
            sizeX = max.getX() - min.getX() + 1;
            sizeY = max.getY() - min.getY() + 1;
            sizeZ = max.getZ() - min.getZ() + 1;
            interior = Math.max(0, sizeX - 2) * Math.max(0, sizeY - 2) * Math.max(0, sizeZ - 2);
        }

        Component fluidName = fluid.isEmpty() || fluid.getFluid() == Fluids.EMPTY
                ? Component.empty()
                : fluid.getHoverName();

        return new TankHudData(
                tank.isFormed(),
                sizeX,
                sizeY,
                sizeZ,
                fluidName,
                fluid.getAmount(),
                tank.currentCapacityMb(),
                interior);
    }
}
