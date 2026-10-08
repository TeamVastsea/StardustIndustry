package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.multiblock.TankMembershipRegistry;
import com.stardustindustry.stardustindustry.multiblock.provider.TankStructureProvider;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the tank a given block belongs to.
 *
 * <p>Only the shell anchor has a block entity; the frame, the glass and the
 * ports do not (ports carry their own, but it points at the anchor). Everything
 * that has to answer "what is this tank's contents?" — the installation tool and
 * every highlight-mod plugin — needs one shared way to go from a clicked cell to
 * the anchor, so the logic lives here rather than being repeated per integrator.</p>
 *
 * <p>The lookup is cheap and layered: a cell that is itself a tank block entity
 * answers directly; a port answers through its binding; a frame or glass cell
 * answers through {@link TankMembershipRegistry}. A registry miss (a fresh chunk,
 * a machine that has not revalidated yet) falls back to a flood-fill of the
 * surrounding tank material, so pointing at a tank always works even before the
 * cache has warmed.</p>
 */
public final class TankHudAccess {

    private TankHudAccess() {}

    /**
     * The tank containing {@code pos}, or {@code null} when {@code pos} is not
     * part of a formed tank.
     */
    public static TankBlockEntity findTank(Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof TankBlockEntity tank) {
            return tank;
        }

        BlockPos anchor = TankMembershipRegistry.anchorOf(level, pos);
        if (anchor == null) {
            BlockState state = level.getBlockState(pos);
            if (!TankStructureProvider.isTankMaterial(state)) {
                return null;
            }
            anchor = findAnchorByScan(level, pos);
        }
        if (anchor == null) {
            return null;
        }
        return level.getBlockEntity(anchor) instanceof TankBlockEntity tank ? tank : null;
    }

    /** True when the block at {@code pos} is any part of a tank. */
    public static boolean isTankPart(Level level, BlockPos pos) {
        if (level == null) {
            return false;
        }
        return TankStructureProvider.isTankMaterial(level.getBlockState(pos));
    }

    /**
     * Floods outward from {@code start} through connected tank material and
     * returns the first shell cell that owns a block entity.
     */
    private static BlockPos findAnchorByScan(Level level, BlockPos start) {
        // A small bounded flood: a tank is at most 9x9x9, so anything much larger
        // than its surface is a runaway and is abandoned.
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        queue.add(start);
        seen.add(start);
        int budget = 16 * 16 * 16;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof TankBlockEntity) {
                return current;
            }
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next) && TankStructureProvider.isTankMaterial(level.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /** True when {@code state} is a tank block of any kind. */
    public static boolean isTankBlock(BlockState state) {
        return state.is(ModBlocks.TANK_FRAME.get())
                || state.is(ModBlocks.TANK_SHELL.get())
                || state.is(ModBlocks.TANK_GLASS.get());
    }
}
