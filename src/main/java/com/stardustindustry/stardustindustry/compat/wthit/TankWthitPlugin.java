package com.stardustindustry.stardustindustry.compat.wthit;

import com.stardustindustry.stardustindustry.compat.hud.TankHudData;
import com.stardustindustry.stardustindustry.compat.hud.TankHudLines;
import com.stardustindustry.stardustindustry.machine.tank.AbstractTankBlockEntity;
import com.stardustindustry.stardustindustry.machine.tank.TankHudAccess;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;

/**
 * WTHIT integration: the same tank tooltip as the Jade plugin, on WTHIT's API.
 *
 * <p>WTHIT discovers plugins through the {@code waila_plugins.json} file shipped
 * alongside this class, so this class is only loaded when WTHIT is present. It
 * implements the client-side plugin interface because the tank tooltip is pure
 * display — nothing here needs the server-side data channel WTHIT offers.</p>
 *
 * <p>The provider is attached to {@link Block} once and filters down to the
 * three tank parts itself, since WTHIT registers against a class and our tank's
 * frame, shell and glass share no narrower common supertype.</p>
 */
public class TankWthitPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(new TankComponentProvider(), Block.class);
    }

    /** Appends the tank's size, contents and capacity to WTHIT's tooltip body. */
    private static final class TankComponentProvider implements IBlockComponentProvider {

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            AbstractTankBlockEntity tank = resolve(accessor);
            if (tank == null) {
                return;
            }
            for (Component line : TankHudLines.build(TankHudData.of(tank, accessor.getPosition()))) {
                tooltip.addLine(line);
            }
        }

        /**
         * Finds the tank from the clicked block. The frame and glass carry no
         * block entity, so the shared lookup walks the tank to its anchor.
         */
        private static AbstractTankBlockEntity resolve(IBlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof AbstractTankBlockEntity tank) {
                return tank;
            }
            Block block = accessor.getBlock();
            // A fluid or gas port belongs to the machine it is bound to, so it
            // shows the same readout; item and energy ports are filtered out. The
            // provider is registered on all ports, so the role check happens here.
            if (block instanceof com.stardustindustry.stardustindustry.machine.MachinePortBlock port) {
                if (port.role() != com.stardustindustry.stardustindustry.multiblock.PartRole.PORT_FLUID
                        && port.role() != com.stardustindustry.stardustindustry.multiblock.PartRole.PORT_GAS) {
                    return null;
                }
            } else if (block != ModBlocks.TANK_FRAME.get()
                    && block != ModBlocks.FLUID_TANK_SHELL.get()
                    && block != ModBlocks.GAS_TANK_SHELL.get()
                    && block != ModBlocks.INDUSTRIAL_GLASS.get()) {
                return null;
            }
            return TankHudAccess.findTank(accessor.getLevel(), accessor.getPosition());
        }
    }
}
