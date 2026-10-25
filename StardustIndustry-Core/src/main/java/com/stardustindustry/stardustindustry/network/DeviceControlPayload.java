package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.StardustIndustry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Validated control request from an open electrical-device screen. */
public record DeviceControlPayload(BlockPos pos, Action action) implements CustomPacketPayload {
    public enum Action {
        TOGGLE_SWITCH,
        CYCLE_CONFIGURATION,
        CYCLE_CREATIVE_TIER
    }

    public static final Type<DeviceControlPayload> TYPE =
            new Type<>(StardustIndustry.id("device_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceControlPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DeviceControlPayload decode(RegistryFriendlyByteBuf buffer) {
                    int ordinal = ByteBufCodecs.VAR_INT.decode(buffer);
                    Action[] actions = Action.values();
                    Action action = ordinal >= 0 && ordinal < actions.length
                            ? actions[ordinal] : Action.TOGGLE_SWITCH;
                    return new DeviceControlPayload(BlockPos.STREAM_CODEC.decode(buffer), action);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, DeviceControlPayload value) {
                    ByteBufCodecs.VAR_INT.encode(buffer, value.action.ordinal());
                    BlockPos.STREAM_CODEC.encode(buffer, value.pos);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
