package com.stardustindustry.stardustindustry.multiblock.provider;

import java.util.Collection;

import com.stardustindustry.stardustindustry.multiblock.StructureDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Supplies a machine with a structure evaluation, hiding whether the machine is
 * a fixed-shape "static" machine or an open-shape "dynamic" one.
 *
 * <p>The machine layer talks only to this interface. Two implementations exist:
 * a static provider that checks an authored {@link StructureDefinition}
 * per-cell, and a dynamic provider that scans a bounding box. Everything else —
 * modules, ports, capabilities, modifiers, projection — is shared, so adding a
 * new structure style means adding a provider rather than a machine base class.</p>
 */
public interface StructureProvider {

    /**
     * Evaluates the structure anchored at {@code controller}.
     *
     * <p>Implementations must be world-read-only: they never mutate blocks and
     * may be called from the client to decide whether to render a formed
     * overlay. The returned evaluation is a pure snapshot.</p>
     *
     * @param level      the level to read block states from
     * @param controller world position of the controller block
     * @param facing     the controller's current horizontal facing
     * @return the evaluation, formed or not
     */
    StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing);

    /**
     * Whether the structure should be re-evaluated while it is still formed.
     *
     * <p>A static machine locks itself once installed and returns {@code false};
     * a dynamic machine allows its shell to be edited in place and returns
     * {@code true}.</p>
     */
    default boolean revalidateWhileFormed() {
        return false;
    }

    /**
     * The chunks that must all be loaded for {@link #evaluate} to read truth.
     *
     * <p>A structure can straddle a chunk border, and a chunk that is not loaded
     * reads as air. Evaluating then sees the machine as torn down — it would
     * report unformed, unbind its ports and, worse, recompute a wrong capacity or
     * size — even though nothing was actually built or broken. The owner of the
     * structure therefore asks this before evaluating and simply <em>freezes</em>
     * the machine until every chunk it spans is loaded again.</p>
     *
     * <p>Returning {@code null} means "no restriction": the structure never reads
     * outside the controller's own chunk, so it can always be evaluated. Machines
     * that span a known box (a static model) or a bounded region (a dynamic scan)
     * override this to name the chunks they may touch.</p>
     *
     * @param controller world position of the controller block
     * @param facing     the controller's current horizontal facing
     * @return the required chunks, or {@code null} when there is no restriction
     */
    default Collection<ChunkPos> footprint(BlockPos controller, Direction facing) {
        return null;
    }
}
