package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.machine.MachinePortBlockEntity;
import com.stardustindustry.stardustindustry.machine.module.EnergyBufferModule;
import com.stardustindustry.stardustindustry.machine.module.FluidBufferModule;
import com.stardustindustry.stardustindustry.machine.module.ItemInventoryModule;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Bridges the module system to NeoForge capabilities.
 *
 * <p>Everything inside a machine speaks the mod's own module interfaces, but the
 * rest of the world speaks NeoForge capabilities. This class is the single
 * translation point: it registers capability providers for the port blocks and
 * resolves each request by finding the port's bound controller and returning the
 * matching module's capability.</p>
 *
 * <p>Registering on the <b>block</b> rather than the block entity type is
 * deliberate. A port may legitimately have no controller (it is placed but its
 * structure is incomplete), in which case it must report no capability at all.
 * That decision depends on runtime state, which only a block-level provider can
 * make cleanly.</p>
 */
public final class ModCapabilities {

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Energy port -> the machine's FE buffer.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, side) -> energyStorage(blockEntity),
                ModBlocks.LV_ENERGY_PORT.get());

        // Fluid ports forward to the machine's fluid buffer. A machine without a
        // tank module reports nothing, so a port on a non-fluid machine is inert
        // rather than a silent black hole. A tiered port further caps the rate at
        // its own tier, so two ports on one machine move fluid at their own speeds.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> fluidHandler(blockEntity, fluidRate(state)),
                ModBlocks.LV_FLUID_PORT.get());

        // The tank's shell is the machine itself, not a port: it exposes its fluid
        // buffer directly, so a pipe can also connect straight to the vessel wall.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> tankHandler(blockEntity),
                ModBlocks.TANK_SHELL.get());

        // LV ports expose the same capabilities, on the same controller.
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> itemHandler(blockEntity),
                ModBlocks.LV_ITEM_PORT.get());
    }

    /**
     * The per-operation fluid cap a port of this block state imposes, in mB.
     *
     * <p>An untiered port carries no rating and passes {@link Integer#MAX_VALUE},
     * leaving the module's own limit in charge. A tiered port asks for its tier's
     * throughput, which is how a tank's ports differ from one another.</p>
     */
    private static int fluidRate(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof MachinePortBlock port && port.tier() != null) {
            return port.tier().fluidTransfer();
        }
        return Integer.MAX_VALUE;
    }

    /** The tank shell's own fluid capability, capped at the module's default. */
    private static IFluidHandler tankHandler(Object blockEntity) {
        if (blockEntity instanceof com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity tank) {
            return tank.fluid().capability();
        }
        return null;
    }

    private static IItemHandler itemHandler(Object blockEntity) {
        MachineBlockEntity controller = controllerOf(blockEntity);
        if (controller == null) {
            return null;
        }
        ItemInventoryModule inventory = controller.modules().get(ItemInventoryModule.class);
        return inventory != null ? inventory.capability() : null;
    }

    private static IEnergyStorage energyStorage(Object blockEntity) {
        MachineBlockEntity controller = controllerOf(blockEntity);
        if (controller == null) {
            return null;
        }
        EnergyBufferModule energy = controller.modules().get(EnergyBufferModule.class);
        return energy != null ? energy.capability() : null;
    }

    private static IFluidHandler fluidHandler(Object blockEntity, int rateLimit) {
        MachineBlockEntity controller = controllerOf(blockEntity);
        if (controller == null) {
            return null;
        }
        FluidBufferModule fluid = controller.modules().get(FluidBufferModule.class);
        return fluid != null ? fluid.capability(rateLimit) : null;
    }

    /** The controller a port is bound to, or {@code null} when the port is idle. */
    private static MachineBlockEntity controllerOf(Object blockEntity) {
        if (blockEntity instanceof MachinePortBlockEntity port) {
            return port.controller();
        }
        return null;
    }
}
