package com.stardustindustry.stardustindustry.machine.port;

import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The item port: exposes the bound machine's inventory to automation.
 *
 * <p>This subclass exists only to fix the {@link PartRole} and to give the
 * capability registration a concrete type to attach to. All behaviour is in
 * {@link MachinePortBlockEntity}.</p>
 */
public class ItemPortBlockEntity extends MachinePortBlockEntity {

    public ItemPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PORT.get(), pos, state, PartRole.PORT_ITEM);
    }
}
