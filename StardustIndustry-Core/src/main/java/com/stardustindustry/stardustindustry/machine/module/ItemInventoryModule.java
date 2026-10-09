package com.stardustindustry.stardustindustry.machine.module;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A fixed-size item inventory exposed as a standard {@link IItemHandler}.
 *
 * <p>Slots are grouped by intent through {@link SlotGroup} so the recipe runner
 * knows where to pull inputs from and push outputs to, while external mods that
 * only speak {@code IItemHandler} see one flat inventory and can still automate
 * it. The grouping is metadata, not a separate inventory, so pipes and ME
 * interfaces do not need to understand it.</p>
 */
public final class ItemInventoryModule implements MachineModule, ModuleHost.ResourceHolder,
        MachineBlockEntity.ContentHolder {

    /** The logical role of a contiguous run of slots. */
    public enum SlotGroup {
        INPUT,
        OUTPUT,
        UPGRADE,
        INTERNAL
    }

    private final int size;
    private final SlotGroup[] groups;
    private final ItemStackHandler handler;
    private MachineBlockEntity machine;

    /**
     * @param groups one entry per slot, in slot order
     */
    public ItemInventoryModule(SlotGroup... groups) {
        this.groups = groups.clone();
        this.size = groups.length;
        this.handler = new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                if (machine != null) {
                    machine.setChanged();
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Upgrades are the only slot class with a hard insertion rule for
                // now; input and output accept anything and the machine sorts it out.
                return groups[slot] != SlotGroup.UPGRADE || ItemInventoryModule.this.acceptsUpgrade(slot, stack);
            }
        };
    }

    /** True when a stack may go into this upgrade slot. Subclasses or machines may narrow this. */
    protected boolean acceptsUpgrade(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    public int size() {
        return size;
    }

    public SlotGroup groupOf(int slot) {
        return groups[slot];
    }

    public ItemStack getStack(int slot) {
        return handler.getStackInSlot(slot);
    }

    /** The number of non-empty slots, reported to the machine host for diagnostics. */
    @Override
    public int buffered() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** The NeoForge capability exposed to ports, hoppers and pipes. */
    public IItemHandler capability() {
        return handler;
    }

    /** Direct access to the backing handler for machine-internal moves. */
    public ItemStackHandler handler() {
        return handler;
    }

    /** The first slot in the given group, or {@code -1} if none exists. */
    public int firstSlot(SlotGroup group) {
        for (int i = 0; i < size; i++) {
            if (groups[i] == group) {
                return i;
            }
        }
        return -1;
    }

    /** True when there is room for {@code stack} somewhere in the given group. */
    public boolean canAccept(SlotGroup group, ItemStack stack) {
        for (int i = 0; i < size; i++) {
            if (groups[i] != group) {
                continue;
            }
            ItemStack current = handler.getStackInSlot(i);
            if (current.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Items", handler.serializeNBT(registries));
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Items")) {
            handler.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }

    /** Snapshot of the whole inventory, used by drop-on-break and GUIs. */
    public NonNullList<ItemStack> snapshot() {
        NonNullList<ItemStack> list = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size; i++) {
            list.set(i, handler.getStackInSlot(i));
        }
        return list;
    }

    /**
     * Every non-empty stack, in slot order. On a tidy dismantle inputs and
     * products are all handed back; the recipe runner removes its in-progress
     * inputs from their slots as it consumes them, so whatever remains here is
     * exactly what the player is owed.
     */
    @Override
    public java.util.List<ItemStack> contentsToDrop() {
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        return drops;
    }

    /** Discards everything the inventory holds. */
    @Override
    public void clearContents() {
        for (int i = 0; i < size; i++) {
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
