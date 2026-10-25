package com.stardustindustry.mekanismex;

import java.util.ArrayList;
import java.util.List;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.demo.PowerDemoCompat;
import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableColor;
import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.CableMutationResult;
import com.stardustindustry.stardustindustry.energy.CableService;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import mekanism.common.tile.TileEntityEnergyCube;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardustIndustry.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismEnergyIntegrationGameTests {
    private static final String TEMPLATE = "empty";
    private static final ResourceLocation ENERGY_CUBE = ResourceLocation.fromNamespaceAndPath(
            "mekanism", "basic_energy_cube");

    private MekanismEnergyIntegrationGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void adjacentMekanismCubesTransferBothDirections(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 1, 5);
        ElectricalDeviceBlockEntity source = placeDevice(
                helper, sourcePos, ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.EAST);
        TerminalMountBlockEntity sourceRack = placeRack(
                helper, sourcePos.east(), Direction.WEST, 4);

        BlockPos firstConverterPos = new BlockPos(8, 1, 4);
        ElectricalDeviceBlockEntity firstConverter = placeDevice(
                helper, firstConverterPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.SOUTH);
        firstConverter.cycleConfiguration();
        TerminalMountBlockEntity firstRack = placeRack(
                helper, firstConverterPos.south(), Direction.NORTH, 4);
        TileEntityEnergyCube firstCube = placeEnergyCube(
                helper, firstConverterPos.north(), Direction.SOUTH);

        BlockPos secondConverterPos = new BlockPos(14, 1, 4);
        ElectricalDeviceBlockEntity secondConverter = placeDevice(
                helper, secondConverterPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.SOUTH);
        secondConverter.cycleConfiguration();
        TerminalMountBlockEntity secondRack = placeRack(
                helper, secondConverterPos.south(), Direction.NORTH, 4);
        TileEntityEnergyCube secondCube = placeEnergyCube(
                helper, secondConverterPos.north(), Direction.SOUTH);

        List<CableConnection> cables = new ArrayList<>();
        for (int conductor = 0; conductor < 4; conductor++) {
            cables.add(connect(helper, sourceRack, conductor, firstRack, conductor));
            cables.add(connect(helper, sourceRack, conductor, secondRack, conductor));
        }

        boolean[] reversePhase = {false};
        long[] peakReverseLoadWatts = {0L};
        helper.onEachTick(() -> {
            if (reversePhase[0]) {
                peakReverseLoadWatts[0] = Math.max(peakReverseLoadWatts[0],
                        secondConverter.telemetryState().deliveredPowerWatts());
            }
        });

        firstConverter.setSwitchClosed(true);
        source.setSwitchClosed(true);
        helper.runAfterDelay(12, () -> {
            long chargedFirstCube = firstCube.getEnergyContainer().getEnergy();
            helper.assertTrue(chargedFirstCube > 0L,
                    "native grid power did not reach the adjacent Mekanism cube");

            source.setSwitchClosed(false);
            firstConverter.cycleConfiguration();
            firstConverter.setSwitchClosed(true);
            secondConverter.setSwitchClosed(true);
            reversePhase[0] = true;

            helper.runAfterDelay(24, () -> {
                try {
                    helper.assertTrue(firstCube.getEnergyContainer().getEnergy() < chargedFirstCube,
                            "the converter did not pull power from its adjacent Mekanism cube");
                    helper.assertTrue(secondCube.getEnergyContainer().getEnergy() > 0L,
                            "FE did not cross the native cable network into the second cube");
                    helper.assertTrue(peakReverseLoadWatts[0] > 0L,
                            "the native-grid load never reported Mekanism-supplied power");
                    helper.succeed();
                } finally {
                    cables.forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
                }
            });
        });
    }

    private static ElectricalDeviceBlockEntity placeDevice(
            GameTestHelper helper, BlockPos pos, ElectricalDeviceBlock block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(ElectricalDeviceBlock.FACING, facing));
        ElectricalDeviceBlockEntity device = helper.getBlockEntity(pos);
        helper.assertTrue(device != null, "electrical device was not created at " + pos);
        return device;
    }

    private static TerminalMountBlockEntity placeRack(
            GameTestHelper helper, BlockPos pos, Direction facing, int terminals) {
        TerminalMountBlock block = ModBlocks.LV_TERMINAL_MOUNT.get();
        helper.setBlock(pos, block.defaultBlockState().setValue(TerminalMountBlock.FACING, facing));
        TerminalMountBlockEntity rack = helper.getBlockEntity(pos);
        helper.assertTrue(rack != null, "terminal rack was not created at " + pos);
        for (int cell = 0; cell < terminals; cell++) {
            rack.addTerminal(cell, TerminalStyle.LV);
        }
        return rack;
    }

    private static TileEntityEnergyCube placeEnergyCube(
            GameTestHelper helper, BlockPos pos, Direction connectingFace) {
        var block = BuiltInRegistries.BLOCK.getOptional(ENERGY_CUBE)
                .filter(candidate -> candidate != Blocks.AIR)
                .orElseThrow();
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, connectingFace);
        }
        helper.setBlock(pos, state);
        helper.assertTrue(PowerDemoCompat.configureFeStorage(
                        ENERGY_CUBE, helper.getLevel(), helper.absolutePos(pos), connectingFace),
                "MekanismEx did not configure the adjacent cube face");
        TileEntityEnergyCube cube = helper.getBlockEntity(pos);
        helper.assertTrue(cube != null, "Mekanism energy cube was not created at " + pos);
        return cube;
    }

    private static CableConnection connect(
            GameTestHelper helper, TerminalMountBlockEntity from, int fromCell,
            TerminalMountBlockEntity to, int toCell) {
        CableMutationResult result = CableService.connectDetailed(helper.getLevel(),
                new TerminalEndpoint(from.getBlockPos(), fromCell),
                new TerminalEndpoint(to.getBlockPos(), toCell),
                CableAmpacity.A150, CableInsulation.CLASS_1, CableColor.UNDYED);
        helper.assertTrue(result.changed(), "runtime cable placement failed: " + result.status());
        return result.cable();
    }
}
