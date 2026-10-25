package com.stardustindustry.stardustindustry.energy;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Product-independent identity of the physical run between two terminals.
 *
 * <p>A pair of terminals can carry at most one cable. Ampacity, insulation and
 * colour therefore describe that cable; they cannot create parallel records on
 * the same pair of endpoints.</p>
 */
public record CableRunId(TerminalEndpoint a, TerminalEndpoint b) implements Comparable<CableRunId> {

    public static final StreamCodec<RegistryFriendlyByteBuf, CableRunId> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public CableRunId decode(RegistryFriendlyByteBuf buffer) {
                    TerminalEndpoint first = new TerminalEndpoint(
                            BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
                    TerminalEndpoint second = new TerminalEndpoint(
                            BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer));
                    return new CableRunId(first, second);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, CableRunId value) {
                    BlockPos.STREAM_CODEC.encode(buffer, value.a().pos());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.a().cell());
                    BlockPos.STREAM_CODEC.encode(buffer, value.b().pos());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.b().cell());
                }
            };

    public CableRunId {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.compareTo(b) > 0) {
            TerminalEndpoint swap = a;
            a = b;
            b = swap;
        }
    }

    public static CableRunId of(CableConnection cable) {
        return new CableRunId(cable.a(), cable.b());
    }

    public boolean touches(BlockPos rackPos) {
        return a.pos().equals(rackPos) || b.pos().equals(rackPos);
    }

    public boolean touches(TerminalEndpoint endpoint) {
        return a.equals(endpoint) || b.equals(endpoint);
    }

    public TerminalEndpoint other(TerminalEndpoint endpoint) {
        if (a.equals(endpoint)) {
            return b;
        }
        if (b.equals(endpoint)) {
            return a;
        }
        return null;
    }

    @Override
    public int compareTo(CableRunId other) {
        int first = a.compareTo(other.a);
        return first != 0 ? first : b.compareTo(other.b);
    }
}
