package com.stardustindustry.stardustindustry.energy;

import java.util.Objects;

import net.minecraft.core.BlockPos;

/** Stable identity for energy and fractional-watt accounting across block-entity reloads. */
public record ElectricalAccountKey(BlockPos owner, String channel) {
    public ElectricalAccountKey {
        owner = Objects.requireNonNull(owner, "owner").immutable();
        channel = Objects.requireNonNull(channel, "channel");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
    }
}
