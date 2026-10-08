package com.stardustindustry.stardustindustry.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * The item view a machine presents to a {@link ProcessingRecipe}.
 *
 * <p>Vanilla recipes are matched against a container-like input. Stardust
 * Industry machines serve a single resource slot to the recipe engine, so this
 * minimal implementation satisfies the contract without pretending to be an
 * inventory.</p>
 *
 * @param stack the resource currently offered to the recipe
 */
public record ProcessingRecipeInput(ItemStack stack) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? stack : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
