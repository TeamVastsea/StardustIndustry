package com.stardustindustry.stardustindustry.multiblock.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The authored description of a static machine: its fixed structure, its free
 * base layer, and the models used to draw it.
 *
 * <h2>Structure and model from one source</h2>
 * A static machine must look like the shape the player builds, so the shape and
 * the artwork are authored together. The model references live here next to the
 * per-cell structure, which is what keeps "the model the client draws" and "the
 * structure the server checks" from drifting apart.
 *
 * <h2>Coordinate model</h2>
 * The controller sits at {@code (0, 0, 0)} and everything is authored facing
 * north. When the controller is placed facing another direction, offsets are
 * rotated around the vertical axis by {@code StructureRotation.forFacing}. Only
 * horizontal facings are supported.
 *
 * <h2>The base layer</h2>
 * The bottom layer of a static machine is declared as {@code BASE_SLOT} cells.
 * Each must hold a port, a filler or a level-bearing base block; leaving one
 * empty is a structure failure. Every level-bearing block on the base layer
 * must share a single voltage tier, which becomes the machine's tier.
 */
public final class StructureModel {

    private final ResourceLocation id;
    private final BlockPos controller;
    private final Predicate<BlockState> controllerMatcher;
    private final List<StructureSlot> body;
    private final List<StructureSlot> baseSlots;
    private final ResourceLocation formedModel;
    private final ResourceLocation unformedModel;

    StructureModel(ResourceLocation id,
                   BlockPos controller,
                   Predicate<BlockState> controllerMatcher,
                   List<StructureSlot> body,
                   List<StructureSlot> baseSlots,
                   ResourceLocation formedModel,
                   ResourceLocation unformedModel) {
        this.id = id;
        this.controller = controller;
        this.controllerMatcher = controllerMatcher;
        this.body = List.copyOf(body);
        this.baseSlots = List.copyOf(baseSlots);
        this.formedModel = formedModel;
        this.unformedModel = unformedModel;
    }

    public ResourceLocation id() {
        return id;
    }

    /** The controller's authored offset (always part of the body). */
    public BlockPos controllerOffset() {
        return controller;
    }

    /** Matcher for the controller block itself. */
    public boolean matchesController(BlockState state) {
        return controllerMatcher.test(state);
    }

    /** Fixed body cells, excluding the controller. */
    public List<StructureSlot> body() {
        return body;
    }

    /** Free base-layer cells. */
    public List<StructureSlot> baseSlots() {
        return baseSlots;
    }

    /** Model drawn once formed, or {@code null} to fall back to the block model. */
    public ResourceLocation formedModel() {
        return formedModel;
    }

    /** Model drawn while unformed (the projection), or {@code null}. */
    public ResourceLocation unformedModel() {
        return unformedModel;
    }

    /** Every authored fixed position, body and controller, for projection. */
    public List<StructureSlot> allFixed() {
        List<StructureSlot> all = new ArrayList<>(body.size() + 1);
        all.add(new StructureSlot(controller, StructureSlotType.CONTROLLER, controllerMatcher));
        all.addAll(body);
        return all;
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    /** Mutable builder for a {@link StructureModel}. */
    public static final class Builder {
        private final ResourceLocation id;
        private final Map<BlockPos, StructureSlot> fixed = new LinkedHashMap<>();
        private final Map<BlockPos, StructureSlot> baseSlots = new LinkedHashMap<>();
        private BlockPos controller = BlockPos.ZERO;
        private Predicate<BlockState> controllerMatcher;
        private ResourceLocation formedModel;
        private ResourceLocation unformedModel;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /** Sets where the controller sits, relative to the structure origin. */
        public Builder controller(int x, int y, int z, Predicate<BlockState> matcher) {
            this.controller = new BlockPos(x, y, z);
            this.controllerMatcher = matcher;
            return this;
        }

        /** Sets the controller position and matches a single block. */
        public Builder controller(int x, int y, int z, Block block) {
            return controller(x, y, z, state -> state.is(block));
        }

        /** Declares a fixed body cell. */
        public Builder fixed(int x, int y, int z, Predicate<BlockState> matcher) {
            BlockPos pos = new BlockPos(x, y, z);
            fixed.put(pos, new StructureSlot(pos, StructureSlotType.FIXED, matcher));
            return this;
        }

        /** Declares a fixed body cell accepting exactly one block. */
        public Builder fixed(int x, int y, int z, Block block) {
            return fixed(x, y, z, state -> state.is(block));
        }

        /** Declares a free base-layer cell. */
        public Builder baseSlot(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            baseSlots.put(pos, new StructureSlot(pos, StructureSlotType.BASE_SLOT, state -> false));
            return this;
        }

        /**
         * Fills a solid cuboid with fixed body cells, skipping any position that
         * is already declared (so base slots and ports can be carved out first
         * or afterwards).
         */
        public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, Predicate<BlockState> matcher) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!baseSlots.containsKey(pos)) {
                            fixed.putIfAbsent(pos, new StructureSlot(pos, StructureSlotType.FIXED, matcher));
                        }
                    }
                }
            }
            return this;
        }

        /** Reference to the model drawn once the machine is formed. */
        public Builder formedModel(ResourceLocation model) {
            this.formedModel = model;
            return this;
        }

        /** Reference to the model drawn while unformed (the projection). */
        public Builder unformedModel(ResourceLocation model) {
            this.unformedModel = model;
            return this;
        }

        public StructureModel build() {
            if (controllerMatcher == null) {
                throw new IllegalStateException("Structure '" + id + "' has no controller");
            }
            if (fixed.isEmpty()) {
                throw new IllegalStateException("Structure '" + id + "' has no body");
            }
            // The controller must not also be declared as a body cell.
            fixed.remove(controller);
            return new StructureModel(id, controller, controllerMatcher,
                    new ArrayList<>(new HashMap<>(fixed).values()),
                    new ArrayList<>(baseSlots.values()),
                    formedModel, unformedModel);
        }
    }
}
