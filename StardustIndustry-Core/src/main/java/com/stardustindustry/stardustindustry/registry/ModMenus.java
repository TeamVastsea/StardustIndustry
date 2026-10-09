package com.stardustindustry.stardustindustry.registry;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.machine.MachineParamsMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu (container) types for this mod's screens.
 *
 * <p>Kept in one register, like every other registry, so the mod class has a
 * single call per registry kind. The parameter menu uses
 * {@link IMenuTypeExtension} because it opens with extra data (the parameter
 * snapshot) rather than a slot layout.</p>
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, StardustIndustry.MODID);

    /** The machine parameter screen, opened with a {@code MachineParamsData} snapshot. */
    public static final DeferredHolder<MenuType<?>, MenuType<MachineParamsMenu>> MACHINE_PARAMS =
            MENUS.register("machine_params",
                    () -> IMenuTypeExtension.create((containerId, inventory, data) ->
                            new MachineParamsMenu(containerId, inventory, data)));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
