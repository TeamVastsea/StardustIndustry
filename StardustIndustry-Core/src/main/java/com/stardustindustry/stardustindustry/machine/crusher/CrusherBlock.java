package com.stardustindustry.stardustindustry.machine.crusher;

import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block form of the starter crusher.
 *
 * <p>A thin block: it only names its block entity type and inherits all
 * behaviour from {@link MachineBlock}. Machine logic lives in the block
 * entity's modules, never here.</p>
 */
public class CrusherBlock extends MachineBlock {

    public CrusherBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends com.stardustindustry.stardustindustry.machine.MachineBlockEntity> blockEntityType() {
        return ModBlockEntities.CRUSHER.get();
    }
}
