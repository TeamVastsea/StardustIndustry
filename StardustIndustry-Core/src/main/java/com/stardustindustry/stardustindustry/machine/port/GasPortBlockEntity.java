package com.stardustindustry.stardustindustry.machine.port;

import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The gas port: exposes the bound machine's gas tanks to pipes.
 *
 * <p>The exact twin of {@link FluidPortBlockEntity}. This subclass exists only to
 * fix the {@link PartRole} and to give the capability registration a concrete
 * type to attach to. All behaviour is in {@link MachinePortBlockEntity}.</p>
 */
public class GasPortBlockEntity extends MachinePortBlockEntity {

    public GasPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_PORT.get(), pos, state, PartRole.PORT_GAS);
    }
}
