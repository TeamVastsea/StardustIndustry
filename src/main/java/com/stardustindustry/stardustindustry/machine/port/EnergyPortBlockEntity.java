package com.stardustindustry.stardustindustry.machine.port;

import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The energy port: exposes the bound machine's FE buffer to cables.
 *
 * <p>This subclass exists only to fix the {@link PartRole} and to give the
 * capability registration a concrete type to attach to. All behaviour is in
 * {@link MachinePortBlockEntity}.</p>
 */
public class EnergyPortBlockEntity extends MachinePortBlockEntity {

    public EnergyPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_PORT.get(), pos, state, PartRole.PORT_ENERGY);
    }
}
