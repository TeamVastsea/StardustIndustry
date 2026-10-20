package com.stardustindustry.stardustindustry.machine.terminal;

import java.util.List;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.TerminalGrid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A terminal mount: the rack that holds individual terminals on one face.
 *
 * <p>The mount is <b>not</b> a power node. It is furniture — a plate with a
 * regular grid of fixing points — and each {@link TerminalItem terminal} the
 * player bolts onto it is what becomes a node. That split is what gives the
 * player free placement: any cell may be filled in any order, and the rack
 * simply records which cells are occupied.</p>
 *
 * <h2>What {@link #FACING} means</h2>
 * <p><b>{@code FACING} is the side the backplate is bolted against</b>, not the
 * side the grid faces. The terminals stand on that plate and grow out of the
 * block towards <em>the opposite side</em>, the way a post insulator stands off
 * a panel towards the operator.</p>
 *
 * <p>That one sentence is the contract every other part depends on, and all of
 * them obey it in the same direction:</p>
 * <ul>
 *   <li>the block model for {@code facing=<dir>} has its plate on the
 *       {@code dir} side of the cube (generated, pre-rotated), e.g.
 *       {@code facing=north} is the slab {@code z = 0..1};</li>
 *   <li>{@link #getShape} is a thin box on the same {@code dir} side;</li>
 *   <li>{@link TerminalMountBlockEntity#cellFromHit} projects the hit onto that
 *       plate and derives u/v from the renderer's basis vectors;</li>
 *   <li>{@code TerminalMountRenderer} puts the foot on the plate's room-facing
 *       surface and grows the terminal towards {@code dir.getOpposite()}.</li>
 * </ul>
 *
 * <p>Three earlier revisions got this wrong, each time in a different way: the
 * plate was put on the far side so the rack floated off the wall; then the
 * models and the collision box ended up on opposite sides for four of the six
 * facings; then the growth direction (and later the foot offset's sign) was
 * flipped, so terminals were buried in the block or drilled through the wall.
 * See docs/electric-power-system-design.md §6.3.3 for the current table.</p>
 *
 * <h2>State</h2>
 * <p>Only one blockstate property exists: which face the backplate is bolted
 * against ({@link #FACING}). The tier is <b>not</b> a property because each tier
 * is its own block, so the Block instance already carries it. The occupied cells
 * are also <b>not</b> blockstates: a 4x4 rack has 2^16 occupancy combinations,
 * so they live in the block entity instead (§6.3.4).</p>
 *
 * <h2>Interactions</h2>
 * <ul>
 *   <li>Terminal item, plain click on a cell: bolt a terminal there.</li>
 *   <li>Empty hand + Shift on a cell: unbolt that terminal.</li>
 *   <li>Installation tool + Shift: take the whole rack, terminals included.</li>
 *   <li>Any other tool: mine it; the rack and its terminals drop separately.</li>
 * </ul>
 */
public class TerminalMountBlock extends Block implements EntityBlock {

    /**
     * Which face the backplate is bolted against.
     *
     * <p>All six directions are valid. A rack is a surface fitting, so it must be
     * able to back onto a wall (north / south / east / west), lie flat on the
     * floor (facing up) or hang under a ceiling (facing down). The property is
     * {@link BlockStateProperties#FACING} rather than {@code HORIZONTAL_FACING}
     * so the blockstate file has a variant for every direction and none of them
     * silently falls back to the default model.</p>
     *
     * <p><b>FACING is the side the plate touches</b>, not the side the grid faces.
     * The terminals stand on that plate and stick out of the block on the
     * opposite side. The earlier reading ("FACING = where the grid points") put
     * the plate on the far side, so a rack clicked onto a wall hung in mid-air
     * away from that wall.</p>
     *
     * <p>Placement stores the face the player clicked, so dropping one on the
     * floor gives a floor plate and clicking a wall gives a wall plate; see
     * {@link #getStateForPlacement}.</p>
     */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final EnergyTier tier;
    private final TerminalGrid grid;

    public TerminalMountBlock(Properties properties, EnergyTier tier) {
        super(properties);
        this.tier = tier;
        this.grid = TerminalGrid.of(tier);
        // The tier is NOT a blockstate property: each tier is its own block, so it
        // is already carried by the Block instance. Making it a property meant the
        // blockstate file needed 6 facings x 4 tiers = 24 variants, while only the
        // six models belonging to that very tier exist; the other 18 resolved to
        // no model and every world load logged "missing model for variant
        // ...facing=west,tier=ehv" 84 times. Tier is readable via {@link #tier()}.
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** The voltage tier this rack is rated for. */
    public EnergyTier tier() {
        return tier;
    }

    /** The grid geometry of this rack, derived from its tier. */
    public TerminalGrid grid() {
        return grid;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The plate backs onto whatever was clicked, exactly like a torch or a
        // ladder: click the floor and the plate lies on it, click a ceiling and it
        // hangs from it, click a wall and it backs onto that wall.
        //
        // The property stores the side the plate TOUCHES, so it is the direction
        // from the placed block back to the clicked block - which is the clicked
        // face's opposite for a block, and the face towards the player when the
        // click replaced something (tall grass and the like).
        Direction facing = context.replacingClickedOnBlock()
                ? context.getHorizontalDirection()
                : context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /**
     * Turns facing into an initial orientation, so a freshly placed rack already
     * lines up with where the player put it rather than always starting north.
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalMountBlockEntity(pos, state);
    }

    // ---- interactions ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalMountBlockEntity mount)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Shift-click is the "remove / take" family and never places.
        if (player.isShiftKeyDown()) {
            // The installation tool's own useOn handles "take the whole rack";
            // reaching here means an empty hand or a plain tool.
            if (stack.isEmpty()) {
                int cell = mount.cellFromHit(state, hit);
                if (cell >= 0 && mount.hasTerminal(cell)) {
                    if (!level.isClientSide()) {
                        mount.removeTerminal(cell, player);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(stack.getItem() instanceof TerminalItem terminalItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // -1 means "the cell is impossible, not merely unoccupied": the rack's grid
        // is empty (a grid is never 0x0 in practice, but a future tier could be) or
        // the level reference is gone. -2 means "the ray missed the plate".
        // They are separate codes so a miss still shows the "clicked off the grid"
        // hint while an impossible cell stays silent: telling the player the click
        // missed when it did not is worse than saying nothing.
        int cell = mount.cellFromHit(state, hit);
        if (cell == TerminalMountBlockEntity.CELL_OUTSIDE_PLATE) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.outside_grid").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (cell < 0) {
            // The rack has no usable cells; nothing the player can do here.
            return ItemInteractionResult.CONSUME;
        }
        if (mount.hasTerminal(cell)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.cell_taken").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (terminalItem.tier() != tier) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.wrong_tier").withStyle(ChatFormatting.RED), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide()) {
            mount.addTerminal(cell, terminalItem.tier());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Mining scatters the rack and its terminals as separate drops, so a player
     * who pickaxes a populated rack does not lose the terminals (§6.3.2).
     *
     * <p>Vanilla collects drops before the block entity is removed, so the
     * rack's contents are read straight off the block entity here. Taking the
     * rack <em>whole</em> (installation tool + Shift) is handled in
     * {@code InstallationToolItem} and never reaches this method.</p>
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(this));
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof TerminalMountBlockEntity mount) {
            collectTerminalDrops(mount, drops);
        }
        return drops;
    }

    /**
     * Called by the block entity before it is removed, to contribute the
     * terminals it was holding to the break drops.
     */
    public void collectTerminalDrops(TerminalMountBlockEntity mount, List<ItemStack> out) {
        for (int cell = 0; cell < grid.cellCount(); cell++) {
            if (mount.hasTerminal(cell)) {
                out.add(TerminalItem.stackFor(mount.terminalTier(cell)));
            }
        }
    }

    // ---- shape ----

    /**
     * The plate is bolted to the {@code FACING} side of the block, exactly where
     * the generated block models put it, and the terminals stand off it into the
     * block.
     *
     * <p>These boxes are kept in step with
     * {@code GenTerminalMountModels3#turn}:</p>
     *
     * <pre>
     *   north -> z 0 .. 0.125      south -> z 0.875 .. 1
     *   west  -> x 0 .. 0.125      east  -> x 0.875 .. 1
     *   up    -> y 0.875 .. 1      down  -> y 0 .. 0.125
     * </pre>
     *
     * <p>Note west and up against intuition: "west" is the -x side so its plate
     * sits at the LOW end of x, and "up" is the +y side so its plate sits at the
     * HIGH end of y. Four of the six facings previously had the model on one side
     * and the collision box on the other, which is why only north/south ever
     * looked merely inverted while the rest looked broken.</p>
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> Shapes.box(0, 0, 0, 1, 1, 0.125);
            case SOUTH -> Shapes.box(0, 0, 0.875, 1, 1, 1);
            case WEST -> Shapes.box(0, 0, 0, 0.125, 1, 1);
            case EAST -> Shapes.box(0.875, 0, 0, 1, 1, 1);
            // Plate on the ceiling: bolted to the block's top face.
            case UP -> Shapes.box(0, 0.875, 0, 1, 1, 1);
            // Plate on the floor: bolted to the block's underside face.
            case DOWN -> Shapes.box(0, 0, 0, 1, 0.125, 1);
        };
    }

    /**
     * A terminal: the item that bolts onto a mount cell and becomes one node.
     *
     * <p>Terminals are one item per voltage tier rather than per pin count,
     * because a mount holds any number of identical pins.</p>
     */
    public static class TerminalItem extends net.minecraft.world.item.Item {

        private final EnergyTier tier;

        public TerminalItem(Properties properties, EnergyTier tier) {
            super(properties);
            this.tier = tier;
        }

        /** The voltage tier this terminal is rated for. */
        public EnergyTier tier() {
            return tier;
        }

        /** Builds a stack of one terminal of {@code tier}. */
        public static ItemStack stackFor(EnergyTier tier) {
            return new ItemStack(switch (tier) {
                case LV -> com.stardustindustry.stardustindustry.registry.ModItems.LV_TERMINAL.get();
                case MV -> com.stardustindustry.stardustindustry.registry.ModItems.MV_TERMINAL.get();
                case HV -> com.stardustindustry.stardustindustry.registry.ModItems.HV_TERMINAL.get();
                case EHV -> com.stardustindustry.stardustindustry.registry.ModItems.EHV_TERMINAL.get();
            });
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.stardustindustry.terminal.tip.mount", tier.translationKey())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

