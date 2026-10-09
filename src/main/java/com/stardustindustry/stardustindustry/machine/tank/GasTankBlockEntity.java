package com.stardustindustry.stardustindustry.machine.tank;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.gas.GasStack;
import com.stardustindustry.stardustindustry.machine.module.GasBufferModule;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The gas tank: a hollow rectangular container for a single gas.
 *
 * <p>It is the structural twin of {@link TankBlockEntity}. The box, its edges
 * and faces, the anchor rule, the capacity arithmetic, the client sync and the
 * parameter screen are all inherited unchanged; this class contributes only what
 * makes it a <em>gas</em> tank — a {@link GasBufferModule}, the gas medium tag,
 * and the gas's name for the HUD.</p>
 *
 * <h2>Appearance</h2>
 * The one visible difference is inside: a fluid tank draws its liquid rising from
 * the floor, a gas tank draws its contents as a concentration filling the whole
 * cavity. That lives in the renderer, keyed off {@link #medium()}.
 *
 * <h2>One gas only</h2>
 * Like the fluid tank, a gas tank holds a single gas. Mixing two gases is refused
 * at every entry point through {@link GasBufferModule}.
 */
public class GasTankBlockEntity extends AbstractTankBlockEntity {

    private final GasBufferModule gas;

    public GasTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAS_TANK_SHELL.get(), pos, state);

        this.gas = modules().provide(ResourceType.GAS,
                modules().add(new GasBufferModule(1)));
        this.gas.setCapacityOverride(this::currentCapacityMb);

        initialiseModules();
    }

    @Override
    public TankMedium medium() {
        return TankMedium.GAS;
    }

    public GasBufferModule gas() {
        return gas;
    }

    @Override
    public int storedAmountMb() {
        return gas.amount();
    }

    @Override
    public TankContents contents() {
        GasStack held = gas.gas();
        if (held.isEmpty()) {
            return null;
        }
        return new TankContents(
                held.gas().getDescriptionId(),
                // The server's language is always English, so this resolves the
                // English name for a client that runs in another language.
                net.minecraft.network.chat.Component.translatable(held.gas().getDescriptionId()).getString(),
                held.amount());
    }
}
