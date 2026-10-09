package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * A tank's shell block, which doubles as its controller.
 *
 * <p>A tank is a closed rectangular box: twelve frame edges and six faces. One
 * of the face cells is a shell block carrying the block entity, and the machine
 * is anchored there. Because the anchor is not a special hole but just one more
 * face cell, a player can finish the wall and the tank still forms — there is no
 * separate controller block to leave a gap for.</p>
 *
 * <p>The same block class serves both media; the {@link TankMedium} it is
 * constructed with picks the block-entity type, so a fluid shell belongs to a
 * fluid tank and a gas shell to a gas tank. That keeps the two shells one piece
 * of code while letting them be two registered blocks with their own models.</p>
 *
 * <p>The block itself is thin: it names its block entity type and inherits the
 * placement, neighbour and tick plumbing from {@link MachineBlock}. The
 * structure comes from the block entity's provider.</p>
 */
public class TankShellBlock extends MachineBlock {

    private final TankMedium medium;

    public TankShellBlock(TankMedium medium, Properties properties) {
        super(properties);
        this.medium = medium;
    }

    /** The medium this shell belongs to. */
    public TankMedium medium() {
        return medium;
    }

    @Override
    public BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return medium == TankMedium.GAS
                ? ModBlockEntities.GAS_TANK_SHELL.get()
                : ModBlockEntities.FLUID_TANK_SHELL.get();
    }
}
