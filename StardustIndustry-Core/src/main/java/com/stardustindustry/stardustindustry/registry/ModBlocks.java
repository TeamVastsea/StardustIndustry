package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.machine.CasingBlock;
import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlock;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;
import com.stardustindustry.stardustindustry.multiblock.PartRole;
import com.stardustindustry.stardustindustry.multiblock.TierMaterials;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * All blocks this mod adds.
 *
 * <p>Three families live here:</p>
 * <ul>
 *   <li><b>Machines</b> - the controller blocks that own a block entity.</li>
 *   <li><b>Casing</b> - inert structural blocks that satisfy casing positions.</li>
 *   <li><b>Ports</b> - the blocks that connect a multiblock to pipes and cables.</li>
 * </ul>
 *
 * <p>Properties read as heavy industrial metal: metal sound, a pickaxe
 * requirement and hardness that scales with the machine's tier.</p>
 */
public final class ModBlocks {

    /** The starter crusher, the first machine of the processing chain. */
    public static final DeferredBlock<CrusherBlock> CRUSHER = ModRegistries.BLOCKS.register("crusher",
            () -> new CrusherBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Structural casing for multiblock shells. Cheap and inert. */
    public static final DeferredBlock<CasingBlock> STEEL_CASING = ModRegistries.BLOCKS.register("steel_casing",
            () -> new CasingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /**
     * The block that replaces a static machine's body once installed. Invisible
     * to the eye but solid underfoot: the controller's renderer draws the machine
     * instead, while the cell keeps a full collision box.
     */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.InvisibleStructureBlock> INVISIBLE_STRUCTURE =
            ModRegistries.BLOCKS.register("invisible_structure",
                    () -> new com.stardustindustry.stardustindustry.machine.InvisibleStructureBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .strength(0.5f, 0.0f)
                                    .noOcclusion()
                                    .sound(SoundType.METAL)
                                    .dynamicShape()));

    // ---- tiered parts (LV set) ----
    //
    // Parts are named by tier, not by material: `lv_base`, `lv_item_port`, ... and
    // it is the crafting recipe that decides progression. D4 ships the LV set as
    // the first closed loop; MV/HV/EHV are added by the same pattern as their
    // recipes are designed.

    /** LV base block: the default filler of an empty base slot, and the tier it declares. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.BaseBlock> LV_BASE =
            ModRegistries.BLOCKS.register("lv_base",
                    () -> new com.stardustindustry.stardustindustry.machine.BaseBlock(baseProperties(),
                            com.stardustindustry.stardustindustry.energy.EnergyTier.LV));

    /** LV item port. */
    public static final DeferredBlock<MachinePortBlock> LV_ITEM_PORT = ModRegistries.BLOCKS.register("lv_item_port",
            () -> new MachinePortBlock(portProperties(), PartRole.PORT_ITEM,
                    com.stardustindustry.stardustindustry.energy.EnergyTier.LV));

    /** LV fluid port. */
    public static final DeferredBlock<MachinePortBlock> LV_FLUID_PORT = ModRegistries.BLOCKS.register("lv_fluid_port",
            () -> new MachinePortBlock(portProperties(), PartRole.PORT_FLUID,
                    com.stardustindustry.stardustindustry.energy.EnergyTier.LV));

    /** LV gas port. */
    public static final DeferredBlock<MachinePortBlock> LV_GAS_PORT = ModRegistries.BLOCKS.register("lv_gas_port",
            () -> new MachinePortBlock(portProperties(), PartRole.PORT_GAS,
                    com.stardustindustry.stardustindustry.energy.EnergyTier.LV));

    /** LV energy port. */
    public static final DeferredBlock<MachinePortBlock> LV_ENERGY_PORT = ModRegistries.BLOCKS.register("lv_energy_port",
            () -> new MachinePortBlock(portProperties(), PartRole.PORT_ENERGY,
                    com.stardustindustry.stardustindustry.energy.EnergyTier.LV));

    /** LV grinding core: speed at a steeper energy cost. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.FillerBlock> LV_GRINDING_CORE =
            ModRegistries.BLOCKS.register("lv_grinding_core",
                    () -> new com.stardustindustry.stardustindustry.machine.FillerBlock(fillerProperties(),
                            com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.GRINDING_CORE));

    /** LV heat exchanger core: lowers energy draw. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.FillerBlock> LV_HEAT_EXCHANGER_CORE =
            ModRegistries.BLOCKS.register("lv_heat_exchanger_core",
                    () -> new com.stardustindustry.stardustindustry.machine.FillerBlock(fillerProperties(),
                            com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.HEAT_EXCHANGER_CORE));

    /** LV parallel core: an extra recipe slot for speed and energy. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.FillerBlock> LV_PARALLEL_CORE =
            ModRegistries.BLOCKS.register("lv_parallel_core",
                    () -> new com.stardustindustry.stardustindustry.machine.FillerBlock(fillerProperties(),
                            com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.PARALLEL_CORE));

    /** LV buffer core: larger internal buffers. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.FillerBlock> LV_BUFFER_CORE =
            ModRegistries.BLOCKS.register("lv_buffer_core",
                    () -> new com.stardustindustry.stardustindustry.machine.FillerBlock(fillerProperties(),
                            com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.BUFFER_CORE));

    /** LV frame removed with the old demo tank; the real tank frame is tierless. */

    // ---- tank ----
    //
    // A tank is a tierless vessel: twelve frame edges, and faces of shell or
    // glass (or a tiered fluid port). Its capacity comes from the interior it
    // encloses, so none of these blocks carries a level.

    /** Tank frame: the twelve edges of a tank. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.tank.TankFrameBlock> TANK_FRAME =
            ModRegistries.BLOCKS.register("tank_frame",
                    () -> new com.stardustindustry.stardustindustry.machine.tank.TankFrameBlock(frameProperties()));

    /** Fluid tank shell: the six faces of a fluid tank; one of them owns the block entity. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.tank.TankShellBlock> FLUID_TANK_SHELL =
            ModRegistries.BLOCKS.register("fluid_tank_shell",
                    () -> new com.stardustindustry.stardustindustry.machine.tank.TankShellBlock(
                            com.stardustindustry.stardustindustry.machine.tank.TankMedium.FLUID,
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.5f, 6.0f)
                                    .sound(SoundType.METAL)
                                    .requiresCorrectToolForDrops()));

    /** Gas tank shell: the six faces of a gas tank; one of them owns the block entity. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.tank.TankShellBlock> GAS_TANK_SHELL =
            ModRegistries.BLOCKS.register("gas_tank_shell",
                    () -> new com.stardustindustry.stardustindustry.machine.tank.TankShellBlock(
                            com.stardustindustry.stardustindustry.machine.tank.TankMedium.GAS,
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.METAL)
                                    .strength(3.5f, 6.0f)
                                    .sound(SoundType.METAL)
                                    .requiresCorrectToolForDrops()));

    /** Industrial glass: a transparent stand-in for a shell panel, shared by both tank kinds. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.tank.TankGlassBlock> INDUSTRIAL_GLASS =
            ModRegistries.BLOCKS.register("industrial_glass",
                    () -> new com.stardustindustry.stardustindustry.machine.tank.TankGlassBlock(
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.NONE)
                                    .strength(0.6f, 0.6f)
                                    .sound(SoundType.GLASS)
                                    .noOcclusion()
                                    .isValidSpawn((state, level, pos, type) -> false)
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false)));

    /** Block item for the crusher. */
    public static final DeferredItem<BlockItem> CRUSHER_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("crusher", CRUSHER);
    /** Block item for the casing. */
    public static final DeferredItem<BlockItem> STEEL_CASING_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("steel_casing", STEEL_CASING);
    /** Block item for the invisible structure block; not offered in the creative tab. */
    public static final DeferredItem<BlockItem> INVISIBLE_STRUCTURE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("invisible_structure", INVISIBLE_STRUCTURE);

    /** Block item for the LV base block. */
    public static final DeferredItem<BlockItem> LV_BASE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_base", LV_BASE);
    /** Block item for the LV item port. */
    public static final DeferredItem<BlockItem> LV_ITEM_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_item_port", LV_ITEM_PORT);
    /** Block item for the LV fluid port. */
    public static final DeferredItem<BlockItem> LV_FLUID_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_fluid_port", LV_FLUID_PORT);
    /** Block item for the LV gas port. */
    public static final DeferredItem<BlockItem> LV_GAS_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_gas_port", LV_GAS_PORT);
    /** Block item for the LV energy port. */
    public static final DeferredItem<BlockItem> LV_ENERGY_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_energy_port", LV_ENERGY_PORT);
    /** Block item for the LV grinding core. */
    public static final DeferredItem<BlockItem> LV_GRINDING_CORE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_grinding_core", LV_GRINDING_CORE);
    /** Block item for the LV heat exchanger core. */
    public static final DeferredItem<BlockItem> LV_HEAT_EXCHANGER_CORE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_heat_exchanger_core", LV_HEAT_EXCHANGER_CORE);
    /** Block item for the LV parallel core. */
    public static final DeferredItem<BlockItem> LV_PARALLEL_CORE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_parallel_core", LV_PARALLEL_CORE);
    /** Block item for the LV buffer core. */
    public static final DeferredItem<BlockItem> LV_BUFFER_CORE_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_buffer_core", LV_BUFFER_CORE);
    /** Block item for the tank frame. */
    public static final DeferredItem<BlockItem> TANK_FRAME_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("tank_frame", TANK_FRAME);
    /** Block item for the fluid tank shell. */
    public static final DeferredItem<BlockItem> FLUID_TANK_SHELL_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("fluid_tank_shell", FLUID_TANK_SHELL);
    /** Block item for the gas tank shell. */
    public static final DeferredItem<BlockItem> GAS_TANK_SHELL_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("gas_tank_shell", GAS_TANK_SHELL);
    /** Block item for the industrial glass. */
    public static final DeferredItem<BlockItem> INDUSTRIAL_GLASS_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("industrial_glass", INDUSTRIAL_GLASS);

    private ModBlocks() {}

    /** Ports share their look and hardness; only their role differs. */
    private static BlockBehaviour.Properties portProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Base blocks are the machine's floor; slightly tougher than a port. */
    private static BlockBehaviour.Properties baseProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Filler cores are internal, decorative blocks; easy to break and reshape. */
    private static BlockBehaviour.Properties fillerProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(2.5f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Frames are exposed structural edges; the toughest non-machined block. */
    private static BlockBehaviour.Properties frameProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(4.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Forces class initialisation so the deferred entries above are created. */
    public static void init() {
        // Referencing the class is enough; the method exists to make the intent explicit.
    }

    /**
     * Classifies the structural blocks so structure code can ask "is this a
     * port / filler / base / casing?" instead of hard-coding block identities.
     * Called once during common setup, after the deferred registers have run.
     */
    public static void registerPartTypes() {
        MachinePartTypes.register(STEEL_CASING.get(), MachinePartTypes.PartType.SHELL);

        // LV set: ports, base block, and the four filler kinds.
        MachinePartTypes.register(LV_ITEM_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(LV_FLUID_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(LV_GAS_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(LV_ENERGY_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(LV_BASE.get(), MachinePartTypes.PartType.BASE);
        MachinePartTypes.registerFiller(LV_GRINDING_CORE.get(),
                com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.GRINDING_CORE);
        MachinePartTypes.registerFiller(LV_HEAT_EXCHANGER_CORE.get(),
                com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.HEAT_EXCHANGER_CORE);
        MachinePartTypes.registerFiller(LV_PARALLEL_CORE.get(),
                com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.PARALLEL_CORE);
        MachinePartTypes.registerFiller(LV_BUFFER_CORE.get(),
                com.stardustindustry.stardustindustry.multiblock.provider.FillerKind.BUFFER_CORE);

        // Tank set: tierless structural parts. The frame classifies as FRAME and
        // the shell/glass as SHELL; the tank's own provider reads their exact
        // block identity for the edge/face rules, but the part types let generic
        // code (projection, break handling) treat them sensibly too.
        MachinePartTypes.register(TANK_FRAME.get(), MachinePartTypes.PartType.FRAME);
        MachinePartTypes.register(FLUID_TANK_SHELL.get(), MachinePartTypes.PartType.SHELL);
        MachinePartTypes.register(GAS_TANK_SHELL.get(), MachinePartTypes.PartType.SHELL);
        MachinePartTypes.register(INDUSTRIAL_GLASS.get(), MachinePartTypes.PartType.SHELL);

        // Level-bearing blocks: base blocks and every tiered port. The tank's own
        // parts are deliberately absent, since a tank has no tier.
        TierMaterials.register(LV_BASE.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_ITEM_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_FLUID_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_GAS_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_ENERGY_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
    }

    /** The block preserved for callers that only need the type. */
    public static Block crusherBlock() {
        return CRUSHER.get();
    }
}
