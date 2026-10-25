package com.stardustindustry.stardustindustry.demo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only commands for building and resetting the executable power demo. */
public final class DemoCommands {

    private DemoCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardustindustry")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("demo")
                        .then(Commands.literal("build")
                                .executes(context -> build(context.getSource())))
                        .then(Commands.literal("reset")
                                .executes(context -> reset(context.getSource())))
                        .then(Commands.literal("fault")
                                .then(Commands.literal("overload")
                                        .executes(context -> fault(context.getSource(),
                                                PowerDemoBuilder.FaultScenario.OVERLOAD)))
                                .then(Commands.literal("phase_loss")
                                        .executes(context -> fault(context.getSource(),
                                                PowerDemoBuilder.FaultScenario.PHASE_LOSS)))
                                .then(Commands.literal("overvoltage")
                                        .executes(context -> fault(context.getSource(),
                                                PowerDemoBuilder.FaultScenario.OVERVOLTAGE)))
                                .then(Commands.literal("reset")
                                        .executes(context -> fault(context.getSource(),
                                                PowerDemoBuilder.FaultScenario.RESET))))));
    }

    private static int build(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        PowerDemoBuilder.BuildReport report = PowerDemoBuilder.build(level);
        source.sendSuccess(() -> Component.translatable(
                "command.stardustindustry.demo.built",
                report.devices(), report.cables(), PowerDemoBuilder.ORIGIN.toShortString()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        int removed = PowerDemoBuilder.reset(level);
        source.sendSuccess(() -> Component.translatable(
                "command.stardustindustry.demo.reset", removed), true);
        return 1;
    }

    private static int fault(CommandSourceStack source, PowerDemoBuilder.FaultScenario scenario)
            throws CommandSyntaxException {
        PowerDemoBuilder.BuildReport report = PowerDemoBuilder.buildFaultLab(source.getLevel(), scenario);
        source.sendSuccess(() -> Component.translatable(
                "command.stardustindustry.demo.fault", scenario.id(), report.cables()), true);
        return 1;
    }
}
