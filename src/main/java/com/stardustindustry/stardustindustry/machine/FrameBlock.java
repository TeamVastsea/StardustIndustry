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
 * A level-bearing frame block: the twelve edges of a dynamic machine.
 *
 * <p>A dynamic machine's voltage tier comes from its frame, exactly as a static
 * machine's comes from its base blocks. All twelve edges must be the same tier,
 * and every port embedded in the shell must match it, which is what makes a
 * mixed-tier tank illegal rather than merely odd.</p>
 *
 * <p>Blocks are named by tier ({@code lv_frame}, {@code mv_frame}, ...) and
 * distinguished by their recipes, never by a metal's real-world feel.</p>
 *
 * <p>Frames of one tier connect to each other and to same-tier parts, so a
 * corner joint loses its seam and the cage reads as one welded frame.</p>
 */
public class FrameBlock extends Block {

    private final EnergyTier tier;

    public FrameBlock(Properties properties, EnergyTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState());
    }

    /** The voltage tier this frame declares. */
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
        return state.setValue(ConnectedPartProperties.propertyFor(direction),
                ConnectedPartProperties.connectsTo(state.getBlock(), level, pos, direction));
    }
}
