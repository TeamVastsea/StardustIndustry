package com.stardustindustry.stardustindustry.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A coil of insulated cable, as an item.
 *
 * <h2>Two independent axes, one item per combination</h2>
 * <p>A cable is <b>not</b> rated by voltage tier. It has two perpendicular
 * properties (§6.1):</p>
 * <ul>
 *   <li>{@link CableAmpacity} — how much current the copper can carry;</li>
 *   <li>{@link CableInsulation} — what the player feels when touching it
 *       (§6.11), and how much current the sheath costs.</li>
 * </ul>
 * <p>Any combination is valid. A thin conductor in thick insulation and a thick
 * conductor bare are both real products, and players will need both.</p>
 *
 * <h2>Colour is not part of the item</h2>
 * <p>The item is the <b>product</b>; colour is the <b>marking</b>. Dyeing a cable
 * writes a {@link CableColor} into the cable when it is laid, so sixteen colours
 * do not become sixteen items (§6.8). An uncoloured cable is black.</p>
 *
 * <h2>The effective ampacity</h2>
 * <p>The nameplate current of a {@link CableAmpacity} is for bare conductor. A
 * sheath traps heat, so the cable's real limit is the nameplate <b>derated</b> by
 * the insulation — see {@link CableInsulation#ampacityFactorPercent()}. Use
 * {@link #effectiveAmpacity()} everywhere; using the nameplate for a sheathed
 * cable would silently allow it to run hot.</p>
 */
public class CableItem extends Item {

    private final CableAmpacity ampacity;
    private final CableInsulation insulation;

    public CableItem(Properties properties, CableAmpacity ampacity, CableInsulation insulation) {
        super(properties);
        this.ampacity = ampacity;
        this.insulation = insulation;
    }

    /** The conductor class of this cable. */
    public CableAmpacity ampacity() {
        return ampacity;
    }

    /** The sheath class of this cable. */
    public CableInsulation insulation() {
        return insulation;
    }

    /**
     * Continuous current this cable carries once wrapped in its sheath.
     *
     * <p>Rounded down, so a cable is never credited with more than it has.</p>
     */
    public int effectiveAmpacity() {
        return ampacity.amps() * insulation.ampacityFactorPercent() / 100;
    }

    /** Ohms per block of run, from the conductor class and its material. */
    public float ohmsPerBlock() {
        return ampacity.ohmsPerBlock();
    }

    /** True when this cable is steel-cored aluminium: bare only, hangs straighter. */
    public boolean isAcsr() {
        return ampacity.material() == com.stardustindustry.stardustindustry.energy.CableMaterial.ACSR;
    }

    /**
     * Builds one uncoloured cable of this exact combination.
     *
     * <p>Used when a cable is cut down or a rack is broken, so the item that comes
     * back is the cable that went out — never a "default" one.</p>
     */
    public static ItemStack stackOf(CableAmpacity ampacity, CableInsulation insulation) {
        return new ItemStack(com.stardustindustry.stardustindustry.registry.ModItems.cableFor(ampacity, insulation));
    }

    /** Builds one cable to match {@code connection}, colour included in the record only. */
    public static ItemStack stackOf(CableConnection connection) {
        return stackOf(connection.ampacity(), connection.insulation());
    }

    /**
     * Builds {@code count} cables of {@code connection}'s product, capped at the
     * stack limit.
     *
     * <p>Used when a line is cut while the same product is already on the ground:
     * merging them would otherwise be impossible once the count exceeds a stack.</p>
     */
    public static ItemStack stackOf(CableConnection connection, int count) {
        ItemStack stack = stackOf(connection);
        stack.setCount(Math.min(count, stack.getMaxStackSize()));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Two mechanical lines, matching the two axes. Showing the derated figure
        // (not the nameplate) is the point: that is the number that decides whether
        // a line overheats.
        tooltip.add(Component.translatable("item.stardustindustry.cable.tip.ampacity",
                effectiveAmpacity()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.stardustindustry.cable.tip.insulation."
                + insulation.id()).withStyle(insulation == CableInsulation.BARE
                ? ChatFormatting.RED
                : ChatFormatting.GRAY));
        // Which metal the conductor is. Not cosmetic: it explains the two numbers
        // above — why an 800 A line hangs straighter than a 500 A one, and why it
        // costs more loss for the same current.
        tooltip.add(Component.translatable("item.stardustindustry.cable.tip.material."
                + ampacity.material().id()).withStyle(ChatFormatting.DARK_AQUA));
        // The item itself is uncoloured: colour is painted on when the cable is
        // laid. Saying so avoids "why is my cable black".
        tooltip.add(Component.translatable("item.stardustindustry.cable.tip.color.none")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** True when this item is the cable for {@code ampacity} and {@code insulation}. */
    public boolean matches(CableAmpacity ampacity, CableInsulation insulation) {
        return this.ampacity == ampacity && this.insulation == insulation;
    }
}
