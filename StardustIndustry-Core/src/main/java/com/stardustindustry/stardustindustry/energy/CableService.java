package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Creates and removes authoritative dimension-level cable records.
 *
 * <h2>Why this is a separate class</h2>
 * <p>A cable used to be stored twice, once on each rack. The dimension store is
 * now the single source of truth and racks only cache the runs needed by the
 * renderer and loaded-grid solver. Every mutation goes through here so those
 * caches cannot disagree with the saved record.</p>
 *
 * <h2>Cables do not restrict which terminals they join</h2>
 * <p>There is no tier check and no per-terminal limit. A cable has no voltage
 * level of its own; it has a current class and an insulation class, and the
 * insulation only decides what a player <b>feels</b> when touching it
 * ({@code CableInsulation#shockAt}). A player may therefore string a thin,
 * LV-sheathed cable onto a 10 kV grid — it will carry the current and shock
 * whoever touches it, which is the lesson §6.10–§6.11 wants to teach. Refusing
 * the connection would hide that.</p>
 *
 * <h2>Server only</h2>
 * <p>These methods mutate saved data and refresh loaded block-entity caches, so
 * they must run on the server thread.</p>
 */
public final class CableService {
    public static final float MAX_CABLE_SPAN_BLOCKS = 64.0f;

    private CableService() {}

    /**
     * Strings a cable between two terminals.
     *
     * <p>Fails and changes nothing unless both endpoints are loaded racks, both
     * cells hold a terminal, and the two ends are not the same terminal. The caller
     * is expected to have checked what it wants to report; this returns
     * {@code false} rather than throwing so a stale client request is a no-op.</p>
     */
    public static boolean connect(Level level, TerminalEndpoint from, TerminalEndpoint to,
                                  CableAmpacity ampacity, CableInsulation insulation) {
        return connectDetailed(level, from, to, ampacity, insulation, CableColor.UNDYED).changed();
    }

    /**
     * The colour a cable starts life in when nobody dyed it: <b>unpainted</b>.
     *
     * <p>Not a fixed colour, and deliberately so. What an unpainted cable looks
     * like is decided when it is drawn ({@link CableColor#naturalColor}): bare
     * conductor shows its metal, insulated cable shows its grey sheath. Storing
     * "unpainted" rather than baking a grey or a metal tone into the record means a
     * cable's appearance can never contradict its product — a bare wire stays
     * copper even if an old save wrote grey into it.</p>
     */
    public static CableColor defaultColorOf(CableAmpacity ampacity) {
        return CableColor.UNDYED;
    }

    /**
     * Whether electrical topology edits are disabled for this dimension.
     *
     * <p>This is true when the world's network data was written by a newer data
     * format. Interactive callers must check it before removing terminals or
     * racks, because those physical edits would otherwise orphan opaque cable
     * records that this version intentionally preserves.</p>
     */
    public static boolean isReadOnly(Level level) {
        return level instanceof ServerLevel serverLevel
                && ElectricalNetworkSavedData.get(serverLevel).isReadOnly();
    }

    /** As {@link #connect(Level, TerminalEndpoint, TerminalEndpoint, CableAmpacity, CableInsulation)}, in a colour. */
    public static boolean connect(Level level, TerminalEndpoint from, TerminalEndpoint to,
                                  CableAmpacity ampacity, CableInsulation insulation, CableColor color) {
        return connectDetailed(level, from, to, ampacity, insulation, color).changed();
    }

    /** Detailed form used by tools that need to explain why placement failed. */
    public static CableMutationResult connectDetailed(Level level, TerminalEndpoint from, TerminalEndpoint to,
                                                      CableAmpacity ampacity, CableInsulation insulation,
                                                      CableColor color) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return CableMutationResult.failure(CableMutationResult.Status.CLIENT_SIDE);
        }
        if (from == null || to == null || ampacity == null || insulation == null || color == null) {
            return CableMutationResult.failure(CableMutationResult.Status.INVALID_PRODUCT);
        }
        if (from.equals(to)) {
            return CableMutationResult.failure(CableMutationResult.Status.SAME_ENDPOINT);
        }
        // Steel-cored aluminium has no insulated product; refuse it here too, so a
        // hand-made or forged request cannot conjure a cable that cannot be made.
        if ((ampacity.isBareOnly() && insulation != CableInsulation.BARE)
                || (insulation == CableInsulation.BARE && color.isPainted())) {
            return CableMutationResult.failure(CableMutationResult.Status.INVALID_PRODUCT);
        }
        if (!hasChunkNow(serverLevel, from.pos()) || !hasChunkNow(serverLevel, to.pos())) {
            return CableMutationResult.failure(CableMutationResult.Status.ENDPOINT_UNAVAILABLE);
        }
        TerminalMountBlockEntity fromRack = rackAt(serverLevel, from.pos());
        TerminalMountBlockEntity toRack = rackAt(serverLevel, to.pos());
        if (fromRack == null || toRack == null) {
            return CableMutationResult.failure(CableMutationResult.Status.TERMINAL_MISSING);
        }
        if (!fromRack.hasTerminal(from.cell()) || !toRack.hasTerminal(to.cell())) {
            return CableMutationResult.failure(CableMutationResult.Status.TERMINAL_MISSING);
        }
        Vector3f fromAnchor = anchorOf(serverLevel, from);
        Vector3f toAnchor = anchorOf(serverLevel, to);
        if (fromAnchor == null || toAnchor == null) {
            return CableMutationResult.failure(CableMutationResult.Status.TERMINAL_MISSING);
        }
        if (!withinMaximumSpan(fromAnchor, toAnchor)) {
            return CableMutationResult.failure(CableMutationResult.Status.TOO_FAR);
        }
        CableConnection cable = new CableConnection(ampacity, insulation, color, from, to);
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        CableMutationResult result = data.connect(cable);
        if (result.changed()) {
            refreshLoadedRacks(serverLevel, data, List.of(cable), Set.of());
        }
        return result;
    }

    /**
     * Repaints one cable on both racks, returning whether it happened.
     *
     * <p>Colour is stored inside the cable, not in its item (§6.8), so changing it
     * means replacing the cable in both racks. Doing it here — rather than editing
     * a list entry in place — is what keeps the two halves from disagreeing.</p>
     */
    public static boolean recolor(Level level, CableConnection cable, CableColor color) {
        return recolorDetailed(level, cable, color).changed();
    }

    /** Repaints the authoritative run even when its far rack is unloaded. */
    public static CableMutationResult recolorDetailed(Level level, CableConnection cable, CableColor color) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return CableMutationResult.failure(CableMutationResult.Status.CLIENT_SIDE);
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        CableMutationResult result = data.recolor(cable, color);
        if (result.changed()) {
            refreshLoadedRacks(serverLevel, data, List.of(result.cable()), Set.of());
        }
        return result;
    }

    /**
     * Removes the specific cable {@code cable} from both racks, returning its item
     * stack (empty when it was not there).
     */
    public static ItemStack disconnect(Level level, CableConnection cable) {
        CableMutationResult result = disconnectDetailed(level, cable);
        return result.changed() ? CableItem.stackOf(result.cable()) : ItemStack.EMPTY;
    }

    /** Cuts the authoritative run and records a tombstone for legacy unloaded chunks. */
    public static CableMutationResult disconnectDetailed(Level level, CableConnection cable) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return CableMutationResult.failure(CableMutationResult.Status.CLIENT_SIDE);
        }
        if (cable == null) {
            return CableMutationResult.failure(CableMutationResult.Status.NOT_FOUND);
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        CableMutationResult result = data.disconnect(cable);
        Collection<CableConnection> affected = result.cable() == null ? List.of(cable) : List.of(result.cable());
        refreshLoadedRacks(serverLevel, data, affected, Set.of());
        return result;
    }

    /**
     * Removes every cable on {@code endpoint} from both racks, returning their
     * stacks.
     */
    public static List<ItemStack> disconnectAll(Level level, TerminalEndpoint endpoint) {
        if (!(level instanceof ServerLevel serverLevel) || endpoint == null) {
            return List.of();
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        List<CableConnection> removed = data.disconnectAll(endpoint);
        refreshLoadedRacks(serverLevel, data, removed, Set.of(endpoint.pos()));
        return cableStacks(removed);
    }

    /** Removes every run touching a rack, including runs whose far chunks are unloaded. */
    public static List<ItemStack> disconnectAll(Level level, BlockPos rackPos) {
        if (!(level instanceof ServerLevel serverLevel) || rackPos == null) {
            return List.of();
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        List<CableConnection> removed = data.disconnectAll(rackPos);
        refreshLoadedRacks(serverLevel, data, removed, Set.of(rackPos));
        return cableStacks(removed);
    }

    /**
     * The loaded rack at {@code pos}, or {@code null}.
     *
     * <p>Returning {@code null} for an unloaded chunk is the normal case for "the far
     * end is elsewhere": callers treat it as "cannot see it right now", never as
     * "delete the cable". That is what stops a long cable from being severed
     * whenever the player walks away from one end.</p>
     */
    private static TerminalMountBlockEntity rackAt(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            var chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk != null && chunk.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack
                    ? rack : null;
        }
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack ? rack : null;
    }

    private static boolean hasChunkNow(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** Every cable leaving the rack at {@code pos}, or an empty list. */
    public static List<CableConnection> cablesAt(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            return ElectricalNetworkSavedData.get(serverLevel).cablesAt(pos);
        }
        TerminalMountBlockEntity rack = rackAt(level, pos);
        return rack == null ? List.of() : new ArrayList<>(rack.cables());
    }

    /**
     * Migrates old block-entity cable halves and rebuilds one rack's render/solver cache.
     * Called once when a server-side rack enters the level.
     */
    public static void synchronizeRack(TerminalMountBlockEntity rack,
                                       Collection<CableConnection> legacyCables) {
        if (!(rack.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(serverLevel);
        Set<BlockPos> touched = new LinkedHashSet<>();
        touched.add(rack.getBlockPos());
        for (CableConnection legacy : legacyCables) {
            data.migrateLegacy(legacy);
            touched.add(legacy.a().pos());
            touched.add(legacy.b().pos());
        }

        // The local rack is loaded, so a missing local post proves the run is
        // orphaned. A missing far post only proves that when its chunk is loaded.
        for (CableConnection cable : new ArrayList<>(data.cablesAt(rack.getBlockPos()))) {
            if (endpointMissingOnRack(cable, rack)
                    || loadedFarEndpointMissing(serverLevel, cable, rack.getBlockPos())) {
                CableMutationResult removed = data.disconnect(cable);
                if (removed.changed()) {
                    touched.add(cable.a().pos());
                    touched.add(cable.b().pos());
                }
            }
        }
        // onLoad runs before ServerChunkCache#getChunkNow is guaranteed to expose this chunk.
        // Refresh the rack instance we already own directly; otherwise a legacy Cables tag can
        // survive beside a recolored authoritative record and look like a parallel conductor.
        rack.replaceCableCacheFromNetwork(data.cablesAt(rack.getBlockPos()));
        touched.remove(rack.getBlockPos());
        refreshLoadedRacks(serverLevel, data, List.of(), touched);
    }

    private static boolean endpointMissingOnRack(CableConnection cable, TerminalMountBlockEntity rack) {
        BlockPos pos = rack.getBlockPos();
        return (cable.a().pos().equals(pos) && !rack.hasTerminal(cable.a().cell()))
                || (cable.b().pos().equals(pos) && !rack.hasTerminal(cable.b().cell()));
    }

    private static boolean loadedFarEndpointMissing(ServerLevel level, CableConnection cable, BlockPos localPos) {
        TerminalEndpoint far = cable.a().pos().equals(localPos) ? cable.b() : cable.a();
        if (far.pos().equals(localPos)
                || !ElectricalChunkAvailability.isActiveNow(level, far.pos())) {
            return false;
        }
        TerminalMountBlockEntity farRack = rackAt(level, far.pos());
        return farRack == null || !farRack.hasTerminal(far.cell());
    }

    private static List<ItemStack> cableStacks(Collection<CableConnection> cables) {
        List<ItemStack> drops = new ArrayList<>(cables.size());
        for (CableConnection cable : cables) {
            ItemStack stack = CableItem.stackOf(cable);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return List.copyOf(drops);
    }

    private static void refreshLoadedRacks(ServerLevel level, ElectricalNetworkSavedData data,
                                           Collection<CableConnection> affected,
                                           Collection<BlockPos> extraPositions) {
        Set<BlockPos> positions = new LinkedHashSet<>(extraPositions);
        for (CableConnection cable : affected) {
            positions.add(cable.a().pos());
            positions.add(cable.b().pos());
        }
        positions.stream().sorted(TerminalEndpointPositionComparator.INSTANCE).forEach(pos -> {
            TerminalMountBlockEntity rack = rackAt(level, pos);
            if (rack != null) {
                rack.replaceCableCacheFromNetwork(data.cablesAt(pos));
            }
        });
    }

    private enum TerminalEndpointPositionComparator implements java.util.Comparator<BlockPos> {
        INSTANCE;

        @Override
        public int compare(BlockPos first, BlockPos second) {
            int x = Integer.compare(first.getX(), second.getX());
            if (x != 0) return x;
            int y = Integer.compare(first.getY(), second.getY());
            return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
        }
    }

    static boolean withinMaximumSpan(Vector3f from, Vector3f to) {
        return from != null && to != null
                && from.distanceSquared(to) <= MAX_CABLE_SPAN_BLOCKS * MAX_CABLE_SPAN_BLOCKS;
    }

    /**
     * The world point a cable hangs from at {@code endpoint}, or {@code null} when
     * that terminal no longer exists.
     *
     * <p>Works on the <b>client</b> too — it only reads block entities and their
     * blockstates — because the renderer needs it every frame. Derived live rather
     * than stored, so a rack that is rotated after a cable was pulled carries the
     * cable to its new face instead of anchoring it to the old one.</p>
     */
    public static org.joml.Vector3f anchorOf(Level level, TerminalEndpoint endpoint) {
        if (level == null) {
            return null;
        }
        TerminalMountBlockEntity rack = rackAt(level, endpoint.pos());
        if (rack == null) {
            return null;
        }
        if (!(rack.getBlockState().getBlock()
                instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock mount)) {
            return null;
        }
        if (!rack.hasTerminal(endpoint.cell())) {
            return null;
        }
        TerminalGrid grid = mount.grid();
        if (!grid.contains(endpoint.cell())) {
            return null;
        }
        TerminalStyle style = rack.terminalStyle(endpoint.cell());
        if (style == null) {
            return null;
        }
        // The anchor is a per-style setting (models/terminal/<style>.json), not a
        // constant: the artist tunes where the wire meets the hardware while
        // leaving the model's own pose alone.
        // The client uses its reloadable resource-pack pose. A dedicated server
        // never runs client setup, so it must read the bundled pose directly;
        // otherwise every authoritative length, shock and pick test silently
        // falls back to the model origin while the cable is drawn elsewhere.
        Vector3f offset = level.isClientSide()
                ? TerminalGeometry.anchorOffsetFor(style.model())
                : com.stardustindustry.stardustindustry.machine.terminal.TerminalPoseReader
                        .of(com.stardustindustry.stardustindustry.machine.terminal.TerminalPlateShapes
                                .serverResourceManager(), style)
                        .anchor();
        return TerminalGeometry.anchorWorld(endpoint.pos(), rack.facing(), grid, endpoint.cell(), offset);
    }
}
