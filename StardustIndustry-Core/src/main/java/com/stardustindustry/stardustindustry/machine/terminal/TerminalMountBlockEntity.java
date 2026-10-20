package com.stardustindustry.stardustindustry.machine.terminal;

import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.TerminalGrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The block entity behind a {@link TerminalMountBlock}.
 *
 * <p>It owns the rack's occupancy: which grid cells hold a terminal, and of
 * which tier. Occupancy lives here rather than in blockstates because a 4x4 rack
 * would otherwise need 2^16 states (§6.3.4).</p>
 *
 * <p>Cell indices are {@code row * columns + column}, counted in the grid's own
 * frame: column increases to the player's right and row increases upward, both
 * as seen looking at the grid face.</p>
 */
public class TerminalMountBlockEntity extends BlockEntity {

    /** Grid cell index to the tier of the terminal bolted there. */
    private final java.util.Map<Integer, EnergyTier> terminals = new java.util.TreeMap<>();

    public TerminalMountBlockEntity(BlockPos pos, BlockState state) {
        super(com.stardustindustry.stardustindustry.registry.ModBlockEntities.TERMINAL_MOUNT.get(), pos, state);
    }

    /** Constructor shape {@link net.minecraft.world.level.block.entity.BlockEntityType.Builder#of} expects. */
    public TerminalMountBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                    BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The grid this rack provides, from its block's tier. */
    /** The tier this rack and its terminals are rated for. */
    public EnergyTier tier() {
        return getBlockState().getBlock() instanceof TerminalMountBlock mount
                ? mount.tier()
                : EnergyTier.LV;
    }

    /** The grid this rack provides, taken from its block's tier. */
    public TerminalGrid grid() {
        return getBlockState().getBlock() instanceof TerminalMountBlock mount
                ? mount.grid()
                : TerminalGrid.GRID_1x1;
    }

    public boolean hasTerminal(int cell) {
        return terminals.containsKey(cell);
    }

    /** The tier of the terminal at {@code cell}, or {@code null} when empty. */
    public EnergyTier terminalTier(int cell) {
        return terminals.get(cell);
    }

    /** Number of terminals currently bolted on. */
    public int terminalCount() {
        return terminals.size();
    }

    /** Bolts a terminal of {@code tier} onto {@code cell}. */
    public void addTerminal(int cell, EnergyTier tier) {
        if (!grid().contains(cell) || terminals.containsKey(cell)) {
            return;
        }
        terminals.put(cell, tier);
        sync();
    }

    /**
     * Unbolts the terminal at {@code cell}, giving the item back to
     * {@code player} (or dropping it when the inventory is full).
     */
    public void removeTerminal(int cell, Player player) {
        EnergyTier tier = terminals.remove(cell);
        if (tier == null) {
            return;
        }
        ItemStack stack = TerminalMountBlock.TerminalItem.stackFor(tier);
        if (player == null) {
            Block.popResource(level, worldPosition, stack);
        } else if (!player.addItem(stack)) {
            Block.popResource(level, worldPosition, stack);
        }
        sync();
    }

