package com.stardustindustry.stardustindustry.compat.top;

import java.util.function.Function;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.compat.hud.TankHudData;
import com.stardustindustry.stardustindustry.compat.hud.TankHudLines;
import com.stardustindustry.stardustindustry.machine.tank.TankBlockEntity;
import com.stardustindustry.stardustindustry.machine.tank.TankHudAccess;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The One Probe integration: the same tank tooltip as the other two plugins.
 *
 * <p>TOP has no annotation scan; a mod asks for its API by sending a function to
 * {@code "theoneprobe"} through {@code "getTheOneProbe"} inter-mod comms. The
 * message is only delivered when TOP is installed, so the send is guarded by a
 * loaded-mod check and this class is only touched from that guard — TOP stays a
 * soft dependency exactly like Jade and WTHIT.</p>
 *
 * <p>The provider is called for every block, so it filters to the three tank
 * parts before doing any work.</p>
 */
public final class TankTopPlugin {

    /** TOP's stable IMC method name for requesting the API. */
    private static final String IMC_GET_API = "getTheOneProbe";

    private TankTopPlugin() {}

    /**
     * Sends TOP the callback that installs the tank provider.
     *
     * <p>Called once from the mod's common setup, and only when TOP is present,
     * so neither the registration nor this class loads in a TOP-less game.</p>
     */
    public static void register() {
        net.neoforged.fml.InterModComms.sendTo(
                "theoneprobe", IMC_GET_API, () -> (Function<ITheOneProbe, Void>) probe -> {
                    probe.registerProvider(new TankProvider());
                    return null;
                });
    }

    /** Adds the tank's size, contents and capacity to TOP's overlay. */
    private static final class TankProvider implements IProbeInfoProvider {

        private static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(StardustIndustry.MODID, "tank_contents");

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player,
                                 Level level, BlockState state, IProbeHitData data) {
            TankBlockEntity tank = resolve(level, state, data);
            if (tank == null) {
                return;
            }
            for (Component line : TankHudLines.build(TankHudData.of(tank, data.getPos()))) {
                info.text(line);
            }
        }

        /**
         * Finds the tank from the probed block, falling back to the shared
         * lookup when the block is a frame or glass cell with no block entity.
         */
        private static TankBlockEntity resolve(Level level, BlockState state, IProbeHitData data) {
            if (level.getBlockEntity(data.getPos()) instanceof TankBlockEntity tank) {
                return tank;
            }
            Block block = state.getBlock();
            if (block != ModBlocks.TANK_FRAME.get()
                    && block != ModBlocks.TANK_SHELL.get()
                    && block != ModBlocks.TANK_GLASS.get()) {
                return null;
            }
            return TankHudAccess.findTank(level, data.getPos());
        }
    }
}
