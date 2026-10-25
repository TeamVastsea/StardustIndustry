package com.stardustindustry.stardustindustry.machine.crusher;

import com.stardustindustry.stardustindustry.capability.ResourceType;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineTier;
import com.stardustindustry.stardustindustry.machine.module.JouleStorageModule;
import com.stardustindustry.stardustindustry.machine.module.ItemInventoryModule;
import com.stardustindustry.stardustindustry.machine.module.ItemInventoryModule.SlotGroup;
import com.stardustindustry.stardustindustry.machine.module.RecipeRunnerModule;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The industrial crusher: the first static multiblock machine of the processing
 * chain.
 *
 * <h2>Shape</h2>
 * A 3x3 floor with a 3x3x3 casing body above it, described once in
 * {@link CrusherStructure}. The controller is the front-centre cell of the floor;
 * the eight cells around it are free base slots that hold every port and
 * engineering block. A static machine never opens a port in its body, so the
 * floor is where all of its plumbing lives.
 *
 * <h2>Installation</h2>
 * Unlike the old auto-forming shell, the crusher is installed with the engineer's
 * tool: right-click the controller once the floor is full and the body matches.
 * On success the body is hidden and the controller's renderer redraws it, so the
 * machine looks exactly as it was built while the server holds the real
 * structure.
 *
 * <p>Slots are one input, two outputs and one upgrade. Items, fluids and power
 * all arrive through the floor ports, which proxy the controller's modules and
 * therefore serve the exact same buffers the recipe runner uses.</p>
 */
public class CrusherBlockEntity extends MachineBlockEntity {

    private static final long BASE_PROCESS_POWER_WATTS = 6_600L;
    private static final long STANDBY_POWER_WATTS = 100L;

    private final ItemInventoryModule inventory;
    private final JouleStorageModule energy;

    /** The crusher's structure, built once and shared by checker and renderer. */
    private static final com.stardustindustry.stardustindustry.multiblock.provider.StaticStructureProvider PROVIDER =
            new com.stardustindustry.stardustindustry.multiblock.provider.StaticStructureProvider(
                    CrusherStructure.build());

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, MachineTier.LV);

        this.inventory = modules().provide(ResourceType.ITEM,
                modules().add(new ItemInventoryModule(SlotGroup.INPUT, SlotGroup.OUTPUT, SlotGroup.OUTPUT, SlotGroup.UPGRADE)));
        this.energy = modules().provide(ResourceType.ENERGY,
                modules().add(new JouleStorageModule(EnergyTier.LV, 13_200L,
                        BASE_PROCESS_POWER_WATTS + STANDBY_POWER_WATTS)));
        RecipeRunnerModule runner = modules().add(new RecipeRunnerModule(inventory, energy));
        energy.setPowerOverride(() -> Math.max(
                BASE_PROCESS_POWER_WATTS + STANDBY_POWER_WATTS,
                saturatedAdd(runner.powerWatts(), STANDBY_POWER_WATTS)));

        initialiseModules();
    }

    @Override
    public com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider provider() {
        return PROVIDER;
    }

    public ItemInventoryModule inventory() {
        return inventory;
    }

    public JouleStorageModule energy() {
        return energy;
    }

    /** The grid asks for the recipe's full 6.6 kW rating while the local buffer has room. */
    public long requestedPowerWatts() {
        return energy.storedJoules() < energy.capacityJoules() ? energy.maxPowerWatts() : 0L;
    }

    @Override
    protected long idleDrawWatts() {
        return STANDBY_POWER_WATTS;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
