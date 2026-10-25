package com.stardustindustry.stardustindustry.machine.port;

import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The item port: exposes the bound machine's inventory to automation.
 *
 * <p>This subclass exists only to fix the {@link PartRole} and to give the
 * capability registration a concrete type to attach to. All behaviour is in
 * {@link MachinePortBlockEntity}.</p>
 */
public class ItemPortBlockEntity extends MachinePortBlockEntity {
    private ItemPortMode mode = ItemPortMode.BOTH;

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state, PartRole.PORT_ITEM);
    }

    public ItemPortMode mode() {
        return mode;
    }

    public boolean setMode(ItemPortMode mode) {
        ItemPortMode next = mode == null ? ItemPortMode.BOTH : mode;
        if (this.mode == next) {
            return false;
        }
        this.mode = next;
        invalidatePortCapabilities();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    public ItemPortMode cycleMode() {
        setMode(mode.next());
        return mode;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ItemPortMode", mode.id());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = ItemPortMode.byId(tag.getString("ItemPortMode"));
    }
}
