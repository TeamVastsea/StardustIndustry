package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.energy.EnergyTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A level-bearing base block: what fills a base slot when the player is not
 * placing a port or a filler.
 *
 * <p>The base layer of a static machine must never be left empty; a base block
 * is the "default" choice and is what actually declares the machine's voltage
 * tier. Its tier is a property of the block, registered in
 * {@code TierMaterials}, so the structure evaluator reads a machine's tier
 * straight off the blocks that were placed rather than from any hidden field.</p>
 *
 * <p>Blocks are named by tier ({@code lv_base}, {@code mv_base}, ...) and
 * distinguished by their crafting recipes, never by a metal's real-world feel.</p>
 *
 * <p>Base blocks take part in connected texturing: a run of same-tier base
 * blocks and ports of the same level loses the seams between them so the floor
 * reads as one plate (see {@link ConnectedPartProperties}).</p>
 */
public class BaseBlock extends Block {

    private final EnergyTier tier;

    public BaseBlock(Properties properties, EnergyTier tier) {
        super(properties);
        this.tier = tier;
        // Every flag defaults to false; updateShape fills them in as neighbours
        // appear, which is what keeps the default state and the placed state
        // agreeing rather than relying on a lucky default.
        registerDefaultState(defaultBlockState());
    }

    /** The voltage tier this base block declares. */
    public EnergyTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        ConnectedPartProperties.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ConnectedPartProperties.recompute(defaultBlockState(),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        // Only the face that changed can have changed, so recomputing just that
        // one flag keeps a floor of hundreds of blocks cheap to keep in sync.
        return state.setValue(ConnectedPartProperties.propertyFor(direction),
                ConnectedPartProperties.connectsTo(state.getBlock(), level, pos, direction));
    }
}
