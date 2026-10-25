package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension authoritative store for physical cable runs.
 *
 * <p>Terminal block entities keep a synchronized rendering cache, but cable
 * existence, colour and thermal state live here. That lets a cable be cut or
 * painted while its far chunk is unloaded, without leaving a second stale half
 * behind.</p>
 */
public final class ElectricalNetworkSavedData extends SavedData {
    public static final String DATA_NAME = "stardustindustry_electrical_network";
    /** Prevents chunk unloading from becoming an instant full-cool exploit. */
    public static final int MAX_OFFLINE_COOLING_TICKS = 5 * ElectricalUnits.TICKS_PER_SECOND;
    private static final int FORMAT_VERSION = 1;
    private static final long NEVER_UPDATED = -1L;

    private static final SavedData.Factory<ElectricalNetworkSavedData> FACTORY =
            new SavedData.Factory<>(ElectricalNetworkSavedData::new, ElectricalNetworkSavedData::load);

    private static final Comparator<CableConnection> LEGACY_PREFERENCE = Comparator
            .comparing((CableConnection cable) -> cable.ampacity().id())
            .thenComparing(cable -> cable.insulation().id())
            .thenComparingInt(cable -> cable.color().dyeColor());

    private final Map<CableRunId, CableRecord> cables = new HashMap<>();
    private final Map<TerminalEndpoint, Set<CableRunId>> runsByEndpoint = new HashMap<>();
    private final Set<CableRunId> tombstones = new HashSet<>();
    private final Map<ElectricalAccountKey, EnergyRemainders> energyRemainders = new HashMap<>();

