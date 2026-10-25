package com.stardustindustry.stardustindustry.network;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.energy.CableRunId;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-selected terminal or cable target for an electrician's meter session. */
public record ElectricalDiagnosticRequestPayload(BlockPos rackPos, CableRunId cableRun)
        implements CustomPacketPayload {

    public static final Type<ElectricalDiagnosticRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardustIndustry.MODID, "electrical_diagnostic_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ElectricalDiagnosticRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ElectricalDiagnosticRequestPayload decode(RegistryFriendlyByteBuf buffer) {
                    BlockPos rackPos = BlockPos.STREAM_CODEC.decode(buffer);
                    CableRunId cableRun = buffer.readBoolean() ? CableRunId.STREAM_CODEC.decode(buffer) : null;
                    return new ElectricalDiagnosticRequestPayload(rackPos, cableRun);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer,
                                   ElectricalDiagnosticRequestPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buffer, payload.rackPos());
                    buffer.writeBoolean(payload.cableRun() != null);
                    if (payload.cableRun() != null) {
                        CableRunId.STREAM_CODEC.encode(buffer, payload.cableRun());
                    }
                }
            };

    public static ElectricalDiagnosticRequestPayload terminal(BlockPos rackPos) {
        return new ElectricalDiagnosticRequestPayload(rackPos, null);
    }

    public static ElectricalDiagnosticRequestPayload cable(BlockPos rackPos, CableRunId cableRun) {
        return new ElectricalDiagnosticRequestPayload(rackPos, cableRun);
    }

    /** A cable request is meaningful only when the clicked rack owns one endpoint. */
    public boolean targetsCableOnClickedRack() {
        return cableRun == null || cableRun.touches(rackPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
