package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity behind an {@link InvisibleStructureBlock}.
 *
 * <p>It exists only to remember which controller owns the cell, so that breaking
 * any single structure block can find the machine and dismantle it as a whole.
 * It holds no storage and does no ticking.</p>
 */
public class InvisibleStructureBlockEntity extends BlockEntity {

    private BlockPos controllerPos;

    public InvisibleStructureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Constructor shape {@link BlockEntityType.Builder#of} expects. */
    public InvisibleStructureBlockEntity(BlockPos pos, BlockState state) {
        this(com.stardustindustry.stardustindustry.registry.ModBlockEntities.INVISIBLE_STRUCTURE.get(), pos, state);
    }

    /** Points this cell at the controller that hid it. */
    public void bind(BlockPos controllerPos) {
        this.controllerPos = controllerPos.immutable();
        setChanged();
    }

    /** The owning controller, or {@code null} when unbound or already removed. */
    public MachineBlockEntity controller() {
        if (controllerPos == null || level == null) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof MachineBlockEntity machine ? machine : null;
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong("Controller", controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
