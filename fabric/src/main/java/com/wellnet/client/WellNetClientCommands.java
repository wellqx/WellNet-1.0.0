package com.wellnet.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class WellNetClientCommands {
    private WellNetClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("wellnet")
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        WellNetStatusReport.show();
                        return 1;
                    })
                    .then(ClientCommandManager.literal("technical").executes(context -> {
                        WellNetStatusReport.showTechnical();
                        return 1;
                    })))
                .then(ClientCommandManager.literal("trace").executes(context -> {
                    WellNetStatusReport.showTrace();
                    return 1;
                }))
                .then(ClientCommandManager.literal("help").executes(context -> {
                    WellNetStatusReport.showGuide("overview");
                    return 1;
                }))
                .then(ClientCommandManager.literal("help")
                    .then(ClientCommandManager.literal("status").executes(context -> {
                        WellNetStatusReport.showGuide("status");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("hosting").executes(context -> {
                        WellNetStatusReport.showGuide("hosting");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("multiplayer").executes(context -> {
                        WellNetStatusReport.showGuide("multiplayer");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("recovery").executes(context -> {
                        WellNetStatusReport.showGuide("recovery");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("traffic").executes(context -> {
                        WellNetStatusReport.showGuide("traffic");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("troubleshooting").executes(context -> {
                        WellNetStatusReport.showGuide("troubleshooting");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("diagnostics").executes(context -> {
                        WellNetStatusReport.showGuide("diagnostics");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("trace").executes(context -> {
                        WellNetStatusReport.showGuide("trace");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("phases").executes(context -> {
                        WellNetStatusReport.showGuide("phases");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("servers").executes(context -> {
                        WellNetStatusReport.showGuide("servers");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("lagspikes").executes(context -> {
                        WellNetStatusReport.showGuide("lagspikes");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("faq").executes(context -> {
                        WellNetStatusReport.showGuide("faq");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("glossary").executes(context -> {
                        WellNetStatusReport.showGuide("glossary");
                        return 1;
                    })))
        );
    }
}
