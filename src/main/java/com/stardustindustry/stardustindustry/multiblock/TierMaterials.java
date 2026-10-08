package com.stardustindustry.stardustindustry.multiblock;

import java.util.EnumMap;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps the blocks that make up a machine's level-bearing parts to a voltage tier.
 *
 * <p>Tiers are expressed by <em>naming</em>, not by material: the blocks are
 * literally {@code lv_base}, {@code mv_frame}, {@code hv_item_port} and so on,
 * and which crafting recipe produces each one is what decides progression. This
 * keeps art and recipes free and means a tier never has to be inferred from a
 * metal's real-world "feel".</p>
 *
 * <p>Registration is explicit and centralised so a new tier variant is one line
 * here, never a change scattered across the machine code. Lookups are by
 * {@link Block}, which is stable across block states.</p>
 */
public final class TierMaterials {

    /** Tiers that may appear on level-bearing parts, in ascending order. */
    public static final EnergyTier[] TIERS = {
            EnergyTier.LV, EnergyTier.MV, EnergyTier.HV, EnergyTier.EHV
    };

    private static final Map<Block, EnergyTier> BY_BLOCK = new java.util.HashMap<>();

    private TierMaterials() {}

    /**
     * Declares that {@code block} belongs to {@code tier}.
     *
     * <p>Called during registration for every base, frame and port block. A
     * block registered twice is a programming error and fails fast rather than
     * silently preferring one tier.</p>
     */
    public static void register(Block block, EnergyTier tier) {
        EnergyTier previous = BY_BLOCK.put(block, tier);
        if (previous != null && previous != tier) {
            throw new IllegalStateException("Block " + block + " already mapped to " + previous + ", cannot remap to " + tier);
        }
    }

    /** The tier of {@code block}, or {@code null} when it is not a level-bearing block. */
    public static EnergyTier tierOf(Block block) {
        return BY_BLOCK.get(block);
    }

    /** The tier of {@code state}'s block, or {@code null}. */
    public static EnergyTier tierOf(BlockState state) {
        return tierOf(state.getBlock());
    }

    /** True when {@code block} carries a level. */
    public static boolean isLevelBearing(Block block) {
        return BY_BLOCK.containsKey(block);
    }

    /** True when every non-null tier in {@code tiers} is the same. Nulls are ignored. */
    public static boolean allSame(Iterable<EnergyTier> tiers) {
        EnergyTier seen = null;
        for (EnergyTier tier : tiers) {
            if (tier == null) {
                continue;
            }
            if (seen == null) {
                seen = tier;
            } else if (seen != tier) {
                return false;
            }
        }
        return true;
    }

    /** The unique tier present in {@code tiers}, or {@code null} when none or several are present. */
    public static EnergyTier commonTier(Iterable<EnergyTier> tiers) {
        EnergyTier seen = null;
        for (EnergyTier tier : tiers) {
            if (tier == null) {
                continue;
            }
            if (seen == null) {
                seen = tier;
            } else if (seen != tier) {
                return null;
            }
        }
        return seen;
    }

    /** Snapshot of the whole mapping, mainly for diagnostics and tests. */
    public static Map<EnergyTier, Integer> countsByTier() {
        Map<EnergyTier, Integer> counts = new EnumMap<>(EnergyTier.class);
        for (EnergyTier tier : BY_BLOCK.values()) {
            counts.merge(tier, 1, Integer::sum);
        }
        return counts;
    }
}
