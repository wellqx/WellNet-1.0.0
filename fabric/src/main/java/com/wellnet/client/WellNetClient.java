package com.wellnet.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class WellNetClient {
    private WellNetClient() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            WellNetClientCommands.register(dispatcher)
        );
    }
}