    /** Removes every terminal and returns the stacks, for a full-rack take. */
    public java.util.List<ItemStack> drainTerminals() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>(terminals.size());
        for (EnergyTier tier : terminals.values()) {
            out.add(TerminalMountBlock.TerminalItem.stackFor(tier));
        }
        terminals.clear();
        sync();
        return out;
    }

    /**
     * The hit ray missed the rack's grid face; the player aimed at empty space
     * inside the block, not at a cell.
     */
    public static final int CELL_OUTSIDE_PLATE = -2;

    /**
     * The rack has no cell at all to hit (an empty grid, or no level loaded).
     * Distinct from {@link #CELL_OUTSIDE_PLATE}: the player's aim was fine, there
     * is simply nothing to return. Callers must not show "you missed the grid".
     */
    public static final int CELL_UNAVAILABLE = -1;

    /**
     * Converts a ray hit on the rack to a grid cell, {@link #CELL_OUTSIDE_PLATE}
     * when it missed the grid face, or {@link #CELL_UNAVAILABLE} when the rack has
     * no usable cell to hit.
     *
     * <p>The grid sits on the block's {@code FACING} face. Hit coordinates are
     * localised to the block (0..1 on each axis), projected onto that face, and
     * quantised. Rows count upward from the bottom of the face as seen by the
     * player looking at it, which for side faces means {@code y}, and for the
     * top/bottom faces means one of the horizontal axes.</p>
     */
    public int cellFromHit(BlockState state, BlockHitResult hit) {
        if (!(state.getBlock() instanceof TerminalMountBlock mount)) {
            return CELL_UNAVAILABLE;
        }
        TerminalGrid grid = mount.grid();
        if (grid.cellCount() <= 0) {
            return CELL_UNAVAILABLE;
        }
        Direction facing = state.getValue(TerminalMountBlock.FACING);

        Vec3 local = hit.getLocation().subtract(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        // u runs left-to-right across the face, v runs bottom-to-top, both general
        // to every face. A floor or ceiling plate has no world-up to read rows
        // from, so "up the face" becomes a horizontal axis there; that is the whole
        // reason this cannot stay a two-axis switch.
        //
        // These formulas are DERIVED from the renderer's placement and verified by
        // round-trip (DeriveUV2): take a cell's centre as the renderer draws it,
        // push it through the formulas below, and the SAME cell must come back.
        // The derivation is  u = 0.5 + (p - 0.5) * right_axis, which reduces to
        // "coord" when right is the positive world axis and "1 - coord" when it is
        // the negative one. Editing one side without the other makes a click land
        // on a different cell than the terminal that is drawn.
        //
        //   north: u = x,   v = y      south: u = 1-x, v = y
        //   west : u = 1-z, v = y      east : u = z,   v = y
        //   up   : u = x,   v = z      down : u = x,   v = 1-z
        double u;
        double v;
        switch (facing) {
            case NORTH -> {
                u = local.x;
                v = local.y;
            }
            case SOUTH -> {
                u = 1 - local.x;
                v = local.y;
            }
            case WEST -> {
                u = 1 - local.z;
                v = local.y;
            }
            case EAST -> {
                u = local.z;
                v = local.y;
            }
            case UP -> {
                // Ceiling plate (backplate on y 15..16): the grid's v runs along +z.
                u = local.x;
                v = local.z;
            }
            case DOWN -> {
                // Floor plate (backplate on y 0..1): v runs along -z.
                u = local.x;
                v = 1 - local.z;
            }
            default -> {
                return CELL_OUTSIDE_PLATE;
            }
        }

        int column = (int) Math.floor(u * grid.columns());
        int row = (int) Math.floor(v * grid.rows());
        int cell = grid.cellAt(column, row);
        // cellAt already rejects out-of-range columns/rows, but a NaN or infinite
        // hit (a degenerate ray) would quantise to a huge int; keep the contract
        // ">= 0 means a real cell" intact.
        return cell >= 0 ? cell : CELL_OUTSIDE_PLATE;
    }

    // ---- persistence & sync ----

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Map.Entry<Integer, EnergyTier> entry : terminals.entrySet()) {
            CompoundTag cell = new CompoundTag();
            cell.putInt("Cell", entry.getKey());
            cell.putString("Tier", entry.getValue().getSerializedName());
            list.add(cell);
        }
        tag.put("Terminals", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        terminals.clear();
        ListTag list = tag.getList("Terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cell = list.getCompound(i);
            EnergyTier tier = byName(cell.getString("Tier"));
            if (tier != null) {
                terminals.put(cell.getInt("Cell"), tier);
            }
        }
    }

    private static EnergyTier byName(String name) {
        for (EnergyTier tier : EnergyTier.values()) {
            if (tier.getSerializedName().equals(name)) {
                return tier;
            }
        }
        return null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

