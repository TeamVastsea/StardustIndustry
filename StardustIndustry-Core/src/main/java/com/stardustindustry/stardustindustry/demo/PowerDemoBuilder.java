package com.stardustindustry.stardustindustry.demo;

import java.util.ArrayList;
import java.util.List;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.energy.CableAmpacity;
import com.stardustindustry.stardustindustry.energy.CableColor;
import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.energy.CableInsulation;
import com.stardustindustry.stardustindustry.energy.CableMutationResult;
import com.stardustindustry.stardustindustry.energy.CableService;
import com.stardustindustry.stardustindustry.energy.ElectricalNetworkSavedData;
import com.stardustindustry.stardustindustry.energy.TerminalEndpoint;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlock;
import com.stardustindustry.stardustindustry.energy.device.ElectricalDeviceBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineBlock;
import com.stardustindustry.stardustindustry.machine.crusher.CrusherBlockEntity;
import com.stardustindustry.stardustindustry.machine.port.EnergyPortBlockEntity;
import com.stardustindustry.stardustindustry.machine.storage.StorageStationBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalStyle;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Builds the repeatable in-world demonstration using the same APIs as normal play. */
public final class PowerDemoBuilder {

    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final BlockPos DEMO_MIN = ORIGIN.offset(-3, 0, -18);
    private static final BlockPos DEMO_MAX = ORIGIN.offset(74, 7, 31);
    private static final BlockPos FAULT_MIN = ORIGIN.offset(-2, 0, -17);
    private static final BlockPos FAULT_MAX = ORIGIN.offset(16, 6, -8);
    private static final int MACHINE_Y = 65;
    /**
     * 6.7 kW crusher load for about 42.5 seconds, before line losses.
     *
     * <p>The quarter-recipe offset is deliberate: the reserve must run dry while a recipe is
     * active, rather than occasionally landing in the one-tick gap between consecutive recipes.</p>
     */
    private static final long DEMO_STORAGE_INITIAL_JOULES = 284_750L;

    private PowerDemoBuilder() {}

    public enum FaultScenario {
        OVERLOAD("overload"),
        PHASE_LOSS("phase_loss"),
        OVERVOLTAGE("overvoltage"),
        RESET("reset");

        private final String id;

