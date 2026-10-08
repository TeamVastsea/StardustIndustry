package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.multiblock.provider.FillerKind;

import net.minecraft.world.level.block.Block;

/**
 * An engineering block placed inside a machine for its modifier contribution.
 *
 * <p>Fillers are the machine's tuning knobs: each kind trades one property for
 * another (see {@link FillerKind}). The block itself does nothing at runtime —
 * the structure evaluator counts fillers by block and folds their
 * {@code FillerRegistry} entries into a modifier set. Keeping the block inert is
 * what lets a filler's numbers change without touching the block or its recipe.</p>
 *
 * <p>A filler is a plain, level-less block: only base blocks and ports carry a
 * voltage tier.</p>
 */
public class FillerBlock extends Block {

    private final FillerKind kind;

    public FillerBlock(Properties properties, FillerKind kind) {
        super(properties);
        this.kind = kind;
    }

    /** The filler identity this block counts as. */
    public FillerKind kind() {
        return kind;
    }
}
