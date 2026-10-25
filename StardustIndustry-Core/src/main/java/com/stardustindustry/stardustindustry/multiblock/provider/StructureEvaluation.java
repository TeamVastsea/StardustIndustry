package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The complete result of evaluating a machine's structure.
 *
 * <p>Both a static provider (authored per-cell DSL) and a dynamic provider (a
 * bounding-box scanner) produce this one type, so the machine layer, the
 * projector and any future user interface never branch on which provider ran.
 * An evaluation is a pure snapshot: it holds no references back into the world
 * and can be cached on the block entity until the structure changes.</p>
 *
 * @param formed     true when every required position was satisfied
 * @param tier       the voltage tier derived from the structure, or {@code null}
 *                   when it could not be determined (for example a mixed-level
 *                   build, which is itself a failure)
 * @param modifiers  the modifier set derived from the fillers found, or
 *                   {@link ModifierSet#BASE} when unformed
 * @param failures   every position that did not match, in scan order; empty on success
 * @param roles      the observed role of each evaluated world position
 * @param fillers    how many of each filler kind were counted, keyed by
 *                   {@link FillerKind}; empty when none were found
 */
public record StructureEvaluation(
        boolean formed,
        EnergyTier tier,
        ModifierSet modifiers,
        List<ScanFailure> failures,
        Map<BlockPos, BlockRole> roles,
        Map<FillerKind, Integer> fillers) {

    /** An evaluation that matches everything, at a known tier and neutral modifiers. */
    public static StructureEvaluation formed(EnergyTier tier, ModifierSet modifiers,
                                             Map<BlockPos, BlockRole> roles,
                                             Map<FillerKind, Integer> fillers) {
        return new StructureEvaluation(true, tier, modifiers, List.of(), Map.copyOf(roles), Map.copyOf(fillers));
    }

    /** An evaluation that failed, carrying the reasons and whatever roles were still observed. */
    public static StructureEvaluation failed(EnergyTier tier, List<ScanFailure> failures,
                                             Map<BlockPos, BlockRole> roles,
                                             Map<FillerKind, Integer> fillers) {
        return new StructureEvaluation(false, tier, ModifierSet.BASE, List.copyOf(failures),
                Map.copyOf(roles), Map.copyOf(fillers));
    }

    public boolean failed() {
        return !formed;
    }

    /** The first position the player should fix, or {@code null} when formed. */
    public ScanFailure firstFailure() {
        return failures.isEmpty() ? null : failures.get(0);
    }

    /**
     * Serialises the authoritative structure snapshot retained by an installed
     * static machine. Once installed, the physical filler blocks are hidden and
     * cannot be scanned again, so the derived modifiers must survive a reload.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Formed", formed);
        if (tier != null) {
            tag.putString("Tier", tier.getSerializedName());
        }

        CompoundTag modifierTag = new CompoundTag();
        modifierTag.putFloat("Speed", modifiers.speedMultiplier());
        modifierTag.putFloat("Energy", modifiers.energyMultiplier());
        modifierTag.putInt("Parallel", modifiers.parallelBonus());
        modifierTag.putFloat("Buffer", modifiers.bufferMultiplier());
        tag.put("Modifiers", modifierTag);

        ListTag roleList = new ListTag();
        for (Map.Entry<BlockPos, BlockRole> entry : roles.entrySet()) {
            CompoundTag roleTag = new CompoundTag();
            roleTag.putLong("Pos", entry.getKey().asLong());
            roleTag.putString("Role", entry.getValue().name());
            roleList.add(roleTag);
        }
        tag.put("Roles", roleList);

        CompoundTag fillerTag = new CompoundTag();
        for (Map.Entry<FillerKind, Integer> entry : fillers.entrySet()) {
            if (entry.getValue() > 0) {
                fillerTag.putInt(entry.getKey().id(), entry.getValue());
            }
        }
        tag.put("Fillers", fillerTag);
        return tag;
    }

    /** Reads a snapshot written by {@link #save}, ignoring unknown future values. */
    public static StructureEvaluation load(CompoundTag tag) {
        EnergyTier tier = null;
        String tierName = tag.getString("Tier");
        for (EnergyTier candidate : EnergyTier.values()) {
            if (candidate.getSerializedName().equals(tierName)) {
                tier = candidate;
                break;
            }
        }

        ModifierSet modifiers = ModifierSet.BASE;
        if (tag.contains("Modifiers", Tag.TAG_COMPOUND)) {
            CompoundTag modifierTag = tag.getCompound("Modifiers");
            modifiers = new ModifierSet(
                    floatOrDefault(modifierTag, "Speed", ModifierSet.BASE.speedMultiplier()),
                    floatOrDefault(modifierTag, "Energy", ModifierSet.BASE.energyMultiplier()),
                    modifierTag.getInt("Parallel"),
                    floatOrDefault(modifierTag, "Buffer", ModifierSet.BASE.bufferMultiplier()));
        }

        Map<BlockPos, BlockRole> roles = new HashMap<>();
        ListTag roleList = tag.getList("Roles", Tag.TAG_COMPOUND);
        for (int index = 0; index < roleList.size(); index++) {
            CompoundTag roleTag = roleList.getCompound(index);
            try {
                roles.put(BlockPos.of(roleTag.getLong("Pos")), BlockRole.valueOf(roleTag.getString("Role")));
            } catch (IllegalArgumentException ignored) {
                // A removed role must not make an existing world unloadable.
            }
        }

        Map<FillerKind, Integer> fillers = new EnumMap<>(FillerKind.class);
        CompoundTag fillerTag = tag.getCompound("Fillers");
        for (FillerKind kind : FillerKind.values()) {
            int count = fillerTag.getInt(kind.id());
            if (count > 0) {
                fillers.put(kind, count);
            }
        }

        return tag.getBoolean("Formed")
                ? formed(tier, modifiers, roles, fillers)
                : failed(tier, List.of(), roles, fillers);
    }

    private static float floatOrDefault(CompoundTag tag, String key, float fallback) {
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return fallback;
        }
        float value = tag.getFloat(key);
        return Float.isFinite(value) ? value : fallback;
    }
}
