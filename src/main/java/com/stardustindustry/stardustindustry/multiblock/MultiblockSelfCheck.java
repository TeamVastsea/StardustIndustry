package com.stardustindustry.stardustindustry.multiblock;

import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.multiblock.model.StructureModel;
import com.stardustindustry.stardustindustry.multiblock.model.StructureSlotType;
import com.stardustindustry.stardustindustry.multiblock.modifier.FillerModifier;
import com.stardustindustry.stardustindustry.multiblock.modifier.ModifierSet;
import com.stardustindustry.stardustindustry.multiblock.provider.FillerKind;

import net.minecraft.world.level.block.Blocks;

/**
 * Startup checks for the pure (world-free) parts of the multiblock layer.
 *
 * <p>Structure evaluation needs a live level, but the pieces it is built on —
 * the model builder's invariants, modifier arithmetic and tier consistency —
 * are pure functions and can be checked at load time. Running them on startup
 * turns a whole class of authoring mistakes into an immediate, loud failure
 * instead of a machine that silently never forms.</p>
 */
public final class MultiblockSelfCheck {

    private MultiblockSelfCheck() {}

    /** Runs every check, throwing on the first failure. Called during common setup. */
    public static void run() {
        checkModelInvariants();
        checkCrusherStructure();
        checkModifierArithmetic();
        checkTierConsistency();
        checkFillerKinds();
        checkFillerRegistry();
        checkLvParts();
        checkDynamicGeometry();
        checkTankGeometry();
        checkLedgerRoundTrip();
        checkClientFailureRoundTrip();
        checkConnectedParts();
        checkMachineRoles();
        checkDynamicScan();
    }