        FaultScenario(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record BuildReport(int devices, int cables) {}

    /** Clears and rebuilds the production line, storage branch, FE branch and catalogue. */
    public static BuildReport build(ServerLevel level) {
        reset(level);
        loadDemoChunks(level);
        buildFoundation(level, DEMO_MIN, DEMO_MAX);

        MutableReport report = new MutableReport();
        buildProductionLine(level, report);
        buildCableCatalogue(level, report);
        buildDeviceCatalogue(level, report);
        report.add(buildFaultLabInternal(level, FaultScenario.RESET));
        return report.freeze();
    }

    /** Removes all demo cables first, then blocks and dropped inventory. */
    public static int reset(ServerLevel level) {
        List<CableConnection> runs = ElectricalNetworkSavedData.get(level).records().stream()
                .map(ElectricalNetworkSavedData.CableRecord::connection)
                .filter(cable -> insideDemo(cable.a().pos()) || insideDemo(cable.b().pos()))
                .toList();
        runs.forEach(cable -> CableService.disconnectDetailed(level, cable));

        int removed = 0;
        // Installed multiblocks restore their ledger from onRemove. The second pass
        // clears those restored blocks without bypassing their lifecycle hooks.
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos mutable : BlockPos.betweenClosed(DEMO_MIN, DEMO_MAX)) {
                BlockPos pos = mutable.immutable();
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    removed++;
                }
            }
        }
        AABB bounds = bounds(DEMO_MIN, DEMO_MAX);
        level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(ItemEntity::discard);
        return removed;
    }

    /** Rebuilds only the isolated fault laboratory, leaving the production line untouched. */
    public static BuildReport buildFaultLab(ServerLevel level, FaultScenario scenario) {
        clearArea(level, FAULT_MIN, FAULT_MAX);
        buildFoundation(level, FAULT_MIN, FAULT_MAX);
        return buildFaultLabInternal(level, scenario);
    }

    private static void buildProductionLine(ServerLevel level, MutableReport report) {
        BlockPos generator = at(0, 1, 0);
        BlockPos generatorOut = at(1, 1, 0);
        BlockPos firstBreaker = at(4, 1, 0);
        BlockPos firstBreakerIn = at(3, 1, 0);
        BlockPos firstBreakerOut = at(5, 1, 0);
        BlockPos stepUp = at(8, 1, 0);
        BlockPos stepUpIn = at(7, 1, 0);
        BlockPos stepUpOut = at(9, 1, 0);
        BlockPos stepDown = at(58, 1, 0);
        BlockPos stepDownIn = at(57, 1, 0);
        BlockPos stepDownOut = at(59, 1, 0);
        BlockPos secondBreaker = at(62, 1, 0);
        BlockPos secondBreakerIn = at(61, 1, 0);
        BlockPos secondBreakerOut = at(63, 1, 0);

        ElectricalDeviceBlockEntity generatorDevice = placeDevice(
                level, generator, ModBlocks.FUEL_GENERATOR.get(), Direction.EAST);
        generatorDevice.cycleConfiguration();
        generatorDevice.cycleConfiguration();
        for (int i = 0; i < 16; i++) {
            require(generatorDevice.insertFuelItem(new ItemStack(Items.COAL)), "generator fuel inventory is full");
        }
        generatorDevice.setSwitchClosed(true);
        report.devices++;

        ElectricalDeviceBlockEntity lvProtection = placeDevice(
                level, firstBreaker, ModBlocks.LV_BREAKER.get(), Direction.EAST);
        lvProtection.setSwitchClosed(true);
        report.devices++;

        ElectricalDeviceBlockEntity up = placeDevice(
                level, stepUp, ModBlocks.TRANSFORMER_LV_MV.get(), Direction.EAST);
        up.setSwitchClosed(true);
        report.devices++;

        ElectricalDeviceBlockEntity down = placeDevice(
                level, stepDown, ModBlocks.TRANSFORMER_LV_MV.get(), Direction.EAST);
        down.cycleConfiguration();
        down.setSwitchClosed(true);
        report.devices++;

        ElectricalDeviceBlockEntity downstreamProtection = placeDevice(
                level, secondBreaker, ModBlocks.LV_BREAKER.get(), Direction.EAST);
        downstreamProtection.setSwitchClosed(true);
        report.devices++;

        placeRack(level, generatorOut, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);
        placeRack(level, firstBreakerIn, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.LV, 4);
        placeRack(level, firstBreakerOut, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);
        placeRack(level, stepUpIn, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.LV, 4);
        placeRack(level, stepUpOut, ModBlocks.MV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.MV, 3);
        placeRack(level, stepDownIn, ModBlocks.MV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.MV, 3);
        placeRack(level, stepDownOut, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);
        placeRack(level, secondBreakerIn, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.LV, 4);
        placeRack(level, secondBreakerOut, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);

        report.cables += connectBundle(level, generatorOut, firstBreakerIn, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);
        report.cables += connectBundle(level, firstBreakerOut, stepUpIn, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);
        // Rack centres are exactly 48 blocks apart; this is the demo's long MV span.
        report.cables += connectBundle(level, stepUpOut, stepDownIn, 3,
                CableAmpacity.A500, CableInsulation.CLASS_2, -1);
        report.cables += connectBundle(level, stepDownOut, secondBreakerIn, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);

        BlockPos storageRack = buildStorageStation(level, report);
        BlockPos crusherRack = buildCrusher(level, report);
        BlockPos converterRack = buildFeBranch(level, report);
        report.cables += connectBundle(level, secondBreakerOut, storageRack, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);
        report.cables += connectBundle(level, secondBreakerOut, crusherRack, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);
        report.cables += connectBundle(level, secondBreakerOut, converterRack, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, -1);
    }

    private static BlockPos buildCrusher(ServerLevel level, MutableReport report) {
        BlockPos controllerPos = at(68, 1, 8);
        BlockState controllerState = ModBlocks.CRUSHER.get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH);
        level.setBlock(controllerPos, controllerState, Block.UPDATE_ALL);

        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                Block block = x == -1 && z == 0 ? ModBlocks.LV_ENERGY_PORT.get()
                        : x == 1 && z == 0 ? ModBlocks.LV_ITEM_PORT.get()
                        : ModBlocks.LV_BASE.get();
                level.setBlock(controllerPos.offset(x, 0, z), block.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = 0; z <= 2; z++) {
                    if (x == 0 && z == 1) continue;
                    level.setBlock(controllerPos.offset(x, y, z),
                            ModBlocks.STEEL_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        BlockPos energyPortPos = controllerPos.offset(-1, 0, 0);
        EnergyPortBlockEntity energyPort = requireBlockEntity(level, energyPortPos, EnergyPortBlockEntity.class);
        energyPort.cycleWiring();
        energyPort.cycleWiring();

        CrusherBlockEntity crusher = requireBlockEntity(level, controllerPos, CrusherBlockEntity.class);
        require(crusher.install(), "crusher structure did not install");
        ItemStack remainder = crusher.inventory().handler().insertItem(
                0, new ItemStack(Items.RAW_IRON, 16), false);
        require(remainder.isEmpty(), "crusher input slot rejected raw iron");
        report.devices++;

        BlockPos rack = energyPortPos.relative(Direction.WEST);
        placeRack(level, rack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.LV, 4);
        return rack;
    }

    private static BlockPos buildStorageStation(ServerLevel level, MutableReport report) {
        BlockPos min = at(66, 1, -10);
        BlockPos max = at(68, 3, -8);
        BlockPos controllerPos = at(66, 2, -9);
        BlockPos energyPortPos = at(68, 2, -9);
        BlockPos cellPos = at(67, 2, -9);

        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            BlockPos pos = mutable.immutable();
            int edges = edgesTouched(pos, min, max);
            Block block;
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
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        EnergyPortBlockEntity energyPort = requireBlockEntity(level, energyPortPos, EnergyPortBlockEntity.class);
        energyPort.cycleWiring();
        energyPort.cycleWiring();
        StorageStationBlockEntity station = requireBlockEntity(
                level, controllerPos, StorageStationBlockEntity.class);
        require(station.revalidate(level, station.provider()), "storage station did not form");
        long initialCharge = Math.min(DEMO_STORAGE_INITIAL_JOULES,
                station.currentCapacityJoules());
        require(station.energy().receiveJoules(initialCharge, false) == initialCharge,
                "storage station rejected its initial charge");
        report.devices++;

        BlockPos rack = energyPortPos.relative(Direction.EAST);
        placeRack(level, rack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);
        return rack;
    }

    private static BlockPos buildFeBranch(ServerLevel level, MutableReport report) {
        BlockPos converterPos = at(68, 1, 3);
        ElectricalDeviceBlockEntity converter = placeDevice(
                level, converterPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.SOUTH);
        converter.cycleConfiguration(); // Native electricity -> FE; close it for the separate FE exercise.
        converter.setSwitchClosed(false);
        report.devices++;

        BlockPos electricalRack = converterPos.relative(Direction.SOUTH);
        placeRack(level, electricalRack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.NORTH,
                TerminalStyle.LV, 4);

        ResourceLocation cubeId = ResourceLocation.fromNamespaceAndPath(
                "mekanism", "basic_energy_cube");
        BuiltInRegistries.BLOCK.getOptional(cubeId)
                .filter(block -> block != Blocks.AIR)
                .ifPresent(block -> {
                    BlockState state = block.defaultBlockState();
                    // Mekanism energy cubes expose their default bidirectional energy face at
                    // the front. Point it at the converter's fixed rear FE face.
                    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                    }
                    BlockPos cubePos = converterPos.relative(Direction.NORTH);
                    level.setBlock(cubePos, state, Block.UPDATE_ALL);
                    PowerDemoCompat.configureFeStorage(
                            cubeId, level, cubePos, Direction.SOUTH);
                });
        return electricalRack;
    }

    private static void buildCableCatalogue(ServerLevel level, MutableReport report) {
        List<Product> products = new ArrayList<>();
        for (CableAmpacity ampacity : CableAmpacity.values()) {
            for (CableInsulation insulation : CableInsulation.values()) {
                if (!ampacity.isBareOnly() || insulation == CableInsulation.BARE) {
                    products.add(new Product(ampacity, insulation));
                }
            }
        }
        for (int index = 0; index < products.size(); index++) {
            int column = index % 9;
            int row = index / 9;
            BlockPos from = at(3 + column * 7, 1, 14 + row * 6);
            BlockPos to = from.offset(3, 0, 0);
            placeRack(level, from, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.DOWN,
                    TerminalStyle.LV, 1);
            placeRack(level, to, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.DOWN,
                    TerminalStyle.LV, 1);
            Product product = products.get(index);
            CableColor color = product.insulation == CableInsulation.BARE
                    ? CableColor.UNDYED
                    : CableColor.ofDyeIndex(product.insulation == CableInsulation.CLASS_1 ? 14 : 11);
            CableMutationResult result = CableService.connectDetailed(level,
                    new TerminalEndpoint(from, 0), new TerminalEndpoint(to, 0),
                    product.ampacity, product.insulation, color);
            require(result.changed(), "catalogue cable rejected: " + result.status());
            report.cables++;
        }
    }

    private static void buildDeviceCatalogue(ServerLevel level, MutableReport report) {
        List<Block> devices = List.of(
                ModBlocks.FUEL_GENERATOR.get(), ModBlocks.CREATIVE_POWER_SOURCE.get(),
                ModBlocks.TRANSFORMER_LV_MV.get(), ModBlocks.TRANSFORMER_MV_HV.get(),
                ModBlocks.TRANSFORMER_HV_EHV.get(),
                ModBlocks.LV_BREAKER.get(), ModBlocks.MV_BREAKER.get(),
                ModBlocks.HV_BREAKER.get(), ModBlocks.EHV_BREAKER.get(),
                ModBlocks.LV_FUSE_BOX.get(), ModBlocks.MV_FUSE_BOX.get(),
                ModBlocks.HV_FUSE_BOX.get(), ModBlocks.EHV_FUSE_BOX.get(),
                ModBlocks.LV_FE_CONVERTER.get(), ModBlocks.MV_FE_CONVERTER.get(),
                ModBlocks.HV_FE_CONVERTER.get(), ModBlocks.EHV_FE_CONVERTER.get());
        for (int i = 0; i < devices.size(); i++) {
            int x = 2 + (i % 9) * 7;
            int z = 26 + (i / 9) * 3;
            placeDevice(level, at(x, 1, z), devices.get(i), Direction.SOUTH);
            report.devices++;
        }
        Block[] mounts = {
                ModBlocks.LV_TERMINAL_MOUNT.get(), ModBlocks.MV_TERMINAL_MOUNT.get(),
                ModBlocks.HV_TERMINAL_MOUNT.get(), ModBlocks.EHV_TERMINAL_MOUNT.get()
        };
        TerminalStyle[] styles = {TerminalStyle.LV, TerminalStyle.MV, TerminalStyle.HV, TerminalStyle.EHV};
        int[] counts = {4, 3, 1, 1};
        for (int i = 0; i < mounts.length; i++) {
            placeRack(level, at(66 + i * 2, 1, 26), mounts[i], Direction.DOWN, styles[i], counts[i]);
        }
    }

    private static BuildReport buildFaultLabInternal(ServerLevel level, FaultScenario scenario) {
        MutableReport report = new MutableReport();
        installFaultControls(level);
        BlockPos sourcePos = at(0, 1, -13);

        if (scenario == FaultScenario.OVERVOLTAGE) {
            ElectricalDeviceBlockEntity source = placeDevice(
                    level, sourcePos, ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.EAST);
            source.cycleCreativeTier();
            source.cycleCreativeTier();
            BlockPos sourceRackA = at(1, 1, -13);
            BlockPos sourceRackB = at(0, 1, -14);
            BlockPos sourceRackC = at(0, 1, -12);
            placeRack(level, sourceRackA, ModBlocks.HV_TERMINAL_MOUNT.get(), Direction.WEST,
                    TerminalStyle.HV, 1);
            placeRack(level, sourceRackB, ModBlocks.HV_TERMINAL_MOUNT.get(), Direction.SOUTH,
                    TerminalStyle.HV, 1);
            placeRack(level, sourceRackC, ModBlocks.HV_TERMINAL_MOUNT.get(), Direction.NORTH,
                    TerminalStyle.HV, 1);

            BlockPos loadPos = at(8, 1, -13);
            ElectricalDeviceBlockEntity load = placeDevice(
                    level, loadPos, ModBlocks.MV_FE_CONVERTER.get(), Direction.WEST);
            load.cycleConfiguration();
            BlockPos loadRack = at(7, 1, -13);
            placeRack(level, loadRack, ModBlocks.MV_TERMINAL_MOUNT.get(), Direction.EAST,
                    TerminalStyle.MV, 3);
            report.cables += connectOne(level, sourceRackA, 0, loadRack, 0,
                    CableAmpacity.A500, CableInsulation.CLASS_2, conductorColor(0));
            report.cables += connectOne(level, sourceRackB, 0, loadRack, 1,
                    CableAmpacity.A500, CableInsulation.CLASS_2, conductorColor(1));
            report.cables += connectOne(level, sourceRackC, 0, loadRack, 2,
                    CableAmpacity.A500, CableInsulation.CLASS_2, conductorColor(2));
            source.setSwitchClosed(true);
            load.setSwitchClosed(true);
            report.devices += 2;
            return report.freeze();
        }

        ElectricalDeviceBlockEntity source = placeDevice(
                level, sourcePos, ModBlocks.CREATIVE_POWER_SOURCE.get(), Direction.EAST);
        BlockPos sourceRack = at(1, 1, -13);
        placeRack(level, sourceRack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                TerminalStyle.LV, 4);
        report.devices++;

        if (scenario == FaultScenario.OVERLOAD) {
            BlockPos breakerPos = at(4, 1, -13);
            ElectricalDeviceBlockEntity breaker = placeDevice(
                    level, breakerPos, ModBlocks.LV_BREAKER.get(), Direction.EAST);
            BlockPos breakerIn = at(3, 1, -13);
            BlockPos breakerOut = at(5, 1, -13);
            placeRack(level, breakerIn, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                    TerminalStyle.LV, 4);
            placeRack(level, breakerOut, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.WEST,
                    TerminalStyle.LV, 4);
            report.cables += connectBundle(level, sourceRack, breakerIn, 4,
                    CableAmpacity.A500, CableInsulation.CLASS_1, -1);
            report.devices++;

            for (int i = 0; i < 3; i++) {
                int z = -16 + i * 3;
                BlockPos converterPos = at(10, 1, z);
                ElectricalDeviceBlockEntity converter = placeDevice(
                        level, converterPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.WEST);
                converter.cycleConfiguration();
                converter.setSwitchClosed(true);
                BlockPos rack = converterPos.relative(Direction.WEST);
                placeRack(level, rack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                        TerminalStyle.LV, 4);
                report.cables += connectBundle(level, breakerOut, rack, 4,
                        CableAmpacity.A50, CableInsulation.CLASS_1, -1);
                report.devices++;
            }
            breaker.setSwitchClosed(true);
            source.setSwitchClosed(true);
            return report.freeze();
        }

        BlockPos converterPos = at(9, 1, -13);
        ElectricalDeviceBlockEntity converter = placeDevice(
                level, converterPos, ModBlocks.LV_FE_CONVERTER.get(), Direction.WEST);
        converter.cycleConfiguration();
        converter.setSwitchClosed(true);
        BlockPos converterRack = at(8, 1, -13);
        placeRack(level, converterRack, ModBlocks.LV_TERMINAL_MOUNT.get(), Direction.EAST,
                TerminalStyle.LV, 4);
        int omitted = scenario == FaultScenario.PHASE_LOSS ? 1 : -1;
        report.cables += connectBundle(level, sourceRack, converterRack, 4,
                CableAmpacity.A150, CableInsulation.CLASS_1, omitted);
        source.setSwitchClosed(true);
        report.devices++;
        return report.freeze();
    }

    private static void installFaultControls(ServerLevel level) {
        FaultScenario[] scenarios = {
                FaultScenario.OVERLOAD,
                FaultScenario.PHASE_LOSS,
                FaultScenario.OVERVOLTAGE,
                FaultScenario.RESET
        };
        String[] labels = {"OVERLOAD", "PHASE LOSS", "OVERVOLT", "RESET"};
        for (int i = 0; i < scenarios.length; i++) {
            BlockPos commandPos = at(i * 2, 1, -9);
            level.setBlock(commandPos, Blocks.COMMAND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            CommandBlockEntity commandBlock = requireBlockEntity(
                    level, commandPos, CommandBlockEntity.class);
            commandBlock.getCommandBlock().setCommand(
                    "stardustindustry demo fault " + scenarios[i].id());
            commandBlock.getCommandBlock().setTrackOutput(false);
            commandBlock.setChanged();

            BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            level.setBlock(commandPos.above(), button, Block.UPDATE_ALL);

            BlockPos signPos = commandPos.relative(Direction.SOUTH);
            level.setBlock(signPos, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
            SignBlockEntity sign = requireBlockEntity(level, signPos, SignBlockEntity.class);
            SignText text = new SignText().setMessage(1, Component.literal(labels[i]));
            sign.setText(text, true);
            sign.setText(text, false);
            sign.setChanged();
            level.sendBlockUpdated(signPos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
        }
    }

    private static ElectricalDeviceBlockEntity placeDevice(
            ServerLevel level, BlockPos pos, Block block, Direction facing) {
        require(block instanceof ElectricalDeviceBlock, "not an electrical device: " + block);
        BlockState state = block.defaultBlockState().setValue(ElectricalDeviceBlock.FACING, facing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return requireBlockEntity(level, pos, ElectricalDeviceBlockEntity.class);
    }

    private static TerminalMountBlockEntity placeRack(ServerLevel level, BlockPos pos, Block block,
                                                       Direction backing, TerminalStyle style,
                                                       int terminals) {
        require(block instanceof TerminalMountBlock, "not a terminal mount: " + block);
        BlockState state = block.defaultBlockState().setValue(TerminalMountBlock.FACING, backing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        TerminalMountBlockEntity rack = requireBlockEntity(level, pos, TerminalMountBlockEntity.class);
        for (int cell = 0; cell < terminals; cell++) {
            rack.addTerminal(cell, style);
        }
        require(rack.terminalCount() == terminals, "terminal rack rejected its fittings at " + pos);
        return rack;
    }

    private static int connectBundle(ServerLevel level, BlockPos from, BlockPos to, int conductors,
                                     CableAmpacity ampacity, CableInsulation insulation, int omitted) {
        int connected = 0;
        for (int cell = 0; cell < conductors; cell++) {
            if (cell == omitted) continue;
            CableMutationResult result = CableService.connectDetailed(level,
                    new TerminalEndpoint(from, cell), new TerminalEndpoint(to, cell),
                    ampacity, insulation, conductorColor(cell));
            require(result.changed(), "cable " + from + " -> " + to + " rejected: " + result.status());
            connected++;
        }
        return connected;
    }

    private static int connectOne(ServerLevel level, BlockPos from, int fromCell,
                                  BlockPos to, int toCell, CableAmpacity ampacity,
                                  CableInsulation insulation, CableColor color) {
        CableMutationResult result = CableService.connectDetailed(level,
                new TerminalEndpoint(from, fromCell), new TerminalEndpoint(to, toCell),
                ampacity, insulation, color);
        require(result.changed(), "cable " + from + " -> " + to + " rejected: " + result.status());
        return 1;
    }

    private static CableColor conductorColor(int conductor) {
        return CableColor.ofDyeIndex(switch (conductor) {
            case 0 -> 14; // red
            case 1 -> 4;  // yellow
            case 2 -> 11; // blue
            default -> 15; // neutral / black
        });
    }

    private static int edgesTouched(BlockPos pos, BlockPos min, BlockPos max) {
        int edges = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) edges++;
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) edges++;
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) edges++;
        return edges;
    }

    private static void loadDemoChunks(ServerLevel level) {
        for (int chunkX = DEMO_MIN.getX() >> 4; chunkX <= DEMO_MAX.getX() >> 4; chunkX++) {
            for (int chunkZ = DEMO_MIN.getZ() >> 4; chunkZ <= DEMO_MAX.getZ() >> 4; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static void buildFoundation(ServerLevel level, BlockPos min, BlockPos max) {
        int y = ORIGIN.getY();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void clearArea(ServerLevel level, BlockPos min, BlockPos max) {
        List<CableConnection> runs = ElectricalNetworkSavedData.get(level).records().stream()
                .map(ElectricalNetworkSavedData.CableRecord::connection)
                .filter(cable -> inside(cable.a().pos(), min, max) || inside(cable.b().pos(), min, max))
                .toList();
        runs.forEach(cable -> CableService.disconnectDetailed(level, cable));
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
                level.setBlock(mutable, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, bounds(min, max))
                .forEach(ItemEntity::discard);
    }

    private static boolean insideDemo(BlockPos pos) {
        return inside(pos, DEMO_MIN, DEMO_MAX);
    }

    private static AABB bounds(BlockPos min, BlockPos max) {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static BlockPos at(int x, int y, int z) {
        return ORIGIN.offset(x, y, z);
    }

    private static <T> T requireBlockEntity(ServerLevel level, BlockPos pos, Class<T> type) {
        Object blockEntity = level.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + " at " + pos
                    + ", found " + blockEntity);
        }
        return type.cast(blockEntity);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            StardustIndustry.LOGGER.error("Power demo build failed: {}", message);
            throw new IllegalStateException(message);
        }
    }

    private record Product(CableAmpacity ampacity, CableInsulation insulation) {}

    private static final class MutableReport {
        private int devices;
        private int cables;

        private void add(BuildReport report) {
            devices += report.devices();
            cables += report.cables();
        }

        private BuildReport freeze() {
            return new BuildReport(devices, cables);
        }
    }
}
