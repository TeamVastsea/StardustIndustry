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
                            ModBlocks.ITEM_PORT.get(),
                            ModBlocks.LV_ITEM_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPortBlockEntity>> FLUID_PORT =
            BLOCK_ENTITIES.register("fluid_port",
                    () -> BlockEntityType.Builder.of(FluidPortBlockEntity::new,
                            ModBlocks.FLUID_PORT.get(),
                            // The tank's fluid ports are the same kind of port as
                            // the machine's, differing only in throughput, so
                            // each tier's port shares this one type.
                            ModBlocks.LV_FLUID_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyPortBlockEntity>> ENERGY_PORT =
            BLOCK_ENTITIES.register("energy_port",
                    () -> BlockEntityType.Builder.of(EnergyPortBlockEntity::new,
                            ModBlocks.ENERGY_PORT.get(),
                            ModBlocks.LV_ENERGY_PORT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.InvisibleStructureBlockEntity>> INVISIBLE_STRUCTURE =
            BLOCK_ENTITIES.register("invisible_structure",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.InvisibleStructureBlockEntity::new,
                            ModBlocks.INVISIBLE_STRUCTURE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity>> TANK_SHELL =
            BLOCK_ENTITIES.register("tank_shell",
                    () -> BlockEntityType.Builder.of(com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity::new,
                            ModBlocks.TANK_SHELL.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
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
        // Each port type is shared by the untiered block and every tiered
        // variant of the same role; both must be wired, or the first placement
        // of an unwired one throws inside newBlockEntity.
        ModBlocks.ITEM_PORT.get().withBlockEntityType(ITEM_PORT.get());
        ModBlocks.LV_ITEM_PORT.get().withBlockEntityType(ITEM_PORT.get());

        ModBlocks.FLUID_PORT.get().withBlockEntityType(FLUID_PORT.get());
        ModBlocks.LV_FLUID_PORT.get().withBlockEntityType(FLUID_PORT.get());

        ModBlocks.ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());
        ModBlocks.LV_ENERGY_PORT.get().withBlockEntityType(ENERGY_PORT.get());

        ModBlocks.INVISIBLE_STRUCTURE.get().withBlockEntityType(INVISIBLE_STRUCTURE.get());
    }
}
