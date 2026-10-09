package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One entry in a machine's <em>original state ledger</em>.
 *
 * <p>When a static machine is installed, its body blocks are swapped for
 * invisible ones so the client can draw a single large model. That means the
 * world no longer remembers what the player originally built. The ledger is
 * that memory: it records, for every position the machine consumed, the block
 * state that was there and what it was for.</p>
 *
 * <p>Dismantling replays the ledger in reverse, which is what makes "restore the
 * machine to exactly what it was" possible and testable.</p>
 *
 * @param worldPos       the position in the world
 * @param originalState  the block state that stood there before installation
 * @param role           what the position was (body, port, base slot, filler)
 */
public record PlacedPart(BlockPos worldPos, BlockState originalState, String role) {

    /** Writes this entry to {@code tag}. The block state is stored via the registry codec. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", worldPos.asLong());
        net.minecraft.world.level.block.state.BlockState.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, originalState)
                .resultOrPartial()
                .ifPresent(encoded -> tag.put("State", encoded));
        tag.putString("Role", role);
        return tag;
    }

    /**
     * Reads an entry written by {@link #save}, or {@code null} when the stored
     * state can no longer be resolved (a mod was removed): the caller then falls
     * back to skipping the position rather than crashing.
     */
    public static PlacedPart load(CompoundTag tag) {
        if (!tag.contains("State")) {
            return null;
        }
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        BlockState state = net.minecraft.world.level.block.state.BlockState.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("State"))
                .resultOrPartial()
                .orElse(null);
        if (state == null) {
            return null;
        }
        return new PlacedPart(pos, state, tag.getString("Role"));
    }
}
