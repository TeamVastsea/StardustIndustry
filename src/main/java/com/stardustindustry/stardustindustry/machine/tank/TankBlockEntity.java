package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineTier;
import com.stardustindustry.stardustindustry.machine.module.FluidBufferModule;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider;
import com.stardustindustry.stardustindustry.multiblock.provider.TankStructureProvider;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tank: a hollow rectangular container for a single fluid.
 *
 * <h2>No tier</h2>
 * Unlike a processing machine, a tank has no voltage tier. It is a vessel, not a
 * machine: its frame and shell are inert, its capacity comes from the volume it
 * encloses, and the only thing its ports change is how fast fluid moves in and
 * out. For that reason the module set is a single fluid buffer, with no energy
 * buffer and no idle draw.
 *
 * <h2>Capacity</h2>
 * The fluid module's capacity is not fixed but read through an override
 * ({@link #currentCapacityMb()}), so a tank that is rebuilt a size larger holds
 * more on the very next tick without a restart.
 *
 * <h2>Formation</h2>
 * A tank forms on its own, with no installation step: the shell is a closed box
 * that the player keeps editing, and a dynamic machine that locks itself would
 * fight that. Because the player gets no confirmation from a button press, a
 * newly formed tank announces itself to nearby players.
 */
public class TankBlockEntity extends MachineBlockEntity {

    private final FluidBufferModule fluid;

    /** The box corners last synced to the client, for the level renderer and the HUD. */
    private BlockPos syncedMin;
    private BlockPos syncedMax;
    /** The capacity last synced to the client, in mB. */
    private int syncedCapacityMb;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        // The tank is not electric, so the machine tier is nominal; the tank
        // provider never reports a tier and the module set has no energy buffer.
        super(ModBlockEntities.TANK_SHELL.get(), pos, state, MachineTier.LV);

        this.fluid = modules().provide(ResourceType.FLUID,
                modules().add(new FluidBufferModule(1)));
        this.fluid.setCapacityOverride(this::currentCapacityMb);

        initialiseModules();
    }

    @Override
    public StructureProvider provider() {
        return TankStructureProvider.INSTANCE;
    }

    public FluidBufferModule fluid() {
        return fluid;
    }

    /**
     * The tank's capacity in mB.
     *
     * <p>On the server this is derived live from the current structure
     * evaluation, so resizing the tank takes effect immediately. On the client
     * there is no evaluation (the server owns it), so the capacity the server
     * shipped in the update tag is used instead.</p>
     */
    public int currentCapacityMb() {
        StructureEvaluation evaluation = evaluation();
        if (evaluation != null && evaluation.formed()) {
            return TankStructureProvider.capacityMb(evaluation);
        }
        return level != null && level.isClientSide() ? syncedCapacityMb : 0;
    }

    /** The box corner the client last received, or {@code null} when none has arrived. */
    public BlockPos syncedMin() {
        return syncedMin;
    }

    /** The opposite box corner the client last received, or {@code null}. */
    public BlockPos syncedMax() {
        return syncedMax;
    }

    /**
     * Announces a newly formed tank, since the player built it without pressing
     * anything and otherwise gets no confirmation that it worked.
     */
    @Override
    protected void onFormedChanged(boolean nowFormed) {
        if (!nowFormed) {
            return;
        }
        int[] size = evaluatedSize();
        if (size.length != 3) {
            return;
        }
        int volume = Math.max(0, size[0] - 2) * Math.max(0, size[1] - 2) * Math.max(0, size[2] - 2);
        int capacity = volume * com.stardustindustry.stardustindustry.Config.TANK_BUCKETS_PER_AIR_BLOCK.get() * 1000;
        broadcastNearby(Component.translatable(
                "message.stardustindustry.tank.formed", size[0], size[1], size[2], capacity), 24.0);
    }

    // ---- client sync ----

    @Override
    protected void saveMachine(CompoundTag tag, HolderLookup.Provider registries) {
        StructureEvaluation evaluation = evaluation();
        if (evaluation != null && evaluation.formed() && !evaluation.roles().isEmpty()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : evaluation.roles().keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            tag.putIntArray("TankBox", new int[] {minX, minY, minZ, maxX, maxY, maxZ});
            tag.putInt("TankCapacity", TankStructureProvider.capacityMb(evaluation));
        }
    }

    @Override
    protected void loadMachine(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("TankBox")) {
            int[] box = tag.getIntArray("TankBox");
            if (box.length == 6) {
                syncedMin = new BlockPos(box[0], box[1], box[2]);
                syncedMax = new BlockPos(box[3], box[4], box[5]);
            }
        }
        syncedCapacityMb = tag.getInt("TankCapacity");
    }
}
