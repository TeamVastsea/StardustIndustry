package com.stardustindustry.utilsex.jei;

import com.stardustindustry.stardustindustry.recipe.ModRecipes;
import com.stardustindustry.stardustindustry.recipe.ProcessingRecipe;
import com.stardustindustry.utilsex.StardustIndustryUtilsEx;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The JEI display for {@code stardustindustry:crushing} recipes.
 *
 * <p>JEI has no idea what a custom recipe type looks like, so it cannot show
 * one until a mod registers a category describing the layout: where inputs go,
 * where outputs go and how to draw the progress arrow. This class is that
 * description. Without it the recipe still exists and still runs in the
 * machine - it simply never appears in the JEI overlay, which is exactly the
 * symptom this fixes.</p>
 *
 * <p>The layout is the familiar furnace shape (input on the left, arrow in the
 * middle, results on the right) so it reads instantly. Extra by-product slots
 * are stacked below the guaranteed results and their tooltips carry the drop
 * chance, so a 25% enrichment is visible rather than mysterious.</p>
 */
public final class CrushingCategory implements IRecipeCategory<RecipeHolder<ProcessingRecipe>> {

    private static final int SLOT_INPUT_X = 0;
    private static final int SLOT_OUTPUT_X = 58;
    private static final int SLOT_START_Y = 9;
    private static final int SLOT_STRIDE = 18;
    private static final int ARROW_X = 26;
    private static final int ARROW_Y = 10;
    /** Width fits a 16px slot, the arrow and a 16px slot with breathing room. */
    private static final int WIDTH = 78;
    /** Tall enough for one result row plus one by-product row. */
    private static final int HEIGHT = 44;

    public static final RecipeType<RecipeHolder<ProcessingRecipe>> RECIPE_TYPE =
            RecipeType.createFromVanilla(ModRecipes.CRUSHING.get());

    private final IDrawable icon;
    private final IDrawable arrow;

    public CrushingCategory(IGuiHelper guiHelper, ItemStack catalystIcon) {
        this.icon = guiHelper.createDrawableItemStack(catalystIcon);
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<RecipeHolder<ProcessingRecipe>> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei." + StardustIndustryUtilsEx.MODID + ".category.crushing");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<ProcessingRecipe> holder, IFocusGroup focuses) {
        ProcessingRecipe recipe = holder.value();

        builder.addInputSlot(SLOT_INPUT_X, SLOT_START_Y)
                .setStandardSlotBackground()
                .addIngredients(recipe.ingredient());

        int outputRow = 0;
        for (ItemStack result : recipe.results()) {
            builder.addOutputSlot(SLOT_OUTPUT_X, slotY(outputRow++))
                    .setOutputSlotBackground()
                    .addItemStack(result);
        }
        for (ProcessingRecipe.Byproduct byproduct : recipe.byproducts()) {
            builder.addOutputSlot(SLOT_OUTPUT_X, slotY(outputRow++))
                    .setOutputSlotBackground()
                    .addItemStack(byproduct.stack())
                    .addRichTooltipCallback((slotView, tooltip) -> addChanceTooltip(tooltip, byproduct));
        }
    }

    /** Vertical position of the n-th output slot, first one level with the input. */
    private static int slotY(int row) {
        return SLOT_START_Y + row * SLOT_STRIDE;
    }

    /** Appends a "chance: N%" line to a by-product slot's tooltip. */
    private static void addChanceTooltip(ITooltipBuilder tooltip, ProcessingRecipe.Byproduct byproduct) {
        int percent = Math.round(byproduct.chance() * 100f);
        tooltip.add(Component.translatable("jei." + StardustIndustryUtilsEx.MODID + ".chance", percent));
    }

    @Override
    public void draw(RecipeHolder<ProcessingRecipe> holder, IRecipeSlotsView slotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
    }
}
