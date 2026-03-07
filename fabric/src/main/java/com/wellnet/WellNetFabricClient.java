package com.wellnet;

import com.wellnet.client.WellNetClient;
import com.wellnet.config.WellNetConfig;
import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.WellNetCore;
import com.wellnet.net.ClientPingSource;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WellNetFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WellNetFabricClient.class);

    @Override
    public void onInitializeClient() {
        WellNetConfig.init();

        WellNetCore core = new WellNetCore();
        NetDiagnosticsModule netDiagnosticsModule = new NetDiagnosticsModule(core, new ClientPingSource(), 1000L);
        AdaptiveClientSettingsModule adaptiveClientModule = new AdaptiveClientSettingsModule(core, netDiagnosticsModule);

        WellNetCore.ModuleManager moduleManager = core.getModuleManager();
        moduleManager.registerModule(netDiagnosticsModule);
        moduleManager.registerModule(adaptiveClientModule);
        moduleManager.initializeAll();

        WellNetEvents.init();
        WellNetClient.init();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WellNetCore currentCore = WellNetCore.getInstance();
            if (currentCore != null) {
                currentCore.shutdown();
            }
        });

        LOGGER.info("WellNet Fabric client initialized.");
    }
}
