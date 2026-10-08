package com.stardustindustry.stardustindustry.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A declarative description of a multiblock structure.
 *
 * <h2>Coordinate model</h2>
 * The structure is authored relative to its controller, which sits at the
 * origin {@code (0, 0, 0)}. Every other position is an offset from there. When
 * the structure is checked in the world, the offsets are rotated to match the
 * controller block's facing and added to its position.
 *
 * <h2>Authoring</h2>
 * Definitions are written in Java DSL (see {@link StructureBuilder}) so they
 * are compile-checked and easy to refactor. A definition can optionally be
 * overridden by a datapack JSON file of the same id, which lets pack authors
 * tweak structure shapes without a recompilation; the Java definition is the
 * fallback and the source of truth for the default.
 *
 * <h2>Predicates</h2>
 * Each position carries a {@link Predicate} over the world's block state rather
 * than a strict block list. This is what allows a structural position to accept
 * a family (for example "any casing block in the {@code c:casing/steel} tag")
 * and what lets ports be interchangeable without enumerating every combination.
 */
public final class StructureDefinition {

    private final String id;
    private final List<StructurePart> parts;
    /** Index from offset to part, for O(1) lookups during matching. */
    private final Map<BlockPos, StructurePart> byOffset;

    StructureDefinition(String id, Map<BlockPos, StructurePart> parts) {
        this.id = id;
        this.parts = List.copyOf(parts.values());
        this.byOffset = Map.copyOf(parts);
    }

    public String id() {
        return id;
    }

    /** Every authored position of the structure. */
    public List<StructurePart> parts() {
        return parts;
    }

    /** The part authored at {@code offset}, or {@code null} if nothing is defined there. */
    public StructurePart partAt(BlockPos offset) {
        return byOffset.get(offset);
    }

    public int size() {
        return parts.size();
    }

    /** True when this definition has a controller part (it always should). */
    public boolean hasController() {
        return parts.stream().anyMatch(p -> p.role() == PartRole.CONTROLLER);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Mutable builder for a {@link StructureDefinition}.
     *
     * <p>Positions are declared with explicit offsets. Convenience helpers for
     * common shapes (hollow box, wall, layer) are added on top so a definition
     * reads like the machine the player builds.</p>
     */
    public static final class Builder {
        private final String id;
        private final Map<BlockPos, StructurePart> parts = new LinkedHashMap<>();
        private BlockPos controller = BlockPos.ZERO;

        private Builder(String id) {
            this.id = id;
        }

        /** Sets where the controller is, relative to the structure origin. */
        public Builder controller(int x, int y, int z) {
            this.controller = new BlockPos(x, y, z);
            return this;
        }

        /** Declares a single position. */
        public Builder part(int x, int y, int z, PartRole role, Predicate<BlockState> matcher) {
            BlockPos pos = new BlockPos(x, y, z);
            parts.put(pos, new StructurePart(pos, role, matcher));
            return this;
        }

        /** Declares a single position that accepts exactly one block. */
        public Builder part(int x, int y, int z, PartRole role, Block block) {
            return part(x, y, z, role, state -> state.is(block));
        }

        /**
         * Fills a solid cuboid from {@code (x0,y0,z0)} to {@code (x1,y1,z1)}
         * inclusive with the given role and matcher. Any explicitly declared
         * position already inside the region is left untouched, so a caller can
         * carve ports out afterwards.
         */
        public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1,
                            PartRole role, Predicate<BlockState> matcher) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        parts.putIfAbsent(pos, new StructurePart(pos, role, matcher));
                    }
                }
            }
            return this;
        }

        /**
         * Builds a hollow cuboid: the shell is casing, the interior is left
         * empty. This is the shape of every reactor vessel, boiler and tank.
         */
        public Builder hollowBox(int x0, int y0, int z0, int x1, int y1, int z1,
                                 Predicate<BlockState> casing) {
            int xa = Math.min(x0, x1), xb = Math.max(x0, x1);
            int ya = Math.min(y0, y1), yb = Math.max(y0, y1);
            int za = Math.min(z0, z1), zb = Math.max(z0, z1);
            for (int x = xa; x <= xb; x++) {
                for (int y = ya; y <= yb; y++) {
                    for (int z = za; z <= zb; z++) {
                        boolean shell = x == xa || x == xb || y == ya || y == yb || z == za || z == zb;
                        if (shell) {
                            BlockPos pos = new BlockPos(x, y, z);
                            parts.putIfAbsent(pos, new StructurePart(pos, PartRole.CASING, casing));
                        }
                    }
                }
            }
            return this;
        }

        /** Declares the controller at {@link #controller} and records it. */
        public Builder controller(Predicate<BlockState> matcher) {
            parts.put(controller, new StructurePart(controller, PartRole.CONTROLLER, matcher));
            return this;
        }

        public StructureDefinition build() {
            if (parts.isEmpty()) {
                throw new IllegalStateException("Structure '" + id + "' has no parts");
            }
            if (parts.values().stream().noneMatch(p -> p.role() == PartRole.CONTROLLER)) {
                throw new IllegalStateException("Structure '" + id + "' has no controller part");
            }
            return new StructureDefinition(id, new HashMap<>(parts));
        }

        /** Copy of the currently declared parts, for reshaping before a build. */
        List<StructurePart> snapshot() {
            return new ArrayList<>(parts.values());
        }
    }
}
