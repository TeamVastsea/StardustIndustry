package com.stardustindustry.stardustindustry.machine.module;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.machine.ModuleHost;
import com.stardustindustry.stardustindustry.machine.port.ItemPortMode;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    private final java.util.EnumMap<ItemPortMode, IItemHandler> automationHandlers =
            new java.util.EnumMap<>(ItemPortMode.class);
    private MachineBlockEntity machine;

    /**
     * @param groups one entry per slot, in slot order
     */
    public ItemInventoryModule(SlotGroup... groups) {
        this.groups = groups.clone();
        this.size = groups.length;
        this.handler = new ItemStackHandler(size) {
            @Override
            public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
                // The inventory layout belongs to the machine, not to save data.
                // ItemStackHandler normally trusts its serialized Size field,
                // which can make groups[] and the backing slots disagree.
                stacks = NonNullList.withSize(ItemInventoryModule.this.size, ItemStack.EMPTY);
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
    public long buffered() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** The backwards-compatible bidirectional capability exposed by an unconfigured port. */
    public IItemHandler capability() {
        return capability(ItemPortMode.BOTH);
    }

    /**
     * A role-filtered automation view. Inputs accept insertion but cannot be
     * extracted, outputs allow extraction but reject insertion, and private
     * upgrade/internal slots never appear at all.
     */
    public IItemHandler capability(ItemPortMode mode) {
        ItemPortMode effectiveMode = mode == null ? ItemPortMode.BOTH : mode;
        return automationHandlers.computeIfAbsent(effectiveMode, this::createAutomationHandler);
    }

    /** Direct access to the backing handler for machine-internal moves. */
    public ItemStackHandler handler() {
        return handler;
    }

    private IItemHandler createAutomationHandler(ItemPortMode mode) {
        int[] exposedSlots = automationSlots(groups, mode);
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return exposedSlots.length;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return handler.getStackInSlot(map(slot));
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                int actual = map(slot);
                return permitsAutomationInsert(groups[actual], mode)
                        ? handler.insertItem(actual, stack, simulate) : stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                int actual = map(slot);
                return permitsAutomationExtract(groups[actual], mode)
                        ? handler.extractItem(actual, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return handler.getSlotLimit(map(slot));
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                int actual = map(slot);
                return permitsAutomationInsert(groups[actual], mode)
                        && handler.isItemValid(actual, stack);
            }

            private int map(int slot) {
                if (slot < 0 || slot >= exposedSlots.length) {
                    throw new IllegalArgumentException("External slot " + slot
                            + " is outside [0, " + exposedSlots.length + ")");
                }
                return exposedSlots[slot];
            }
        };
    }

    static int[] automationSlots(SlotGroup[] groups, ItemPortMode mode) {
        return java.util.stream.IntStream.range(0, groups.length)
                .filter(slot -> permitsAutomationInsert(groups[slot], mode)
                        || permitsAutomationExtract(groups[slot], mode))
                .toArray();
    }

    static boolean permitsAutomationInsert(SlotGroup group, ItemPortMode mode) {
        return group == SlotGroup.INPUT && mode.allowsInsert();
    }

    static boolean permitsAutomationExtract(SlotGroup group, ItemPortMode mode) {
        return group == SlotGroup.OUTPUT && mode.allowsExtract();
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
            list.set(i, handler.getStackInSlot(i).copy());
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
