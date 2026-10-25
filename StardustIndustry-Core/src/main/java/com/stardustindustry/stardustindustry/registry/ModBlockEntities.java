package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.machine.port.EnergyPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.port.FluidPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.port.ItemPortBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block entity types for every machine and port.
 *
 * <p>Kept in one register so the mod class has a single call per registry kind.
 * The three port types are separate so capability registration can attach one
 * capability to each without runtime role checks.</p>
 *
 * <p>There is one port type per <em>role</em>, not per tier. Every tier of, say,
 * a fluid port is the same block entity, differing only in the throughput its
 * block declares, so a new tier adds a block to an existing type here rather
 * than a type of its own.</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StardustIndustry.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER =
            BLOCK_ENTITIES.register("crusher",
                    () -> BlockEntityType.Builder.of(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemPortBlockEntity>> ITEM_PORT =
            BLOCK_ENTITIES.register("item_port",
                    () -> BlockEntityType.Builder.of(ItemPortBlockEntity::new,
                            ModBlocks.LV_ITEM_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPortBlockEntity>> FLUID_PORT =
            BLOCK_ENTITIES.register("fluid_port",
                    () -> BlockEntityType.Builder.of(FluidPortBlockEntity::new,
                            ModBlocks.LV_FLUID_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.port.GasPortBlockEntity>> GAS_PORT =
            BLOCK_ENTITIES.register("gas_port",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.port.GasPortBlockEntity::new,
                            ModBlocks.LV_GAS_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyPortBlockEntity>> ENERGY_PORT =
            BLOCK_ENTITIES.register("energy_port",
                    () -> BlockEntityType.Builder.of(EnergyPortBlockEntity::new,
                            ModBlocks.LV_ENERGY_PORT.get(),
                            ModBlocks.MV_ENERGY_PORT.get(),
                            ModBlocks.HV_ENERGY_PORT.get(),
                            ModBlocks.EHV_ENERGY_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity>> STORAGE_STATION_CONTROLLER =
            BLOCK_ENTITIES.register("storage_station_controller",
                    () -> BlockEntityType.Builder.of(
                            com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity::new,
                            ModBlocks.LV_STORAGE_STATION_CONTROLLER.get(),
                            ModBlocks.MV_STORAGE_STATION_CONTROLLER.get(),
                            ModBlocks.HV_STORAGE_STATION_CONTROLLER.get(),
                            ModBlocks.EHV_STORAGE_STATION_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.InvisibleStructureBlockEntity>> INVISIBLE_STRUCTURE =
            BLOCK_ENTITIES.register("invisible_structure",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.InvisibleStructureBlockEntity::new,
                            ModBlocks.INVISIBLE_STRUCTURE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity>> FLUID_TANK_SHELL =
            BLOCK_ENTITIES.register("fluid_tank_shell",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity::new,
                            ModBlocks.FLUID_TANK_SHELL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.tank.GasTankBlockEntity>> GAS_TANK_SHELL =
            BLOCK_ENTITIES.register("gas_tank_shell",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.tank.GasTankBlockEntity::new,
                            ModBlocks.GAS_TANK_SHELL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity>> TERMINAL_MOUNT =
            BLOCK_ENTITIES.register("terminal_mount",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity::new,
                            ModBlocks.LV_TERMINAL_MOUNT.get(),
                            ModBlocks.MV_TERMINAL_MOUNT.get(),
                            ModBlocks.HV_TERMINAL_MOUNT.get(),
                            ModBlocks.EHV_TERMINAL_MOUNT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity>> ELECTRICAL_DEVICE =
            BLOCK_ENTITIES.register("electrical_device",
                    () -> BlockEntityType.Builder.of(
                            com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity::new,
                            ModBlocks.FUEL_GENERATOR.get(), ModBlocks.CREATIVE_POWER_SOURCE.get(),
                            ModBlocks.TRANSFORMER_LV_MV.get(), ModBlocks.TRANSFORMER_MV_HV.get(), ModBlocks.TRANSFORMER_HV_EHV.get(),
                            ModBlocks.LV_BREAKER.get(), ModBlocks.MV_BREAKER.get(), ModBlocks.HV_BREAKER.get(), ModBlocks.EHV_BREAKER.get(),
                            ModBlocks.LV_FUSE_BOX.get(), ModBlocks.MV_FUSE_BOX.get(), ModBlocks.HV_FUSE_BOX.get(), ModBlocks.EHV_FUSE_BOX.get(),
                            ModBlocks.LV_FE_CONVERTER.get(), ModBlocks.MV_FE_CONVERTER.get(), ModBlocks.HV_FE_CONVERTER.get(), ModBlocks.EHV_FE_CONVERTER.get())
                            .build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    /**
     * Links the port blocks to their freshly registered block-entity types.
     *
     * <p>A port block has to name its type to create it, but the block is
     * declared before the type exists. Rather than a circular reference this
     * small post-registration step wires the two together once both are
     * available, which is called from the mod constructor.</p>
     */
    public static void wirePorts() {
        // Each role shares one type across every tier that exists; a new tier
        // must be wired here as well, or its first placement throws inside
        // newBlockEntity.
        ModBlocks.LV_ITEM_PORT.get().withBlockEntityType(ITEM_PORT.get());
        ModBlocks.LV_FLUID_PORT.get().withBlockEntityType(FLUID_PORT.get());
        ModBlocks.LV_GAS_PORT.get().withBlockEntityType(GAS_PORT.get());
        ModBlocks.LV_ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());
        ModBlocks.MV_ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());
        ModBlocks.HV_ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());
        ModBlocks.EHV_ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());
        ModBlocks.INVISIBLE_STRUCTURE.get().withBlockEntityType(INVISIBLE_STRUCTURE.get());
    }
}