    public static ElectricalNetworkSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Public for focused unit tests; worlds obtain the instance through {@link #get}. */
    public ElectricalNetworkSavedData() {}

    public Collection<CableRecord> records() {
        return cables.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    public List<CableConnection> cablesAt(BlockPos rackPos) {
        LinkedHashSet<CableConnection> result = new LinkedHashSet<>();
        cables.entrySet().stream()
                .filter(entry -> entry.getKey().a().pos().equals(rackPos)
                        || entry.getKey().b().pos().equals(rackPos))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().connection())
                .forEach(result::add);
        return List.copyOf(result);
    }

    public List<CableConnection> cablesAt(TerminalEndpoint endpoint) {
        Set<CableRunId> ids = runsByEndpoint.get(endpoint);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .sorted()
                .map(cables::get)
                .filter(record -> record != null)
                .map(CableRecord::connection)
                .toList();
    }

    public CableRecord record(CableRunId id) {
        return cables.get(id);
    }

    public CableConnection cable(CableRunId id) {
        CableRecord record = cables.get(id);
        return record == null ? null : record.connection();
    }

    public boolean hasTombstone(CableRunId id) {
        return tombstones.contains(id);
    }

    /** Adds a newly placed cable, enforcing endpoint uniqueness and an acyclic topology. */
    public CableMutationResult connect(CableConnection cable) {
        CableMutationResult validation = validateCable(cable);
        if (validation != null) {
            return validation;
        }
        CableRunId id = CableRunId.of(cable);
        CableRecord existing = cables.get(id);
        if (existing != null) {
            return CableMutationResult.unchanged(
                    CableMutationResult.Status.DUPLICATE_ENDPOINTS, existing.connection());
        }
        if (pathExists(id.a(), id.b())) {
            return CableMutationResult.failure(CableMutationResult.Status.WOULD_CREATE_CYCLE);
        }

        tombstones.remove(id);
        putRecord(id, new CableRecord(cable, CableThermalModel.State.COLD, NEVER_UPDATED, false));
        setDirty();
        return CableMutationResult.success(cable);
    }

    /**
     * Imports one cable half from the pre-SavedData block-entity format.
     * Tombstones win over stale chunks, and duplicate halves collapse into one run.
     */
    public CableMutationResult migrateLegacy(CableConnection cable) {
        if (cable != null && cable.insulation() == CableInsulation.BARE
                && cable.color() != null && cable.color().isPainted()) {
            cable = cable.withColor(CableColor.UNDYED);
        }
        CableMutationResult validation = validateCable(cable);
        if (validation != null) {
            return validation;
        }
        CableRunId id = CableRunId.of(cable);
        if (tombstones.contains(id)) {
            return CableMutationResult.failure(CableMutationResult.Status.TOMBSTONED);
        }

        CableRecord existing = cables.get(id);
        if (existing != null) {
            if (!existing.legacyMigration()) {
                return CableMutationResult.unchanged(
                        CableMutationResult.Status.DUPLICATE_ENDPOINTS, existing.connection());
            }
            CableConnection preferred = LEGACY_PREFERENCE.compare(cable, existing.connection()) < 0
                    ? cable : existing.connection();
            if (preferred.equals(existing.connection())) {
                return CableMutationResult.unchanged(
                        CableMutationResult.Status.DUPLICATE_ENDPOINTS, existing.connection());
            }
            cables.put(id, new CableRecord(preferred, existing.thermalState(),
                    existing.lastThermalUpdateTick(), true));
            setDirty();
            return CableMutationResult.success(preferred);
        }
        if (pathExists(id.a(), id.b())) {
            return CableMutationResult.failure(CableMutationResult.Status.WOULD_CREATE_CYCLE);
        }

        putRecord(id, new CableRecord(cable, CableThermalModel.State.COLD, NEVER_UPDATED, true));
        setDirty();
        return CableMutationResult.success(cable);
    }

    public CableMutationResult recolor(CableConnection expected, CableColor color) {
        if (expected == null || color == null) {
            return CableMutationResult.failure(CableMutationResult.Status.INVALID_PRODUCT);
        }
        CableRunId id = CableRunId.of(expected);
        CableRecord existing = cables.get(id);
        if (existing == null) {
            return CableMutationResult.failure(CableMutationResult.Status.NOT_FOUND);
        }
        if (!sameProduct(existing.connection(), expected)) {
            return CableMutationResult.unchanged(
                    CableMutationResult.Status.PRODUCT_MISMATCH, existing.connection());
        }
        if (!CableColor.isPaintable(existing.connection().insulation())) {
            return CableMutationResult.unchanged(
                    CableMutationResult.Status.NOT_PAINTABLE, existing.connection());
        }
        if (existing.connection().color().equals(color)) {
            return CableMutationResult.unchanged(
                    CableMutationResult.Status.NO_CHANGE, existing.connection());
        }

        CableConnection painted = existing.connection().withColor(color);
        cables.put(id, new CableRecord(painted, existing.thermalState(),
                existing.lastThermalUpdateTick(), false));
        setDirty();
        return CableMutationResult.success(painted);
    }

    /** Removes a cable only when the caller's product description matches it. */
    public CableMutationResult disconnect(CableConnection expected) {
        if (expected == null) {
            return CableMutationResult.failure(CableMutationResult.Status.NOT_FOUND);
        }
        CableRunId id = CableRunId.of(expected);
        CableRecord existing = cables.get(id);
        if (existing != null && !sameProduct(existing.connection(), expected)) {
            return CableMutationResult.unchanged(
                    CableMutationResult.Status.PRODUCT_MISMATCH, existing.connection());
        }
        if (existing == null) {
            if (tombstones.add(id)) {
                setDirty();
            }
            return CableMutationResult.failure(CableMutationResult.Status.NOT_FOUND);
        }
        return remove(id, existing);
    }

    public CableMutationResult disconnect(CableRunId id) {
        CableRecord existing = cables.get(id);
        if (existing == null) {
            if (tombstones.add(id)) {
                setDirty();
            }
            return CableMutationResult.failure(CableMutationResult.Status.NOT_FOUND);
        }
        return remove(id, existing);
    }

    public List<CableConnection> disconnectAll(TerminalEndpoint endpoint) {
        Set<CableRunId> indexed = runsByEndpoint.get(endpoint);
        if (indexed == null || indexed.isEmpty()) {
            return List.of();
        }
        List<CableRunId> ids = indexed.stream().sorted().toList();
        List<CableConnection> removed = new ArrayList<>(ids.size());
        for (CableRunId id : ids) {
            CableRecord record = cables.get(id);
            if (record != null && remove(id, record).changed()) {
                removed.add(record.connection());
            }
        }
        return List.copyOf(removed);
    }

    public List<CableConnection> disconnectAll(BlockPos rackPos) {
        List<CableRunId> ids = cables.keySet().stream()
                .filter(id -> id.a().pos().equals(rackPos) || id.b().pos().equals(rackPos))
                .sorted()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<CableConnection> removed = new ArrayList<>(ids.size());
        for (CableRunId id : ids) {
            CableRecord record = cables.get(id);
            if (record != null && remove(id, record).changed()) {
                removed.add(record.connection());
            }
        }
        return List.copyOf(removed);
    }

    public CableThermalModel.State thermalState(CableRunId id) {
        CableRecord record = cables.get(id);
        return record == null ? CableThermalModel.State.COLD : record.thermalState();
    }

    /**
     * Returns thermal state after applying bounded cooling for ticks in which the
     * cable was not present in the loaded solver.
     */
    public CableThermalModel.State thermalState(CableRunId id, long currentGameTick) {
        CableRecord existing = cables.get(id);
        if (existing == null || existing.thermalState().cold()) {
            return CableThermalModel.State.COLD;
        }
        long now = Math.max(0L, currentGameTick);
        long last = existing.lastThermalUpdateTick();
        long elapsedInactiveTicks;
        if (last == NEVER_UPDATED) {
            elapsedInactiveTicks = MAX_OFFLINE_COOLING_TICKS;
        } else if (now <= last || now - last <= 1L) {
            elapsedInactiveTicks = 0L;
        } else {
            elapsedInactiveTicks = Math.min(MAX_OFFLINE_COOLING_TICKS, now - last - 1L);
        }
        if (elapsedInactiveTicks == 0L) {
            return existing.thermalState();
        }

        CableThermalModel.State cooled = CableThermalModel.coolForTicks(
                existing.thermalState(), elapsedInactiveTicks);
        long processedThrough = cooled.cold() ? NEVER_UPDATED : Math.max(0L, now - 1L);
        cables.put(id, new CableRecord(existing.connection(), cooled, processedThrough,
                existing.legacyMigration()));
        setDirty();
        return cooled;
    }

    public boolean setThermalState(CableRunId id, CableThermalModel.State state) {
        CableRecord existing = cables.get(id);
        if (existing == null || state == null) {
            return false;
        }
        long updateTick = state.cold() ? NEVER_UPDATED : existing.lastThermalUpdateTick();
        if (existing.thermalState().equals(state)
                && existing.lastThermalUpdateTick() == updateTick) {
            return false;
        }
        cables.put(id, new CableRecord(existing.connection(), state, updateTick,
                existing.legacyMigration()));
        setDirty();
        return true;
    }

    public boolean setThermalState(CableRunId id, CableThermalModel.State state, long gameTick) {
        CableRecord existing = cables.get(id);
        if (existing == null || state == null) {
            return false;
        }
        long updateTick = state.cold() ? NEVER_UPDATED : Math.max(0L, gameTick);
        if (existing.thermalState().equals(state)
                && existing.lastThermalUpdateTick() == updateTick) {
            return false;
        }
        cables.put(id, new CableRecord(existing.connection(), state, updateTick,
                existing.legacyMigration()));
        setDirty();
        return true;
    }

    public long energyRemainder(ElectricalAccountKey account, boolean extracting) {
        EnergyRemainders values = energyRemainders.get(account);
        if (values == null) {
            return 0L;
        }
        return extracting ? values.extractWattTicks() : values.receiveWattTicks();
    }

    public void setEnergyRemainder(ElectricalAccountKey account, boolean extracting, long value) {
        if (account == null) {
            return;
        }
        long normalized = Math.max(0L, value) % ElectricalUnits.TICKS_PER_SECOND;
        EnergyRemainders existing = energyRemainders.getOrDefault(account, EnergyRemainders.ZERO);
        EnergyRemainders replacement = extracting
                ? new EnergyRemainders(existing.receiveWattTicks(), normalized)
                : new EnergyRemainders(normalized, existing.extractWattTicks());
        if (replacement.equals(existing)) {
            return;
        }
        if (replacement.empty()) {
            energyRemainders.remove(account);
        } else {
            energyRemainders.put(account, replacement);
        }
        setDirty();
    }

    void retainEnergyRemainderAccounts(Collection<ElectricalAccountKey> accounts) {
        Set<ElectricalAccountKey> retained = new HashSet<>(accounts);
        if (energyRemainders.keySet().removeIf(account -> !retained.contains(account))) {
            setDirty();
        }
    }

    private CableMutationResult remove(CableRunId id, CableRecord existing) {
        cables.remove(id);
        unlink(id);
        tombstones.add(id);
        setDirty();
        return CableMutationResult.success(existing.connection());
    }

    private static CableMutationResult validateCable(CableConnection cable) {
        if (cable == null || cable.ampacity() == null || cable.insulation() == null
                || cable.color() == null) {
            return CableMutationResult.failure(CableMutationResult.Status.INVALID_PRODUCT);
        }
        if (cable.a().equals(cable.b())) {
            return CableMutationResult.failure(CableMutationResult.Status.SAME_ENDPOINT);
        }
        if (cable.a().cell() < 0 || cable.b().cell() < 0
                || (cable.ampacity().isBareOnly() && cable.insulation() != CableInsulation.BARE)
                || (cable.insulation() == CableInsulation.BARE && cable.color().isPainted())) {
            return CableMutationResult.failure(CableMutationResult.Status.INVALID_PRODUCT);
        }
        return null;
    }

    private static boolean sameProduct(CableConnection first, CableConnection second) {
        return first.ampacity() == second.ampacity()
                && first.insulation() == second.insulation();
    }

    private boolean pathExists(TerminalEndpoint start, TerminalEndpoint target) {
        Deque<TerminalEndpoint> pending = new ArrayDeque<>();
        Set<TerminalEndpoint> visited = new HashSet<>();
        pending.add(start);
        visited.add(start);
        while (!pending.isEmpty()) {
            TerminalEndpoint current = pending.removeFirst();
            Set<CableRunId> adjacent = runsByEndpoint.get(current);
            if (adjacent == null) {
                continue;
            }
            for (CableRunId id : adjacent) {
                TerminalEndpoint next = id.other(current);
                if (target.equals(next)) {
                    return true;
                }
                if (next != null && visited.add(next)) {
                    pending.addLast(next);
                }
            }
        }
        return false;
    }

    private void putRecord(CableRunId id, CableRecord record) {
        cables.put(id, record);
        runsByEndpoint.computeIfAbsent(id.a(), ignored -> new HashSet<>()).add(id);
        runsByEndpoint.computeIfAbsent(id.b(), ignored -> new HashSet<>()).add(id);
    }

    private void unlink(CableRunId id) {
        unlink(id.a(), id);
        unlink(id.b(), id);
    }

    private void unlink(TerminalEndpoint endpoint, CableRunId id) {
        Set<CableRunId> ids = runsByEndpoint.get(endpoint);
        if (ids == null) {
            return;
        }
        ids.remove(id);
        if (ids.isEmpty()) {
            runsByEndpoint.remove(endpoint);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT_VERSION);
        ListTag cableList = new ListTag();
        cables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag saved = writeId(entry.getKey());
            CableRecord record = entry.getValue();
            saved.putString("Ampacity", record.connection().ampacity().id());
            saved.putString("Insulation", record.connection().insulation().id());
            saved.putInt("Color", record.connection().color().dyeColor());
            saved.putDouble("Heat", record.thermalState().heat());
            saved.putInt("ContinuousOverloadTicks", record.thermalState().continuousOverloadTicks());
            saved.putLong("LastThermalUpdateTick", record.lastThermalUpdateTick());
            saved.putBoolean("LegacyMigration", record.legacyMigration());
            cableList.add(saved);
        });
        tag.put("Cables", cableList);

