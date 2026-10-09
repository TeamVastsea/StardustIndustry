package com.stardustindustry.stardustindustry.capability;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A type-erased amount of a resource, used by the machine and recipe engines.
 *
 * <p>Exactly one of the payload slots is meaningful, selected by
 * {@link #type()}: an item id, a fluid id, or a bare integer amount for energy
 * and heat. Building a stack through the static factories is what keeps the
 * three cases consistent.</p>
 *
 * <p>This type is a recipe-engine convenience only. Anything that crosses the
 * machine boundary (ports, pipes, external capabilities) uses the native
 * {@link ItemStack}/{@link FluidStack}/{@code IEnergyStorage} instead, so the
 * mod interoperates with the rest of the FE ecosystem without translation
 * surprises.</p>
 */
public record ResourceStack(ResourceType type, ResourceLocation id, int amount) {

    public ResourceStack {
        if (amount < 0) {
            throw new IllegalArgumentException("Resource amount may not be negative: " + amount);
        }
    }

    // ---- item ----

    public static ResourceStack ofItem(Item item, int count) {
        return new ResourceStack(ResourceType.ITEM, BuiltInRegistries.ITEM.getKey(item), count);
    }

    public static ResourceStack ofItem(ResourceLocation itemId, int count) {
        return new ResourceStack(ResourceType.ITEM, itemId, count);
    }

    // ---- fluid / gas ----

    public static ResourceStack ofFluid(ResourceLocation fluidId, int millibuckets) {
        return new ResourceStack(ResourceType.FLUID, fluidId, millibuckets);
    }

    public static ResourceStack ofGas(ResourceLocation gasId, int millibuckets) {
        return new ResourceStack(ResourceType.GAS, gasId, millibuckets);
    }

    // ---- energy / heat ----

    public static ResourceStack ofEnergy(int fe) {
        return new ResourceStack(ResourceType.ENERGY, null, fe);
    }

    public static ResourceStack ofHeat(int units) {
        return new ResourceStack(ResourceType.HEAT, null, units);
    }

    /** True when this stack carries no resource at all. */
    public boolean isEmpty() {
        return amount <= 0;
    }

    /** A copy with the amount replaced. */
    public ResourceStack withAmount(int newAmount) {
        return new ResourceStack(type, id, newAmount);
    }

    /** The item this stack describes, or {@code null} when it is not an item stack. */
    public Item item() {
        return type == ResourceType.ITEM ? BuiltInRegistries.ITEM.get(id) : null;
    }

    /** The fluid this stack describes, or {@code null} when it is not a fluid or gas. */
    public Fluid fluid() {
        if (type != ResourceType.FLUID && type != ResourceType.GAS) {
            return null;
        }
        return BuiltInRegistries.FLUID.get(id);
    }

    /** Adapts an item stack into a resource stack (empty stacks become an empty resource). */
    public static ResourceStack of(ItemStack stack) {
        if (stack.isEmpty()) {
            return ofItem(net.minecraft.resources.ResourceLocation.withDefaultNamespace("air"), 0);
        }
        return ofItem(stack.getItem(), stack.getCount());
    }

    /** Adapts a fluid stack into a resource stack. */
    public static ResourceStack of(FluidStack stack) {
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return ofFluid(key, stack.getAmount());
    }

    /** The amount of the matching fluid type held in a holder, for convenience. */
    public static ResourceStack of(Holder<Fluid> fluid, int millibuckets) {
        return ofFluid(BuiltInRegistries.FLUID.getKey(fluid.value()), millibuckets);
    }
}
