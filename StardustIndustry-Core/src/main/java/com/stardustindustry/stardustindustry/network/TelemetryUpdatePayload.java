package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server-to-client update for the one telemetry menu the player currently has open. */
public record TelemetryUpdatePayload(int containerId, ElectricalTelemetrySnapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<TelemetryUpdatePayload> TYPE =
            new Type<>(StardustIndustry.id("telemetry_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelemetryUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TelemetryUpdatePayload::containerId,
                    ElectricalTelemetrySnapshot.STREAM_CODEC, TelemetryUpdatePayload::snapshot,
                    TelemetryUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
