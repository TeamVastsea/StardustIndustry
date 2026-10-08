package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The read-only parameter snapshot a machine sends when its parameter screen is
 * opened.
 *
 * <p>The screen must show numbers (tier, speed, energy, buffers) that the client
 * cannot compute, so the server packs them once at open time and the screen
 * renders them verbatim. Keeping the snapshot a separate record rather than a
 * pile of {@code ContainerData} slots means the screen never guesses an index,
 * and adding a displayed value is one field here plus one line in the codec.</p>
 *
 * <p>A snapshot is immutable and carries no reference to the world; it is only
 * ever a display artefact. Everything the player can <em>change</em> goes
 * through an explicit request packet instead.</p>
 *
 * @param controllerPos the machine this screen belongs to
 * @param installed     whether the machine has been installed (and can be dismantled)
 * @param formed        whether the structure is currently complete
 * @param tierName      the structure tier's serialised name, or {@code ""} when unknown
 * @param speed         the speed multiplier, {@code 1.0} baseline
 * @param energy        the energy multiplier, {@code 1.0} baseline
 * @param parallel      the parallel bonus over the machine's own slots
 * @param buffer        the buffer multiplier, {@code 1.0} baseline
 * @param energyStored  current FE in the buffer, or {@code -1} when there is none
 * @param energyMax     buffer capacity in FE, or {@code -1} when there is none
 * @param fillerCount   total number of filler blocks counted
 * @param fluidName     the translation key of the held fluid, or {@code ""} when empty/none
 * @param fluidAmount   mB currently held, or {@code -1} when the machine has no tank
 * @param fluidCapacity mB the tank can hold, or {@code -1} when there is none
 * @param fluidInRate   mB/s the tank last accepted, {@code 0} without a tank
 * @param fluidOutRate  mB/s the tank last released, {@code 0} without a tank
 */
public record MachineParamsData(
        BlockPos controllerPos,
        boolean installed,
        boolean formed,
        String tierName,
        float speed,
        float energy,
        int parallel,
        float buffer,
        int energyStored,
        int energyMax,
        int fillerCount,
        String fluidName,
        int fluidAmount,
        int fluidCapacity,
        int fluidInRate,
        int fluidOutRate) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineParamsData> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, data) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, data.controllerPos());
                        buffer.writeBoolean(data.installed());
                        buffer.writeBoolean(data.formed());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, data.tierName());
                        buffer.writeFloat(data.speed());
                        buffer.writeFloat(data.energy());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.parallel());
                        buffer.writeFloat(data.buffer());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.energyStored());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.energyMax());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.fillerCount());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, data.fluidName());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.fluidAmount());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.fluidCapacity());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.fluidInRate());
                        ByteBufCodecs.VAR_INT.encode(buffer, data.fluidOutRate());
                    },
                    buffer -> new MachineParamsData(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            buffer.readFloat(),
                            buffer.readFloat(),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            buffer.readFloat(),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer)));
}
