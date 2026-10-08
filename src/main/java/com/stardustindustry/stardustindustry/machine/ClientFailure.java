package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A structure failure as the client needs it for the projection overlay.
 *
 * <p>The server's {@link com.stardustindustry.stardustindustry.multiblock.provider.ScanFailure}
 * carries a {@code BlockRole}, which is server-side vocabulary. The client only
 * needs to know where to draw a ghost and how to colour it, so the role is
 * flattened to a small colour group here and the expectation is kept as a
 * translation key plus arguments so it can be shown in the player's language.</p>
 *
 * @param worldPos    where the missing block belongs
 * @param colourGroup which colour the ghost should use
 * @param expectation the translation key describing what is needed there
 * @param args        the arguments that key needs, if any
 */
public record ClientFailure(BlockPos worldPos, ColourGroup colourGroup, String expectation, Object... args) {

    /** How a failed cell should be coloured in the projection overlay. */
    public enum ColourGroup {
        /** A structural block is missing or wrong: neutral/amber. */
        STRUCTURE,
        /** Something the player must choose (a base slot, a port): blue. */
        CHOICE,
        /** A hard error such as a mixed tier: red. */
        ERROR
    }

    /** The expectation as a translatable component, ready to show a player. */
    public Component expectationComponent() {
        return Component.translatable(expectation, args);
    }

    /**
     * Packs this failure for the update tag.
     *
     * <p>Arguments are flattened to their string forms; every argument a
     * structure failure carries today is a number or a short name, so the
     * round-trip is lossless for display.</p>
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", worldPos.asLong());
        tag.putString("Colour", colourGroup.name());
        tag.putString("Expect", expectation);
        net.minecraft.nbt.ListTag argsTag = new net.minecraft.nbt.ListTag();
        for (Object arg : args) {
            argsTag.add(net.minecraft.nbt.StringTag.valueOf(String.valueOf(arg)));
        }
        tag.put("Args", argsTag);
        return tag;
    }

    public static ClientFailure load(CompoundTag tag) {
        ColourGroup group;
        try {
            group = ColourGroup.valueOf(tag.getString("Colour"));
        } catch (IllegalArgumentException e) {
            group = ColourGroup.STRUCTURE;
        }
        net.minecraft.nbt.ListTag argsTag = tag.getList("Args", net.minecraft.nbt.Tag.TAG_STRING);
        Object[] args = new Object[argsTag.size()];
        for (int i = 0; i < argsTag.size(); i++) {
            args[i] = argsTag.getString(i);
        }
        return new ClientFailure(BlockPos.of(tag.getLong("Pos")), group, tag.getString("Expect"), args);
    }
}
