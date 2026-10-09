package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.multiblock.PartRole;

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
 * The block entity behind every machine port.
 *
 * <h2>Binding</h2>
 * A port does not search for its machine. When a controller's structure forms,
 * the controller walks the matched placement map and writes its own position
 * into each port's block entity, and clears it again when the structure breaks.
 * A port therefore always knows exactly which controller it serves, and can
 * forward capability requests to it in constant time.</p>
 *
 * <h2>Capabilities</h2>
 * The port itself holds no storage. Capability registration (see
 * {@code ModCapabilities}) asks a port for its bound controller and returns the
 * controller module's capability, so from a pipe's point of view the port is the
 * machine. Nothing is duplicated, so a full inventory is full for every port at
 * once.</p>
 */
public class MachinePortBlockEntity extends BlockEntity {

    /** The role this port satisfies in a structure. */
    private final PartRole role;
    private BlockPos controllerPos;

    public MachinePortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, PartRole role) {
        super(type, pos, state);
        this.role = role;
    }

    public PartRole role() {
        return role;
    }

    /** The controller this port currently serves, or {@code null} when unbound. */
    public BlockPos controllerPos() {
        return controllerPos;
    }

    /**
     * The bound controller, resolved to a live block entity. Returns {@code null}
     * when the port is unbound or the controller has since been removed, which is
     * exactly the condition under which the port should report no capability.
     */
    public MachineBlockEntity controller() {
        if (controllerPos == null || level == null) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof MachineBlockEntity machine ? machine : null;
    }

    /** Called by the controller when the structure forms. */
    public void bind(BlockPos controllerPos) {
        this.controllerPos = controllerPos.immutable();
        setChanged();
    }

    /** Called by the controller when the structure breaks, or before re-binding. */
    public void unbind() {
        this.controllerPos = null;
        setChanged();
    }

    public boolean isBound() {
        return controllerPos != null;
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
