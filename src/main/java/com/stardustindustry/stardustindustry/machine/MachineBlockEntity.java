package com.stardustindustry.stardustindustry.machine;

import java.util.Map;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.machine.module.EnergyBufferModule;
import com.stardustindustry.stardustindustry.multiblock.StructureDefinition;
import com.stardustindustry.stardustindustry.multiblock.StructureMatchResult;
import com.stardustindustry.stardustindustry.multiblock.StructureMatcher;
import com.stardustindustry.stardustindustry.multiblock.StructurePart;
import com.stardustindustry.stardustindustry.multiblock.StructureRotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base block entity for every machine in Stardust Industry.
 *
 * <h2>Composition model</h2>
 * A machine is a {@link ModuleHost} plus a structure. Subclasses build their
 * behaviour by attaching modules in {@link #createModules()} and, for
 * multiblocks, returning a {@link StructureDefinition} from {@link #definition()}.
 * A single-block machine returns {@code null} and is always "formed".
 *
 * <h2>Structure lifecycle</h2>
 * The controller revalidates its structure whenever a boundary block changes
 * (see {@link #onNeighborChanged}) and at a slow heartbeat otherwise. When the
 * formed state flips, every module is notified through
 * {@link ModuleHost#onStructureChanged(boolean)}, so a module only has to react
 * to edges rather than poll.
 */
public abstract class MachineBlockEntity extends BlockEntity {

    /** How often (in ticks) an unformed structure is re-scanned even if nothing changed. */
    protected static final int STRUCTURE_RECHECK_INTERVAL = 20;

    private final ModuleHost modules = new ModuleHost(this);
    private final MachineTier tier;

    private boolean formed;
    private int recheckTimer;
    /** Baseline of the last successful match, used to shorten re-checks. */
    private Map<BlockPos, BlockState> structureBaseline;
    /** Latest provider-based evaluation, or {@code null} for definition-based machines. */
    private com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation evaluation;
    /** True once the machine has been installed (static machines lock on install). */
    private boolean installed;
    /**
     * The original block states the machine consumed, so dismantling can restore
     * exactly what the player built. Empty until the machine is installed.
     */
    private final java.util.List<PlacedPart> ledger = new java.util.ArrayList<>();
    /**
     * Structure failures, as the client needs them to draw the projection ghost.
     * Populated on both sides: the server fills it on revalidation and ships it
     * in the update tag, the client reads it back for rendering.
     */
    private final java.util.List<ClientFailure> clientFailures = new java.util.ArrayList<>();

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineTier tier) {
        super(type, pos, state);
        this.tier = tier;
    }

    /** Attaches the modules this machine is composed of. Called once, from the constructor. */
    protected final void initialiseModules() {
        createModules();
    }

    /** Override to attach modules. Called once during construction. */
    protected void createModules() {}

    /** The multiblock structure, or {@code null} for a single-block machine. */
    protected StructureDefinition definition() {
        return null;
    }

    /**
     * An alternative structure description used by machines that opt into the
     * provider-based framework (see {@link com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider}).
     *
     * <p>When this returns non-null it takes precedence over
     * {@link #definition()}, and the machine is validated by evaluating the
     * provider instead of matching a static definition. The two are mutually
     * exclusive so a machine never has two sources of truth.</p>
     */
    public com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider provider() {
        return null;
    }

    /** The last evaluation produced by {@link #provider()}, or {@code null} for definition-based machines. */
    public final com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation evaluation() {
        return evaluation;
    }

    /**
     * The modifiers currently in force, never {@code null}.
     *
     * <p>A definition-based machine has no structure evaluation, so it reports
     * the neutral set. Machines read this once per use so a filler change (which
     * arrives through a re-evaluation) takes effect on the next operation.</p>
     */
    public final com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet modifiers() {
        return evaluation == null
                ? com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet.BASE
                : evaluation.modifiers();
    }

    public final ModuleHost modules() {
        return modules;
    }

    public final MachineTier tier() {
        return tier;
    }

    public BlockPos controllerPos() {
        return worldPosition;
    }

    /** True when the machine is complete and may run recipes. */
    public final boolean isFormed() {
        return formed;
    }

    /**
     * The horizontal facing used to rotate the structure. Subclasses with a
     * facing property override this to read it from the block state.
     */
    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
    }

    // ---- ticking ----

    /** Called every tick by the block entity ticker. */
    public final void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            modules.clientTick();
            return;
        }

        maintainStructure(level, pos, state);
        // Idle draw: a formed machine keeps its electronics warm whether or not
        // it is working. It is charged before any module runs, so a machine that
        // cannot pay simply finds no energy and stalls (progress is kept), which
        // is the agreed behaviour on power loss.
        chargeIdleDraw();
        modules.serverTick();

        // Modules only run inside a complete structure. Singles are always formed.
        if (formed) {
            serverTickFormed(level, pos, state);
        } else {
            serverTickUnformed(level, pos, state);
        }
    }

    /** Runs each server tick while the structure is complete. */
    protected void serverTickFormed(Level level, BlockPos pos, BlockState state) {}

    /** Runs each server tick while the structure is incomplete. */
    protected void serverTickUnformed(Level level, BlockPos pos, BlockState state) {}

    // ---- idle draw ----

    /**
     * True when the machine has enough energy for its idle draw. A machine that
     * reports {@code false} is stalled: modules find an empty buffer and wait.
     */
    private boolean powered;
    /** True once at least one tick has run, so a fresh machine does not begin unpowered. */
    private boolean powerInitialised;

    /**
     * Charges this tick's idle draw from the machine's energy buffer.
     *
     * <p>Only a formed machine with an energy module pays. A single-block machine
     * (always formed) with no energy module is skipped entirely, keeping the
     * crusher and other simple machines free. The draw is the tier's voltage,
     * which is small but non-zero and forces a real power line to exist.</p>
     */
    private void chargeIdleDraw() {
        if (!formed) {
            powered = false;
            powerInitialised = true;
            return;
        }
        EnergyBufferModule buffer = modules.get(EnergyBufferModule.class);
        if (buffer == null) {
            // Not an electric machine: nothing to charge.
            powered = true;
            powerInitialised = true;
            return;
        }
        int draw = idleDraw();
        powered = buffer.extract(draw, false) >= draw;
        powerInitialised = true;
    }

    /** The per-tick idle draw in FE before filler effects. */
    protected int idleDraw() {
        // Charged at the machine tier's voltage: small, but enough that a machine
        // with no power line visibly stalls rather than running forever for free.
        return tier.energyTier().voltage();
    }

    /** True when the machine currently has enough energy to run. */
    public final boolean isPowered() {
        return powered || !powerInitialised;
    }

    // ---- structure ----

    private void maintainStructure(Level level, BlockPos pos, BlockState state) {
        com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider provider = provider();
        if (provider != null) {
            maintainProviderStructure(level, provider);
            return;
        }

        StructureDefinition definition = definition();
        if (definition == null) {
            if (!formed) {
                setFormed(true);
            }
            return;
        }

        if (--recheckTimer > 0 && !needsRecheck(level, definition)) {
            return;
        }
        recheckTimer = STRUCTURE_RECHECK_INTERVAL;
        revalidate(level, definition);
    }

    /**
     * Validates a provider-based structure, honouring the provider's
     * {@link com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider#revalidateWhileFormed()}
     * policy: a static machine that has locked itself is not re-scanned until it
     * is unlocked, while a dynamic machine is re-scanned freely.
     */
    private void maintainProviderStructure(Level level, com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider provider) {
        if (formed && !provider.revalidateWhileFormed()) {
            return;
        }
        if (--recheckTimer > 0) {
            return;
        }
        recheckTimer = STRUCTURE_RECHECK_INTERVAL;
        revalidate(level, provider);
    }

    /** Re-runs a provider-based structure check and fires the formed/unformed edge. */
    public final boolean revalidate(Level level, com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider provider) {
        // A chunk the structure reaches may be unloaded. Reading it would return
        // air and make the machine look torn down, so the evaluation is skipped
        // entirely: the current formed state, port bindings and stored contents
        // stay exactly as they were until every chunk is back.
        if (!com.stardustindustry.stardustindustry.multiblock.provider.StructureChunkGuard
                .allLoaded(level, provider.footprint(worldPosition, facing()))) {
            return formed;
        }

        com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation result =
                provider.evaluate(level, worldPosition, facing());
        this.evaluation = result;

        boolean nowFormed = result.formed();
        if (nowFormed) {
            bindProviderPorts(level, result);
        } else {
            unbindProviderPorts(level, result);
        }

        updateClientFailures(result.failures());
        // Push the new formed state and failures to clients so the projection
        // overlay appears or clears without waiting for a chunk reload.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        if (nowFormed != formed) {
            setFormed(nowFormed);
        }
        return nowFormed;
    }

    /** Converts the server's scan failures into the client-renderable list. */
    private void updateClientFailures(java.util.List<com.stardustindustry.stardustindustry.multiblock.provider.ScanFailure> failures) {
        clientFailures.clear();
        for (com.stardustindustry.stardustindustry.multiblock.provider.ScanFailure failure : failures) {
            clientFailures.add(new ClientFailure(failure.worldPos(), colourGroupOf(failure.role()),
                    failure.expectation(), failure.args()));
        }
    }

    /** Maps a failed cell's observed role onto the projection colour group. */
    private static ClientFailure.ColourGroup colourGroupOf(com.stardustindustry.stardustindustry.multiblock.BlockRole role) {
        return switch (role) {
            // A choice the player must make (an empty base slot, a port) reads
            // differently from a plain missing structure block.
            case BASE_SLOT, PORT, CONTROL_PORT -> ClientFailure.ColourGroup.CHOICE;
            // A tier clash is reported on the controller and is a hard error.
            default -> role == com.stardustindustry.stardustindustry.multiblock.BlockRole.CONTROLLER
                    ? ClientFailure.ColourGroup.ERROR
                    : ClientFailure.ColourGroup.STRUCTURE;
        };
    }

    /** The projection ghosts for this machine, empty when it is formed. */
    public final java.util.List<ClientFailure> clientFailures() {
        return java.util.Collections.unmodifiableList(clientFailures);
    }

    /**
     * Whether an unformed machine should draw projection ghost blocks.
     *
     * <p>A fixed-shape machine is authored cell by cell, so a missing cell can be
     * shown as a ghost of exactly the block that belongs there. A dynamic machine
     * has no authored layout — the player builds whatever box they like — so
     * there is no single correct block to ghost, and drawing one would be a lie.
     * Those machines override this to {@code false}; the floating text that says
     * what is wrong is still shown.</p>
     */
    public boolean supportsProjection() {
        return true;
    }

    /**
     * The size of the machine's evaluated box, as {@code [x, y, z]}, or an empty
     * array when nothing has been evaluated. Derived from the roles the last
     * evaluation recorded, so it works for both static and dynamic machines
     * without either provider having to expose its geometry.
     */
    public final int[] evaluatedSize() {
        var eval = evaluation;
        if (eval == null || eval.roles().isEmpty()) {
            return new int[0];
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : eval.roles().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new int[] {maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    /**
     * The original blocks the machine consumed, as the renderer needs them.
     *
     * <p>Restoring a static machine depends on remembering exactly what was
     * built, and drawing one depends on the same memory: once the body is hidden
     * behind invisible blocks, the controller's renderer is the only thing that
     * can put it back on screen. Sharing one list keeps the picture and the
     * dismantle result from disagreeing.</p>
     */
    public final java.util.List<PlacedPart> ledgerParts() {
        return java.util.Collections.unmodifiableList(ledger);
    }

    /** Binds every port the evaluation observed to this controller. */
    private void bindProviderPorts(Level level, com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation result) {
        for (Map.Entry<BlockPos, com.stardustindustry.stardustindustry.multiblock.BlockRole> entry : result.roles().entrySet()) {
            if (entry.getValue() != com.stardustindustry.stardustindustry.multiblock.BlockRole.PORT) {
                continue;
            }
            if (level.getBlockEntity(entry.getKey()) instanceof MachinePortBlockEntity port) {
                port.bind(worldPosition);
            }
        }
    }

    /** Clears the controller reference from every bound port when the structure breaks. */
    private void unbindProviderPorts(Level level, com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation result) {
        for (Map.Entry<BlockPos, com.stardustindustry.stardustindustry.multiblock.BlockRole> entry : result.roles().entrySet()) {
            if (entry.getValue() != com.stardustindustry.stardustindustry.multiblock.BlockRole.PORT) {
                continue;
            }
            if (level.getBlockEntity(entry.getKey()) instanceof MachinePortBlockEntity port
                    && worldPosition.equals(port.controllerPos())) {
                port.unbind();
            }
        }
    }

    /** Re-runs the structure check and fires the formed/unformed edge. */
    public final boolean revalidate(Level level, StructureDefinition definition) {
        // Same rule as the provider path: never evaluate a multiblock whose
        // chunks are not all loaded, or an unloaded neighbour reads as air and
        // the machine appears broken.
        if (!com.stardustindustry.stardustindustry.multiblock.provider.StructureChunkGuard
                .allLoaded(level, definitionFootprint(definition))) {
            return formed;
        }

        StructureMatchResult result = StructureMatcher.match(level, worldPosition, definition, facing());
        boolean nowFormed = result.matched();

        if (nowFormed) {
            structureBaseline = captureBaseline(level, result);
            bindPorts(level, result, definition);
        } else {
            unbindPorts(level, definition);
        }

        if (nowFormed != formed) {
            setFormed(nowFormed);
        }
        return nowFormed;
    }

    /**
     * The chunks a legacy (definition-based) structure may occupy, from its
     * authored offsets under the controller's facing.
     */
    private java.util.Collection<net.minecraft.world.level.ChunkPos> definitionFootprint(StructureDefinition definition) {
        net.minecraft.world.level.block.Rotation rotation =
                com.stardustindustry.stardustindustry.multiblock.StructureRotation.forFacing(facing());
        BlockPos min = worldPosition;
        BlockPos max = worldPosition;
        for (StructurePart part : definition.parts()) {
            BlockPos world = worldPosition.offset(
                    com.stardustindustry.stardustindustry.multiblock.StructureRotation.rotate(part.offset(), rotation));
            min = new BlockPos(Math.min(min.getX(), world.getX()),
                    Math.min(min.getY(), world.getY()),
                    Math.min(min.getZ(), world.getZ()));
            max = new BlockPos(Math.max(max.getX(), world.getX()),
                    Math.max(max.getY(), world.getY()),
                    Math.max(max.getZ(), world.getZ()));
        }
        return com.stardustindustry.stardustindustry.multiblock.provider.StructureChunkGuard.chunksOf(min, max);
    }

    /**
     * Points every port position of a matched structure at this controller, so
     * each port can forward capability requests to the right machine. Ports are
     * bound on both a fresh form and a still-formed revalidation, which makes
     * binding idempotent and self-healing after a chunk reload.
     */
    private void bindPorts(Level level, StructureMatchResult result, StructureDefinition definition) {
        for (StructurePart part : definition.parts()) {
            if (!part.role().isPort()) {
                continue;
            }
            BlockPos worldPos = result.worldPos(part.offset());
            if (level.getBlockEntity(worldPos) instanceof MachinePortBlockEntity port) {
                port.bind(worldPosition);
            }
        }
    }

    /** Clears the controller reference from every port of a broken structure. */
    private void unbindPorts(Level level, StructureDefinition definition) {
        for (StructurePart part : definition.parts()) {
            if (!part.role().isPort()) {
                continue;
            }
            BlockPos worldPos = worldPosition.offset(StructureRotation.rotate(part.offset(), StructureRotation.forFacing(facing())));
            if (level.getBlockEntity(worldPos) instanceof MachinePortBlockEntity port
                    && worldPosition.equals(port.controllerPos())) {
                port.unbind();
            }
        }
    }

    /** Forces a re-check ignoring the timer, e.g. after a neighbour changes. */
    public final void requestStructureCheck() {
        recheckTimer = 0;
    }

    private boolean needsRecheck(Level level, StructureDefinition definition) {
        if (formed && structureBaseline != null) {
            return StructureMatcher.needsRecheck(structureBaseline, level);
        }
        return true;
    }

    private Map<BlockPos, BlockState> captureBaseline(Level level, StructureMatchResult result) {
        Map<BlockPos, BlockState> baseline = new java.util.HashMap<>(result.placement().size());
        for (BlockPos worldPos : result.placement().values()) {
            baseline.put(worldPos, level.getBlockState(worldPos));
        }
        return baseline;
    }

    private void setFormed(boolean nowFormed) {
        this.formed = nowFormed;
        modules.onStructureChanged(nowFormed);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            onFormedChanged(nowFormed);
        }
    }

    /**
     * Marks a change to the machine's stored contents and pushes it to clients.
     *
     * <p>{@link #setChanged()} alone only schedules a disk save; it does not tell
     * the client anything. A buffer that changes while a player watches — a tank
     * being filled by a pipe — would then keep showing its old contents until the
     * chunk reloaded, which is exactly the sort of stale display a highlight
     * tooltip is most likely to be judged by. Modules call this instead so the
     * client's copy is refreshed in the same tick.</p>
     */
    public final void markContentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Called on the server when the structure transitions, so a machine can tell
     * nearby players what happened. The default is silent; a machine that forms
     * without any player action (a dynamic tank) overrides this to announce
     * itself, since the player gets no other feedback.
     */
    protected void onFormedChanged(boolean nowFormed) {}

    /**
     * Sends {@code message} to every player within {@code radius} blocks of this
     * machine. Used for the "your tank formed" notice, which is only useful to
     * someone standing nearby.
     */
    protected final void broadcastNearby(net.minecraft.network.chat.Component message, double radius) {
        if (level == null || level.isClientSide()) {
            return;
        }
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player.distanceToSqr(center) <= radius * radius) {
                player.displayClientMessage(message, false);
            }
        }
    }

    /** Called by the block when a neighbour changes, so the structure can react quickly. */
    public void onNeighborChanged() {
        recheckTimer = 0;
        // A neighbour change can add or remove a port, so make sure the ports
        // reflect the structure the controller actually sees.
        if (formed && level != null && !level.isClientSide()) {
            requestStructureCheck();
        }
    }

    @Override
    public void setRemoved() {
        // A controller that is broken must not leave dangling ports pointing at
        // a dead position, or pipes would keep trying to reach a ghost machine.
        if (level != null && !level.isClientSide()) {
            if (installed) {
                // The controller is gone: restore the rest of the machine and drop
                // its contents, or the shell would be stranded invisible.
                clearContents();
                for (int i = ledger.size() - 1; i >= 0; i--) {
                    PlacedPart part = ledger.get(i);
                    if (level.getBlockState(part.worldPos())
                            .is(com.stardustindustry.stardustindustry.registry.ModBlocks.INVISIBLE_STRUCTURE.get())) {
                        level.setBlock(part.worldPos(), part.originalState(), 3);
                    }
                }
                ledger.clear();
                installed = false;
            }
            if (evaluation != null) {
                unbindProviderPorts(level, evaluation);
            }
            StructureDefinition definition = definition();
            if (definition != null) {
                unbindPorts(level, definition);
            }
        }
        super.setRemoved();
    }

    /** True when any module provides the given resource type. */
    public final boolean provides(ResourceType type) {
        return modules.provides(type);
    }

    // ---- install / dismantle ----

    /** True once the player has installed the machine with the installation tool. */
    public final boolean isInstalled() {
        return installed;
    }

    /**
     * Builds the display snapshot for this machine's parameter screen.
     *
     * <p>All the numbers the screen shows are gathered here, on the server, from
     * the machine's own state: the structure tier and modifiers, the energy
     * buffer, and how many fillers were counted. The client receives them as
     * data and never recomputes anything.</p>
     */
    public MachineParamsData paramsData() {
        var eval = evaluation;
        String tierName = eval != null && eval.tier() != null ? eval.tier().getSerializedName() : "";
        var mods = modifiers();

        int stored = -1;
        int max = -1;
        EnergyBufferModule buffer = modules.get(EnergyBufferModule.class);
        if (buffer != null) {
            stored = buffer.stored();
            max = buffer.capacity();
        }

        int fillerCount = 0;
        if (eval != null) {
            for (int count : eval.fillers().values()) {
                fillerCount += count;
            }
        }

        String fluidName = "";
        int fluidAmount = -1;
        int fluidCapacity = -1;
        int fluidInRate = 0;
        int fluidOutRate = 0;
        com.stardustindustry.stardustindustry.machine.module.FluidBufferModule fluid =
                modules.get(com.stardustindustry.stardustindustry.machine.module.FluidBufferModule.class);
        if (fluid != null) {
            fluidAmount = fluid.amount();
            fluidCapacity = fluid.capacity();
            fluidInRate = fluid.inputRate();
            fluidOutRate = fluid.outputRate();
            if (!fluid.fluid().isEmpty()) {
                fluidName = fluid.fluid().getHoverName().getString();
            }
        }

        return new MachineParamsData(
                worldPosition,
                installed,
                formed,
                tierName,
                mods.speedMultiplier(),
                mods.energyMultiplier(),
                mods.parallelBonus(),
                mods.bufferMultiplier(),
                stored,
                max,
                fillerCount,
                fluidName,
                fluidAmount,
                fluidCapacity,
                fluidInRate,
                fluidOutRate,
                tankParams());
    }

    /**
     * The tank-specific display block for this machine, or {@code null}.
     *
     * <p>Machines that are vessels rather than processors override this to
     * describe themselves as a size, a set of ports and a fluid; everything else
     * inherits {@code null} and the screen falls back to the generic layout. The
     * hook exists so a tank adds no fields to the common snapshot and no copy of
     * it.</p>
     */
    protected MachineParamsData.TankParams tankParams() {
        return null;
    }

    /** The original states recorded at install time, for display and dismantling. */
    public final java.util.List<PlacedPart> ledger() {
        return java.util.Collections.unmodifiableList(ledger);
    }

    /**
     * Installs the machine: validates its structure, records the original block
     * states, and hides the body behind invisible blocks.
     *
     * <p>Only machines that expose a {@link #provider()} may be installed; the
     * fixed-shape framework is the one that needs locking and hiding. A
     * definition-based machine returns {@code false} and keeps its old
     * auto-forming behaviour.</p>
     *
     * @return true when the machine is now installed
     */
    public final boolean install() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        var provider = provider();
        if (provider == null) {
            return false;
        }

        // Never install from a half-loaded footprint: an unloaded chunk reads as
        // air, so the machine would be captured with a hole in it.
        if (!com.stardustindustry.stardustindustry.multiblock.provider.StructureChunkGuard
                .allLoaded(level, provider.footprint(worldPosition, facing()))) {
            return false;
        }

        var result = provider.evaluate(level, worldPosition, facing());
        this.evaluation = result;
        if (!result.formed()) {
            if (formed) {
                setFormed(false);
            }
            return false;
        }

        // Snapshot every position the structure covers, before hiding anything.
        ledger.clear();
        for (Map.Entry<BlockPos, com.stardustindustry.stardustindustry.multiblock.BlockRole> entry : result.roles().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState original = level.getBlockState(pos);
            ledger.add(new PlacedPart(pos, original, entry.getValue().name()));
        }

        // Hide every body position, leaving ports and the controller visible.
        // Ports keep their block entity so automation still reaches the machine;
        // only inert body and filler cells are hidden.
        for (PlacedPart part : ledger) {
            if (!isHidableRole(part.role())) {
                continue;
            }
            BlockState hidden = com.stardustindustry.stardustindustry.registry.ModBlocks.INVISIBLE_STRUCTURE.get()
                    .defaultBlockState();
            level.setBlock(part.worldPos(), hidden, 3);
            if (level.getBlockEntity(part.worldPos()) instanceof InvisibleStructureBlockEntity invisible) {
                invisible.bind(worldPosition);
            }
        }

        installed = true;
        if (!formed) {
            setFormed(true);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /**
     * Removes the machine.
     *
     * <p>Restores every recorded position to its original block and either
     * returns the machine's contents to the world or destroys them. The two
     * modes are the two exit paths: a tidy dismantle returns everything, while a
     * smashing break destroys whatever the machine was holding.</p>
     *
     * @param returnContents true to drop the machine's buffered items and the
     *                       in-progress inputs as materials; false to destroy them
     */
    public final void dismantle(boolean returnContents) {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (returnContents) {
            dropContents();
        } else {
            clearContents();
        }

        // Restore the ledger in reverse so later writes do not clobber earlier ones.
        for (int i = ledger.size() - 1; i >= 0; i--) {
            PlacedPart part = ledger.get(i);
            // Skip positions that are no longer ours (a player may have broken one
            // already, which is exactly what triggered this dismantle).
            if (!level.getBlockState(part.worldPos()).is(com.stardustindustry.stardustindustry.registry.ModBlocks.INVISIBLE_STRUCTURE.get())) {
                continue;
            }
            level.setBlock(part.worldPos(), part.originalState(), 3);
        }

        ledger.clear();
        installed = false;
        if (formed) {
            setFormed(false);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** True for roles a machine hides behind the invisible block once installed. */
    private static boolean isHidableRole(String roleName) {
        return MachineRoles.isHidable(roleName);
    }

    /**
     * Called when a player destroys one of the machine's structure blocks.
     *
     * <p>The whole machine comes apart, but the cell the player actually broke is
     * left alone so vanilla can hand them that one block. Everything else is
     * restored and the machine's contents are destroyed: this is the "smash it
     * apart" path, the counterpart to a tidy dismantle through the tool.</p>
     *
     * @param brokenPos the cell being destroyed, skipped during restoration
     */
    public final void onStructureBroken(BlockPos brokenPos) {
        if (level == null || level.isClientSide() || !installed) {
            return;
        }
        clearContents();
        for (int i = ledger.size() - 1; i >= 0; i--) {
            PlacedPart part = ledger.get(i);
            if (part.worldPos().equals(brokenPos)) {
                continue;
            }
            if (!level.getBlockState(part.worldPos())
                    .is(com.stardustindustry.stardustindustry.registry.ModBlocks.INVISIBLE_STRUCTURE.get())) {
                continue;
            }
            level.setBlock(part.worldPos(), part.originalState(), 3);
        }
        ledger.clear();
        installed = false;
        if (formed) {
            setFormed(false);
        }
        setChanged();
    }

    /** Returns the machine's buffered items to the world. */
    protected void dropContents() {
        if (level == null) {
            return;
        }
        for (MachineModule module : modules.all()) {
            if (module instanceof ContentHolder holder) {
                for (net.minecraft.world.item.ItemStack stack : holder.contentsToDrop()) {
                    if (!stack.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level,
                                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                    }
                }
            }
        }
    }

    /** Destroys the machine's buffered items, leaving nothing behind. */
    protected void clearContents() {
        for (MachineModule module : modules.all()) {
            if (module instanceof ContentHolder holder) {
                holder.clearContents();
            }
        }
    }

    /**
     * Implemented by modules that hold items the machine should return on a tidy
     * dismantle, or destroy on a break.
     */
    public interface ContentHolder {
        /** Stacks to hand back to the player, including in-progress inputs. */
        java.util.List<net.minecraft.world.item.ItemStack> contentsToDrop();

        /** Discards everything the module holds. */
        void clearContents();
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Installed", installed);
        modules.save(tag, registries);
        saveMachine(tag, registries);
        if (!ledger.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (PlacedPart part : ledger) {
                list.add(part.save());
            }
            tag.put("Ledger", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("Formed");
        installed = tag.getBoolean("Installed");
        modules.load(tag, registries);
        loadMachine(tag, registries);
        ledger.clear();
        if (tag.contains("Ledger")) {
            net.minecraft.nbt.ListTag list = tag.getList("Ledger", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                PlacedPart part = PlacedPart.load(list.getCompound(i));
                if (part != null) {
                    ledger.add(part);
                }
            }
        }
    }

    /** Override to persist machine-specific state beyond its modules. */
    protected void saveMachine(CompoundTag tag, HolderLookup.Provider registries) {}

    /** Override to restore machine-specific state beyond its modules. */
    protected void loadMachine(CompoundTag tag, HolderLookup.Provider registries) {}

    // ---- client sync ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        // The projection overlay only needs this while unformed; skip the bytes
        // entirely once the machine is complete.
        if (!formed && !clientFailures.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (ClientFailure failure : clientFailures) {
                list.add(failure.save());
            }
            tag.put("Failures", list);
        }
        return tag;
    }

    /**
     * Reads the projection data shipped in the update tag.
     *
     * <p>Only the client receives it. The server keeps its own authoritative
     * failure list, so loading never has to reconstruct it from the wire.</p>
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (level != null && level.isClientSide()) {
            clientFailures.clear();
            if (tag.contains("Failures")) {
                net.minecraft.nbt.ListTag list = tag.getList("Failures", net.minecraft.nbt.Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    clientFailures.add(ClientFailure.load(list.getCompound(i)));
                }
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
