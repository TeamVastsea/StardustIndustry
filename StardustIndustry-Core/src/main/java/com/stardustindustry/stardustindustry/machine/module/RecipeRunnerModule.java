package com.stardustindustry.stardustindustry.machine.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipe;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipeInput;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

/**
 * Runs {@link ProcessingRecipe} recipes for a machine.
 *
 * <p>The runner owns the recipe progress clock. Each tick it asks the level's
 * recipe manager for a match against the machine's input, checks that the
 * outputs will fit and that the energy buffer can pay, then advances progress.
 * When progress completes, inputs are consumed, outputs and by-products are
 * produced, and the energy for the elapsed work is deducted.</p>
 *
 * <p>By-products are rolled independently per completed craft, using the
 * machine's level random, so a by-product of chance 0.1 drops roughly one time
 * in ten without any hidden state.</p>
 */
public final class RecipeRunnerModule implements MachineModule, MachineBlockEntity.ContentHolder {

    private final ItemInventoryModule inventory;
    private final JouleStorageModule energy;
    private final Random random = new Random();
    private MachineBlockEntity machine;

    private ProcessingRecipe activeRecipe;
    private ResourceLocation activeRecipeId;
    /** Input removed at start and held until this operation commits. */
    private ItemStack reservedInput = ItemStack.EMPTY;
    /** Completed products waiting for output space; these are never discarded. */
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private int progress;
    private int totalTime;
    /** Continuous demand while working, already multiplied by the filler set. */
    private long powerWatts;
    /** Machine-level fractional-energy account; deliberately independent of active recipes. */
    private final RecipeEnergyLedger energyLedger = new RecipeEnergyLedger();

    public RecipeRunnerModule(ItemInventoryModule inventory, JouleStorageModule energy) {
        this.inventory = inventory;
        this.energy = energy;
    }

    @Override
    public void attach(MachineBlockEntity machine) {
        this.machine = machine;
    }

    /** Progress in [0,1] for GUIs and the recipe-progress arrow. */
    public float progressFraction() {
        return totalTime <= 0 ? 0f : Math.min(1f, progress / (float) totalTime);
    }

    public boolean isRunning() {
        return activeRecipeId != null;
    }

    public ProcessingRecipe activeRecipe() {
        return activeRecipe;
    }

    /** Current process demand before the machine's separate standby draw. */
    public long powerWatts() {
        return powerWatts;
    }

    @Override
    public void serverTick() {
        if (machine == null) {
            return;
        }
        Level level = machine.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!pendingOutputs.isEmpty()) {
            flushPendingOutputs();
            return;
        }

        if (activeRecipeId != null && activeRecipe == null && !resolveActiveRecipe(level)) {
            // A removed or changed datapack recipe cannot safely complete. Cancel
            // the operation and return its exact reserved stack instead.
            clearActiveOperation();
            flushReservedInput();
            return;
        }

        if (activeRecipeId == null && !reservedInput.isEmpty()) {
            flushReservedInput();
            return;
        }

