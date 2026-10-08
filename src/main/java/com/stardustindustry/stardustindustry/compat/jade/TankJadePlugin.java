package com.stardustindustry.stardustindustry.compat.jade;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.compat.hud.TankHudData;
import com.stardustindustry.stardustindustry.compat.hud.TankHudLines;
import com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity;
import com.stardustindustry.stardustindustry.machine.tank.TankHudAccess;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration: shows a tank's contents on any of its blocks.
 *
 * <p>The plugin is discovered by Jade's own annotation scan, so it is only ever
 * class-loaded when Jade is installed; the mod declares no hard dependency on
 * Jade and works without it. One provider is registered per tank block class,
 * because the frame and glass have no block entity and Jade's per-class
 * registration is the only hook they get — the provider then resolves the tank
 * through {@link TankHudAccess}, which handles all three cases identically.</p>
 */
@WailaPlugin(StardustIndustry.MODID)
public class TankJadePlugin implements IWailaPlugin {

    /** Jade's per-provider id, used for its config screen and for de-duplication. */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(StardustIndustry.MODID, "tank_contents");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        TankComponentProvider provider = new TankComponentProvider();

        // Register on Block rather than on each tank class. Jade resolves a
        // component provider by the exact block class it was registered with,
        // and the fluid port is a MachinePortBlock shared with the item and
        // energy ports — so a per-class registration either misses a part or
        // pulls in every port. Hooking Block once and filtering inside keeps the
        // port, frame, shell and glass on one identical path, which is also how
        // the WTHIT plugin is attached.
        registration.registerBlockComponent(provider, Block.class);
    }

    /** Appends the tank's size, contents and capacity to Jade's tooltip. */
    private static final class TankComponentProvider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            TankBlockEntity tank = resolve(accessor);
            if (tank == null) {
                return;
            }
            for (Component line : TankHudLines.build(TankHudData.of(tank, accessor.getPosition()))) {
                tooltip.add(line);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        /**
         * Finds the tank from the clicked block, using the shared lookup so a
         * frame or glass cell (which has no block entity) is handled the same
         * way the installation tool handles it.
         */
        private static TankBlockEntity resolve(BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof TankBlockEntity tank) {
                return tank;
            }
            Block block = accessor.getBlockState().getBlock();
            // A fluid port is part of whatever machine it is bound to, so the
            // shared lookup is asked for the same answer the tool gets. The
            // provider is registered on all ports, so filter the roles here.
            if (block instanceof com.stardustindustry.stardustindustry.machine.MachinePortBlock port
                    && port.role() != com.stardustindustry.stardustindustry.multiblock.PartRole.PORT_FLUID) {
                return null;
            }
            if (block != ModBlocks.TANK_FRAME.get()
                    && block != ModBlocks.TANK_SHELL.get()
                    && block != ModBlocks.TANK_GLASS.get()
                    && !(block instanceof com.stardustindustry.stardustindustry.machine.MachinePortBlock)) {
                return null;
            }
            return TankHudAccess.findTank(accessor.getLevel(), accessor.getPosition());
        }
    }
}
