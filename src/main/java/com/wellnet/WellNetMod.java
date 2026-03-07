package com.wellnet;

import com.wellnet.config.ConfigMigrator;
import com.wellnet.config.WellNetConfig;
import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.WellNetCore;
import com.wellnet.net.ClientPingSource;
import java.io.Serializable;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(WellNetMod.MODID)
public final class WellNetMod {
    public static final String MODID = "wellnet";
    private static final Logger LOGGER = LoggerFactory.getLogger(WellNetMod.class);

    private WellNetCore core;

    @SuppressWarnings("removal")
    public WellNetMod() {
        ConfigMigrator.migrateIfNeeded();
        WellNetConfig.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        LOGGER.info("WellNet constructed. Waiting for common setup.");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("WellNet common setup starting.");
        this.core = new WellNetCore();

        NetDiagnosticsModule.Source source = DistExecutor.safeRunForDist(
            () -> ClientPingSource::new,
            () -> (DistExecutor.SafeSupplier<NetDiagnosticsModule.Source> & Serializable) WellNetMod::disabledSource
        );
        NetDiagnosticsModule netDiagnosticsModule = new NetDiagnosticsModule(this.core, source, 1000L);
        AdaptiveClientSettingsModule adaptiveClientModule = new AdaptiveClientSettingsModule(this.core, netDiagnosticsModule);

        WellNetCore.ModuleManager moduleManager = this.core.getModuleManager();
        moduleManager.registerModule(netDiagnosticsModule);
        moduleManager.registerModule(adaptiveClientModule);
        moduleManager.initializeAll();

        LOGGER.info("WellNet common setup completed.");
    }

    private static NetDiagnosticsModule.Source disabledSource() {
        return () -> -1;
    }
}
