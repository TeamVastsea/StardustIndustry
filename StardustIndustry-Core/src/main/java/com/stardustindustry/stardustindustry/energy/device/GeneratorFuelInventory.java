package com.stardustindustry.stardustindustry.energy.device;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One fuel input plus one container-output slot for generator automation. */
final class GeneratorFuelInventory {
    static final int FUEL_SLOT = 0;
    static final int CONTAINER_SLOT = 1;

    private final Runnable changed;
    private final ItemStackHandler contents;
    private final IItemHandler automation;

    GeneratorFuelInventory(Runnable changed) {
        this.changed = changed;
        this.contents = new ItemStackHandler(2) {
            @Override
            public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
                stacks = NonNullList.withSize(2, ItemStack.EMPTY);
                ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
                for (int index = 0; index < items.size(); index++) {
                    CompoundTag itemTag = items.getCompound(index);
                    int slot = itemTag.getInt("Slot");
                    if (slot >= 0 && slot < stacks.size()) {
                        ItemStack.parse(registries, itemTag).ifPresent(stack -> stacks.set(slot, stack));
                    }
                }
                onLoad();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == FUEL_SLOT && stack.getBurnTime(null) > 0;
            }

            @Override
            protected void onContentsChanged(int slot) {
                GeneratorFuelInventory.this.changed.run();
            }
        };
        this.automation = new IItemHandler() {
            @Override public int getSlots() { return 2; }
            @Override public ItemStack getStackInSlot(int slot) { return contents.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return contents.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return slot == FUEL_SLOT && contents.isItemValid(slot, stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return slot == FUEL_SLOT ? contents.insertItem(slot, stack, simulate) : stack;
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot == CONTAINER_SLOT
                        ? contents.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
        };
    }

    IItemHandler capability() {
        return automation;
    }

    /** Full inventory view for the owning generator's authenticated container menu. */
    IItemHandler menuHandler() {
        return contents;
    }

    /** True when one input item can be converted without losing its container. */
    boolean canRefill() {
        ItemStack input = contents.getStackInSlot(FUEL_SLOT);
        return !input.isEmpty() && input.getBurnTime(null) > 0
                && canAcceptContainer(input.getCraftingRemainingItem());
    }

    /** Converts one item only when the grid commits a real energy withdrawal. */
    boolean refill(FuelBurnState fuel, long ratedPowerWatts) {
        if (fuel.hasFuel()) {
            return false;
        }
        ItemStack input = contents.getStackInSlot(FUEL_SLOT);
        int burnTicks = input.getBurnTime(null);
        ItemStack remainder = input.getCraftingRemainingItem();
        if (input.isEmpty() || burnTicks <= 0 || !canAcceptContainer(remainder)) {
            return false;
        }

        contents.extractItem(FUEL_SLOT, 1, false);
        insertContainer(remainder);
        fuel.addBurnTicks(burnTicks, ratedPowerWatts);
        changed.run();
        return true;
    }

    private boolean canAcceptContainer(ItemStack remainder) {
        if (remainder.isEmpty()) {
            return true;
        }
        ItemStack current = contents.getStackInSlot(CONTAINER_SLOT);
        return current.isEmpty() || ItemStack.isSameItemSameComponents(current, remainder)
                && current.getCount() + remainder.getCount()
                <= Math.min(current.getMaxStackSize(), contents.getSlotLimit(CONTAINER_SLOT));
    }

    private void insertContainer(ItemStack remainder) {
        if (remainder.isEmpty()) {
            return;
        }
        ItemStack current = contents.getStackInSlot(CONTAINER_SLOT);
        if (current.isEmpty()) {
            contents.setStackInSlot(CONTAINER_SLOT, remainder.copy());
        } else {
            ItemStack combined = current.copy();
            combined.grow(remainder.getCount());
            contents.setStackInSlot(CONTAINER_SLOT, combined);
        }
    }

    CompoundTag save(HolderLookup.Provider registries) {
        return contents.serializeNBT(registries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        contents.deserializeNBT(registries, tag);
    }

    /** Removes and returns every physical item so block removal cannot lose it. */
    List<ItemStack> drain() {
        List<ItemStack> drained = new ArrayList<>(2);
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                drained.add(stack.copy());
                contents.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        return drained;
    }
}
