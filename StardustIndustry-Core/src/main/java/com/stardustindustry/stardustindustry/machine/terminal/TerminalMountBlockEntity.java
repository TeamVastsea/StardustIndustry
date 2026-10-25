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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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
    private final java.util.Map<Integer, TerminalStyle> terminals = new java.util.TreeMap<>();

    /**
     * Client/render/solver cache of authoritative dimension-level cable records.
     *
     * <p><b>A cell may hold any number of cables.</b> The design first said "one
     * cable per terminal", and that was wrong: in reality a terminal is a busbar
     * post and you bolt as many lugs onto it as fit. The restriction also made the
     * rack's whole point (free placement of terminals to fan out) impossible to use
     * — a player could fan out by adding terminals but could not run two circuits
     * off one. Removing the cap lets a terminal act as the distribution node §6.2
     * describes.</p>
     *
     * <p>Grouped per cell rather than keyed by cell so several cables can share one
     * terminal. The outer list is usually tiny (number of wired cells), and each
     * inner list is the handful of cables on that post.</p>
     */
    private final java.util.Map<Integer, java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection>>
            cables = new java.util.TreeMap<>();

    /** Cable halves read from old block-entity NBT and consumed once by onLoad. */
    private final java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection>
            legacyCableCandidates = new java.util.ArrayList<>();

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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            com.stardustindustry.stardustindustry.energy.CableService.synchronizeRack(
                    this, java.util.List.copyOf(legacyCableCandidates));
            legacyCableCandidates.clear();
            com.stardustindustry.stardustindustry.energy.ElectricalGridService.registerRack(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            com.stardustindustry.stardustindustry.energy.ElectricalGridService.unregisterRack(this);
        }
        super.setRemoved();
    }

    /** The tier of the terminal at {@code cell}, or {@code null} when empty. */
    public EnergyTier terminalTier(int cell) {
        TerminalStyle style = terminals.get(cell);
        return style == null ? null : style.tier();
    }

    /** The style of the terminal at {@code cell}, or {@code null} when empty. */
    public TerminalStyle terminalStyle(int cell) {
        return terminals.get(cell);
    }

    /** Number of terminals currently bolted on. */
    public int terminalCount() {
        return terminals.size();
    }

    /** Stable cell order used to assign the conductors of a machine port. */
    public java.util.Set<Integer> terminalCells() {
        return java.util.Collections.unmodifiableSet(terminals.keySet());
    }

    /** Bolts a terminal of {@code style} onto {@code cell}. */
    public void addTerminal(int cell, TerminalStyle style) {
        if (!grid().contains(cell) || terminals.containsKey(cell)) {
            return;
        }
        terminals.put(cell, style);
        sync();
    }

    /**
     * Unbolts the terminal at {@code cell}, giving the item back to
     * {@code player} (or dropping it when the inventory is full).
     *
     * <p>Any cable attached to that terminal comes off too: a cable whose end has
     * been unscrewed would otherwise hang off nothing. §6.3.2's rule is that
     * removing a piece gives the player its parts back, and a cable is a part.</p>
     */
    public void removeTerminal(int cell, Player player) {
        if (level != null && !level.isClientSide()
                && com.stardustindustry.stardustindustry.energy.CableService.isReadOnly(level)) {
            return;
        }
        TerminalStyle style = terminals.remove(cell);
        if (style == null) {
            return;
        }
        com.stardustindustry.stardustindustry.energy.TerminalEndpoint here =
                new com.stardustindustry.stardustindustry.energy.TerminalEndpoint(worldPosition, cell);
        if (level != null && !level.isClientSide()) {
            for (ItemStack cableStack
                    : com.stardustindustry.stardustindustry.energy.CableService.disconnectAll(level, here)) {
                Block.popResource(level, worldPosition, cableStack);
            }
        }
        ItemStack stack = TerminalMountBlock.TerminalItem.stackFor(style);
        if (player == null) {
            Block.popResource(level, worldPosition, stack);
        } else if (!player.addItem(stack)) {
            Block.popResource(level, worldPosition, stack);
        }
        sync();
    }

    /** Removes every cable and terminal and returns their stacks, for a full-rack take. */
    public java.util.List<ItemStack> drainTerminals() {
        if (level != null && !level.isClientSide()
                && com.stardustindustry.stardustindustry.energy.CableService.isReadOnly(level)) {
            return java.util.List.of();
        }
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        if (level != null && !level.isClientSide()) {
            out.addAll(com.stardustindustry.stardustindustry.energy.CableService.disconnectAll(
                    level, worldPosition));
        }
        for (TerminalStyle style : terminals.values()) {
            out.add(TerminalMountBlock.TerminalItem.stackFor(style));
        }
        terminals.clear();
        sync();
        return out;
    }

    // ---- cables ----
    //
    // The server-side authority is ElectricalNetworkSavedData. This local map is
    // deliberately retained as a compact rendering and interaction cache.

    /** The facing this rack is bolted against, read from its blockstate. */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(TerminalMountBlock.FACING)
                ? state.getValue(TerminalMountBlock.FACING)
                : Direction.NORTH;
    }

    /** The cells of this rack that currently have at least one cable leaving them. */
    public java.util.Set<Integer> connectedCells() {
        return java.util.Collections.unmodifiableSet(cables.keySet());
    }

    /** Every cable leaving {@code cell}; empty when the terminal has none. */
    public java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> cablesAt(int cell) {
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list = cables.get(cell);
        return list == null ? java.util.List.of() : java.util.Collections.unmodifiableList(list);
    }

    /** How many cables leave {@code cell}. */
    public int cableCount(int cell) {
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list = cables.get(cell);
        return list == null ? 0 : list.size();
    }

    /** Every cable leaving this rack, across all cells. */
    public java.util.Collection<com.stardustindustry.stardustindustry.energy.CableConnection> cables() {
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> all = new java.util.ArrayList<>();
        for (var list : cables.values()) {
            all.addAll(list);
        }
        return java.util.Collections.unmodifiableList(all);
    }

    /** Replaces this rack's local cache with the dimension store's current view. */
    public void replaceCableCacheFromNetwork(
            java.util.Collection<com.stardustindustry.stardustindustry.energy.CableConnection> authoritative) {
        java.util.Map<Integer, java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection>> next =
                new java.util.TreeMap<>();
        for (com.stardustindustry.stardustindustry.energy.CableConnection cable : authoritative) {
            int cell = thisCellOf(cable);
            if (cell < 0) {
                continue;
            }
            java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list =
                    next.computeIfAbsent(cell, ignored -> new java.util.ArrayList<>());
            if (!list.contains(cable)) {
                list.add(cable);
            }
        }
        next.values().forEach(list -> list.sort(java.util.Comparator.comparing(
                com.stardustindustry.stardustindustry.energy.CableConnection::runId)));
        if (cables.equals(next)) {
            return;
        }
        cables.clear();
        cables.putAll(next);
        sync();
    }

    /**
     * Records a cable half on this rack and pushes it to clients.
     *
     * <p>The caller (the server) has already validated that both terminals exist;
     * this only stores it. A terminal may hold several cables — bolting another lug
     * onto a post is how a real busbar works, and §6.2 wants terminals to be the
     * fan-out points.</p>
     *
     * @return true when this half was actually added; false when the identical
     *         cable (same class, sheath and colour between the same two cells) was
     *         already recorded here. {@code CableService} needs that answer: two
     *         endpoints that already carry this exact run must not charge the
     *         player a second spool for a cable nothing changed on.
     */
    public boolean putCable(com.stardustindustry.stardustindustry.energy.CableConnection cable) {
        if (level != null && !level.isClientSide()) {
            return com.stardustindustry.stardustindustry.energy.CableService.connect(
                    level, cable.a(), cable.b(), cable.ampacity(), cable.insulation(), cable.color());
        }
        int cell = thisCellOf(cable);
        if (cell < 0) {
            return false;
        }
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list =
                cables.computeIfAbsent(cell, key -> new java.util.ArrayList<>());
        if (list.contains(cable)) {
            return false;
        }
        list.add(cable);
        sync();
        return true;
    }

    /** The cell of {@code cable} that lies on this rack, or {@code -1}. */
    private int thisCellOf(com.stardustindustry.stardustindustry.energy.CableConnection cable) {
        if (cable.a().pos().equals(worldPosition)) {
            return cable.a().cell();
        }
        if (cable.b().pos().equals(worldPosition)) {
            return cable.b().cell();
        }
        return -1;
    }

    /**
     * Removes every cable leaving {@code cell} from this rack only, returning them.
     *
     * <p>Removing one half never touches the other rack; see {@code CableService}
     * for the operation that removes both ends.</p>
     */
    public java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> disconnectCablesAt(int cell) {
        if (level != null && !level.isClientSide()) {
            com.stardustindustry.stardustindustry.energy.TerminalEndpoint endpoint =
                    new com.stardustindustry.stardustindustry.energy.TerminalEndpoint(worldPosition, cell);
            java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> removed =
                    com.stardustindustry.stardustindustry.energy.CableService.cablesAt(level, worldPosition)
                            .stream().filter(cable -> cable.touches(endpoint)).toList();
            com.stardustindustry.stardustindustry.energy.CableService.disconnectAll(level, endpoint);
            return removed;
        }
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> removed = cables.remove(cell);
        if (removed == null) {
            return java.util.List.of();
        }
        sync();
        return removed;
    }

    /**
     * Removes one specific cable from this rack, returning whether it was there.
     *
     * <p>Used when only one of several cables on a terminal is being cut.</p>
     */
    public boolean disconnectCable(com.stardustindustry.stardustindustry.energy.CableConnection cable) {
        if (level != null && !level.isClientSide()) {
            return com.stardustindustry.stardustindustry.energy.CableService
                    .disconnectDetailed(level, cable).changed();
        }
        int cell = thisCellOf(cable);
        if (cell < 0) {
            return false;
        }
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list = cables.get(cell);
        if (list == null || !list.remove(cable)) {
            return false;
        }
        if (list.isEmpty()) {
            cables.remove(cell);
        }
        sync();
        return true;
    }

    /**
     * Replaces one cable half with {@code replacement}, keeping its place.
     *
     * <p>Used for re-painting, where the cable is the same run but a different
     * colour. Matching is by {@link com.stardustindustry.stardustindustry.energy.CableConnection#sameRun
     * sameRun}, which ignores colour: the colour is what is changing.</p>
     */
    public boolean replaceCable(com.stardustindustry.stardustindustry.energy.CableConnection oldCable,
                                com.stardustindustry.stardustindustry.energy.CableConnection replacement) {
        if (level != null && !level.isClientSide()) {
            if (!oldCable.runId().equals(replacement.runId())
                    || oldCable.ampacity() != replacement.ampacity()
                    || oldCable.insulation() != replacement.insulation()) {
                return false;
            }
            return com.stardustindustry.stardustindustry.energy.CableService.recolor(
                    level, oldCable, replacement.color());
        }
        int cell = thisCellOf(oldCable);
        if (cell < 0) {
            return false;
        }
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> list = cables.get(cell);
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sameRun(oldCable)) {
                list.set(i, replacement);
                sync();
                return true;
            }
        }
        return false;
    }

    /**
     * The exact cable on this rack that {@code target} identifies, or
     * {@code null}.
     *
     * <p>Hits are resolved by matching components rather than the whole record,
     * because the caller may only know "the thin black one from this cell to that
     * cell" and not its live colour.</p>
     */
    public com.stardustindustry.stardustindustry.energy.CableConnection findCable(
            com.stardustindustry.stardustindustry.energy.CableConnection target) {
        for (var cable : cables()) {
            if (cable.sameRun(target)) {
                return cable;
            }
        }
        return null;
    }

    /** Removes every cable half on this rack, returning them. */
    public java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> disconnectAllCables() {
        if (level != null && !level.isClientSide()) {
            java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> removed =
                    com.stardustindustry.stardustindustry.energy.CableService.cablesAt(level, worldPosition);
            com.stardustindustry.stardustindustry.energy.CableService.disconnectAll(level, worldPosition);
            return removed;
        }
        if (cables.isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> removed = new java.util.ArrayList<>();
        for (var list : cables.values()) {
            removed.addAll(list);
        }
        cables.clear();
        sync();
        return removed;
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

    /**
     * The cable on {@code rack} that the aim ray touched, or {@code null}.
     *
     * <p>The ray is tested in <b>world space</b> against the same hanging curve the
     * renderer draws ({@link com.stardustindustry.stardustindustry.energy.CableGeometry#isNear}),
     * not against the block's collision box: a cable has no collision box, and the
     * rack's box is the plate. The nearest cable to the ray wins, so two parallel
     * runs can be told apart.</p>
     *
     * @param pickRadius  how close the ray must pass, in blocks
     * @param maxDistance how far along the ray to look
     */
    public com.stardustindustry.stardustindustry.energy.CableConnection cableAtRay(
            net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 direction,
            float pickRadius, double maxDistance) {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        com.stardustindustry.stardustindustry.energy.CableConnection best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var cable : cables()) {
            Vector3f a = com.stardustindustry.stardustindustry.energy.CableService.anchorOf(level, cable.a());
            Vector3f b = com.stardustindustry.stardustindustry.energy.CableService.anchorOf(level, cable.b());
            if (a == null || b == null) {
                continue;
            }
            // Rank by the cable's midpoint along the ray, then test the ray point
            // against the actual curve.
            double mx = (a.x + b.x) * 0.5;
            double my = (a.y + b.y) * 0.5;
            double mz = (a.z + b.z) * 0.5;
            double t = Math.max(0.0, (mx - from.x) * direction.x + (my - from.y) * direction.y
                    + (mz - from.z) * direction.z);
            if (t > maxDistance || t >= bestDistance) {
                continue;
            }
            Vec3 probe = new Vec3(from.x + direction.x * t, from.y + direction.y * t, from.z + direction.z * t);
            if (com.stardustindustry.stardustindustry.energy.CableGeometry.isNear(
                    a, b, probe, pickRadius, cable.sagMultiplier())) {
                best = cable;
                bestDistance = t;
            }
        }
        return best;
    }

    // ---- persistence & sync ----

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            // A rack's shape depends on which cells hold terminals (see
            // TerminalMountBlock#shapeFor), but the blockstate itself never
            // changes — bolting a terminal on is a block-entity change. Without
            // this the server keeps the cached plate-only shape, so the player
            // walks straight through the terminal they just placed while a
            // freshly joined client collides with it: the two sides disagree.
            level.setBlocksDirty(worldPosition, getBlockState(), getBlockState());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Map.Entry<Integer, TerminalStyle> entry : terminals.entrySet()) {
            CompoundTag cell = new CompoundTag();
            cell.putInt("Cell", entry.getKey());
            // The style id, not the tier name: a tier may hold several terminals
            // and each one has its own pose and its own cable anchor.
            cell.putString("Style", entry.getValue().id());
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
            // "Style" is the current key; "Tier" is what worlds saved before
            // styles existed, and TerminalStyle#fromSaved maps it back.
            String saved = cell.contains("Style") ? cell.getString("Style") : cell.getString("Tier");
            TerminalStyle style = TerminalStyle.fromSaved(saved);
            if (style != null) {
                terminals.put(cell.getInt("Cell"), style);
            }
        }

        cables.clear();
        legacyCableCandidates.clear();
        ListTag cableList = tag.getList("Cables", Tag.TAG_COMPOUND);
        for (int i = 0; i < cableList.size(); i++) {
            CompoundTag saved = cableList.getCompound(i);
            com.stardustindustry.stardustindustry.energy.CableAmpacity ampacity =
                    com.stardustindustry.stardustindustry.energy.CableAmpacity.byId(saved.getString("Ampacity"));
            com.stardustindustry.stardustindustry.energy.CableInsulation insulation =
                    com.stardustindustry.stardustindustry.energy.CableInsulation.byId(saved.getString("Insulation"));
            if (ampacity == null || insulation == null
                    || !saved.contains("Cell", Tag.TAG_INT)
                    || !saved.contains("FarPos", Tag.TAG_LONG)
                    || !saved.contains("FarCell", Tag.TAG_INT)) {
                continue;
            }
            int cell = saved.getInt("Cell");
            int farCell = saved.getInt("FarCell");
            if (cell < 0 || farCell < 0) {
                continue;
            }
            // Older saves have no Color (they predate dyeable cable) or wrote 0
            // for "unpainted black" (the pre-grey default). Both mean unpainted.
            // A real black dye is 0x1D1D21, never 0, so 0 is unambiguous.
            com.stardustindustry.stardustindustry.energy.CableColor color =
                    saved.contains("Color")
                            ? new com.stardustindustry.stardustindustry.energy.CableColor(saved.getInt("Color"))
                            : com.stardustindustry.stardustindustry.energy.CableColor.UNDYED;
            if (color.dyeColor() == 0) {
                color = com.stardustindustry.stardustindustry.energy.CableColor.UNDYED;
            }
            com.stardustindustry.stardustindustry.energy.CableConnection cable =
                    new com.stardustindustry.stardustindustry.energy.CableConnection(
                            ampacity, insulation, color,
                            new com.stardustindustry.stardustindustry.energy.TerminalEndpoint(worldPosition, cell),
                            new com.stardustindustry.stardustindustry.energy.TerminalEndpoint(
                                    BlockPos.of(saved.getLong("FarPos")), farCell));
            java.util.List<com.stardustindustry.stardustindustry.energy.CableConnection> atCell =
                    cables.computeIfAbsent(cell, key -> new java.util.ArrayList<>());
            if (!atCell.contains(cable)) {
                atCell.add(cable);
            }
            if (!legacyCableCandidates.contains(cable)) {
                legacyCableCandidates.add(cable);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        writeCableCache(tag);
        return tag;
    }

    /** Writes the rack-local view used only by client rendering and picking. */
    private void writeCableCache(CompoundTag tag) {
        ListTag cableList = new ListTag();
        for (var entry : cables.entrySet()) {
            for (com.stardustindustry.stardustindustry.energy.CableConnection cable : entry.getValue()) {
                var far = cable.a().pos().equals(worldPosition) ? cable.b() : cable.a();
                CompoundTag saved = new CompoundTag();
                saved.putInt("Cell", entry.getKey());
                saved.putString("Ampacity", cable.ampacity().id());
                saved.putString("Insulation", cable.insulation().id());
                saved.putInt("Color", cable.color().dyeColor());
                saved.putLong("FarPos", far.pos().asLong());
                saved.putInt("FarCell", far.cell());
                cableList.add(saved);
            }
        }
        tag.put("Cables", cableList);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

