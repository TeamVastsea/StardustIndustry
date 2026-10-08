package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A structure cell the machine has hidden: no model, but a full collision box.
 *
 * <p>When a static machine is installed, each body block is replaced by this
 * one. The client therefore sees nothing there, and the controller's block
 * entity renderer draws the whole machine as a single model. Invisible does not
 * mean gone: the cell keeps a full cube collision so the player is stopped by
 * the machine they can see, and cannot walk through its body.</p>
 *
 * <p>The block still exists in the world, so the machine keeps owning its
 * footprint: it cannot be overwritten by accident, and breaking it is what tells
 * the controller to dismantle the machine. The {@code LIT} property lets the
 * block carry a visual/debug state without a second block type.</p>
 */
public class InvisibleStructureBlock extends Block implements EntityBlock {

    /** Present so the block has a mutable state to sync; unused for rendering. */
    public static final BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    private BlockEntityType<? extends InvisibleStructureBlockEntity> typeSupplier;

    public InvisibleStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** Wires the block-entity type after registration, avoiding a class-init cycle. */
    public InvisibleStructureBlock withBlockEntityType(BlockEntityType<? extends InvisibleStructureBlockEntity> type) {
        this.typeSupplier = type;
        return this;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (typeSupplier == null) {
            throw new IllegalStateException("Invisible structure block used before its block entity type was wired");
        }
        return typeSupplier.create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The cell is invisible, not absent: the player is stopped by the machine
        // they can see. Without this the body would be a hole one could walk
        // through while the redrawn model says otherwise.
        return Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // No highlight/selection outline: there is nothing to outline, and the
        // player should aim at the controller's drawn model, not the hidden cell.
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public BlockState playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                        net.minecraft.world.entity.player.Player player) {
        // Breaking any hidden cell takes the whole machine down: restore every
        // other cell and destroy whatever the machine was holding. The broken
        // cell itself is left to vanilla so the player still gets that block.
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InvisibleStructureBlockEntity invisible) {
            MachineBlockEntity controller = invisible.controller();
            if (controller != null) {
                controller.onStructureBroken(pos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
