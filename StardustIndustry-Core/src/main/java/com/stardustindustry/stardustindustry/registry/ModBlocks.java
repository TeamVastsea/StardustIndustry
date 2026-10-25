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

    /** Higher-voltage energy ports are shared by storage stations and future machines. */
    public static final DeferredBlock<MachinePortBlock> MV_ENERGY_PORT = energyPort(
            "mv_energy_port", com.stardustindustry.stardustindustry.energy.EnergyTier.MV);
    public static final DeferredBlock<MachinePortBlock> HV_ENERGY_PORT = energyPort(
            "hv_energy_port", com.stardustindustry.stardustindustry.energy.EnergyTier.HV);
    public static final DeferredBlock<MachinePortBlock> EHV_ENERGY_PORT = energyPort(
            "ehv_energy_port", com.stardustindustry.stardustindustry.energy.EnergyTier.EHV);

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

    // ---- cables ----
    //
    // A terminal is two pieces: a mount (the rack, one block per tier) and
    // terminals (the individual pins bolted onto its grid). See
    // docs/electric-power-system-design.md §6.3.

    /** The LV terminal mount: a 4 x 4 rack of 16 terminals. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock> LV_TERMINAL_MOUNT =
            terminalMount("lv_terminal_mount", com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
    /** The MV terminal mount: a 2 x 2 rack of 4 terminals. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock> MV_TERMINAL_MOUNT =
            terminalMount("mv_terminal_mount", com.stardustindustry.stardustindustry.energy.EnergyTier.MV);
    /** The HV terminal mount: a single 110 kV insulator. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock> HV_TERMINAL_MOUNT =
            terminalMount("hv_terminal_mount", com.stardustindustry.stardustindustry.energy.EnergyTier.HV);
    /** The EHV terminal mount: a single 500 kV insulator. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock> EHV_TERMINAL_MOUNT =
            terminalMount("ehv_terminal_mount", com.stardustindustry.stardustindustry.energy.EnergyTier.EHV);

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

    // ---- storage station ----

    /** Tierless shell parts shared by all four station voltage levels. */
    public static final DeferredBlock<Block> STORAGE_STATION_FRAME =
            ModRegistries.BLOCKS.register("storage_station_frame", () -> new Block(frameProperties()));
    public static final DeferredBlock<Block> STORAGE_STATION_CASING =
            ModRegistries.BLOCKS.register("storage_station_casing", () -> new Block(baseProperties()));

    /** One face-mounted controller and one internal cell variant per voltage tier. */
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock>
            LV_STORAGE_STATION_CONTROLLER = storageController("lv_storage_station_controller",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.LV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock>
            MV_STORAGE_STATION_CONTROLLER = storageController("mv_storage_station_controller",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.MV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock>
            HV_STORAGE_STATION_CONTROLLER = storageController("hv_storage_station_controller",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.HV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock>
            EHV_STORAGE_STATION_CONTROLLER = storageController("ehv_storage_station_controller",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.EHV);

    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock>
            LV_STORAGE_CELL = storageCell("lv_storage_cell",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.LV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock>
            MV_STORAGE_CELL = storageCell("mv_storage_cell",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.MV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock>
            HV_STORAGE_CELL = storageCell("hv_storage_cell",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.HV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock>
            EHV_STORAGE_CELL = storageCell("ehv_storage_cell",
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.EHV);

    // ---- native electrical equipment ----
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            FUEL_GENERATOR = electricalDevice("fuel_generator", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.FUEL_GENERATOR);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            CREATIVE_POWER_SOURCE = electricalDevice("creative_power_source", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.CREATIVE_SOURCE);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            TRANSFORMER_LV_MV = electricalDevice("transformer_lv_mv", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.TRANSFORMER_LV_MV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            TRANSFORMER_MV_HV = electricalDevice("transformer_mv_hv", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.TRANSFORMER_MV_HV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            TRANSFORMER_HV_EHV = electricalDevice("transformer_hv_ehv", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.TRANSFORMER_HV_EHV);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            LV_BREAKER = electricalDevice("lv_breaker", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.LV_BREAKER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            MV_BREAKER = electricalDevice("mv_breaker", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.MV_BREAKER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            HV_BREAKER = electricalDevice("hv_breaker", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.HV_BREAKER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            EHV_BREAKER = electricalDevice("ehv_breaker", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.EHV_BREAKER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            LV_FUSE_BOX = electricalDevice("lv_fuse_box", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.LV_FUSE_BOX);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            MV_FUSE_BOX = electricalDevice("mv_fuse_box", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.MV_FUSE_BOX);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            HV_FUSE_BOX = electricalDevice("hv_fuse_box", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.HV_FUSE_BOX);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            EHV_FUSE_BOX = electricalDevice("ehv_fuse_box", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.EHV_FUSE_BOX);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            LV_FE_CONVERTER = electricalDevice("lv_fe_converter", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.LV_FE_CONVERTER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            MV_FE_CONVERTER = electricalDevice("mv_fe_converter", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.MV_FE_CONVERTER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            HV_FE_CONVERTER = electricalDevice("hv_fe_converter", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.HV_FE_CONVERTER);
    public static final DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            EHV_FE_CONVERTER = electricalDevice("ehv_fe_converter", com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind.EHV_FE_CONVERTER);

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
    public static final DeferredItem<BlockItem> MV_ENERGY_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("mv_energy_port", MV_ENERGY_PORT);
    public static final DeferredItem<BlockItem> HV_ENERGY_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("hv_energy_port", HV_ENERGY_PORT);
    public static final DeferredItem<BlockItem> EHV_ENERGY_PORT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("ehv_energy_port", EHV_ENERGY_PORT);
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

    public static final DeferredItem<BlockItem> STORAGE_STATION_FRAME_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("storage_station_frame", STORAGE_STATION_FRAME);
    public static final DeferredItem<BlockItem> STORAGE_STATION_CASING_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("storage_station_casing", STORAGE_STATION_CASING);

    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            LV_STORAGE_STATION_CONTROLLER_ITEM = storageItem("lv_storage_station_controller",
                    LV_STORAGE_STATION_CONTROLLER, com.stardustindustry.stardustindustry.machine.storage.StorageTier.LV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            MV_STORAGE_STATION_CONTROLLER_ITEM = storageItem("mv_storage_station_controller",
                    MV_STORAGE_STATION_CONTROLLER, com.stardustindustry.stardustindustry.machine.storage.StorageTier.MV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            HV_STORAGE_STATION_CONTROLLER_ITEM = storageItem("hv_storage_station_controller",
                    HV_STORAGE_STATION_CONTROLLER, com.stardustindustry.stardustindustry.machine.storage.StorageTier.HV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            EHV_STORAGE_STATION_CONTROLLER_ITEM = storageItem("ehv_storage_station_controller",
                    EHV_STORAGE_STATION_CONTROLLER, com.stardustindustry.stardustindustry.machine.storage.StorageTier.EHV);

    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            LV_STORAGE_CELL_ITEM = storageItem("lv_storage_cell", LV_STORAGE_CELL,
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.LV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            MV_STORAGE_CELL_ITEM = storageItem("mv_storage_cell", MV_STORAGE_CELL,
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.MV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            HV_STORAGE_CELL_ITEM = storageItem("hv_storage_cell", HV_STORAGE_CELL,
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.HV);
    public static final DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            EHV_STORAGE_CELL_ITEM = storageItem("ehv_storage_cell", EHV_STORAGE_CELL,
                    com.stardustindustry.stardustindustry.machine.storage.StorageTier.EHV);

    public static final DeferredItem<BlockItem> FUEL_GENERATOR_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("fuel_generator", FUEL_GENERATOR);
    public static final DeferredItem<BlockItem> CREATIVE_POWER_SOURCE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("creative_power_source", CREATIVE_POWER_SOURCE);
    public static final DeferredItem<BlockItem> TRANSFORMER_LV_MV_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("transformer_lv_mv", TRANSFORMER_LV_MV);
    public static final DeferredItem<BlockItem> TRANSFORMER_MV_HV_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("transformer_mv_hv", TRANSFORMER_MV_HV);
    public static final DeferredItem<BlockItem> TRANSFORMER_HV_EHV_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("transformer_hv_ehv", TRANSFORMER_HV_EHV);
    public static final DeferredItem<BlockItem> LV_BREAKER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("lv_breaker", LV_BREAKER);
    public static final DeferredItem<BlockItem> MV_BREAKER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("mv_breaker", MV_BREAKER);
    public static final DeferredItem<BlockItem> HV_BREAKER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("hv_breaker", HV_BREAKER);
    public static final DeferredItem<BlockItem> EHV_BREAKER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("ehv_breaker", EHV_BREAKER);
    public static final DeferredItem<BlockItem> LV_FUSE_BOX_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("lv_fuse_box", LV_FUSE_BOX);
    public static final DeferredItem<BlockItem> MV_FUSE_BOX_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("mv_fuse_box", MV_FUSE_BOX);
    public static final DeferredItem<BlockItem> HV_FUSE_BOX_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("hv_fuse_box", HV_FUSE_BOX);
    public static final DeferredItem<BlockItem> EHV_FUSE_BOX_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("ehv_fuse_box", EHV_FUSE_BOX);
    public static final DeferredItem<BlockItem> LV_FE_CONVERTER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("lv_fe_converter", LV_FE_CONVERTER);
    public static final DeferredItem<BlockItem> MV_FE_CONVERTER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("mv_fe_converter", MV_FE_CONVERTER);
    public static final DeferredItem<BlockItem> HV_FE_CONVERTER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("hv_fe_converter", HV_FE_CONVERTER);
    public static final DeferredItem<BlockItem> EHV_FE_CONVERTER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem("ehv_fe_converter", EHV_FE_CONVERTER);

    /**
     * The terminal mounts, one per tier. Each is a distinct block because the
     * grid density is a property of the voltage level.
     */
    public static final DeferredItem<BlockItem> LV_TERMINAL_MOUNT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("lv_terminal_mount", LV_TERMINAL_MOUNT);
    public static final DeferredItem<BlockItem> MV_TERMINAL_MOUNT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("mv_terminal_mount", MV_TERMINAL_MOUNT);
    public static final DeferredItem<BlockItem> HV_TERMINAL_MOUNT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("hv_terminal_mount", HV_TERMINAL_MOUNT);
    public static final DeferredItem<BlockItem> EHV_TERMINAL_MOUNT_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem("ehv_terminal_mount", EHV_TERMINAL_MOUNT);

    private ModBlocks() {}

    /** A terminal mount of one tier; the grid comes from the tier, not the block. */
    private static DeferredBlock<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock> terminalMount(
            String name, com.stardustindustry.stardustindustry.energy.EnergyTier tier) {
        return ModRegistries.BLOCKS.register(name,
                () -> new com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock(
                        terminalProperties(), tier));
    }

    private static DeferredBlock<MachinePortBlock> energyPort(
            String name, com.stardustindustry.stardustindustry.energy.EnergyTier tier) {
        return ModRegistries.BLOCKS.register(name,
                () -> new MachinePortBlock(portProperties(), PartRole.PORT_ENERGY, tier));
    }

    private static DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock>
            storageController(String name, com.stardustindustry.stardustindustry.machine.storage.StorageTier tier) {
        return ModRegistries.BLOCKS.register(name,
                () -> new com.stardustindustry.stardustindustry.machine.storage.StorageStationControllerBlock(
                        baseProperties(), tier));
    }

    private static DeferredBlock<com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock>
            storageCell(String name, com.stardustindustry.stardustindustry.machine.storage.StorageTier tier) {
        return ModRegistries.BLOCKS.register(name,
                () -> new com.stardustindustry.stardustindustry.machine.storage.StorageCellBlock(
                        fillerProperties(), tier));
    }

    private static DeferredItem<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem>
            storageItem(String name, DeferredBlock<? extends Block> block,
                        com.stardustindustry.stardustindustry.machine.storage.StorageTier tier) {
        return ModRegistries.ITEMS.register(name,
                () -> new com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockItem(
                        block.get(), new net.minecraft.world.item.Item.Properties(), tier));
    }

    private static DeferredBlock<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock>
            electricalDevice(String name,
                    com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceKind kind) {
        return ModRegistries.BLOCKS.register(name,
                () -> new com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock(
                        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                                .sound(SoundType.METAL).requiresCorrectToolForDrops(), kind));
    }

    /** Terminal racks are light fittings: a stone pickaxe is enough to recover one. */
    private static BlockBehaviour.Properties terminalProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.5f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

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
        MachinePartTypes.register(MV_ENERGY_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(HV_ENERGY_PORT.get(), MachinePartTypes.PartType.PORT);
        MachinePartTypes.register(EHV_ENERGY_PORT.get(), MachinePartTypes.PartType.PORT);
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
        MachinePartTypes.register(STORAGE_STATION_FRAME.get(), MachinePartTypes.PartType.FRAME);
        MachinePartTypes.register(STORAGE_STATION_CASING.get(), MachinePartTypes.PartType.SHELL);

        // Level-bearing blocks: base blocks and every tiered port. The tank's own
        // parts are deliberately absent, since a tank has no tier.
        TierMaterials.register(LV_BASE.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_ITEM_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_FLUID_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_GAS_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(LV_ENERGY_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(MV_ENERGY_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.MV);
        TierMaterials.register(HV_ENERGY_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.HV);
        TierMaterials.register(EHV_ENERGY_PORT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.EHV);

        // Terminal mounts: not machine parts (they never form a multiblock), so
        // they are deliberately absent from MachinePartTypes. Their tier is read
        // from the block itself.
        TierMaterials.register(LV_TERMINAL_MOUNT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.LV);
        TierMaterials.register(MV_TERMINAL_MOUNT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.MV);
        TierMaterials.register(HV_TERMINAL_MOUNT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.HV);
        TierMaterials.register(EHV_TERMINAL_MOUNT.get(), com.stardustindustry.stardustindustry.energy.EnergyTier.EHV);
    }

    /** The block preserved for callers that only need the type. */
    public static Block crusherBlock() {
        return CRUSHER.get();
    }
}
