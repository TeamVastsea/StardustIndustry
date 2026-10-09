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
 * <p>A machine that has a more specific story to tell attaches a
 * {@link TankParams} block to the snapshot; the generic fields above stay
 * meaningful for every machine, and a screen that does not understand the extra
 * block simply ignores it. That keeps one snapshot type while letting, say, a
 * tank show its size and its ports rather than a speed multiplier it does not
 * have.</p>
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
 * @param tank          the tank-specific display block, or {@code null} for other machines
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
        int fluidOutRate,
        TankParams tank) {

    /**
     * The extra lines a tank shows.
     *
     * <p>Fluid names are carried as <em>translation keys</em> rather than as
     * already-resolved text. The server has no language of the player's, so a
     * name resolved there would arrive in the wrong locale; shipping the key
     * lets the screen resolve the current language, and the English name beside
     * it, itself.</p>
     *
     * @param structureName  translation key suffix for the structure kind, e.g. {@code "tank"}
     * @param sizeX          size along X, or {@code 0} while unformed
     * @param sizeY          size along Y, or {@code 0} while unformed
     * @param sizeZ          size along Z, or {@code 0} while unformed
     * @param fluidKey       the held fluid's translation key, or {@code ""} when empty
     * @param fluidNameEn    the held fluid's English name, or {@code ""} when empty
     * @param ports          installed ports as {@code "tier:count"} pairs, e.g. {@code "lv:1,mv:2"}
     * @param maxRateTier    the fastest installed port's tier name, or {@code ""} when none
     * @param maxRateMbPerTick that port's throughput in mB/t, or {@code 0} when none
     */
    public record TankParams(
            String structureName,
            int sizeX,
            int sizeY,
            int sizeZ,
            String fluidKey,
            String fluidNameEn,
            String ports,
            String maxRateTier,
            int maxRateMbPerTick) {

        public static final StreamCodec<RegistryFriendlyByteBuf, TankParams> STREAM_CODEC =
                StreamCodec.of(
                        (buffer, params) -> {
                            ByteBufCodecs.STRING_UTF8.encode(buffer, params.structureName());
                            ByteBufCodecs.VAR_INT.encode(buffer, params.sizeX());
                            ByteBufCodecs.VAR_INT.encode(buffer, params.sizeY());
                            ByteBufCodecs.VAR_INT.encode(buffer, params.sizeZ());
                            ByteBufCodecs.STRING_UTF8.encode(buffer, params.fluidKey());
                            ByteBufCodecs.STRING_UTF8.encode(buffer, params.fluidNameEn());
                            ByteBufCodecs.STRING_UTF8.encode(buffer, params.ports());
                            ByteBufCodecs.STRING_UTF8.encode(buffer, params.maxRateTier());
                            ByteBufCodecs.VAR_INT.encode(buffer, params.maxRateMbPerTick());
                        },
                        buffer -> new TankParams(
                                ByteBufCodecs.STRING_UTF8.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer),
                                ByteBufCodecs.STRING_UTF8.decode(buffer),
                                ByteBufCodecs.STRING_UTF8.decode(buffer),
                                ByteBufCodecs.STRING_UTF8.decode(buffer),
                                ByteBufCodecs.STRING_UTF8.decode(buffer),
                                ByteBufCodecs.VAR_INT.decode(buffer)));
    }

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
                        buffer.writeBoolean(data.tank() != null);
                        if (data.tank() != null) {
                            TankParams.STREAM_CODEC.encode(buffer, data.tank());
                        }
                    },
                    buffer -> {
                        BlockPos controllerPos = BlockPos.STREAM_CODEC.decode(buffer);
                        boolean installed = buffer.readBoolean();
                        boolean formed = buffer.readBoolean();
                        String tierName = ByteBufCodecs.STRING_UTF8.decode(buffer);
                        float speed = buffer.readFloat();
                        float energy = buffer.readFloat();
                        int parallel = ByteBufCodecs.VAR_INT.decode(buffer);
                        float bufferMultiplier = buffer.readFloat();
                        int energyStored = ByteBufCodecs.VAR_INT.decode(buffer);
                        int energyMax = ByteBufCodecs.VAR_INT.decode(buffer);
                        int fillerCount = ByteBufCodecs.VAR_INT.decode(buffer);
                        String fluidName = ByteBufCodecs.STRING_UTF8.decode(buffer);
                        int fluidAmount = ByteBufCodecs.VAR_INT.decode(buffer);
                        int fluidCapacity = ByteBufCodecs.VAR_INT.decode(buffer);
                        int fluidInRate = ByteBufCodecs.VAR_INT.decode(buffer);
                        int fluidOutRate = ByteBufCodecs.VAR_INT.decode(buffer);
                        TankParams tank = buffer.readBoolean() ? TankParams.STREAM_CODEC.decode(buffer) : null;
                        return new MachineParamsData(controllerPos, installed, formed, tierName, speed, energy,
                                parallel, bufferMultiplier, energyStored, energyMax, fillerCount, fluidName,
                                fluidAmount, fluidCapacity, fluidInRate, fluidOutRate, tank);
                    });

    /** True when this snapshot carries a tank display block. */
    public boolean hasTank() {
        return tank != null;
    }
}
