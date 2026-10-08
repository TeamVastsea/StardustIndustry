package com.stardustindustry.stardustindustry.multiblock;

import java.util.Map;

import com.stardustindustry.stardustindustry.multiblock.provider.FillerKind;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central classification of the blocks that may appear inside a machine
 * structure.
 *
 * <p>Structure code must not hard-code block identities: it has to ask "is this
 * a port? a filler? a base block?" and act on the answer. Every relevant block
 * registers itself here once, at registration time, which keeps a new block a
 * one-line addition instead of a change woven through the matcher, the
 * projector and the user interface.</p>
 *
 * <p>Classification is by {@link Block}, since a block's category does not vary
 * with its state.</p>
 */
public final class MachinePartTypes {

    /** The categories a structural block can belong to. */
    public enum PartType {
        /** A functional or control port; exposes a capability. */
        PORT,

        /** An engineering block; contributes a modifier when counted. */
        FILLER,

        /** A level-bearing base block filling a base slot by default. */
        BASE,

        /** Inert casing; satisfies fixed casing positions only. */
        CASING,

        /** A level-bearing edge block of a dynamic machine; declares its tier. */
        FRAME,

        /** An inert face block of a dynamic machine; appearance and durability only. */
        SHELL
    }

    private static final Map<Block, PartType> TYPES = new java.util.HashMap<>();
    private static final Map<Block, FillerKind> FILLERS = new java.util.HashMap<>();

    private MachinePartTypes() {}

    /** Registers a block's structural category. */
    public static void register(Block block, PartType type) {
        PartType previous = TYPES.put(block, type);
        if (previous != null && previous != type) {
            throw new IllegalStateException("Block " + block + " already registered as " + previous + ", cannot remap to " + type);
        }
        if (type == PartType.FILLER && !FILLERS.containsKey(block)) {
            throw new IllegalStateException("Filler block " + block + " must be registered with a FillerKind");
        }
    }

    /** Registers a filler block together with the kind it counts as. */
    public static void registerFiller(Block block, FillerKind kind) {
        // Record the kind first: register(...) validates that a FILLER block has
        // one, so putting it in place afterwards would always fail.
        FILLERS.put(block, kind);
        register(block, PartType.FILLER);
    }

    /** The category of {@code block}, or {@code null} when it is not a machine part. */
    public static PartType typeOf(Block block) {
        return TYPES.get(block);
    }

    /** The category of {@code state}'s block, or {@code null}. */
    public static PartType typeOf(BlockState state) {
        return typeOf(state.getBlock());
    }

    /** The filler kind of {@code block}, or {@code null} when it is not a filler. */
    public static FillerKind fillerKindOf(Block block) {
        return FILLERS.get(block);
    }

    /** The filler kind of {@code state}'s block, or {@code null}. */
    public static FillerKind fillerKindOf(BlockState state) {
        return fillerKindOf(state.getBlock());
    }

    public static boolean isPort(Block block) {
        return typeOf(block) == PartType.PORT;
    }

    public static boolean isFiller(Block block) {
        return typeOf(block) == PartType.FILLER;
    }

    public static boolean isBase(Block block) {
        return typeOf(block) == PartType.BASE;
    }

    public static boolean isCasing(Block block) {
        return typeOf(block) == PartType.CASING;
    }

    public static boolean isFrame(Block block) {
        return typeOf(block) == PartType.FRAME;
    }

    public static boolean isShell(Block block) {
        return typeOf(block) == PartType.SHELL;
    }

    /** True when {@code block} is a block a dynamic machine's shell may be built from. */
    public static boolean isShellMaterial(Block block) {
        PartType type = typeOf(block);
        return type == PartType.SHELL || type == PartType.PORT;
    }

    /**
     * True when {@code block} may sit inside a dynamic machine's interior in
     * place of air: fillers, ports (a port may face inward is not allowed, so
     * this is only fillers in practice) and nothing else.
     */
    public static boolean isInteriorMaterial(Block block) {
        return typeOf(block) == PartType.FILLER;
    }
}
