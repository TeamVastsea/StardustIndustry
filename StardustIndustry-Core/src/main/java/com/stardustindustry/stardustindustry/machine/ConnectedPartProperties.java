package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The six neighbour flags that turn a ring of parts into one connected surface.
 *
 * <p>A machine's base layer is made of base blocks, ports and frames of the same
 * voltage tier laid side by side. Drawn naively each one is a separate cube with
 * its own border, so a nine-block floor looks like nine unrelated tiles. These
 * flags mark which faces touch a block of the same level, and the blockstate
 * models use them to hide the seam, so the floor reads as a single machined
 * plate.</p>
 *
 * <p>Connection is decided by the same rule the structure uses — the blocks must
 * share a voltage tier — so the picture follows the machine's real wiring rather
 * than mere adjacency. A base block next to a port of the same tier joins; next
 * to a different tier it does not, which is also a visible hint that the tier is
 * mixed.</p>
 */
public final class ConnectedPartProperties {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public static final BooleanProperty[] ALL = { NORTH, EAST, SOUTH, WEST, UP, DOWN };

    private ConnectedPartProperties() {}

    /** Adds the six flags to a block's state definition. */
    public static void addProperties(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        for (BooleanProperty property : ALL) {
            builder.add(property);
        }
    }

    /** The flag for a direction. */
    public static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /**
     * Recomputes every flag on {@code state} from its neighbours.
     *
     * <p>Written by hand rather than delegated to a library so the "same tier"
     * rule is stated once, in the same vocabulary the structure evaluator uses.</p>
     */
    public static BlockState recompute(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            result = result.setValue(propertyFor(direction), connectsTo(state.getBlock(), level, pos, direction));
        }
        return result;
    }

    /** True when the block in {@code direction} belongs to the same connected surface. */
    public static boolean connectsTo(net.minecraft.world.level.block.Block self, LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        return isConnectable(neighbour.getBlock()) && sharesTier(self, neighbour.getBlock());
    }

    /** A block that takes part in the connected surface: a base, port or frame. */
    public static boolean isConnectable(net.minecraft.world.level.block.Block block) {
        // A part must carry a level to join. An untiered legacy port has no level
        // to share, so it never connects — which is also what keeps it from
        // claiming a tiered floor's seam.
        return tierOf(block) != null
                && (block instanceof BaseBlock || block instanceof MachinePortBlock || block instanceof FrameBlock);
    }

    /**
     * Whether two parts join.
     *
     * <p>Same tier joins. An untiered legacy port never joins, because it has no
     * level to share and joining it would draw a seam that lies about the
     * machine's wiring.</p>
     */
    private static boolean sharesTier(net.minecraft.world.level.block.Block first,
                                      net.minecraft.world.level.block.Block second) {
        com.stardustindustry.stardustindustry.energy.EnergyTier a = tierOf(first);
        com.stardustindustry.stardustindustry.energy.EnergyTier b = tierOf(second);
        return a != null && a == b;
    }

    private static com.stardustindustry.stardustindustry.energy.EnergyTier tierOf(net.minecraft.world.level.block.Block block) {
        if (block instanceof BaseBlock base) {
            return base.tier();
        }
        if (block instanceof FrameBlock frame) {
            return frame.tier();
        }
        if (block instanceof MachinePortBlock port) {
            return port.tier();
        }
        return null;
    }
}
