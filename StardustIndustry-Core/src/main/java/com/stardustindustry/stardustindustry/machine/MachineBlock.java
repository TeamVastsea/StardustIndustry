package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.multiblock.StructureDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Base block for every machine.
 *
 * <h2>Role</h2>
 * The block is deliberately thin: it creates the block entity, owns the
 * horizontal facing used to rotate a multiblock, and forwards neighbour and
 * tick events. All behaviour lives in the block entity's modules, so a new
 * machine is a block-entity class and a block instance rather than a new block
 * class per machine.
 *
 * <h2>Facing</h2>
 * Every machine carries a {@link BlockStateProperties#HORIZONTAL_FACING}
 * property so its structure knows which way the authored layout points.
 * Rotation and mirroring use the standard overrides, so a machine placed by a
 * structure block or pushed by a piston keeps a correct front. The property is
 * always part of the state definition; machines simply ignore it when they are
 * symmetric.
 */
public abstract class MachineBlock extends Block implements EntityBlock {

    /** The direction a machine's front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MachineBlock(Properties properties) {
        super(properties);
        // The state definition already contains FACING because
        // createBlockStateDefinition ran during the super constructor; this only
        // fixes the default value.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** The block entity type this block creates. */
    public abstract BlockEntityType<? extends MachineBlockEntity> blockEntityType();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The machine faces the player, so the front is what the player sees.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType().create(pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.onNeighborChanged();
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof MachineBlockEntity machine) {
                machine.tick(tickLevel, pos, tickState);
            }
        };
    }

    /** Allows subclasses to declare a structure statically for tooling. */
    protected StructureDefinition structure() {
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Ask the controller to validate immediately so a correctly built shell
        // forms on placement rather than after the first heartbeat.
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.requestStructureCheck();
        }
    }
}
