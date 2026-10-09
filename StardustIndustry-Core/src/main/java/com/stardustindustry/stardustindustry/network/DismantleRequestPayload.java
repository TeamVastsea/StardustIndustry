package com.stardustindustry.stardustindustry.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client request to dismantle a machine it has open.
 *
 * <p>The payload carries only a position. It is deliberately not trusted: the
 * server handler re-checks that the player is close enough and that the block is
 * really an installed machine before acting. A packet is a hint to the server,
 * never an instruction.</p>
 *
 * @param controllerPos the controller the client believes it has open
 */
public record DismantleRequestPayload(BlockPos controllerPos) implements CustomPacketPayload {

    /** The packet id, namespaced to this mod. */
    public static final CustomPacketPayload.Type<DismantleRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    com.stardustindustry.stardustindustry.StardustIndustry.MODID, "dismantle_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DismantleRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DismantleRequestPayload::controllerPos,
                    DismantleRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
