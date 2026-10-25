package com.stardustindustry.stardustindustry.energy.device;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One-block electrical equipment with explicit switch state and front/back terminals. */
public final class ElectricalDeviceBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final ElectricalDeviceKind kind;

    public ElectricalDeviceBlock(Properties properties, ElectricalDeviceKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    public ElectricalDeviceKind kind() { return kind; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricalDeviceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ElectricalDeviceBlockEntity device) {
                device.serverTick();
            }
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof ElectricalDeviceBlockEntity device) {
            device.dropFuelInventory();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ElectricalDeviceBlockEntity device) {
            if (!level.isClientSide()) {
                if (player.isShiftKeyDown()) {
                    device.toggleSwitch();
                    player.displayClientMessage(device.statusText(), true);
                } else if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.stardustindustry.stardustindustry.menu.ElectricalUi.openDevice(serverPlayer, device);
                } else {
                    return InteractionResult.PASS;
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElectricalDeviceBlockEntity device)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (hand == InteractionHand.MAIN_HAND
                && com.stardustindustry.stardustindustry.energy.ElectriciansMeterItem
                        .offhandMeterOwnsEmptyMainHand(
                                player.getMainHandItem(), player.getOffhandItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof com.stardustindustry.stardustindustry.energy.ElectriciansMeterItem) {
            if (!level.isClientSide()
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.stardustindustry.stardustindustry.menu.ElectricalUi.openDiagnostic(serverPlayer, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof FuseItem fuse && kind.fuseBox()) {
            if (!level.isClientSide()) {
                if (device.replaceFuse(fuse.tier())) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(Component.translatable(
                            "message.stardustindustry.electrical.fuse.replaced"), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.stardustindustry.electrical.fuse.rejected",
                            Component.translatable("tier." + kind.lowTier().translationKey())), true);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (!(stack.getItem() instanceof com.stardustindustry.stardustindustry.machine.tool.InstallationToolItem)) {
            if (kind == ElectricalDeviceKind.FUEL_GENERATOR && stack.getBurnTime(null) > 0) {
                if (!level.isClientSide() && device.insertFuelItem(stack)) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            device.cycleConfiguration();
            player.displayClientMessage(device.statusText(), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ElectricalDeviceBlockEntity device) {
            device.setSwitchClosed(false);
        }
    }
}
