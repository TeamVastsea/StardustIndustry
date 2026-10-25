package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.machine.storage.StorageMode;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests one explicit storage policy; the server validates the open station menu. */
public record StorageModePayload(BlockPos pos, StorageMode mode) implements CustomPacketPayload {
    public static final Type<StorageModePayload> TYPE =
            new Type<>(StardustIndustry.id("storage_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageModePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public StorageModePayload decode(RegistryFriendlyByteBuf buffer) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
                    return new StorageModePayload(pos,
                            StorageMode.byId(ByteBufCodecs.STRING_UTF8.decode(buffer)));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, StorageModePayload value) {
                    BlockPos.STREAM_CODEC.encode(buffer, value.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.mode.id());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
