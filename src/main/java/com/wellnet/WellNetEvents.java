package com.wellnet;

import com.wellnet.client.MainThreadApplier;
import com.wellnet.config.WellNetConfig;
import com.wellnet.core.ClientSnapshot;
import com.wellnet.core.WellNetCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "wellnet", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WellNetEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(WellNetEvents.class);

    private static boolean worldLoadedFired = false;
    private static int snapshotTickCounter = 0;
    private static long lastTickNano = 0L;
    private static double sumTickDeltaMs = 0.0;
    private static double maxTickDeltaMs = 0.0;
    private static int tickDeltaSamples = 0;
    private static double lastSampleX = 0.0;
    private static double lastSampleY = 0.0;
    private static double lastSampleZ = 0.0;
    private static boolean lastSamplePosValid = false;

    private WellNetEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        WellNetCore core = WellNetCore.getInstance();
        if (core == null) {
            return;
        }
        core.onConnectStart();
        core.getModuleManager().startAll();
    }

    @SubscribeEvent
    public static void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WellNetCore core = WellNetCore.getInstance();
        if (core != null) {
            core.onDisconnect();
        }
        worldLoadedFired = false;
        resetSamplingState();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        WellNetCore core = WellNetCore.getInstance();
        boolean worldLoaded = minecraft.level != null;
        if (worldLoaded && !worldLoadedFired) {
            worldLoadedFired = true;
            if (core != null) {
                core.onWorldLoaded();
            }
            resetSamplingState();
        } else if (!worldLoaded && worldLoadedFired) {
            worldLoadedFired = false;
            if (core != null) {
                core.onWorldUnload();
            }
            resetSamplingState();
            return;
        }

        LocalPlayer player = minecraft.player;
        if (!worldLoaded || player == null || core == null) {
            if (!worldLoaded) {
                resetSamplingState();
            }
            return;
        }

        boolean paused = minecraft.isPaused();
        MainThreadApplier.applyPendingActionPlanFromMinecraft(core);
        if (paused) {
            resetFrameWindow();
        } else {
            updateFrameSampling();
        }

        snapshotTickCounter++;
        if (snapshotTickCounter % WellNetConfig.snapshotIntervalTicks() != 0) {
            return;
        }

        long sampleTimeMs = System.currentTimeMillis();
        int pingMs = readPingMillis(minecraft, player);
        int renderDistance = readRenderDistance(minecraft);
        ServerSample serverSample = resolveServerSample(minecraft);
        PositionSample positionSample = readPositionSample(player);
        double avgTickDeltaMs = paused ? Double.NaN : tickDeltaSamples > 0 ? sumTickDeltaMs / tickDeltaSamples : Double.NaN;
        int approxFps = paused ? -1 : avgTickDeltaMs > 0.0 ? (int) Math.round(1000.0 / avgTickDeltaMs) : -1;
        double maxFrameMs = paused ? Double.NaN : maxTickDeltaMs > 0.0 ? maxTickDeltaMs : Double.NaN;
        resetFrameWindow();

        ClientSnapshot snapshot = new ClientSnapshot(
            sampleTimeMs,
            serverSample.serverId(),
            pingMs,
            positionSample.x(),
            positionSample.y(),
            positionSample.z(),
            positionSample.movementDelta(),
            serverSample.integratedHost(),
            renderDistance,
            approxFps,
            maxFrameMs,
            paused,
            true,
            true
        );
        core.pushSnapshotFromMainThread(snapshot);
    }

    private static void updateFrameSampling() {
        long nowNano = System.nanoTime();
        if (lastTickNano != 0L) {
            double tickDeltaMs = (nowNano - lastTickNano) / 1_000_000.0;
            if (tickDeltaMs > maxTickDeltaMs) {
                maxTickDeltaMs = tickDeltaMs;
            }
            sumTickDeltaMs += tickDeltaMs;
            tickDeltaSamples++;
        }
        lastTickNano = nowNano;
    }

    private static int readPingMillis(Minecraft minecraft, LocalPlayer player) {
        try {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection == null) {
                return -1;
            }
            PlayerInfo selfInfo = connection.getPlayerInfo(player.getUUID());
            return selfInfo == null ? -1 : selfInfo.getLatency();
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to read player latency", throwable);
            return -1;
        }
    }

    private static int readRenderDistance(Minecraft minecraft) {
        try {
            if (minecraft.options == null) {
                return -1;
            }
            return minecraft.options.renderDistance().get();
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to read render distance", throwable);
            return -1;
        }
    }

    private static ServerSample resolveServerSample(Minecraft minecraft) {
        try {
            IntegratedServer integratedServer = minecraft.getSingleplayerServer();
            if (integratedServer != null) {
                return new ServerSample(integratedServer.getAverageTickTime() > 0.0f, "integrated");
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to resolve integrated-server state", throwable);
        }
        return new ServerSample(false, "remote");
    }

    private static PositionSample readPositionSample(LocalPlayer player) {
        try {
            Vec3 position = player.position();
            if (position == null) {
                return new PositionSample(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            }

            double x = position.x;
            double y = position.y;
            double z = position.z;
            double movementDelta = 0.0;
            if (lastSamplePosValid) {
                double dx = x - lastSampleX;
                double dy = y - lastSampleY;
                double dz = z - lastSampleZ;
                movementDelta = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            lastSampleX = x;
            lastSampleY = y;
            lastSampleZ = z;
            lastSamplePosValid = true;
            return new PositionSample(x, y, z, movementDelta);
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to sample player position", throwable);
            return new PositionSample(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }

    private static void resetSamplingState() {
        snapshotTickCounter = 0;
        lastSamplePosValid = false;
        lastSampleX = 0.0;
        lastSampleY = 0.0;
        lastSampleZ = 0.0;
        resetFrameWindow();
    }

    private static void resetFrameWindow() {
        lastTickNano = 0L;
        sumTickDeltaMs = 0.0;
        maxTickDeltaMs = 0.0;
        tickDeltaSamples = 0;
    }

    private record ServerSample(boolean integratedHost, String serverId) {
    }

    private record PositionSample(double x, double y, double z, double movementDelta) {
    }
}
