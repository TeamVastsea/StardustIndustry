package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client request to string a cable between two terminals.
 *
 * <p>The pending "start" end is client-local ({@code PendingCable}), so the server
 * never knows where the player began. The client therefore sends <b>both</b>
 * endpoints: the one it believes it started from, and the one just clicked.</p>
 *
 * <p>Like every payload in this mod this is a <b>hint, not an instruction</b>. The
 * handler re-derives everything from the server world — that both blocks are
 * terminal mounts, that both cells really hold terminals, that the tiers match,
 * and that neither end is already carrying a cable. A forged or replayed packet
 * can therefore at most produce the same cable the player could have pulled by
 * hand; it cannot create one through a wall or between terminals that do not
 * exist.</p>
 *
 * @param fromPos  block of the terminal the player started from
 * @param fromCell grid cell of that terminal
 * @param toPos    block of the terminal just clicked
 * @param toCell   grid cell of that terminal
 */
public record ConnectCablePayload(BlockPos fromPos, int fromCell, BlockPos toPos, int toCell)
        implements CustomPacketPayload {

    /** The packet id, namespaced to this mod. */
    public static final CustomPacketPayload.Type<ConnectCablePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    com.stardustindustry.stardustindustry.StardustIndustry.MODID, "connect_cable"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectCablePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConnectCablePayload::fromPos,
                    ByteBufCodecs.VAR_INT, ConnectCablePayload::fromCell,
                    BlockPos.STREAM_CODEC, ConnectCablePayload::toPos,
                    ByteBufCodecs.VAR_INT, ConnectCablePayload::toCell,
                    ConnectCablePayload::new);

    /** The starting endpoint, as a value. */
    public TerminalEndpoint start() {
        return new TerminalEndpoint(fromPos, fromCell);
    }

    /** The just-clicked endpoint, as a value. */
    public TerminalEndpoint end() {
        return new TerminalEndpoint(toPos, toCell);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
