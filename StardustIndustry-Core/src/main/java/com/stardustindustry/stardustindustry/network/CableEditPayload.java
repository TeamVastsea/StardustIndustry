package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A client request to re-colour or cut one existing cable.
 *
 * <h2>Why the cable has to be identified by its run</h2>
 * <p>A terminal may carry any number of cables, so "the cable on this terminal" is
 * ambiguous once there are two. The client sends the endpoints plus the
 * conductor and sheath class — everything that decides <b>which</b> cable is meant
 * — and the server looks it up on both racks. Colour is deliberately <b>not</b> part
 * of the identification: for a repaint it is what is changing, and for a cut it is
 * irrelevant.</p>
 *
 * <h2>Request, not command</h2>
 * <p>As with every payload here, the server re-derives the cable from its own
 * world: it must exist on the rack at {@code pos}, be within reach, and (for a
 * repaint) the player must be holding the dye that names the colour. A forged
 * packet can therefore at most repaint a cable the player could have repainted by
 * hand.</p>
 *
 * @param pos        the rack the click landed on
 * @param fromPos    one end of the cable
 * @param fromCell   grid cell of that end
 * @param toPos      the other end
 * @param toCell     grid cell of the other end
 * @param ampacity   conductor class of the cable
 * @param insulation sheath class of the cable
 * @param dyeIndex   dye index to paint with, or {@code -1} to cut the cable
 */
public record CableEditPayload(BlockPos pos, BlockPos fromPos, int fromCell, BlockPos toPos, int toCell,
                               CableAmpacity ampacity, CableInsulation insulation, int dyeIndex)
        implements CustomPacketPayload {

    /** Marks "cut this cable" in {@link #dyeIndex}. */
    public static final int CUT = -1;

    /** The packet id, namespaced to this mod. */
    public static final CustomPacketPayload.Type<CableEditPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    com.stardustindustry.stardustindustry.StardustIndustry.MODID, "cable_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CableEditPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CableEditPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new CableEditPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            BlockPos.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            CableAmpacity.byId(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                            CableInsulation.byId(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                            ByteBufCodecs.VAR_INT.decode(buffer));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CableEditPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                    BlockPos.STREAM_CODEC.encode(buffer, payload.fromPos());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.fromCell());
                    BlockPos.STREAM_CODEC.encode(buffer, payload.toPos());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.toCell());
                    // Classes travel as names, not ordinals: a name survives enum
                    // reordering and is readable in a packet dump. An unknown name
                    // decodes to null and the handler ignores it.
                    ByteBufCodecs.STRING_UTF8.encode(buffer,
                            payload.ampacity() == null ? "" : payload.ampacity().id());
                    ByteBufCodecs.STRING_UTF8.encode(buffer,
                            payload.insulation() == null ? "" : payload.insulation().id());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.dyeIndex());
                }
            };

    /** True when the classes came off the wire as valid values. */
    public boolean isValid() {
        return ampacity != null && insulation != null;
    }

    /** True when this request cuts rather than paints. */
    public boolean isCut() {
        return dyeIndex == CUT;
    }

    /** The clicked end, as a value. */
    public TerminalEndpoint start() {
        return new TerminalEndpoint(fromPos, fromCell);
    }

    /** The far end, as a value. */
    public TerminalEndpoint end() {
        return new TerminalEndpoint(toPos, toCell);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
