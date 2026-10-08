package com.stardustindustry.stardustindustry.machine.module;

import java.util.List;
import java.util.Random;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineModule;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipe;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipeInput;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
public final class RecipeRunnerModule implements MachineModule {

    private final ItemInventoryModule inventory;
    private final EnergyBufferModule energy;
    private final Random random = new Random();
    private MachineBlockEntity machine;

    private ProcessingRecipe activeRecipe;
    private int progress;
    private int totalTime;
    /** Energy charged each working tick, already multiplied by the filler set. */
    private int energyPerTick;

    public RecipeRunnerModule(ItemInventoryModule inventory, EnergyBufferModule energy) {
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
        return activeRecipe != null;
    }

    public ProcessingRecipe activeRecipe() {
        return activeRecipe;
    }

    @Override
    public void onStructureChanged(boolean formed) {
        // A filler change arrives as a re-evaluation; the in-flight recipe was
        // timed and priced with the old modifiers, so it is abandoned and
        // re-matched on the next tick rather than finishing at stale numbers.
        activeRecipe = null;
        progress = 0;
        totalTime = 0;
        energyPerTick = 0;
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

        if (activeRecipe == null) {
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

        ProcessingRecipe recipe = matches.get(0).value();

        // Fillers are read once here, when the recipe starts: speed shortens the
        // time, the energy multiplier raises the per-tick draw. A set with no
        // fillers leaves both at the recipe's own values.
        com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet modifiers = machine.modifiers();
        int time = modifiers.effectiveProcessingTime(recipe.processingTime());
        int costPerTick = modifiers.effectiveEnergyCost(recipe.energyCost());

        // The recipe must be affordable for at least one tick of work and its
        // outputs must fit before any input is consumed, otherwise a full output
        // would silently eat the input.
        if (!energy.hasEnergy(costPerTick)) {
            return;
        }
        if (!outputsFit(recipe)) {
            return;
        }

        activeRecipe = recipe;
        totalTime = Math.max(1, time);
        energyPerTick = costPerTick;
        progress = 0;
    }

    private void advance() {
        if (!energy.hasEnergy(energyPerTick)) {
            return;
        }
        energy.extract(energyPerTick, false);
        progress++;

        if (progress >= totalTime) {
            complete(activeRecipe);
            activeRecipe = null;
            progress = 0;
            totalTime = 0;
            energyPerTick = 0;
        }
        machine.setChanged();
    }

    private void complete(ProcessingRecipe recipe) {
        int inputSlot = inventory.firstSlot(ItemInventoryModule.SlotGroup.INPUT);
        if (inputSlot >= 0) {
            inventory.handler().extractItem(inputSlot, recipe.primaryInput().amount(), false);
        }
        for (ItemStack result : recipe.results()) {
            insertOutput(result.copy());
        }
        for (ProcessingRecipe.Byproduct byproduct : recipe.byproducts()) {
            if (random.nextFloat() <= byproduct.chance()) {
                insertOutput(byproduct.stack().copy());
            }
        }
    }

    /** Inserts into the first output slot that can take the stack, merging as needed. */
    private void insertOutput(ItemStack stack) {
        ItemStack remaining = stack;
        for (int i = 0; i < inventory.size() && !remaining.isEmpty(); i++) {
            if (inventory.groupOf(i) != ItemInventoryModule.SlotGroup.OUTPUT) {
                continue;
            }
            remaining = inventory.handler().insertItem(i, remaining, false);
        }
        // Anything that still does not fit is discarded; outputsFit should
        // have prevented this, so reaching here means the machine is misconfigured.
    }

    private boolean outputsFit(ProcessingRecipe recipe) {
        for (ItemStack result : recipe.results()) {
            if (!inventory.canAccept(ItemInventoryModule.SlotGroup.OUTPUT, result)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Progress", progress);
        tag.putInt("TotalTime", totalTime);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        progress = tag.getInt("Progress");
        totalTime = tag.getInt("TotalTime");
        // A reloaded machine never resumes a recipe directly: it re-matches on
        // the next tick so a stale reference to a removed recipe cannot survive.
        activeRecipe = null;
    }

    @Override
    public String storageKey() {
        return "recipeRunner";
    }
}
