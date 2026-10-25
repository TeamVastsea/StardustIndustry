package com.stardustindustry.stardustindustry.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.demo.PowerDemoBuilder;
import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableColor;
import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.CableMutationResult;
import com.stardustindustry.stardustindustry.energy.CableRunId;
import com.stardustindustry.stardustindustry.energy.CableService;
import com.stardustindustry.stardustindustry.energy.CableThermalModel;
import com.stardustindustry.stardustindustry.energy.ElectricalGridService;
import com.stardustindustry.stardustindustry.energy.ElectricalNetworkSavedData;
import com.stardustindustry.stardustindustry.energy.EnergyTier;
import com.stardustindustry.stardustindustry.energy.FaultKind;
import com.stardustindustry.stardustindustry.energy.IElectricalPort;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.machine.module.RecipeRunnerModule;
import com.stardustindustry.stardustindustry.machine.port.EnergyPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.storage.StorageMode;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;
import com.stardustindustry.stardustindustry.registry.ModBlocks;
import com.stardustindustry.stardustindustry.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world acceptance tests for the first playable electrical loop. */
@GameTestHolder(StardustIndustry.MODID)
@PrefixGameTestTemplate(false)
public final class ElectricalClosedLoopGameTests {
    private static final String TEMPLATE = "empty";

    private ElectricalClosedLoopGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void offhandMeterOwnsEmptyMainHandInteractions(GameTestHelper helper) {
        BlockPos machinePos = new BlockPos(1, 1, 1);
        BlockPos devicePos = new BlockPos(3, 1, 1);
        BlockPos portPos = new BlockPos(5, 1, 1);
        BlockPos rackPos = new BlockPos(7, 1, 1);
        BlockPos peerRackPos = new BlockPos(9, 1, 1);
        helper.setBlock(machinePos, ModBlocks.CRUSHER.get());
        helper.setBlock(devicePos, ModBlocks.FUEL_GENERATOR.get());
        helper.setBlock(portPos, ModBlocks.LV_ENERGY_PORT.get());
        TerminalMountBlockEntity rack = placeRack(
                helper, rackPos, Direction.NORTH, TerminalStyle.LV, 11);
        TerminalMountBlockEntity peerRack = placeRack(
                helper, peerRackPos, Direction.NORTH, TerminalStyle.LV, 1);
        CableConnection protectedCable = connect(
                helper, rack, 10, peerRack, 0, CableAmpacity.A150);

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND,
                new ItemStack(ModItems.ELECTRICIANS_METER.get()));
        player.setShiftKeyDown(true);

        for (BlockPos target : List.of(machinePos, devicePos, portPos, rackPos)) {
            BlockPos absolute = helper.absolutePos(target);
            BlockState state = helper.getLevel().getBlockState(absolute);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
            ItemInteractionResult mainHand = state.useItemOn(
                    player.getMainHandItem(), helper.getLevel(), player,
                    InteractionHand.MAIN_HAND, hit);
            helper.assertValueEqual(mainHand,
                    ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                    "empty main hand did not defer to offhand meter at " + target);
        }
        helper.assertTrue(rack.hasTerminal(10),
                "empty main hand removed a terminal before the offhand meter ran");
        helper.assertTrue(ElectricalNetworkSavedData.get(helper.getLevel())
                        .cable(protectedCable.runId()) != null,
                "empty main hand cut a cable before the offhand meter ran");

