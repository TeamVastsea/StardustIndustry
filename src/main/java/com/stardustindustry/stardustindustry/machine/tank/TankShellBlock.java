package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The tank's shell block, which doubles as its controller.
 *
 * <p>A tank is a closed rectangular box: twelve frame edges and six faces. One
 * of the face cells is a shell block carrying the block entity, and the machine
 * is anchored there. Because the anchor is not a special hole but just one more
 * face cell, a player can finish the wall and the tank still forms — there is no
 * separate controller block to leave a gap for.</p>
 *
 * <p>The block itself is thin: it names its block entity type and inherits the
 * placement, neighbour and tick plumbing from {@link MachineBlock}. The
 * structure comes from the block entity's provider.</p>
 *
 * <p>The shell renders as an ordinary cube. An earlier attempt gave the tank
 * panels connected textures so a wall would read as one plate; that is being
 * handled by a separate add-on mod instead (see {@code docs/connected-textures-plan.md}),
 * so the base mod keeps the plain block model.</p>
 */
public class TankShellBlock extends MachineBlock {

    public TankShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.TANK_SHELL.get();
    }
}
