package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.multiblock.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A machine port: the block that connects a multiblock to the outside world.
 *
 * <p>A port is the only part of a machine that external automation interacts
 * with. It holds no storage itself; instead it forwards capability requests to
 * the controller it is bound to (see {@link MachinePortBlockEntity}), so items,
 * fluids and energy all enter and leave through the module that actually owns
 * them. That keeps the port honest: if the machine's buffer is full, every port
 * reports full.</p>
 *
 * <p>The role ({@link PartRole#PORT_ITEM}, {@link PartRole#PORT_FLUID},
 * {@link PartRole#PORT_ENERGY}) decides which capability the port exposes and
 * which structure positions it may fill.</p>
 */
public class MachinePortBlock extends Block implements EntityBlock {

    private final PartRole role;
    /** The voltage tier this port is rated for, or {@code null} for an untiered legacy port. */
    private final com.stardustindustry.stardustindustry.energy.EnergyTier tier;
    /** Supplies the block entity type; set after registration to avoid a class-init cycle. */
    private BlockEntityType<? extends MachinePortBlockEntity> typeSupplier;

    public MachinePortBlock(Properties properties, PartRole role) {
        this(properties, role, null);
    }

    /**
     * @param tier the tier this port matches on a base layer, or {@code null}
     *             when the port carries no level (legacy untiered ports)
     */
    public MachinePortBlock(Properties properties, PartRole role, com.stardustindustry.stardustindustry.energy.EnergyTier tier) {
        super(properties);
        this.role = role;
        this.tier = tier;
        registerDefaultState(defaultBlockState());
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        ConnectedPartProperties.addProperties(builder);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return ConnectedPartProperties.recompute(defaultBlockState(),
                context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighbourState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(ConnectedPartProperties.propertyFor(direction),
                ConnectedPartProperties.connectsTo(state.getBlock(), level, pos, direction));
    }

    public PartRole role() {
        return role;
    }

    /** The voltage tier this port is rated for, or {@code null}. */
    public com.stardustindustry.stardustindustry.energy.EnergyTier tier() {
        return tier;
    }

    /** Wires the block-entity type after the deferred register has produced it. */
    public MachinePortBlock withBlockEntityType(BlockEntityType<? extends MachinePortBlockEntity> type) {
        this.typeSupplier = type;
        return this;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (typeSupplier == null) {
            throw new IllegalStateException("Port block used before its block entity type was wired: " + role);
        }
        return typeSupplier.create(pos, state);
    }

    /**
     * Lets a player move fluid through a fluid port by hand.
     *
     * <p>A right-click with a filled bucket or a fluid container pours into the
     * machine, and a right-click with an empty one draws from it, which is what
     * a player reaches for before any pipes exist. The work is delegated to
     * NeoForge's fluid handler interaction so bucket, bottle and any other
     * container a mod adds all behave the same, and so the exact amount and the
     * resulting stack are the platform's problem rather than this block's.</p>
     *
     * <p>Only fluid ports take part. An item or energy port passes, leaving the
     * click for whatever the held item would otherwise do.</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (role != PartRole.PORT_FLUID) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // A port that is not bound to a machine exposes no fluid handler, and the
        // helper simply reports that nothing happened.
        if (net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** The port at {@code pos}, or {@code null} when it is not a port. */
    public static MachinePortBlockEntity portAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachinePortBlockEntity port ? port : null;
    }
}
