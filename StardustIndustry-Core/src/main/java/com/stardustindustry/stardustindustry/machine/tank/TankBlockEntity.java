package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.machine.module.FluidBufferModule;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fluid tank: a hollow rectangular container for a single fluid.
 *
 * <p>It is the fluid medium of {@link AbstractTankBlockEntity}: the shared vessel
 * logic lives in the base, and this class contributes only what makes it a
 * <em>liquid</em> tank — a {@link FluidBufferModule}, the fluid medium tag, and
 * the fluid's name for the HUD. A gas tank is its exact structural twin with a
 * gas buffer instead.</p>
 *
 * <h2>Capacity</h2>
 * The fluid module's capacity is not fixed but read through an override
 * ({@link #currentCapacityMb()}), so a tank that is rebuilt a size larger holds
 * more on the very next tick without a restart.
 */
public class TankBlockEntity extends AbstractTankBlockEntity {

    private final FluidBufferModule fluid;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK_SHELL.get(), pos, state);

        this.fluid = modules().provide(ResourceType.FLUID,
                modules().add(new FluidBufferModule(1)));
        this.fluid.setCapacityOverride(this::currentCapacityMb);

        initialiseModules();
    }

    @Override
    public TankMedium medium() {
        return TankMedium.FLUID;
    }

    public FluidBufferModule fluid() {
        return fluid;
    }

    @Override
    public int storedAmountMb() {
        return fluid.amount();
    }

    @Override
    public TankContents contents() {
        if (fluid.fluid().isEmpty()) {
            return null;
        }
        return new TankContents(
                fluid.fluid().getFluidType().getDescriptionId(),
                // The server's language is always English, so this resolves the
                // English name for a client that runs in another language.
                fluid.fluid().getHoverName().getString(),
                fluid.amount());
    }
}
