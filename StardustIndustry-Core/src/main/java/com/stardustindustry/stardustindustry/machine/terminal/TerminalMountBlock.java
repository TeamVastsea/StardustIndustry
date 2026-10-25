package com.stardustindustry.stardustindustry.machine.terminal;

import java.util.List;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.TerminalGrid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A terminal mount: the rack that holds individual terminals on one face.
 *
 * <p>The mount is <b>not</b> a power node. It is furniture — a plate with a
 * regular grid of fixing points — and each {@link TerminalItem terminal} the
 * player bolts onto it is what becomes a node. That split is what gives the
 * player free placement: any cell may be filled in any order, and the rack
 * simply records which cells are occupied.</p>
 *
 * <p>Two different items belong here and must not be confused: a <b>terminal</b>
 * is the fitting bolted onto the plate, a <b>cable</b> is the conductor pulled
 * between two terminals. Holding a terminal never lays cable.</p>
 *
 * <h2>What {@link #FACING} means</h2>
 * <p><b>{@code FACING} is the side the backplate is bolted against</b>, not the
 * side the grid faces. The terminals stand on that plate and grow out of the
 * block towards <em>the opposite side</em>, the way a post insulator stands off
 * a panel towards the operator.</p>
 *
 * <p>That one sentence is the contract every other part depends on, and all of
 * them obey it in the same direction:</p>
 * <ul>
 *   <li>the block model for {@code facing=<dir>} has its plate on the
 *       {@code dir} side of the cube (generated, pre-rotated), e.g.
 *       {@code facing=north} is the slab {@code z = 0..1};</li>
 *   <li>{@link #getShape} is a thin box on the same {@code dir} side;</li>
 *   <li>{@link TerminalMountBlockEntity#cellFromHit} projects the hit onto that
 *       plate and derives u/v from the renderer's basis vectors;</li>
 *   <li>{@code TerminalMountRenderer} puts the foot on the plate's room-facing
 *       surface and grows the terminal towards {@code dir.getOpposite()}.</li>
 * </ul>
 *
 * <p>Three earlier revisions got this wrong, each time in a different way: the
 * plate was put on the far side so the rack floated off the wall; then the
 * models and the collision box ended up on opposite sides for four of the six
 * facings; then the growth direction (and later the foot offset's sign) was
 * flipped, so terminals were buried in the block or drilled through the wall.
 * See docs/electric-power-system-design.md §6.3.3 for the current table.</p>
 *
 * <h2>State</h2>
 * <p>Only one blockstate property exists: which face the backplate is bolted
 * against ({@link #FACING}). The tier is <b>not</b> a property because each tier
 * is its own block, so the Block instance already carries it. The occupied cells
 * are also <b>not</b> blockstates: a 4x4 rack has 2^16 occupancy combinations,
 * so they live in the block entity instead (§6.3.4).</p>
 *
 * <h2>Interactions</h2>
 * <ul>
 *   <li>Terminal item, plain click on a cell: bolt a terminal there.</li>
 *   <li>Empty hand + Shift on a cell: unbolt that terminal.</li>
 *   <li>Installation tool + Shift: take the whole rack, terminals included.</li>
 *   <li>Any other tool: mine it; the rack and its terminals drop separately.</li>
 * </ul>
 */
public class TerminalMountBlock extends Block implements EntityBlock {

    /**
     * Which face the backplate is bolted against.
     *
     * <p>All six directions are valid. A rack is a surface fitting, so it must be
     * able to back onto a wall (north / south / east / west), lie flat on the
     * floor (facing up) or hang under a ceiling (facing down). The property is
     * {@link BlockStateProperties#FACING} rather than {@code HORIZONTAL_FACING}
     * so the blockstate file has a variant for every direction and none of them
     * silently falls back to the default model.</p>
     *
     * <p><b>FACING is the side the plate touches</b>, not the side the grid faces.
     * The terminals stand on that plate and stick out of the block on the
     * opposite side. The earlier reading ("FACING = where the grid points") put
     * the plate on the far side, so a rack clicked onto a wall hung in mid-air
     * away from that wall.</p>
     *
     * <p>Placement stores the face the player clicked, so dropping one on the
     * floor gives a floor plate and clicking a wall gives a wall plate; see
     * {@link #getStateForPlacement}.</p>
     */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final EnergyTier tier;
    private final TerminalGrid grid;

    public TerminalMountBlock(Properties properties, EnergyTier tier) {
        // Occupied cells live in the block entity, so two equal block states can
        // have different collision shapes. Enforce this at the owning type rather
        // than relying on every registration site to remember it.
        super(properties.dynamicShape());
        this.tier = tier;
        this.grid = TerminalGrid.of(tier);
        // The tier is NOT a blockstate property: each tier is its own block, so it
        // is already carried by the Block instance. Making it a property meant the
        // blockstate file needed 6 facings x 4 tiers = 24 variants, while only the
        // six models belonging to that very tier exist; the other 18 resolved to
        // no model and every world load logged "missing model for variant
        // ...facing=west,tier=ehv" 84 times. Tier is readable via {@link #tier()}.
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** The voltage tier this rack is rated for. */
    public EnergyTier tier() {
        return tier;
    }

    /** The grid geometry of this rack, derived from its tier. */
    public TerminalGrid grid() {
        return grid;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The plate backs onto whatever was clicked, exactly like a torch or a
        // ladder: click the floor and the plate lies on it, click a ceiling and it
        // hangs from it, click a wall and it backs onto that wall.
        //
        // The property stores the side the plate TOUCHES, so it is the direction
        // from the placed block back to the clicked block - which is the clicked
        // face's opposite for a block, and the face towards the player when the
        // click replaced something (tall grass and the like).
        Direction facing = context.replacingClickedOnBlock()
                ? context.getHorizontalDirection()
                : context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /**
     * Turns facing into an initial orientation, so a freshly placed rack already
     * lines up with where the player put it rather than always starting north.
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalMountBlockEntity(pos, state);
    }

    // ---- interactions ----

    /**
     * Handles a plain right-click on a bolted terminal: start, move or finish a
     * cable.
     *
     * <h2>Client and server play different halves</h2>
     * <p>The <b>client</b> owns the pending "loose end": the first click remembers
     * it, a click on another terminal sends one request, and a click on the same
     * terminal again drops it. The <b>server</b> keeps no pending state at all and
     * treats every request as "validate and try" — see
     * {@code PendingCable#complete}. That split is why a desynced client can at
     * worst send a request that is rejected, never half-create a cable.</p>
     *
     * <h2>The held item decides when a cable is made</h2>
     * <p>A click <b>moves the loose end</b>, nothing more. The cable is only strung
     * when the player clicks with a cable in hand, and the cable's class is taken
     * from that item. That is what makes "hold a terminal to pull a cable" wrong:
     * a terminal is a fitting, not a conductor.</p>
     *
     * <p>The click is consumed either way, so a right-click on a terminal never
     * falls through to block placement.</p>
     */
    private ItemInteractionResult handleCableClick(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                   Player player, int cell) {
        if (!(level.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack)) {
            return ItemInteractionResult.CONSUME;
        }
        if (rack.terminalTier(cell) == null) {
            return ItemInteractionResult.CONSUME;
        }
        com.stardustindustry.stardustindustry.energy.TerminalEndpoint clicked =
                new com.stardustindustry.stardustindustry.energy.TerminalEndpoint(pos, cell);

        if (level.isClientSide()) {
            com.stardustindustry.stardustindustry.energy.PendingCable.validateOwner(player);
            com.stardustindustry.stardustindustry.energy.TerminalEndpoint from =
                    com.stardustindustry.stardustindustry.energy.PendingCable.from();
            // Both hands, not just the hand this click arrived in. A player who
            // keeps the spool in the offhand right-clicks with an empty main hand,
            // so testing `stack` alone read "no cable" and silently moved the
            // loose end instead of stringing the wire — the click appeared to do
            // nothing at all. The server's PendingCable#heldCable checks both
            // hands too, so the client and server now agree on the gesture.
            boolean holdingCable =
                    com.stardustindustry.stardustindustry.energy.PendingCable.heldCable(player) != null;

            if (from == null) {
                // Nothing held yet: this click picks up a loose end here. It does
                // not matter what the player is holding — the loose end is just a
                // place the next cable will come from.
                com.stardustindustry.stardustindustry.energy.PendingCable.begin(player, clicked);
                player.displayClientMessage(Component.translatable(
                        holdingCable
                                ? "message.stardustindustry.cable.started"
                                : "message.stardustindustry.cable.started_no_cable")
                        .withStyle(ChatFormatting.GRAY), true);
            } else if (from.equals(clicked)) {
                // Clicking the loose end's own terminal again puts it back.
                com.stardustindustry.stardustindustry.energy.PendingCable.clear();
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.cable.cancelled").withStyle(ChatFormatting.GRAY), true);
            } else if (!holdingCable) {
                // Moving the end is free and is the natural thing to do when the
                // first choice was wrong, so an empty hand re-aims instead of
                // erroring.
                com.stardustindustry.stardustindustry.energy.PendingCable.begin(player, clicked);
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.cable.moved").withStyle(ChatFormatting.GRAY), true);
            } else {
                // A cable in hand: hand both ends to the server, which decides.
                // The client forgets its end immediately so a rejected request
                // leaves nothing dangling behind.
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.stardustindustry.stardustindustry.network.ConnectCablePayload(
                                from.pos(), from.cell(), pos, cell));
                com.stardustindustry.stardustindustry.energy.PendingCable.clear();
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Cut or paint a cable that the aim ray crosses.
     *
     * <h2>The gestures</h2>
     * <ul>
     *   <li><b>Dye in hand</b> + right-click a cable → paint it that colour.</li>
     *   <li><b>Empty hand</b> + right-click a cable → cut it and take the spool
     *       back. This is the "I laid the wrong wire" escape, and it has to exist
     *       separately from unscrewing terminals because a terminal can now hold
     *       several cables.</li>
     * </ul>
     *
     * <p>Neither gesture touches the terminals: the client tests its view ray
     * against the hanging wire itself ({@link TerminalMountBlockEntity#cableAtRay}),
     * which is the only thing that makes "click the cable" possible at all, since
     * a cable has no collision box. The server then acts on the cable the client
     * names, having checked that the cable really is on this rack and that the dye
     * claimed is really in hand.</p>
     *
     * @return true when the click was about a cable and has been handled
     */
    private boolean handleCableEdit(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof TerminalMountBlockEntity mount)) {
            return false;
        }
        // A dye always means "paint"; an empty hand means "cut". Anything else
        // (the installation tool, a pickaxe, a cable spool) is left to its own
        // handler, so dyeing never swallows an unrelated click.
        int dyeIndex = -1;
        boolean cutting = false;
        if (stack.getItem() instanceof net.minecraft.world.item.DyeItem) {
            dyeIndex = ((net.minecraft.world.item.DyeItem) stack.getItem()).getDyeColor().ordinal();
        } else if (stack.isEmpty()) {
            // An empty hand cuts — unless a cable is held in the *other* hand. A
            // player who keeps the spool in the offhand right-clicks with an empty
            // main hand, and the aim ray often grazes an existing wire on its way
            // to the terminal; treating that as "cut" silently destroyed a cable
            // during what was meant to be a connection. Holding a cable anywhere
            // therefore means "wiring", never "cutting".
            if (com.stardustindustry.stardustindustry.energy.PendingCable.heldCable(player) != null) {
                return false;
            }
            cutting = true;
        } else {
            return false;
        }

        if (level.isClientSide()) {
            // The client owns the aim. It finds the cable along the real view ray —
            // the same approximation the player sees — and sends exactly which
            // cable it hit. The server does not try to re-derive that ray, because
            // the click's impact point is on the plate, which is nowhere near the
            // wire: it validates what was claimed instead (the cable exists on
            // this rack, the dye in hand matches), which is what actually matters.
            var hitCable = CablePicking.pick(mount);
            if (hitCable == null) {
                // Not a cable: let the click fall through to whatever it really hit.
                return false;
            }
            if (!cutting && !com.stardustindustry.stardustindustry.energy.CableColor.isPaintable(hitCable.insulation())) {
                // Bare conductor has no sheath to paint. Refuse here where the
                // message is cheap and immediate; the server refuses the same way,
                // so a forged packet can never paint a bare wire either.
                player.displayClientMessage(Component.translatable(
                                "message.stardustindustry.cable.bare_not_paintable")
                        .withStyle(ChatFormatting.YELLOW), true);
                return true;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.stardustindustry.stardustindustry.network.CableEditPayload(
                            pos, hitCable.a().pos(), hitCable.a().cell(),
                            hitCable.b().pos(), hitCable.b().cell(),
                            hitCable.ampacity(), hitCable.insulation(),
                            cutting
                                    ? com.stardustindustry.stardustindustry.network.CableEditPayload.CUT
                                    : dyeIndex));
            return true;
        }
        // Server side the work is done by the packet handler, which has the
        // player's exact position and the claimed cable. Reaching here means a
        // client sent no packet, so there is nothing to do.
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalMountBlockEntity mount)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // A meter click inspects the aimed cable, or the rack when no cable was
        // hit. Handle it before the occupied-terminal cable-pull gesture.
        if (stack.getItem() instanceof com.stardustindustry.stardustindustry.energy.ElectriciansMeterItem) {
            com.stardustindustry.stardustindustry.energy.ElectriciansMeterItem
                    .requestTerminalDiagnostic(pos, mount);
            return ItemInteractionResult.SUCCESS;
        }

        // Minecraft asks the empty main hand before the offhand. Let an offhand
        // meter receive its own click instead of treating this empty stack as the
        // cable-cut gesture below.
        if (hand == InteractionHand.MAIN_HAND
                && com.stardustindustry.stardustindustry.energy.ElectriciansMeterItem
                        .offhandMeterOwnsEmptyMainHand(
                                player.getMainHandItem(), player.getOffhandItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        // Cables are cut and painted before anything else: they have no collision
        // box, so the ray has to be tested against the wire itself, and a click
        // that lands on a wire must not be read as "place a terminal here".
        if (handleCableEdit(stack, level, pos, player)) {
            return ItemInteractionResult.SUCCESS;
        }

        // The cable gesture outranks shift-click. Holding a cable means "wire this
        // up", and the old order (shift first) made a crouching player's click do
        // nothing at all when the spool was in the main hand, and unscrew the
        // terminal when it was in the offhand. Sneaking while clicking a terminal is
        // far too common to leave that ambiguity in.
        int cell = mount.cellFromHit(state, hit);
        boolean hasTerminalHere = cell >= 0 && mount.hasTerminal(cell);
        boolean wiring =
                com.stardustindustry.stardustindustry.energy.PendingCable.heldCable(player) != null;
        if (hasTerminalHere && (wiring || !player.isShiftKeyDown())) {
            return handleCableClick(stack, state, level, pos, player, cell);
        }

        // Shift-click is the "remove / take" family and never places.
        if (player.isShiftKeyDown()) {
            // The installation tool's own useOn handles "take the whole rack";
            // reaching here means an empty hand or a plain tool.
            if (stack.isEmpty() && hasTerminalHere) {
                if (!level.isClientSide()) {
                    mount.removeTerminal(cell, player);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(stack.getItem() instanceof TerminalItem terminalItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // -1 means "the cell is impossible, not merely unoccupied": the rack's grid
        // is empty (a grid is never 0x0 in practice, but a future tier could be) or
        // the level reference is gone. -2 means "the ray missed the plate".
        // They are separate codes so a miss still shows the "clicked off the grid"
        // hint while an impossible cell stays silent: telling the player the click
        // missed when it did not is worse than saying nothing.
        if (cell == TerminalMountBlockEntity.CELL_OUTSIDE_PLATE) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.outside_grid").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (cell < 0) {
            // The rack has no usable cells; nothing the player can do here.
            return ItemInteractionResult.CONSUME;
        }
        if (mount.hasTerminal(cell)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.cell_taken").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (terminalItem.tier() != tier) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardustindustry.terminal.wrong_tier").withStyle(ChatFormatting.RED), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide()) {
            mount.addTerminal(cell, terminalItem.style());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Mining scatters the rack and its terminals as separate drops, so a player
     * who pickaxes a populated rack does not lose the terminals (§6.3.2).
     *
     * <p>Vanilla collects drops before the block entity is removed, so the
     * rack's contents are read straight off the block entity here. Taking the
     * rack <em>whole</em> (installation tool + Shift) is handled in
     * {@code InstallationToolItem} and never reaches this method.</p>
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(this));
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof TerminalMountBlockEntity mount) {
            collectTerminalDrops(mount, drops);
            // Cables come off with the rack, and they come off from <b>both</b>
            // ends. Clearing only this rack's half (the first version did exactly
            // that) left the far rack still holding a record pointing here, so
            // putting a new rack in the same place made the cable reappear out of
            // nowhere — the line was never deleted, only orphaned.
            Level level = params.getLevel();
            for (com.stardustindustry.stardustindustry.energy.CableConnection cable
                    : new java.util.ArrayList<>(mount.cables())) {
                ItemStack cableStack =
                        com.stardustindustry.stardustindustry.energy.CableService.disconnect(level, cable);
                if (cableStack.isEmpty()) {
                    // The far half was unloaded: this half is gone either way, and
                    // the player must still get the spool back. The far record is
                    // pruned by its own block entity when its chunk loads — see
                    // TerminalMountBlockEntity#onLoad.
                    cableStack = com.stardustindustry.stardustindustry.energy.CableItem.stackOf(cable);
                }
                if (!cableStack.isEmpty()) {
                    drops.add(cableStack);
                }
            }
            // Belt and braces: whatever the loop did not reach is cleared here, so
            // the block entity cannot be handed back to the world still holding
            // cable halves.
            mount.disconnectAllCables();
        }
        return drops;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            // Normal mining already returned cable items through getDrops. This is
            // the no-drop fallback for commands, replacement and other removal
            // paths: mutate the authoritative store, but never create extra drops.
            com.stardustindustry.stardustindustry.energy.CableService.disconnectAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Called by the block entity before it is removed, to contribute the
     * terminals it was holding to the break drops.
     */
    public void collectTerminalDrops(TerminalMountBlockEntity mount, List<ItemStack> out) {
        for (int cell = 0; cell < grid.cellCount(); cell++) {
            TerminalStyle style = mount.terminalStyle(cell);
            if (style != null) {
                out.add(TerminalItem.stackFor(style));
            }
        }
    }

    // ---- shape ----

    /**
     * The plate is bolted to the {@code FACING} side of the block, exactly where
     * the generated block models put it, and the terminals stand off it into the
     * block.
     *
     * <p>These boxes are kept in step with
     * {@code GenTerminalMountModels3#turn}:</p>
     *
     * <pre>
     *   north -> z 0 .. 0.125      south -> z 0.875 .. 1
     *   west  -> x 0 .. 0.125      east  -> x 0.875 .. 1
     *   up    -> y 0.875 .. 1      down  -> y 0 .. 0.125
     * </pre>
     *
     * <p>Note west and up against intuition: "west" is the -x side so its plate
     * sits at the LOW end of x, and "up" is the +y side so its plate sits at the
     * HIGH end of y. Four of the six facings previously had the model on one side
     * and the collision box on the other, which is why only north/south ever
     * looked merely inverted while the rest looked broken.</p>
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state, level, pos);
    }

    /**
     * The rack's whole collision volume: the backplate plus every bolted
     * terminal, as one shape.
     *
     * <h2>What each box comes from</h2>
     * <ul>
     *   <li><b>Backplate</b> — the {@code elements} of this tier's and facing's
     *       block model ({@code models/block/<tier>_terminal_mount_<facing>.json}).
     *       Those models used to be 1/16 thick on all four tiers while the boxes
     *       here were hard-coded to {@code 0.125} for all six facings, so the
     *       collision never matched the art: MV/HV/EHV plates are 1.5/2/2.5
     *       sixteenths thick, and a terminal model that changes thickness picks
     *       that up with no code change. Reading the model is also why this works
     *       on a dedicated server — a block model JSON is plain data.</li>
     *   <li><b>Terminals</b> — the OBJ's vertex extent for the style bolted in
     *       each cell, carried onto the cell by the same pose transform the
     *       renderer uses (see {@link TerminalGeometry#placeBox}). This is where
     *       "the smallest rectangle that encloses the model" comes from: it is
     *       the art's own bounds, not a guessed column.</li>
     * </ul>
     *
     * <p>An empty cell adds nothing: an empty rack is not a solid cube. The plate
     * alone still gives the rack a surface to click, and a terminal makes it
     * bump into the player and the aim ray, which is what tells the player the
     * click is lined up.</p>
     *
     * <p>When a terminal's OBJ cannot be read the cell falls back to a
     * conservative post-sized box, so a missing file never makes a terminal
     * walk-through — it just means the box no longer hugs the art.</p>
     */
    private VoxelShape shapeFor(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);

        // Shape queries are hot — the client asks every frame the player looks at
        // the rack, and the server asks on every movement tick. The assembled
        // shape only changes when the terminals do, so it is cached per rack
        // contents. The key lists occupied cells with their styles; a rack with
        // the same terminals in the same cells is the same shape.
        StringBuilder key = new StringBuilder(tier.getSerializedName())
                .append('/').append(facing.getSerializedName()).append('|');
        if (level.getBlockEntity(pos) instanceof TerminalMountBlockEntity mount) {
            for (int cell = 0; cell < grid.cellCount(); cell++) {
                TerminalStyle style = mount.terminalStyle(cell);
                if (style != null) {
                    key.append(cell).append(':').append(style.id()).append(',');
                }
            }
        }
        String cacheKey = key.toString();
        VoxelShape cached = SHAPE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        VoxelShape shape = TerminalPlateShapes.forFacing(this, facing);
        if (level.getBlockEntity(pos) instanceof TerminalMountBlockEntity mount) {
            for (int cell = 0; cell < grid.cellCount(); cell++) {
                TerminalStyle style = mount.terminalStyle(cell);
                if (style != null) {
                    shape = Shapes.or(shape, terminalShape(facing, style, cell));
                }
            }
        }
        // Unbounded growth is not a worry: there are four tiers, six facings and
        // at most a handful of occupied-cell combinations per world; the map is
        // also cleared on a resource reload, when the models behind it change.
        SHAPE_CACHE.put(cacheKey, shape);
        return shape;
    }

    /** Assembled rack shapes, keyed by tier, facing and the terminals bolted on. */
    private static final java.util.Map<String, VoxelShape> SHAPE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Drops every cached rack shape. Called when the server swaps resource managers. */
    public static void clearShapeCache() {
        SHAPE_CACHE.clear();
    }

    /** One terminal's block-local box, in the shape space of {@code pos}. */
    private VoxelShape terminalShape(Direction facing, TerminalStyle style, int cell) {
        float[] box = TerminalPoser.blockLocalBox(facing, grid, cell, style);
        // Keep the full model extent. Minecraft accepts voxel shapes outside the
        // owning block (fences do this too); clamping made EHV's visible last
        // sixteenth walk-through.
        return Shapes.box(
                box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /** Block item for an empty rack, with the tier and grid size visible in survival. */
    public static final class MountItem extends BlockItem {
        private final EnergyTier tier;

        public MountItem(TerminalMountBlock block, Properties properties) {
            super(block, properties);
            this.tier = block.tier();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    List<Component> tooltip, TooltipFlag flag) {
            TerminalGrid grid = TerminalGrid.of(tier);
            tooltip.add(Component.translatable("item.stardustindustry.terminal_mount.tip",
                            Component.translatable("tier." + tier.translationKey()),
                            grid.columns(), grid.rows())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * A terminal: the item that bolts onto a mount cell and becomes one node.
     *
     * <p>A terminal item carries its {@link TerminalStyle}, not just its voltage
     * tier. The tier decides which rack accepts it; the style decides which model
     * is drawn and where its cable is anchored. Keeping the style on the item is
     * what lets several different terminals exist at one voltage without them
     * fighting over a single {@code models/terminal/<tier>.json}.</p>
     */
    public static class TerminalItem extends net.minecraft.world.item.Item {

        private final TerminalStyle style;

        public TerminalItem(Properties properties, TerminalStyle style) {
            super(properties);
            this.style = style;
        }

        /** The style of this terminal: model key, anchor and identity. */
        public TerminalStyle style() {
            return style;
        }

        /** The voltage tier this terminal is rated for. */
        public EnergyTier tier() {
            return style.tier();
        }

        /** Builds a stack of one terminal of {@code style}. */
        public static ItemStack stackFor(TerminalStyle style) {
            return new ItemStack(com.stardustindustry.stardustindustry.registry.ModItems.terminalFor(style));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.stardustindustry.terminal.tip.mount",
                            Component.translatable("tier." + tier().translationKey()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