        ListTag deletedList = new ListTag();
        tombstones.stream().sorted().map(ElectricalNetworkSavedData::writeId).forEach(deletedList::add);
        tag.put("Tombstones", deletedList);

        ListTag remainderList = new ListTag();
        energyRemainders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(ElectricalNetworkSavedData::compareAccountKeys))
                .forEach(entry -> {
                    CompoundTag saved = new CompoundTag();
                    saved.putLong("Owner", entry.getKey().owner().asLong());
                    saved.putString("Channel", entry.getKey().channel());
                    saved.putLong("ReceiveWattTicks", entry.getValue().receiveWattTicks());
                    saved.putLong("ExtractWattTicks", entry.getValue().extractWattTicks());
                    remainderList.add(saved);
                });
        tag.put("EnergyRemainders", remainderList);
        return tag;
    }

    public static ElectricalNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ElectricalNetworkSavedData data = new ElectricalNetworkSavedData();
        ListTag deletedList = tag.getList("Tombstones", Tag.TAG_COMPOUND);
        for (int i = 0; i < deletedList.size(); i++) {
            CableRunId id = readId(deletedList.getCompound(i));
            if (id != null && !id.a().equals(id.b())) {
                data.tombstones.add(id);
            }
        }

        List<LoadedRecord> loaded = new ArrayList<>();
        ListTag cableList = tag.getList("Cables", Tag.TAG_COMPOUND);
        for (int i = 0; i < cableList.size(); i++) {
            LoadedRecord record = readRecord(cableList.getCompound(i));
            if (record != null) {
                loaded.add(record);
            }
        }
        loaded.sort(Comparator.comparing(LoadedRecord::id)
                .thenComparing(record -> record.record().connection(), LEGACY_PREFERENCE));
        for (LoadedRecord loadedRecord : loaded) {
            CableRunId id = loadedRecord.id();
            if (data.tombstones.contains(id) || data.cables.containsKey(id)
                    || data.pathExists(id.a(), id.b())) {
                continue;
            }
            data.putRecord(id, loadedRecord.record());
        }

        ListTag remainderList = tag.getList("EnergyRemainders", Tag.TAG_COMPOUND);
        for (int i = 0; i < remainderList.size(); i++) {
            CompoundTag saved = remainderList.getCompound(i);
            if (!saved.contains("Owner", Tag.TAG_LONG) || !saved.contains("Channel", Tag.TAG_STRING)) {
                continue;
            }
            try {
                ElectricalAccountKey account = new ElectricalAccountKey(
                        BlockPos.of(saved.getLong("Owner")), saved.getString("Channel"));
                long receive = saved.contains("ReceiveWattTicks", Tag.TAG_ANY_NUMERIC)
                        ? Math.max(0L, saved.getLong("ReceiveWattTicks")) % ElectricalUnits.TICKS_PER_SECOND
                        : 0L;
                long extract = saved.contains("ExtractWattTicks", Tag.TAG_ANY_NUMERIC)
                        ? Math.max(0L, saved.getLong("ExtractWattTicks")) % ElectricalUnits.TICKS_PER_SECOND
                        : 0L;
                EnergyRemainders remainders = new EnergyRemainders(receive, extract);
                if (!remainders.empty()) {
                    data.energyRemainders.put(account, remainders);
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed account keys are skipped without rejecting other saved data.
            }
        }
        return data;
    }

    private static CompoundTag writeId(CableRunId id) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("APos", id.a().pos().asLong());
        tag.putInt("ACell", id.a().cell());
        tag.putLong("BPos", id.b().pos().asLong());
        tag.putInt("BCell", id.b().cell());
        return tag;
    }

    private static CableRunId readId(CompoundTag tag) {
        if (!tag.contains("APos", Tag.TAG_LONG) || !tag.contains("ACell", Tag.TAG_INT)
                || !tag.contains("BPos", Tag.TAG_LONG) || !tag.contains("BCell", Tag.TAG_INT)) {
            return null;
        }
        int aCell = tag.getInt("ACell");
        int bCell = tag.getInt("BCell");
        if (aCell < 0 || bCell < 0) {
            return null;
        }
        return new CableRunId(
                new TerminalEndpoint(BlockPos.of(tag.getLong("APos")), aCell),
                new TerminalEndpoint(BlockPos.of(tag.getLong("BPos")), bCell));
    }

    private static LoadedRecord readRecord(CompoundTag tag) {
        CableRunId id = readId(tag);
        if (id == null || id.a().equals(id.b())) {
            return null;
        }
        CableAmpacity ampacity = CableAmpacity.byId(tag.getString("Ampacity"));
        CableInsulation insulation = CableInsulation.byId(tag.getString("Insulation"));
        if (ampacity == null || insulation == null
                || (ampacity.isBareOnly() && insulation != CableInsulation.BARE)) {
            return null;
        }
        CableColor color = tag.contains("Color", Tag.TAG_INT)
                ? new CableColor(tag.getInt("Color")) : CableColor.UNDYED;
        if (insulation == CableInsulation.BARE) {
            color = CableColor.UNDYED;
        }
        double heat = tag.contains("Heat", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("Heat") : 0.0;
        if (!Double.isFinite(heat)) {
            heat = 0.0;
        }
        int overloadTicks = tag.contains("ContinuousOverloadTicks", Tag.TAG_ANY_NUMERIC)
                ? tag.getInt("ContinuousOverloadTicks") : 0;
        long lastThermalUpdateTick = tag.contains("LastThermalUpdateTick", Tag.TAG_ANY_NUMERIC)
                ? Math.max(NEVER_UPDATED, tag.getLong("LastThermalUpdateTick")) : NEVER_UPDATED;
        CableConnection connection = new CableConnection(ampacity, insulation, color, id.a(), id.b());
        return new LoadedRecord(id, new CableRecord(connection,
                new CableThermalModel.State(heat, overloadTicks), lastThermalUpdateTick,
                tag.getBoolean("LegacyMigration")));
    }

    private static int compareAccountKeys(ElectricalAccountKey first, ElectricalAccountKey second) {
        int owner = compareBlockPos(first.owner(), second.owner());
        return owner != 0 ? owner : first.channel().compareTo(second.channel());
    }

    private static int compareBlockPos(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    public record CableRecord(CableConnection connection, CableThermalModel.State thermalState,
                              long lastThermalUpdateTick, boolean legacyMigration) {
        public CableRecord {
            if (connection == null || thermalState == null) {
                throw new IllegalArgumentException("Cable records require a connection and thermal state");
            }
            lastThermalUpdateTick = thermalState.cold()
                    ? NEVER_UPDATED : Math.max(NEVER_UPDATED, lastThermalUpdateTick);
        }
    }

    private record EnergyRemainders(long receiveWattTicks, long extractWattTicks) {
        private static final EnergyRemainders ZERO = new EnergyRemainders(0L, 0L);

        private boolean empty() {
            return receiveWattTicks == 0L && extractWattTicks == 0L;
        }
    }

    private record LoadedRecord(CableRunId id, CableRecord record) {}
}