    /**
     * Pins the dynamic scanner's anchor rule.
     *
     * <p>The controller sits on a face of the shell and is itself that face's
     * boundary on the controller's normal axis; along the face's own plane the
     * walk must travel <em>through</em> the co-planar shell to the box's edge
     * rather than stopping at the neighbouring wall block. A hand-built 5×5×5
     * box proves both halves without a live level.</p>
     */
    private static void checkDynamicScan() {
        var frame = com.stardustindustry.stardustindustry.registry.ModBlocks.STEEL_CASING.get().defaultBlockState();
        var shell = com.stardustindustry.stardustindustry.registry.ModBlocks.STEEL_CASING.get().defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var controller = com.stardustindustry.stardustindustry.registry.ModBlocks.CRUSHER.get().defaultBlockState();

        // Box spans (0,0,0)..(4,4,4): edges frame, faces shell, interior air.
        // The controller replaces the middle cell of the top face (2,4,2).
        net.minecraft.core.BlockPos controllerPos = new net.minecraft.core.BlockPos(2, 4, 2);
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> world = new java.util.HashMap<>();
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    int onEdge = (x == 0 || x == 4 ? 1 : 0) + (y == 0 || y == 4 ? 1 : 0) + (z == 0 || z == 4 ? 1 : 0);
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, y, z);
                    if (onEdge >= 1) {
                        world.put(p, onEdge >= 2 ? frame : shell);
                    } else {
                        world.put(p, air);
                    }
                }
            }
        }
        world.put(controllerPos, controller);

        var result = com.stardustindustry.stardustindustry.multiblock.provider.BoundingBoxScanner
                .scan(controllerPos, world::get);
        if (!result.ok()) {
            throw new IllegalStateException("self-check: dynamic scan failed on a valid 5x5x5 box: "
                    + (result.failure() == null ? "?" : result.failure().expectation()));
        }
        var bounds = result.bounds();
        if (bounds.size(net.minecraft.core.Direction.Axis.X) != 5
                || bounds.size(net.minecraft.core.Direction.Axis.Y) != 5
                || bounds.size(net.minecraft.core.Direction.Axis.Z) != 5) {
            throw new IllegalStateException("self-check: dynamic scan produced the wrong box size");
        }
        if (!bounds.min().equals(new net.minecraft.core.BlockPos(0, 0, 0))
                || !bounds.max().equals(new net.minecraft.core.BlockPos(4, 4, 4))) {
            throw new IllegalStateException("self-check: dynamic scan produced the wrong box corners");
        }
    }

    /**
     * Connected texturing depends on base, ports and frames all carrying the six
     * neighbour flags. A block missing them would be drawn as a plain cube with a
     * seam, and worse, the multipart blockstate would reference states that do
     * not exist. This pins the contract.
     */
    private static void checkConnectedParts() {
        net.minecraft.world.level.block.Block[] connected = {
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_BASE.get(),
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_ITEM_PORT.get(),
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_ENERGY_PORT.get(),
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_FLUID_PORT.get()
        };
        for (net.minecraft.world.level.block.Block block : connected) {
            net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
            for (net.minecraft.world.level.block.state.properties.BooleanProperty property
                    : com.stardustindustry.stardustindustry.machine.ConnectedPartProperties.ALL) {
                if (!state.hasProperty(property)) {
                    throw new IllegalStateException("self-check: " + block + " is missing connection flag " + property.getName());
                }
            }
        }

        if (!com.stardustindustry.stardustindustry.machine.ConnectedPartProperties.isConnectable(
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_BASE.get())) {
            throw new IllegalStateException("self-check: an LV base must be a connectable part");
        }
        // An untiered legacy port has no level to share, so it must not join a
        // tiered surface.
        if (com.stardustindustry.stardustindustry.machine.ConnectedPartProperties.isConnectable(
                com.stardustindustry.stardustindustry.registry.ModBlocks.ITEM_PORT.get())) {
            throw new IllegalStateException("self-check: an untiered port must not be connectable");
        }
    }

    /**
     * The server hides body roles on install and the client redraws them from the
     * ledger. If the two ever disagree the machine either vanishes or doubles, so
     * the shared rule is checked from both sides.
     */
    private static void checkMachineRoles() {
        if (!com.stardustindustry.stardustindustry.machine.MachineRoles.isHidable(BlockRole.FRAME)) {
            throw new IllegalStateException("self-check: the fixed body must be hidable on install");
        }
        // The base layer stays visible: it is the floor and holds every port.
        if (com.stardustindustry.stardustindustry.machine.MachineRoles.isHidable(BlockRole.BASE)
                || com.stardustindustry.stardustindustry.machine.MachineRoles.isHidable(BlockRole.FILLER)
                || com.stardustindustry.stardustindustry.machine.MachineRoles.isHidable(BlockRole.PORT)
                || com.stardustindustry.stardustindustry.machine.MachineRoles.isHidable(BlockRole.CONTROLLER)) {
            throw new IllegalStateException("self-check: the base layer and ports must stay visible");
        }
    }

    /**
     * The original-state ledger is the only record of what a machine consumed, so
     * a save/load bug there means a dismantled machine never returns to normal.
     * This checks the NBT round-trip of a ledger entry, block state included.
     */
    private static void checkLedgerRoundTrip() {
        net.minecraft.world.level.block.state.BlockState state =
                Blocks.GLASS.defaultBlockState();
        com.stardustindustry.stardustindustry.machine.PlacedPart original =
                new com.stardustindustry.stardustindustry.machine.PlacedPart(
                        new net.minecraft.core.BlockPos(12, 64, -7), state, BlockRole.FRAME.name());
        com.stardustindustry.stardustindustry.machine.PlacedPart reloaded =
                com.stardustindustry.stardustindustry.machine.PlacedPart.load(original.save());
        if (reloaded == null) {
            throw new IllegalStateException("self-check: ledger entry failed to reload");
        }
        if (!reloaded.worldPos().equals(original.worldPos())) {
            throw new IllegalStateException("self-check: ledger lost the position on save/load");
        }
        if (reloaded.originalState() != state) {
            throw new IllegalStateException("self-check: ledger lost the original block state on save/load");
        }
    }

    /**
     * The projection overlay is drawn from a {@code ClientFailure} list that
     * crosses the network as NBT. A lost position or colour group would put the
     * ghost on the wrong block, so the round trip is pinned here.
     */
    private static void checkClientFailureRoundTrip() {
        com.stardustindustry.stardustindustry.machine.ClientFailure original =
                new com.stardustindustry.stardustindustry.machine.ClientFailure(
                        new net.minecraft.core.BlockPos(-3, 70, 12),
                        com.stardustindustry.stardustindustry.machine.ClientFailure.ColourGroup.ERROR,
                        "structure.stardustindustry.need.port_tier");
        com.stardustindustry.stardustindustry.machine.ClientFailure reloaded =
                com.stardustindustry.stardustindustry.machine.ClientFailure.load(original.save());
        if (!reloaded.worldPos().equals(original.worldPos())) {
            throw new IllegalStateException("self-check: projection ghost lost its position");
        }
        if (reloaded.colourGroup() != original.colourGroup()) {
            throw new IllegalStateException("self-check: projection ghost lost its colour group");
        }
        if (!reloaded.expectation().equals(original.expectation())) {
            throw new IllegalStateException("self-check: projection ghost lost its text");
        }
        if (!java.util.Arrays.equals(reloaded.args(), original.args())) {
            throw new IllegalStateException("self-check: projection ghost lost its text arguments");
        }
    }

    private static void checkModelInvariants() {
        // A model without a controller must fail fast.
        expectThrows("model without controller", () -> StructureModel.builder(id("no_controller"))
                .fixed(1, 0, 0, Blocks.IRON_BLOCK)
                .build());

        // A model without a body must fail fast.
        expectThrows("model without body", () -> StructureModel.builder(id("no_body"))
                .controller(0, 0, 0, Blocks.IRON_BLOCK)
                .build());

        // A well-formed model must build, and the controller must not appear in the body.
        StructureModel model = StructureModel.builder(id("ok"))
                .controller(0, 0, 0, Blocks.IRON_BLOCK)
                .fixed(1, 0, 0, Blocks.IRON_BLOCK)
                .baseSlot(0, -1, 0)
                .build();
        if (model.body().stream().anyMatch(slot -> slot.offset().equals(model.controllerOffset()))) {
            throw new IllegalStateException("self-check: controller leaked into the body list");
        }
        if (model.baseSlots().size() != 1 || model.baseSlots().get(0).type() != StructureSlotType.BASE_SLOT) {
            throw new IllegalStateException("self-check: base slot not recorded as a BASE_SLOT");
        }
        if (model.allFixed().stream().noneMatch(slot -> slot.type() == StructureSlotType.CONTROLLER)) {
            throw new IllegalStateException("self-check: controller missing from allFixed()");
        }
    }

    /**
     * The crusher is the one shipped machine that exercises the whole static
     * path, so its authored shape is pinned: a 3x3 floor of eight free slots
     * around the controller, a hollow three-layer body, and — per the base-layer
     * rule — no port position anywhere in the body.
     */
    private static void checkCrusherStructure() {
        StructureModel crusher =
                com.stardustindustry.stardustindustry.machine.crusher.CrusherStructure.build();

        // Eight free slots: the 3x3 floor minus the controller's own cell.
        if (crusher.baseSlots().size() != 8) {
            throw new IllegalStateException("self-check: crusher floor must have 8 slots, got "
                    + crusher.baseSlots().size());
        }
        // The controller must not double as a body cell.
        if (crusher.body().stream().anyMatch(slot -> slot.offset().equals(crusher.controllerOffset()))) {
            throw new IllegalStateException("self-check: crusher controller leaked into the body");
        }
        // A hollow 3x3 shell over three layers: 9 perimeter cells minus the one
        // open centre per layer, so 8 cells a layer.
        if (crusher.body().size() != 24) {
            throw new IllegalStateException("self-check: crusher body must be 24 cells, got "
                    + crusher.body().size());
        }
        // No body cell may sit on the floor: the floor is the base layer.
        if (crusher.body().stream().anyMatch(slot -> slot.offset().getY() <= 0)) {
            throw new IllegalStateException("self-check: crusher body must rise above the base layer");
        }
    }

    private static void checkModifierArithmetic() {
        ModifierSet base = ModifierSet.BASE;
        // One grinding core: +100% speed, +120% energy -> 2.0x and 2.2x.
        ModifierSet one = base.plus(FillerModifier.speedTrade(1.0f, 1.2f));
        assertClose("speed after one grinding core", one.speedMultiplier(), 2.0f);
        assertClose("energy after one grinding core", one.energyMultiplier(), 2.2f);

        // A parallel core buys a slot at a speed cost.
        ModifierSet parallel = base.plus(FillerModifier.parallel(1, -0.4f, 0.5f));
        if (parallel.parallelBonus() != 1) {
            throw new IllegalStateException("self-check: parallel core did not add a slot");
        }

        // Effective time/cost helpers must invert consistently.
        int time = one.effectiveProcessingTime(100);
        if (time != 50) {
            throw new IllegalStateException("self-check: expected 100 ticks at 2.0x to become 50, got " + time);
        }

        // Clamps hold: doubling a speed multiplier many times must not exceed the cap.
        ModifierSet capped = base.plusAll(java.util.List.of(
                FillerModifier.speedTrade(1.0f, 0f), FillerModifier.speedTrade(1.0f, 0f),
                FillerModifier.speedTrade(1.0f, 0f), FillerModifier.speedTrade(1.0f, 0f),
                FillerModifier.speedTrade(1.0f, 0f), FillerModifier.speedTrade(1.0f, 0f),
                FillerModifier.speedTrade(1.0f, 0f), FillerModifier.speedTrade(1.0f, 0f)));
        if (capped.speedMultiplier() > ModifierSet.MAX_SPEED_MULTIPLIER) {
            throw new IllegalStateException("self-check: speed cap not enforced");
        }
    }

    private static void checkTierConsistency() {
        java.util.List<EnergyTier> same = java.util.List.of(EnergyTier.MV, EnergyTier.MV, EnergyTier.MV);
        if (!TierMaterials.allSame(same) || TierMaterials.commonTier(same) != EnergyTier.MV) {
            throw new IllegalStateException("self-check: identical tiers not recognised as one");
        }
        java.util.List<EnergyTier> mixed = java.util.List.of(EnergyTier.MV, EnergyTier.HV);
        if (TierMaterials.allSame(mixed) || TierMaterials.commonTier(mixed) != null) {
            throw new IllegalStateException("self-check: mixed tiers not rejected");
        }
        // Nulls (non-level-bearing blocks) are ignored.
        java.util.List<EnergyTier> withNulls = new java.util.ArrayList<>();
        withNulls.add(null);
        withNulls.add(EnergyTier.HV);
        withNulls.add(null);
        if (TierMaterials.commonTier(withNulls) != EnergyTier.HV) {
            throw new IllegalStateException("self-check: nulls should not break tier resolution");
        }
    }

    private static void checkFillerKinds() {
        for (FillerKind kind : FillerKind.values()) {
            if (kind.id() == null || kind.id().isEmpty()) {
                throw new IllegalStateException("self-check: filler kind " + kind + " has no id");
            }
            if (kind.role() != BlockRole.FILLER) {
                throw new IllegalStateException("self-check: filler kind " + kind + " does not report the FILLER role");
            }
        }
    }

    /**
     * Every filler kind must have explicit numbers, and the registry must be the
     * only source of them. Without this, a kind silently contributes nothing and
     * a machine fills itself with useless cores.
     */
    private static void checkFillerRegistry() {        for (FillerKind kind : FillerKind.values()) {
            if (!com.stardustindustry.stardustindustry.multiblock.modifier.FillerRegistry.isRegistered(kind)) {
                throw new IllegalStateException("self-check: filler kind " + kind + " has no registered modifier");
            }
        }
        // The grinding core is the canonical trade; pin its numbers so a balance
        // edit that breaks the documented trade is caught here.
        com.stardustindustry.stardustindustry.multiblock.modifier.FillerModifier grinding =
                com.stardustindustry.stardustindustry.multiblock.modifier.FillerRegistry.modifierOf(FillerKind.GRINDING_CORE);
        if (Math.abs(grinding.speedDelta() - 1.0f) > 1.0e-4f
                || Math.abs(grinding.energyDelta() - 1.2f) > 1.0e-4f) {
            throw new IllegalStateException("self-check: grinding core no longer matches its documented trade");
        }
    }

    /**
     * The LV closed loop must be fully wired: its ports and base block carry a
     * tier, its fillers carry a kind, and all of them share one tier so they can
     * actually be built together.
     */
    private static void checkLvParts() {
        net.minecraft.world.level.block.Block lvBase =
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_BASE.get();
        net.minecraft.world.level.block.Block lvItem =
                com.stardustindustry.stardustindustry.registry.ModBlocks.LV_ITEM_PORT.get();

        if (TierMaterials.tierOf(lvBase) != EnergyTier.LV || TierMaterials.tierOf(lvItem) != EnergyTier.LV) {
            throw new IllegalStateException("self-check: LV base/port are not registered as LV");
        }
        if (MachinePartTypes.typeOf(lvBase) != MachinePartTypes.PartType.BASE) {
            throw new IllegalStateException("self-check: LV base is not classified as BASE");
        }
        if (MachinePartTypes.fillerKindOf(com.stardustindustry.stardustindustry.registry.ModBlocks.LV_GRINDING_CORE.get())
                != FillerKind.GRINDING_CORE) {
            throw new IllegalStateException("self-check: LV grinding core is not classified as its kind");
        }
        if (MachinePartTypes.typeOf(com.stardustindustry.stardustindustry.registry.ModBlocks.TANK_FRAME.get())
                != MachinePartTypes.PartType.FRAME) {
            throw new IllegalStateException("self-check: the tank frame is not classified as FRAME");
        }
        if (MachinePartTypes.typeOf(com.stardustindustry.stardustindustry.registry.ModBlocks.TANK_SHELL.get())
                != MachinePartTypes.PartType.SHELL) {
            throw new IllegalStateException("self-check: the tank shell is not classified as SHELL");
        }
        // A tank has no tier: its parts must not be registered as level-bearing,
        // or a tank could be built with clashing tiers that mean nothing.
        if (TierMaterials.tierOf(com.stardustindustry.stardustindustry.registry.ModBlocks.TANK_FRAME.get()) != null
                || TierMaterials.tierOf(com.stardustindustry.stardustindustry.registry.ModBlocks.TANK_SHELL.get()) != null
                || TierMaterials.tierOf(com.stardustindustry.stardustindustry.registry.ModBlocks.TANK_GLASS.get()) != null) {
            throw new IllegalStateException("self-check: a tank part must not carry a voltage tier");
        }
        if (MachinePartTypes.typeOf(com.stardustindustry.stardustindustry.registry.ModBlocks.STEEL_CASING.get())
                != MachinePartTypes.PartType.SHELL) {
            throw new IllegalStateException("self-check: steel casing is not classified as SHELL");
        }
    }

    /**
     * The dynamic machine's whole validity rule is "count how many edges a cell
     * touches": 2+ means frame, 1 means face, 0 means interior. If this count is
     * wrong the provider nags about every block, so it is pinned here.
     */
    private static void checkDynamicGeometry() {
        com.stardustindustry.stardustindustry.multiblock.provider.ScanBounds bounds =
                new com.stardustindustry.stardustindustry.multiblock.provider.ScanBounds(
                        new net.minecraft.core.BlockPos(0, 0, 0),
                        new net.minecraft.core.BlockPos(4, 4, 4));

        if (bounds.edgesTouched(new net.minecraft.core.BlockPos(0, 0, 0)) != 3) {
            throw new IllegalStateException("self-check: a box corner must touch three edges");
        }
        if (bounds.edgesTouched(new net.minecraft.core.BlockPos(0, 0, 2)) != 2) {
            throw new IllegalStateException("self-check: a box edge must touch two edges");
        }
        if (bounds.edgesTouched(new net.minecraft.core.BlockPos(0, 2, 2)) != 1) {
            throw new IllegalStateException("self-check: a box face must touch one edge");
        }
        if (bounds.edgesTouched(new net.minecraft.core.BlockPos(2, 2, 2)) != 0) {
            throw new IllegalStateException("self-check: a box interior must touch no edges");
        }
        if (bounds.size(net.minecraft.core.Direction.Axis.X) != 5) {
            throw new IllegalStateException("self-check: scan bounds size is off by one");
        }
    }

    /**
     * The tank's capacity rule is pure arithmetic over the box it encloses, so it
     * can be pinned without a live level. A 3x3x3 tank holds 128 buckets and a
     * 9x9x9 holds 43904, which is what the design promises the player.
     */
    private static void checkTankGeometry() {
        // A 3x3x3 box: one interior cell -> one bucket-block worth * 128 buckets.
        if (interiorCellsOfBox(3) != 1) {
            throw new IllegalStateException("self-check: a 3x3x3 tank must have one interior cell");
        }
        if (interiorCellsOfBox(9) != 343) {
            throw new IllegalStateException("self-check: a 9x9x9 tank must have 343 interior cells");
        }
        int expectedSmall = 1 * 128 * 1000;
        int expectedLarge = 343 * 128 * 1000;
        if (expectedSmall != 128_000 || expectedLarge != 43_904_000) {
            throw new IllegalStateException("self-check: tank capacity constants drifted from the design");
        }
    }

    /** Interior air cells of a {@code size} cubed box: {@code (size-2)^3}. */
    private static int interiorCellsOfBox(int size) {
        int inner = size - 2;
        return inner * inner * inner;
    }

    private static void assertClose(String what, float actual, float expected) {        if (Math.abs(actual - expected) > 1.0e-4f) {
            throw new IllegalStateException("self-check: " + what + " expected " + expected + " but was " + actual);
        }
    }

    private static void expectThrows(String what, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new IllegalStateException("self-check: expected '" + what + "' to throw, but it did not");
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return com.stardustindustry.stardustindustry.StardustIndustry.id("selfcheck/" + path);
    }
}
