package com.wellnet.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class WellNetClientCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wellnet");
        dispatcher.register(
            root
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        showStatus();
                        return 1;
                    })
                    .then(Commands.literal("technical").executes(ctx -> {
                        showTechnicalStatus();
                        return 1;
                    })))
                .then(Commands.literal("trace").executes(ctx -> {
                    showTrace();
                    return 1;
                }))
                .then(Commands.literal("help").executes(ctx -> {
                    showGuide("overview");
                    return 1;
                }))
                .then(Commands.literal("help")
                    .then(Commands.literal("status").executes(ctx -> {
                        showGuide("status");
                        return 1;
                    }))
                    .then(Commands.literal("hosting").executes(ctx -> {
                        showGuide("hosting");
                        return 1;
                    }))
                    .then(Commands.literal("multiplayer").executes(ctx -> {
                        showGuide("multiplayer");
                        return 1;
                    }))
                    .then(Commands.literal("recovery").executes(ctx -> {
                        showGuide("recovery");
                        return 1;
                    }))
                    .then(Commands.literal("traffic").executes(ctx -> {
                        showGuide("traffic");
                        return 1;
                    }))
                    .then(Commands.literal("troubleshooting").executes(ctx -> {
                        showGuide("troubleshooting");
                        return 1;
                    }))
                    .then(Commands.literal("diagnostics").executes(ctx -> {
                        showGuide("diagnostics");
                        return 1;
                    }))
                    .then(Commands.literal("trace").executes(ctx -> {
                        showGuide("trace");
                        return 1;
                    }))
                    .then(Commands.literal("phases").executes(ctx -> {
                        showGuide("phases");
                        return 1;
                    }))
                    .then(Commands.literal("servers").executes(ctx -> {
                        showGuide("servers");
                        return 1;
                    }))
                    .then(Commands.literal("lagspikes").executes(ctx -> {
                        showGuide("lagspikes");
                        return 1;
                    }))
                    .then(Commands.literal("faq").executes(ctx -> {
                        showGuide("faq");
                        return 1;
                    }))
                    .then(Commands.literal("glossary").executes(ctx -> {
                        showGuide("glossary");
                        return 1;
                    })))
        );
    }

    private static void showStatus() {
        WellNetStatusReport.show();
    }

    private static void showTechnicalStatus() {
        WellNetStatusReport.showTechnical();
    }

    private static void showTrace() {
        WellNetStatusReport.showTrace();
    }

    private static void showGuide(String topic) {
        WellNetStatusReport.showGuide(topic);
    }
}
