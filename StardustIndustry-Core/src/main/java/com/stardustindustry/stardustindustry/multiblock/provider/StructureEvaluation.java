package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.List;
import java.util.Map;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;

import net.minecraft.core.BlockPos;

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
}
