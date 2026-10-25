package com.stardustindustry.stardustindustry.energy;

import java.util.Objects;

import net.minecraft.core.BlockPos;

/** Stable identity for one logical electrical port assembled from physical ports or faces. */
public record ElectricalPortGroupKey(BlockPos owner, String channel) {
    public ElectricalPortGroupKey {
        owner = Objects.requireNonNull(owner, "owner").immutable();
        channel = Objects.requireNonNull(channel, "channel");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
    }
}
