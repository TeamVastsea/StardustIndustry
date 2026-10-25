package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.network.TelemetryUpdatePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared live-telemetry session used by the device, crusher, storage and meter menus. */
public abstract class TelemetryMenu extends AbstractContainerMenu {
    public static final double MAX_DISTANCE_SQUARED = 64.0;

    private final Inventory playerInventory;
    private final BlockPos targetPos;
    private ElectricalTelemetrySnapshot snapshot;
    private ElectricalTelemetrySnapshot lastSent;
    private int ticksSinceSync;
    protected int machineSlotCount;

    protected TelemetryMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                            ElectricalTelemetrySnapshot snapshot) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.targetPos = snapshot.targetPos();
        this.snapshot = snapshot;
        this.lastSent = snapshot;
    }

    public final ElectricalTelemetrySnapshot snapshot() {
        return snapshot;
    }

    public final BlockPos targetPos() {
        return targetPos;
    }

    public final boolean hasPlayerInventory() {
        return slots.size() > machineSlotCount;
    }

    public final void acceptTelemetry(ElectricalTelemetrySnapshot next) {
        if (next != null && targetPos.equals(next.targetPos())) {
            snapshot = next;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(playerInventory.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.containerMenu != this) {
            return;
        }
        if (!stillValid(player)) {
            player.closeContainer();
            return;
        }
        ElectricalTelemetrySnapshot next = captureSnapshot(level);
        ticksSinceSync++;
        if (next.materiallyDiffers(lastSent) || ticksSinceSync >= 5) {
            snapshot = next;
            lastSent = next;
            ticksSinceSync = 0;
            PacketDistributor.sendToPlayer(player, new TelemetryUpdatePayload(containerId, next));
        }
    }

    protected ElectricalTelemetrySnapshot captureSnapshot(ServerLevel level) {
        return ElectricalTelemetrySnapshot.capture(level, targetPos);
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().isLoaded(targetPos)) {
            return false;
        }
        return player.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5) <= MAX_DISTANCE_SQUARED
                && isExpectedTarget(player, targetPos);
    }

    protected abstract boolean isExpectedTarget(Player player, BlockPos pos);

    protected final void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        left + column * 18, top + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, left + column * 18, top + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlotCount) {
            if (!moveItemStackTo(stack, machineSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlotCount, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