        if (activeRecipeId == null) {
            tryStart(level);
        } else {
            advance();
        }
    }

    private void tryStart(Level level) {
        int inputSlot = inventory.firstSlot(ItemInventoryModule.SlotGroup.INPUT);
        if (inputSlot < 0) {
            return;
        }
        ItemStack input = inventory.getStack(inputSlot);
        if (input.isEmpty()) {
            return;
        }

        List<RecipeHolder<ProcessingRecipe>> matches = level.getRecipeManager()
                .getRecipesFor(com.stardustindustry.stardustindustry.recipe.ModRecipes.CRUSHING.get(),
                        new ProcessingRecipeInput(input), level);
        if (matches.isEmpty()) {
            return;
        }

        RecipeHolder<ProcessingRecipe> holder = matches.get(0);
        ProcessingRecipe recipe = holder.value();
        int inputAmount = Math.max(1, recipe.primaryInput().amount());
        if (input.getCount() < inputAmount) {
            return;
        }

        // Fillers are read once here, when the recipe starts: speed shortens the
        // time, the energy multiplier raises the per-tick draw. A set with no
        // fillers leaves both at the recipe's own values.
        com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet modifiers = machine.modifiers();
        int time = modifiers.effectiveProcessingTime(recipe.processingTime());
        long recipePowerWatts = modifiers.effectivePowerWatts(recipe.powerWatts());
        com.stardustindustry.stardustindustry.energy.ElectricalUnits.TickEnergy firstTick =
                com.stardustindustry.stardustindustry.energy.ElectricalUnits.tickEnergy(recipePowerWatts, 0L);

        // The recipe must be affordable for at least one tick of work and its
        // outputs must fit before any input is consumed, otherwise a full output
        // would silently eat the input.
        if (!energy.hasJoules(firstTick.joules())) {
            return;
        }
        if (!outputsFit(recipe)) {
            return;
        }

        ItemStack extracted = inventory.handler().extractItem(inputSlot, inputAmount, false);
        if (extracted.getCount() != inputAmount) {
            if (!extracted.isEmpty()) {
                inventory.handler().insertItem(inputSlot, extracted, false);
            }
            return;
        }

        activeRecipe = recipe;
        activeRecipeId = holder.id();
        reservedInput = extracted;
        totalTime = Math.max(1, time);
        powerWatts = recipePowerWatts;
        progress = 0;
        machine.setChanged();
    }

    private void advance() {
        com.stardustindustry.stardustindustry.energy.ElectricalUnits.TickEnergy tickEnergy =
                energyLedger.quote(powerWatts);
        if (!energy.hasJoules(tickEnergy.joules())) {
            return;
        }
        energy.extractJoules(tickEnergy.joules(), false);
        energyLedger.commit(tickEnergy);
        progress++;

        if (progress >= totalTime) {
            complete(activeRecipe);
            clearActiveOperation();
            flushPendingOutputs();
        }
        machine.setChanged();
    }

    private void complete(ProcessingRecipe recipe) {
        for (ItemStack result : recipe.results()) {
            pendingOutputs.add(result.copy());
        }
        for (ProcessingRecipe.Byproduct byproduct : recipe.byproducts()) {
            if (random.nextFloat() < byproduct.chance()) {
                pendingOutputs.add(byproduct.stack().copy());
            }
        }
        reservedInput = ItemStack.EMPTY;
    }

    /** Inserts into output slots and returns anything that still has no room. */
    private ItemStack insertOutput(ItemStack stack) {
        ItemStack remaining = stack;
        for (int i = 0; i < inventory.size() && !remaining.isEmpty(); i++) {
            if (inventory.groupOf(i) != ItemInventoryModule.SlotGroup.OUTPUT) {
                continue;
            }
            remaining = inventory.handler().insertItem(i, remaining, false);
        }
        return remaining;
    }

    private ItemStack insertInput(ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            if (inventory.groupOf(slot) == ItemInventoryModule.SlotGroup.INPUT) {
                remaining = inventory.handler().insertItem(slot, remaining, false);
            }
        }
        return remaining;
    }

    private void flushReservedInput() {
        if (reservedInput.isEmpty()) {
            return;
        }
        ItemStack before = reservedInput;
        reservedInput = insertInput(before);
        if (reservedInput.getCount() != before.getCount() && machine != null) {
            machine.setChanged();
        }
    }

    private void flushPendingOutputs() {
        boolean changed = false;
        for (int index = 0; index < pendingOutputs.size();) {
            ItemStack before = pendingOutputs.get(index);
            ItemStack remaining = insertOutput(before);
            if (remaining.isEmpty()) {
                pendingOutputs.remove(index);
                changed = true;
                continue;
            }
            if (remaining.getCount() != before.getCount()) {
                pendingOutputs.set(index, remaining);
                changed = true;
            }
            index++;
        }
        if (changed && machine != null) {
            machine.setChanged();
        }
    }

    private boolean outputsFit(ProcessingRecipe recipe) {
        List<ItemStack> simulated = new ArrayList<>();
        List<Integer> slotLimits = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.groupOf(slot) == ItemInventoryModule.SlotGroup.OUTPUT) {
                simulated.add(inventory.getStack(slot).copy());
                slotLimits.add(inventory.handler().getSlotLimit(slot));
            }
        }
        for (ItemStack result : recipe.results()) {
            ItemStack remaining = result.copy();
            for (int slot = 0; slot < simulated.size() && !remaining.isEmpty(); slot++) {
                ItemStack current = simulated.get(slot);
                int limit = Math.min(slotLimits.get(slot), remaining.getMaxStackSize());
                if (current.isEmpty()) {
                    int moved = Math.min(limit, remaining.getCount());
                    ItemStack inserted = remaining.copyWithCount(moved);
                    simulated.set(slot, inserted);
                    remaining.shrink(moved);
                } else if (ItemStack.isSameItemSameComponents(current, remaining)) {
                    int room = Math.max(0, Math.min(limit, current.getMaxStackSize()) - current.getCount());
                    int moved = Math.min(room, remaining.getCount());
                    current.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean resolveActiveRecipe(Level level) {
        if (activeRecipeId == null || reservedInput.isEmpty()) {
            return false;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(activeRecipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof ProcessingRecipe recipe)
                || holder.value().getType() != com.stardustindustry.stardustindustry.recipe.ModRecipes.CRUSHING.get()
                || !recipe.ingredient().test(reservedInput)
                || reservedInput.getCount() != Math.max(1, recipe.primaryInput().amount())) {
            return false;
        }
        activeRecipe = recipe;
        return true;
    }

    private void clearActiveOperation() {
        activeRecipe = null;
        activeRecipeId = null;
        progress = 0;
        totalTime = 0;
        powerWatts = 0L;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Progress", progress);
        tag.putInt("TotalTime", totalTime);
        tag.putLong("PowerWatts", powerWatts);
        tag.putLong("EnergyRemainder", energyLedger.remainderWattTicks());
        if (activeRecipeId != null) {
            tag.putString("RecipeId", activeRecipeId.toString());
        }
        if (!reservedInput.isEmpty()) {
            tag.put("ReservedInput", reservedInput.saveOptional(registries));
        }
        if (!pendingOutputs.isEmpty()) {
            ListTag outputs = new ListTag();
            for (ItemStack stack : pendingOutputs) {
                outputs.add(stack.saveOptional(registries));
            }
            tag.put("PendingOutputs", outputs);
        }
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        progress = Math.max(0, tag.getInt("Progress"));
        totalTime = Math.max(0, tag.getInt("TotalTime"));
        powerWatts = Math.max(0L, tag.getLong("PowerWatts"));
        energyLedger.load(tag.getLong("EnergyRemainder"));
        activeRecipeId = tag.contains("RecipeId")
                ? ResourceLocation.tryParse(tag.getString("RecipeId")) : null;
        reservedInput = tag.contains("ReservedInput")
                ? ItemStack.parseOptional(registries, tag.getCompound("ReservedInput")) : ItemStack.EMPTY;
        activeRecipe = null;
        pendingOutputs.clear();
        ListTag outputs = tag.getList("PendingOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < outputs.size(); index++) {
            ItemStack stack = ItemStack.parseOptional(registries, outputs.getCompound(index));
            if (!stack.isEmpty()) {
                pendingOutputs.add(stack);
            }
        }

        // Legacy saves did not reserve their input. Their progress cannot be
        // resumed safely because the visible input may since have changed.
        if (activeRecipeId == null || reservedInput.isEmpty() || totalTime <= 0) {
            clearActiveOperation();
        } else {
            progress = Math.min(progress, totalTime);
        }
    }

    @Override
    public List<ItemStack> contentsToDrop() {
        List<ItemStack> drops = new ArrayList<>();
        if (!reservedInput.isEmpty()) {
            drops.add(reservedInput.copy());
        }
        for (ItemStack stack : pendingOutputs) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        return drops;
    }

    @Override
    public void clearContents() {
        reservedInput = ItemStack.EMPTY;
        pendingOutputs.clear();
        clearActiveOperation();
    }

    @Override
    public String storageKey() {
        return "recipeRunner";
    }
}
