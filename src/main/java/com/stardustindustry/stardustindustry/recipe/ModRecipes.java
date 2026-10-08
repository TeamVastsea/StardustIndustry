package com.stardustindustry.stardustindustry.recipe;

import com.stardustindustry.stardustindustry.StardustIndustry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe types and serializers for this mod.
 *
 * <p>The first type, {@code crushing}, is the one the metal addon already
 * references from its generated compat recipes, so it is registered first to
 * make those recipes resolve instead of logging a parse error.</p>
 *
 * <p>Additional types (smelting, leaching, electrolysis, ...) are added here as
 * their machines are implemented; the runner module that consumes a recipe is
 * selected by the machine, not by the type.</p>
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, StardustIndustry.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, StardustIndustry.MODID);

    /** Ore reduction: one input becomes one or more outputs, optionally with by-products. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>> CRUSHING =
            RECIPE_TYPES.register("crushing", () -> RecipeType.simple(StardustIndustry.id("crushing")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ProcessingRecipe>> CRUSHING_SERIALIZER =
            RECIPE_SERIALIZERS.register("crushing", () -> ProcessingRecipe.SERIALIZER);

    private ModRecipes() {}

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