        player.setShiftKeyDown(false);
        for (BlockPos target : List.of(machinePos, devicePos, portPos, rackPos)) {
            BlockPos absolute = helper.absolutePos(target);
            BlockState state = helper.getLevel().getBlockState(absolute);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
            ItemInteractionResult offhand = state.useItemOn(
                    player.getOffhandItem(), helper.getLevel(), player,
                    InteractionHand.OFF_HAND, hit);
            helper.assertTrue(offhand.consumesAction(),
                    "offhand meter did not claim electrical target at " + target);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1_200)
    public static void demoBuilderIsIdempotent(GameTestHelper helper) {
        try {
            PowerDemoBuilder.BuildReport first = PowerDemoBuilder.build(helper.getLevel());
            StorageStationBlockEntity storage = requireDemoStorage(
                    helper, new BlockPos(66, 2, -9));
            helper.assertValueEqual(storage.energy().storedJoules(), 301_500L,
                    "demo storage reserve is not the intended roughly 45-second crusher supply");
            ElectricalDeviceBlockEntity feConverter = requireDemoDevice(
                    helper, new BlockPos(68, 1, 3));
            helper.assertValueEqual(feConverter.telemetryState().fault(), FaultKind.OPEN_SWITCH,
                    "demo FE branch must start open so it cannot drain the takeover reserve");
            PowerDemoBuilder.BuildReport second = PowerDemoBuilder.build(helper.getLevel());
            helper.assertTrue(first.devices() > 0 && first.cables() > 0,
                    "demo must contain powered equipment and real cable runs");
            helper.assertValueEqual(second, first,
                    "rebuilding the demo must produce the same inventory and topology");
            helper.succeed();
        } finally {
            PowerDemoBuilder.reset(helper.getLevel());
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void demoFaultLabIsIsolatedAndResettable(GameTestHelper helper) {
        BlockPos productionSentinel = PowerDemoBuilder.ORIGIN.offset(68, 1, 8);
        helper.getLevel().setBlock(productionSentinel,
                Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

        PowerDemoBuilder.BuildReport overload = PowerDemoBuilder.buildFaultLab(
                helper.getLevel(), PowerDemoBuilder.FaultScenario.OVERLOAD);
        helper.assertValueEqual(overload.devices(), 5,
                "overload lab did not build its source, breaker and three loads");
        helper.assertValueEqual(overload.cables(), 16,
                "overload lab did not build all four-conductor branches");
        assertProductionSentinel(helper, productionSentinel);

        helper.runAfterDelay(4, () -> {
            ElectricalDeviceBlockEntity breaker = requireDemoDevice(
                    helper, new BlockPos(4, 1, -13));
            helper.assertValueEqual(breaker.telemetryModeId(), "open",
                    "overload lab did not trip its LV breaker");

            PowerDemoBuilder.BuildReport phaseLoss = PowerDemoBuilder.buildFaultLab(
                    helper.getLevel(), PowerDemoBuilder.FaultScenario.PHASE_LOSS);
            helper.assertValueEqual(phaseLoss.devices(), 2,
                    "phase-loss lab did not build its source and load");
            helper.assertValueEqual(phaseLoss.cables(), 3,
                    "phase-loss lab did not omit exactly one conductor");
            assertProductionSentinel(helper, productionSentinel);

            helper.runAfterDelay(4, () -> {
                ElectricalDeviceBlockEntity phaseLossLoad = requireDemoDevice(
                        helper, new BlockPos(9, 1, -13));
                helper.assertValueEqual(phaseLossLoad.telemetryState().fault(),
                        FaultKind.MISSING_PHASE,
                        "phase-loss lab did not expose a live missing-phase fault");

                PowerDemoBuilder.BuildReport overvoltage = PowerDemoBuilder.buildFaultLab(
                        helper.getLevel(), PowerDemoBuilder.FaultScenario.OVERVOLTAGE);
                helper.assertValueEqual(overvoltage.devices(), 2,
                        "overvoltage lab did not build its source and load");
                helper.assertValueEqual(overvoltage.cables(), 3,
                        "overvoltage lab did not build its three HV conductors");
                assertProductionSentinel(helper, productionSentinel);

                helper.runAfterDelay(4, () -> {
                    ElectricalDeviceBlockEntity overvoltageLoad = requireDemoDevice(
                            helper, new BlockPos(8, 1, -13));
                    helper.assertValueEqual(overvoltageLoad.telemetryState().fault(),
                            FaultKind.OVERVOLTAGE,
                            "overvoltage lab did not expose an HV-on-MV fault");

                    PowerDemoBuilder.BuildReport reset = PowerDemoBuilder.buildFaultLab(
                            helper.getLevel(), PowerDemoBuilder.FaultScenario.RESET);
                    helper.assertValueEqual(reset.devices(), 2,
                            "fault reset did not rebuild the healthy source and load");
                    helper.assertValueEqual(reset.cables(), 4,
                            "fault reset did not restore all four LV conductors");
                    assertProductionSentinel(helper, productionSentinel);

                    helper.runAfterDelay(4, () -> {
                        try {
                            ElectricalDeviceBlockEntity healthyLoad = requireDemoDevice(
                                    helper, new BlockPos(9, 1, -13));
                            helper.assertValueEqual(healthyLoad.telemetryState().fault(),
                                    FaultKind.NONE,
                                    "fault reset left the isolated load faulted");
                            helper.assertTrue(healthyLoad.telemetryState().energized(),
                                    "fault reset did not re-energize the isolated load");
                            assertProductionSentinel(helper, productionSentinel);
                            helper.succeed();
                        } finally {
                            PowerDemoBuilder.reset(helper.getLevel());
                        }
                    });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unloadedFarCableCanBeRecoloredAndCut(GameTestHelper helper) {
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos far = near.offset(512, 0, 0);
        CableConnection original = CableConnection.of(
                CableAmpacity.A150,
                CableInsulation.CLASS_1,
                new TerminalEndpoint(near, 0),
                new TerminalEndpoint(far, 0));
        CableRunId id = CableRunId.of(original);
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(helper.getLevel());

        // Seed a record without loading the remote chunk; all player-visible
        // mutations below still go through the authoritative CableService.
        data.disconnect(id);
        helper.assertTrue(data.connect(original).changed(), "fixture cable was not created");
        helper.assertFalse(helper.getLevel().hasChunkAt(far), "far fixture chunk unexpectedly loaded");

        CableMutationResult recolored = CableService.recolorDetailed(
                helper.getLevel(), original, CableColor.ofDyeIndex(14));
        helper.assertTrue(recolored.changed(), "unloaded far end blocked cable recoloring");
        helper.assertFalse(helper.getLevel().hasChunkAt(far), "recolor loaded the far chunk");

        CableMutationResult removed = CableService.disconnectDetailed(helper.getLevel(), recolored.cable());
        helper.assertTrue(removed.changed(), "unloaded far end blocked cable removal");
        helper.assertTrue(data.hasTombstone(id), "cable removal did not create a migration tombstone");
        helper.assertValueEqual(data.migrateLegacy(original).status(), CableMutationResult.Status.TOMBSTONED,
                "an unloaded legacy endpoint revived a removed cable");
        helper.assertFalse(helper.getLevel().hasChunkAt(far), "disconnect loaded the far chunk");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void generatorStepsUpAcrossLongMvLineAndStepsDownThroughGrid(GameTestHelper helper) {
        ElectricalDeviceBlockEntity generator = placeDevice(
                helper, new BlockPos(2, 1, 2), ModBlocks.FUEL_GENERATOR.get(), Direction.EAST);
        ElectricalDeviceBlockEntity stepUp = placeDevice(
                helper, new BlockPos(6, 1, 2), ModBlocks.TRANSFORMER_LV_MV.get(), Direction.EAST);
        ElectricalDeviceBlockEntity stepDown = placeDevice(
                helper, new BlockPos(56, 1, 2), ModBlocks.TRANSFORMER_LV_MV.get(), Direction.EAST);
        ElectricalDeviceBlockEntity load = placeDevice(
                helper, new BlockPos(60, 1, 2), ModBlocks.LV_FE_CONVERTER.get(), Direction.WEST);

        generator.cycleConfiguration();
        generator.cycleConfiguration(); // Balanced LV three-phase four-wire output.
        stepDown.cycleConfiguration();
        load.cycleConfiguration(); // Native electricity -> FE, so the electrical side is a load.
        helper.assertTrue(generator.insertFuelItem(new ItemStack(Items.COAL)),
                "generator rejected valid fuel");

        TerminalMountBlockEntity generatorRack = placeRack(
                helper, new BlockPos(3, 1, 2), Direction.WEST, TerminalStyle.LV, 4);
        TerminalMountBlockEntity stepUpInput = placeRack(
                helper, new BlockPos(5, 1, 2), Direction.EAST, TerminalStyle.LV, 4);
        TerminalMountBlockEntity stepUpOutput = placeRack(
                helper, new BlockPos(7, 1, 2), Direction.WEST, TerminalStyle.MV, 3);
        TerminalMountBlockEntity stepDownInput = placeRack(
                helper, new BlockPos(55, 1, 2), Direction.EAST, TerminalStyle.MV, 3);
        TerminalMountBlockEntity stepDownOutput = placeRack(
                helper, new BlockPos(57, 1, 2), Direction.WEST, TerminalStyle.LV, 4);
        TerminalMountBlockEntity loadRack = placeRack(
                helper, new BlockPos(59, 1, 2), Direction.EAST, TerminalStyle.LV, 4);

        List<CableConnection> cables = new ArrayList<>();
        for (int conductor = 0; conductor < 4; conductor++) {
            cables.add(connect(helper, generatorRack, conductor, stepUpInput, conductor,
                    CableAmpacity.A150));
            cables.add(connect(helper, stepDownOutput, conductor, loadRack, conductor,
                    CableAmpacity.A150));
        }
        List<CableConnection> mvSpan = new ArrayList<>();
        for (int conductor = 0; conductor < 3; conductor++) {
            CableConnection cable = connect(helper, stepUpOutput, conductor,
                    stepDownInput, conductor, CableAmpacity.A500, CableInsulation.CLASS_2);
            mvSpan.add(cable);
            cables.add(cable);
        }
        helper.assertValueEqual(stepDownInput.getBlockPos().distManhattan(stepUpOutput.getBlockPos()),
                48, "MV transmission rack centres were not 48 blocks apart");
        load.setSwitchClosed(true);
        stepDown.setSwitchClosed(true);
        stepUp.setSwitchClosed(true);
        generator.setSwitchClosed(true);
        long initialLoadJoules = load.telemetryCapacityJoules() - 400L;
        helper.assertValueEqual(requirePort(load, Direction.WEST).receiveJoules(
                        initialLoadJoules, false), initialLoadJoules,
                "the FE load could not be precharged for a generator-sized sustained demand");
        IEnergyStorage feLoad = load.feStorage(Direction.EAST);
        helper.assertTrue(feLoad != null, "the converter did not expose its fixed FE output face");
        helper.onEachTick(() -> feLoad.extractEnergy(200, false));

        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(load.telemetryStoredJoules() > initialLoadJoules,
                        "the world-ticked transformer chain delivered no energy to the LV load");
                helper.assertTrue(load.telemetryState().supplyTier() == EnergyTier.LV,
                        "the downstream load did not receive stepped-down LV power: "
                                + load.telemetryState());
                helper.assertValueEqual(load.telemetryState().fault(), FaultKind.NONE,
                        "the valid stepped-down load circuit reported a fault");
                helper.assertTrue(load.telemetryState().deliveredPowerWatts() > 0L,
                        "the grid solver reported no delivered LV load power");
                for (CableConnection cable : mvSpan) {
                    var energized = ElectricalGridService.energizedCable(helper.getLevel(), cable.runId());
                    helper.assertTrue(energized != null,
                            "the 48-block MV run was not present in live grid telemetry");
                    helper.assertValueEqual(energized.volts(), EnergyTier.MV.volts(),
                            "the long transmission run was not energized at MV");
                    helper.assertTrue(energized.currentAmps() > 0.0,
                            "the long MV conductor carried no current");
                }
                helper.succeed();
            } finally {
                cables.forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void storageModePersistsAcrossReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState state = ModBlocks.LV_STORAGE_STATION_CONTROLLER.get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH);
        StorageStationBlockEntity original = new StorageStationBlockEntity(pos, state);
        original.setLevel(helper.getLevel());
        helper.assertTrue(original.setStorageMode(StorageMode.DISCHARGE_ONLY),
                "storage mode did not change");

        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        StorageStationBlockEntity reloaded = new StorageStationBlockEntity(pos, state);
        reloaded.setLevel(helper.getLevel());
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertValueEqual(reloaded.storageMode(), StorageMode.DISCHARGE_ONLY,
                "storage policy was lost during reload");
        helper.assertFalse(reloaded.storageMode().allowsCharge(),
                "discharge-only storage still accepted charging");
        helper.assertTrue(reloaded.storageMode().allowsDischarge(),
                "discharge-only storage refused discharge");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void storageChargesTakesOverAndStopsLoadWhenExhausted(GameTestHelper helper) {
        ElectricalDeviceBlockEntity source = placeDevice(
                helper, new BlockPos(2, 1, 3), ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.EAST);
        TerminalMountBlockEntity sourceRack = placeRack(
                helper, new BlockPos(3, 1, 3), Direction.WEST, TerminalStyle.LV, 4);

        StorageFixture storage = buildLvStorageStation(helper, new BlockPos(8, 1, 2));
        // A sustained FE consumer exercises a real native-grid load without relying on an
        // in-line transformer's former persistent buffer.
        ElectricalDeviceBlockEntity load = placeDevice(
                helper, new BlockPos(8, 1, 8), ModBlocks.LV_FE_CONVERTER.get(), Direction.SOUTH);
        load.cycleConfiguration(); // Native electricity -> FE.
        TerminalMountBlockEntity loadRack = placeRack(
                helper, new BlockPos(8, 1, 9), Direction.NORTH, TerminalStyle.LV, 4);

        List<CableConnection> cables = new ArrayList<>();
        for (int conductor = 0; conductor < 4; conductor++) {
            cables.add(connect(helper, sourceRack, conductor, storage.rack(), conductor,
                    CableAmpacity.A500));
            cables.add(connect(helper, sourceRack, conductor, loadRack, conductor,
                    CableAmpacity.A500));
        }
        helper.assertValueEqual(storage.station().storageMode(), StorageMode.AUTO,
                "new storage station did not start in automatic mode");

        load.setSwitchClosed(true);
        source.setSwitchClosed(true);
        IEnergyStorage feLoad = load.feStorage(Direction.NORTH);
        helper.assertTrue(feLoad != null, "the storage test load did not expose its fixed FE face");
        long converterCapacity = load.telemetryCapacityJoules();
        helper.assertValueEqual(requirePort(load, Direction.SOUTH).receiveJoules(
                        converterCapacity, false), converterCapacity,
                "the FE load could not be precharged for a sub-nameplate sustained demand");
        long[] extractedFe = {0L};
        helper.onEachTick(() -> extractedFe[0] += feLoad.extractEnergy(2_000, false));
        helper.runAfterDelay(4, () -> {
            long chargedJoules = storage.station().energy().storedJoules();
            long extractedBeforeTakeover = extractedFe[0];
            helper.assertTrue(chargedJoules > 0L,
                    "the live source did not charge storage through the cable network");
            helper.assertTrue(storage.port().gridState().netPowerWatts() > 0L,
                    "storage telemetry did not report positive charging power");
            helper.assertTrue(extractedBeforeTakeover > 0L,
                    "the primary source did not serve the FE load before charging storage");

            source.setSwitchClosed(false);
            helper.runAfterDelay(2, () -> {
                long remainingJoules = storage.station().energy().storedJoules();
                helper.assertTrue(remainingJoules > 0L && remainingJoules < chargedJoules,
                        "storage did not discharge after the dedicated source opened");
                helper.assertTrue(storage.port().gridState().netPowerWatts() < 0L,
                        "storage telemetry did not report takeover as discharge");
                helper.assertTrue(extractedFe[0] > extractedBeforeTakeover,
                        "the storage island did not keep the FE load supplied");
                helper.assertValueEqual(load.telemetryState().fault(), FaultKind.NONE,
                        "the load faulted while charged storage was supplying it");
                helper.assertTrue(load.telemetryState().deliveredPowerWatts() > 0L,
                        "the load received no power during storage takeover");

                helper.runAfterDelay(12, () -> {
                    helper.assertValueEqual(storage.station().energy().storedJoules(), 0L,
                            "storage did not run down under the sustained real load");
                    helper.assertValueEqual(load.telemetryState().fault(), FaultKind.UNDERVOLTAGE,
                            "the load did not report undervoltage after storage exhaustion");
                    helper.assertValueEqual(load.telemetryState().deliveredPowerWatts(), 0L,
                            "the exhausted storage island still reported delivered load power");

                    // The FE converter intentionally owns a two-second buffer. Let that real
                    // buffer drain, then prove the external consumer has stopped as well.
                    helper.runAfterDelay(70, () -> {
                        long stoppedAtFe = extractedFe[0];
                        helper.runAfterDelay(3, () -> {
                            try {
                                helper.assertValueEqual(extractedFe[0], stoppedAtFe,
                                        "the FE load kept running after storage and converter buffers emptied");
                                helper.succeed();
                            } finally {
                                cables.forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
                            }
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void breakerTripsOnActualPhaseCurrentAndResets(GameTestHelper helper) {
        ElectricalDeviceBlockEntity source = placeDevice(
                helper, new BlockPos(2, 1, 2), ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.EAST);
        TerminalMountBlockEntity sourceRack = placeRack(
                helper, new BlockPos(3, 1, 2), Direction.WEST, TerminalStyle.LV, 4);

        ElectricalDeviceBlockEntity breaker = placeDevice(
                helper, new BlockPos(6, 1, 2), ModBlocks.LV_BREAKER.get(), Direction.EAST);
        TerminalMountBlockEntity breakerInput = placeRack(
                helper, new BlockPos(5, 1, 2), Direction.EAST, TerminalStyle.LV, 4);
        TerminalMountBlockEntity breakerOutput = placeRack(
                helper, new BlockPos(7, 1, 2), Direction.WEST, TerminalStyle.LV, 4);

        ElectricalDeviceBlockEntity firstLoad = placeDevice(
                helper, new BlockPos(11, 1, 1), ModBlocks.LV_FE_CONVERTER.get(), Direction.WEST);
        firstLoad.cycleConfiguration();
        TerminalMountBlockEntity firstLoadRack = placeRack(
                helper, new BlockPos(10, 1, 1), Direction.EAST, TerminalStyle.LV, 4);

        ElectricalDeviceBlockEntity secondLoad = placeDevice(
                helper, new BlockPos(11, 1, 4), ModBlocks.LV_FE_CONVERTER.get(), Direction.WEST);
        secondLoad.cycleConfiguration();
        TerminalMountBlockEntity secondLoadRack = placeRack(
                helper, new BlockPos(10, 1, 4), Direction.EAST, TerminalStyle.LV, 4);

        List<CableConnection> cables = new ArrayList<>();
        for (int conductor = 0; conductor < 4; conductor++) {
            cables.add(connect(helper, sourceRack, conductor, breakerInput, conductor,
                    CableAmpacity.A500));
            cables.add(connect(helper, breakerOutput, conductor, firstLoadRack, conductor,
                    CableAmpacity.A500));
            cables.add(connect(helper, breakerOutput, conductor, secondLoadRack, conductor,
                    CableAmpacity.A500));
        }

        firstLoad.setSwitchClosed(true);
        secondLoad.setSwitchClosed(true);
        breaker.setSwitchClosed(true);
        source.setSwitchClosed(true);

        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(breaker.telemetryModeId(), "open",
                    "real 200 A downstream load did not trip the 150 A breaker");

            secondLoad.setSwitchClosed(false);
            breaker.setSwitchClosed(true);
            helper.runAfterDelay(3, () -> {
                helper.assertValueEqual(breaker.telemetryModeId(), "closed",
                        "breaker could not be reset after downstream current fell below its rating");
                cables.forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void crusherResumesReservedRecipeAndWaitsForOutputSpace(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(8, 1, 4);
        CrusherBlockEntity crusher = buildCrusher(helper, controllerPos,
                Set.of(new BlockPos(-1, 0, 0)), true);
        ItemStack remainder = crusher.inventory().handler().insertItem(
                0, new ItemStack(Items.RAW_IRON), false);
        helper.assertTrue(remainder.isEmpty(), "crusher input rejected raw iron");

        for (int tick = 0; tick < 20; tick++) {
            tickPoweredCrusher(crusher);
        }
        RecipeRunnerModule beforeReload = crusher.modules().get(RecipeRunnerModule.class);
        helper.assertTrue(beforeReload != null && beforeReload.isRunning(),
                "crusher did not reserve and start its recipe");
        helper.assertTrue(beforeReload.progressFraction() > 0.0f,
                "crusher made no progress before reload");
        float progressBeforeReload = beforeReload.progressFraction();
        helper.assertTrue(crusher.inventory().getStack(0).isEmpty(),
                "reserved recipe input remained externally visible");

        BlockPos absoluteController = crusher.getBlockPos();
        BlockState controllerState = crusher.getBlockState();
        CompoundTag saved = crusher.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.getLevel().removeBlockEntity(absoluteController);
        CrusherBlockEntity reloaded = new CrusherBlockEntity(absoluteController, controllerState);
        reloaded.setLevel(helper.getLevel());
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(reloaded);

        RecipeRunnerModule afterReload = reloaded.modules().get(RecipeRunnerModule.class);
        helper.assertTrue(afterReload != null && afterReload.isRunning(),
                "reload discarded the active recipe id or reserved input");
        helper.assertValueEqual(afterReload.progressFraction(), progressBeforeReload,
                "reload changed recipe progress");

        reloaded.inventory().handler().setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, 64));
        reloaded.inventory().handler().setStackInSlot(2, new ItemStack(Blocks.COBBLESTONE, 64));
        for (int tick = 0; tick < 240; tick++) {
            tickPoweredCrusher(reloaded);
        }
        helper.assertFalse(afterReload.isRunning(), "crusher recipe did not complete after reload");
        helper.assertTrue(reloaded.inventory().getStack(1).is(Blocks.COBBLESTONE.asItem())
                        && reloaded.inventory().getStack(2).is(Blocks.COBBLESTONE.asItem()),
                "blocked outputs were overwritten instead of retaining pending products");

        reloaded.inventory().handler().setStackInSlot(1, ItemStack.EMPTY);
        tickPoweredCrusher(reloaded);
        int produced = countItem(reloaded, ModItems.CRUSHED_IRON.get());
        helper.assertTrue(produced >= 2 && produced <= 3,
                "pending primary output was lost or duplicated after space became available: " + produced);
        for (int tick = 0; tick < 5; tick++) {
            tickPoweredCrusher(reloaded);
        }
        helper.assertValueEqual(countItem(reloaded, ModItems.CRUSHED_IRON.get()), produced,
                "completed recipe emitted its pending output more than once");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void physicalEnergyPortsAggregateIntoOnePoweredAccount(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(10, 1, 4);
        BlockPos leftOffset = new BlockPos(-1, 0, 0);
        BlockPos rightOffset = new BlockPos(1, 0, 0);
        CrusherBlockEntity crusher = buildCrusher(helper, controllerPos,
                Set.of(leftOffset, rightOffset), false);
        EnergyPortBlockEntity left = helper.getBlockEntity(controllerPos.offset(leftOffset));
        EnergyPortBlockEntity right = helper.getBlockEntity(controllerPos.offset(rightOffset));
        helper.assertTrue(left != null && right != null, "crusher energy ports were not created");
        helper.assertValueEqual(left.energyAccount(), right.energyAccount(),
                "physical ports did not share one stable energy account");
        helper.assertValueEqual(left.portGroup(), right.portGroup(),
                "physical ports were not grouped into one logical port");
        helper.assertValueEqual(left.controllerPos(), crusher.getBlockPos(),
                "left energy port was not bound to the crusher");
        helper.assertValueEqual(right.controllerPos(), crusher.getBlockPos(),
                "right energy port was not bound to the crusher");

        TerminalMountBlockEntity leftRack = placeRack(helper, controllerPos.offset(-2, 0, 0),
                Direction.EAST, TerminalStyle.LV, 1);
        TerminalMountBlockEntity rightRack = placeRack(helper, controllerPos.offset(2, 0, 0),
                Direction.WEST, TerminalStyle.LV, 1);
        ElectricalDeviceBlockEntity source = placeDevice(
                helper, controllerPos.offset(0, 0, 6), ModBlocks.CREATIVE_POWER_SOURCE.get());
        source.cycleConfiguration(); // LV single phase, split across the two physical machine ports.
        TerminalMountBlockEntity sourceRack = placeRack(helper, controllerPos.offset(0, 0, 5),
                Direction.SOUTH, TerminalStyle.LV, 2);

        List<CableConnection> cables = List.of(
                connect(helper, sourceRack, 0, leftRack, 0, CableAmpacity.A150),
                connect(helper, sourceRack, 1, rightRack, 0, CableAmpacity.A150));
        source.setSwitchClosed(true);

        helper.runAfterDelay(4, () -> {
            helper.assertTrue(crusher.energy().storedJoules() > 0L,
                    "the two physical conductors did not power their logical machine port");
            helper.assertValueEqual(left.gridState().deliveredPowerWatts(),
                    right.gridState().deliveredPowerWatts(),
                    "logical-port telemetry was not replicated to every physical port");
            helper.assertTrue(left.gridState().deliveredPowerWatts() > 0L
                            && left.gridState().deliveredPowerWatts() <= crusher.energy().maxPowerWatts(),
                    "physical ports multiplied the machine's total power rating");
            cables.forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void overloadedLiveCableBurnsAndLeavesTombstone(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(4, 1, 2);
        ElectricalDeviceBlockEntity source = placeDevice(
                helper, sourcePos, ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.SOUTH);
        TerminalMountBlockEntity sourceRack = placeRack(helper, sourcePos.south(),
                Direction.NORTH, TerminalStyle.LV, 4);

        BlockPos loadPos = new BlockPos(4, 1, 9);
        ElectricalDeviceBlockEntity load = placeDevice(
                helper, loadPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.NORTH);
        load.cycleConfiguration(); // Native electricity -> FE, so this side is a real load.
        TerminalMountBlockEntity loadRack = placeRack(helper, loadPos.north(),
                Direction.SOUTH, TerminalStyle.LV, 4);

        List<CableConnection> cables = new ArrayList<>();
        for (int conductor = 0; conductor < 4; conductor++) {
            cables.add(connect(helper, sourceRack, conductor, loadRack, conductor, CableAmpacity.A50));
        }
        CableConnection target = cables.get(0);
        ElectricalNetworkSavedData data = ElectricalNetworkSavedData.get(helper.getLevel());
        helper.assertTrue(data.setThermalState(target.runId(),
                        new CableThermalModel.State(1.0,
                                CableThermalModel.DESTRUCTIVE_CONFIRMATION_TICKS - 1),
                        helper.getLevel().getGameTime()),
                "failed to seed the live cable at its final confirmation tick");
        load.setSwitchClosed(true);
        source.setSwitchClosed(true);

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(data.cable(target.runId()) == null,
                    "sustained 100 A load did not burn the 45 A effective cable");
            helper.assertTrue(data.hasTombstone(target.runId()),
                    "burned cable did not leave a migration tombstone");
            cables.stream().filter(cable -> !cable.runId().equals(target.runId()))
                    .forEach(cable -> CableService.disconnectDetailed(helper.getLevel(), cable));
            helper.succeed();
        });
    }

    private static ElectricalDeviceBlockEntity placeDevice(
            GameTestHelper helper, BlockPos pos, ElectricalDeviceBlock block) {
        return placeDevice(helper, pos, block, Direction.NORTH);
    }

    private static ElectricalDeviceBlockEntity placeDevice(
            GameTestHelper helper, BlockPos pos, ElectricalDeviceBlock block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(ElectricalDeviceBlock.FACING, facing);
        helper.setBlock(pos, state);
        Object blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof ElectricalDeviceBlockEntity,
                "electrical device block entity was not created at " + pos);
        return (ElectricalDeviceBlockEntity) blockEntity;
    }

    private static IElectricalPort requirePort(ElectricalDeviceBlockEntity device, Direction side) {
        IElectricalPort port = device.portFor(side);
        if (port == null) {
            throw new IllegalStateException("Missing electrical port on " + side);
        }
        return port;
    }

    private static ElectricalDeviceBlockEntity requireDemoDevice(
            GameTestHelper helper, BlockPos offset) {
        BlockPos pos = PowerDemoBuilder.ORIGIN.offset(offset);
        Object blockEntity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof ElectricalDeviceBlockEntity,
                "demo electrical device was not created at " + pos);
        return (ElectricalDeviceBlockEntity) blockEntity;
    }

    private static StorageStationBlockEntity requireDemoStorage(
            GameTestHelper helper, BlockPos offset) {
        BlockPos pos = PowerDemoBuilder.ORIGIN.offset(offset);
        Object blockEntity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof StorageStationBlockEntity,
                "demo storage station was not created at " + pos);
        return (StorageStationBlockEntity) blockEntity;
    }

    private static void assertProductionSentinel(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.DIAMOND_BLOCK),
                "fault-lab rebuild modified the main production area");
    }

    private static CrusherBlockEntity buildCrusher(GameTestHelper helper, BlockPos controllerPos,
                                                    Set<BlockPos> energyPortOffsets,
                                                    boolean includeItemPort) {
        helper.setBlock(controllerPos, ModBlocks.CRUSHER.get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                BlockPos offset = new BlockPos(x, 0, z);
                if (energyPortOffsets.contains(offset)) {
                    helper.setBlock(controllerPos.offset(offset), ModBlocks.LV_ENERGY_PORT.get());
                } else if (includeItemPort && offset.equals(new BlockPos(1, 0, 0))) {
                    helper.setBlock(controllerPos.offset(offset), ModBlocks.LV_ITEM_PORT.get());
                } else {
                    helper.setBlock(controllerPos.offset(offset), ModBlocks.LV_BASE.get());
                }
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (x == 0 && z == 1) {
                        continue;
                    }
                    helper.setBlock(controllerPos.offset(x, y, z), ModBlocks.STEEL_CASING.get());
                }
            }
        }
        CrusherBlockEntity crusher = helper.getBlockEntity(controllerPos);
        helper.assertTrue(crusher != null, "crusher controller block entity was not created");
        helper.assertTrue(crusher.install(), "valid crusher structure did not install");
        return crusher;
    }

    private static StorageFixture buildLvStorageStation(GameTestHelper helper, BlockPos min) {
        BlockPos max = min.offset(2, 2, 2);
        BlockPos controllerPos = min.offset(0, 1, 1);
        BlockPos energyPortPos = min.offset(2, 1, 1);
        BlockPos cellPos = min.offset(1, 1, 1);

        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = mutable.immutable();
            int edges = edgesTouched(pos, min, max);
            net.minecraft.world.level.block.Block block;
            if (pos.equals(controllerPos)) {
                block = ModBlocks.LV_STORAGE_STATION_CONTROLLER.get();
            } else if (pos.equals(energyPortPos)) {
                block = ModBlocks.LV_ENERGY_PORT.get();
            } else if (pos.equals(cellPos)) {
                block = ModBlocks.LV_STORAGE_CELL.get();
            } else if (edges >= 2) {
                block = ModBlocks.STORAGE_STATION_FRAME.get();
            } else if (edges == 1) {
                block = pos.getY() == min.getY()
                        ? ModBlocks.STORAGE_STATION_CASING.get()
                        : ModBlocks.INDUSTRIAL_GLASS.get();
            } else {
                block = Blocks.AIR;
            }
            BlockState state = block.defaultBlockState();
            if (pos.equals(controllerPos)) {
                state = state.setValue(MachineBlock.FACING, Direction.WEST);
            }
            helper.setBlock(pos, state);
        }

        EnergyPortBlockEntity port = helper.getBlockEntity(energyPortPos);
        helper.assertTrue(port != null, "storage energy port block entity was not created");
        port.cycleWiring();
        port.cycleWiring();

        StorageStationBlockEntity station = helper.getBlockEntity(controllerPos);
        helper.assertTrue(station != null, "storage controller block entity was not created");
        for (var chunk : station.provider().footprint(station.getBlockPos(), station.facing())) {
            helper.getLevel().getChunk(chunk.x, chunk.z);
        }
        helper.assertTrue(station.revalidate(helper.getLevel(), station.provider()),
                "valid storage station structure did not form");
        helper.assertValueEqual(port.controllerPos(), station.getBlockPos(),
                "formed storage station did not bind its energy port");

        TerminalMountBlockEntity rack = placeRack(helper, energyPortPos.east(),
                Direction.WEST, TerminalStyle.LV, 4);
        return new StorageFixture(station, port, rack);
    }

    private static int edgesTouched(BlockPos pos, BlockPos min, BlockPos max) {
        int edges = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) edges++;
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) edges++;
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) edges++;
        return edges;
    }

    private static void tickPoweredCrusher(CrusherBlockEntity crusher) {
        crusher.energy().receiveJoules(crusher.energy().capacityJoules(), false);
        crusher.tick(crusher.getLevel(), crusher.getBlockPos(), crusher.getBlockState());
    }

    private static int countItem(CrusherBlockEntity crusher, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < crusher.inventory().size(); slot++) {
            ItemStack stack = crusher.inventory().getStack(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static TerminalMountBlockEntity placeRack(GameTestHelper helper, BlockPos pos,
                                                       Direction facing, TerminalStyle style,
                                                       int terminals) {
        TerminalMountBlock block = switch (style.tier()) {
            case LV -> ModBlocks.LV_TERMINAL_MOUNT.get();
            case MV -> ModBlocks.MV_TERMINAL_MOUNT.get();
            case HV -> ModBlocks.HV_TERMINAL_MOUNT.get();
            case EHV -> ModBlocks.EHV_TERMINAL_MOUNT.get();
        };
        helper.setBlock(pos, block.defaultBlockState().setValue(TerminalMountBlock.FACING, facing));
        TerminalMountBlockEntity rack = helper.getBlockEntity(pos);
        helper.assertTrue(rack != null, "terminal rack block entity was not created at " + pos);
        for (int cell = 0; cell < terminals; cell++) {
            rack.addTerminal(cell, style);
        }
        helper.assertValueEqual(rack.terminalCount(), terminals,
                "terminal rack rejected required conductors");
        return rack;
    }

    private static CableConnection connect(GameTestHelper helper,
                                           TerminalMountBlockEntity from, int fromCell,
                                           TerminalMountBlockEntity to, int toCell,
                                           CableAmpacity ampacity) {
        return connect(helper, from, fromCell, to, toCell, ampacity, CableInsulation.CLASS_1);
    }

    private static CableConnection connect(GameTestHelper helper,
                                           TerminalMountBlockEntity from, int fromCell,
                                           TerminalMountBlockEntity to, int toCell,
                                           CableAmpacity ampacity, CableInsulation insulation) {
        CableMutationResult result = CableService.connectDetailed(helper.getLevel(),
                new TerminalEndpoint(from.getBlockPos(), fromCell),
                new TerminalEndpoint(to.getBlockPos(), toCell),
                ampacity, insulation, CableColor.UNDYED);
        helper.assertTrue(result.changed(), "runtime cable placement failed: " + result.status());
        return result.cable();
    }

    private record StorageFixture(StorageStationBlockEntity station,
                                  EnergyPortBlockEntity port,
                                  TerminalMountBlockEntity rack) {}
}
