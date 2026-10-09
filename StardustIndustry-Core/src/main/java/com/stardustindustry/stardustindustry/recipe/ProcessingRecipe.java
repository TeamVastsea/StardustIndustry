package com.stardustindustry.stardustindustry.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardustindustry.stardustindustry.capability.ResourceStack;
import com.stardustindustry.stardustindustry.capability.ResourceType;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A single-input industrial processing recipe.
 *
 * <p>This is the general shape shared by the whole processing chain: one
 * resource goes in, one or more come out, with an optional set of probabilistic
 * by-products and a time/energy cost. The concrete machine decides what it can
 * accept through its modules, so the same recipe class serves crushing,
 * grinding, leaching and so on.</p>
 *
 * <p>Inputs and outputs are item-based for now. When fluid machines land, the
 * ingredient and result fields gain a fluid variant backed by the same
 * {@link ResourceStack} abstraction, without changing the machine-facing
 * contract.</p>
 *
 * @param ingredient   the item consumed
 * @param results      the deterministic outputs
 * @param byproducts   outputs rolled independently, each with a chance in [0,1]
 * @param processingTime ticks the recipe takes at 1x speed
 * @param energyCost   FE consumed per tick while running
 */
public record ProcessingRecipe(
        Ingredient ingredient,
        List<ItemStack> results,
        List<Byproduct> byproducts,
        int processingTime,
        int energyCost) implements Recipe<ProcessingRecipeInput> {

    /** A probabilistic extra output. */
    public record Byproduct(ItemStack stack, float chance) {
        public static final Codec<Byproduct> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Byproduct::stack),
                Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Byproduct::chance)
        ).apply(instance, Byproduct::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Byproduct> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Byproduct::stack,
                        ByteBufCodecs.FLOAT, Byproduct::chance,
                        Byproduct::new);
    }

    /**
     * Accepts either a single {@code result} or a list of {@code results}, or
     * both, and merges them into one output list.
     *
     * <p>Both spellings are supported on purpose. The metal addon's generated
     * compat recipes (and casual hand-written ones) use the singular form, while
     * the multi-output stages of the processing chain need the list form. Reading
     * both means neither has to be rewritten.</p>
     */
    private static final MapCodec<List<ItemStack>> RESULTS_CODEC = new MapCodec<>() {
        @Override
        public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops) {
            return java.util.stream.Stream.of(ops.createString("result"), ops.createString("results"));
        }

        @Override
        public <T> com.mojang.serialization.DataResult<List<ItemStack>> decode(
                com.mojang.serialization.DynamicOps<T> ops, com.mojang.serialization.MapLike<T> input) {
            var single = ItemStack.CODEC.parse(ops, input.get("result")).result();
            var many = ItemStack.CODEC.listOf().parse(ops, input.get("results")).result();
            java.util.List<ItemStack> merged = new java.util.ArrayList<>();
            single.ifPresent(merged::add);
            many.ifPresent(merged::addAll);
            if (merged.isEmpty()) {
                return com.mojang.serialization.DataResult.error(
                        () -> "A stardustindustry recipe needs a 'result' or 'results' key");
            }
            return com.mojang.serialization.DataResult.success(List.copyOf(merged));
        }

        @Override
        public <T> com.mojang.serialization.RecordBuilder<T> encode(
                List<ItemStack> input, com.mojang.serialization.DynamicOps<T> ops,
                com.mojang.serialization.RecordBuilder<T> prefix) {
            return prefix.add("results", input, ItemStack.CODEC.listOf());
        }
    };

    public static final MapCodec<ProcessingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ProcessingRecipe::ingredient),
            RESULTS_CODEC.forGetter(ProcessingRecipe::results),
            Byproduct.CODEC.listOf().optionalFieldOf("byproducts", List.of()).forGetter(ProcessingRecipe::byproducts),
            Codec.INT.optionalFieldOf("processing_time", 200).forGetter(ProcessingRecipe::processingTime),
            Codec.INT.optionalFieldOf("energy_cost", 20).forGetter(ProcessingRecipe::energyCost)
    ).apply(instance, ProcessingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, ProcessingRecipe::ingredient,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::results,
                    Byproduct.STREAM_CODEC.apply(ByteBufCodecs.list()), ProcessingRecipe::byproducts,
                    ByteBufCodecs.VAR_INT, ProcessingRecipe::processingTime,
                    ByteBufCodecs.VAR_INT, ProcessingRecipe::energyCost,
                    ProcessingRecipe::new);

    /** Serializer used by the {@code stardustindustry:crushing} type. */
    public static final RecipeSerializer<ProcessingRecipe> SERIALIZER = new Serializer();

    /** The {@link RecipeSerializer} implementation, exposed through {@link #SERIALIZER}. */
    private static final class Serializer implements RecipeSerializer<ProcessingRecipe> {
        @Override
        public MapCodec<ProcessingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ProcessingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    /** True when the supplied resources satisfy this recipe's input. */
    public boolean matches(ResourceStack input) {
        if (input.type() != ResourceType.ITEM) {
            return false;
        }
        return ingredient.test(new ItemStack(input.item(), input.amount()));
    }

    /** The single resource this recipe consumes, derived from its ingredient. */
    public ResourceStack primaryInput() {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ResourceStack.ofEnergy(0);
        }
        return ResourceStack.ofItem(items[0].getItem(), items[0].getCount());
    }

    // ---- Recipe contract ----

    @Override
    public boolean matches(ProcessingRecipeInput input, Level level) {
        return input.stack().isEmpty() ? false : ingredient.test(input.stack());
    }

    @Override
    public ItemStack assemble(ProcessingRecipeInput input, HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CRUSHING.get();
    }
}
