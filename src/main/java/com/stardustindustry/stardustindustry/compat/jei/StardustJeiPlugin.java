package com.stardustindustry.stardustindustry.compat.jei;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.recipe.ModRecipes;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipe;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * JEI integration for Stardust Industry.
 *
 * <p>This is a <b>compile-time-only</b> dependency: JEI is not required to run
 * the mod, but when it is present JEI discovers this class through the
 * {@link JeiPlugin} annotation and asks it to describe the mod's custom recipe
 * categories. Nothing here is loaded without JEI.</p>
 *
 * <p>Recipes themselves are not enumerated by hand. Because
 * {@code stardustindustry:crushing} is a normal vanilla {@code RecipeType}, JEI
 * collects all of its loaded recipes automatically once the category is
 * registered; this plugin only has to teach JEI how to draw them and which
 * machine is the catalyst.</p>
 */
@JeiPlugin
public final class StardustJeiPlugin implements IModPlugin {

    /** Stable id so JEI can identify this plugin across reloads. */
    public static final ResourceLocation PLUGIN_UID = StardustIndustry.id("jei_plugin");

    public StardustJeiPlugin() {
        // JEI requires a public no-argument constructor.
    }

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // The crusher block's icon doubles as the category tab icon.
        registration.addRecipeCategories(
                new CrushingCategory(registration.getJeiHelpers().getGuiHelper(),
                        ModBlocks.CRUSHER.get().asItem().getDefaultInstance()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Tells JEI "the crusher is what performs crushing recipes", which puts
        // the machine on the recipe's catalyst list and enables the
        // "uses/recipes for this item" lookup from the crusher item itself.
        registration.addRecipeCatalyst(ModBlocks.CRUSHER.get().asItem(), CrushingCategory.RECIPE_TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // JEI only auto-collects vanilla recipe types it knows about, so a custom
        // type such as stardustindustry:crushing must be handed its recipes
        // explicitly. They are read from the live recipe manager, which means the
        // viewer always shows exactly what the machine can run - including any
        // recipes a datapack added or replaced.
        registration.addRecipes(CrushingCategory.RECIPE_TYPE, crushingRecipes());
    }

    private static List<RecipeHolder<ProcessingRecipe>> crushingRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        RecipeManager recipeManager = minecraft.level.getRecipeManager();
        return recipeManager.getAllRecipesFor(ModRecipes.CRUSHING.get());
    }
}
