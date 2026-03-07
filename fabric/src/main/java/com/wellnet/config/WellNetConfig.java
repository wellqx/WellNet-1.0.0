package com.wellnet.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;

public final class WellNetConfig {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Properties PROPERTIES = new Properties();
    private static final Map<String, String> DEFAULTS = createDefaults();
    private static Path configPath;

    private WellNetConfig() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        configPath = FabricLoader.getInstance().getConfigDir().resolve("wellnet-client.properties");
        loadProperties();
    }

    public static void initIfNeeded() {
        if (!INITIALIZED.get()) {
            init();
        }
    }

    public static Object specOrNull() {
        initIfNeeded();
        return configPath;
    }

    public static boolean enabled() {
        return booleanValue("general.enabled", true);
    }

    public static boolean debug() {
        return booleanValue("general.debug", false);
    }

    public static int snapshotIntervalTicks() {
        return intValue("sampling.snapshotIntervalTicks", 20, 1, 200);
    }

    public static long decisionIntervalMs() {
        return longValue("sampling.decisionIntervalMs", 5000L, 250L, 60000L);
    }

    public static int renderDistanceMin() {
        return Math.min(rawRenderDistanceMin(), rawRenderDistanceMax());
    }

    public static int renderDistanceMax() {
        return Math.max(32, Math.max(rawRenderDistanceMin(), rawRenderDistanceMax()));
    }

    public static long renderDistanceCooldownMs() {
        return longValue("renderDistance.cooldownMs", 30000L, 0L, 600000L);
    }

    public static boolean restoreOnExit() {
        return booleanValue("renderDistance.restoreOnExit", true);
    }

    public static long afkTimeoutMs() {
        return longValue("renderDistance.afkTimeoutMs", 60000L, 5000L, 3600000L);
    }

    public static int afkMaxRenderDistance() {
        return clampInt(intValue("renderDistance.afkMaxRD", 10, 2, 24), renderDistanceMin(), renderDistanceMax());
    }

    public static int pingExcellentMaxMs() {
        return intValue("qualityThresholds.pingExcellentMaxMs", 80, 1, 5000);
    }

    public static int pingGoodMaxMs() {
        return intValue("qualityThresholds.pingGoodMaxMs", 140, 1, 10000);
    }

    public static int pingFairMaxMs() {
        return intValue("qualityThresholds.pingFairMaxMs", 220, 1, 20000);
    }

    public static int jitterExcellentMaxMs() {
        return intValue("qualityThresholds.jitterExcellentMaxMs", 12, 0, 5000);
    }

    public static int jitterGoodMaxMs() {
        return intValue("qualityThresholds.jitterGoodMaxMs", 25, 0, 10000);
    }

    public static int jitterFairMaxMs() {
        return intValue("qualityThresholds.jitterFairMaxMs", 45, 0, 20000);
    }

    public static int spikeExcellentMaxPct() {
        return intValue("qualityThresholds.spikeExcellentMaxPct", 3, 0, 100);
    }

    public static int spikeGoodMaxPct() {
        return intValue("qualityThresholds.spikeGoodMaxPct", 8, 0, 100);
    }

    public static int spikeFairMaxPct() {
        return intValue("qualityThresholds.spikeFairMaxPct", 15, 0, 100);
    }

    public static int spikePingThresholdMs() {
        return intValue("qualityThresholds.spikePingThresholdMs", 250, 1, 20000);
    }

    public static int spikeDeltaThresholdMs() {
        return intValue("qualityThresholds.spikeDeltaThresholdMs", 80, 0, 20000);
    }

    public static int lateThresholdMs() {
        return intValue("qualityThresholds.lateThresholdMs", 250, 1, 20000);
    }

    public static int frameSpikeThresholdMs() {
        return intValue("clientPerformance.frameSpikeThresholdMs", 80, 0, 10000);
    }

    public static int frameStallThresholdMs() {
        return intValue("clientPerformance.frameStallThresholdMs", 200, 0, 60000);
    }

    public static int frameSpikeBadPct() {
        return intValue("clientPerformance.frameSpikeBadPct", 15, 0, 100);
    }

    public static int frameStallBadPct() {
        return intValue("clientPerformance.frameStallBadPct", 3, 0, 100);
    }

    public static long targetInboundBps() {
        return longValue("bpsThresholds.targetInBps", 5_000_000L, 0L, 1_000_000_000L);
    }

    public static long targetOutboundBps() {
        return longValue("bpsThresholds.targetOutBps", 5_000_000L, 0L, 1_000_000_000L);
    }

    public static int maxBurstinessPercent() {
        return intValue("bpsThresholds.maxBurstinessPercent", 220, 0, 2000);
    }

    public static int severeBurstinessPercent() {
        return Math.max(maxBurstinessPercent(), intValue("bpsThresholds.severeBurstinessPercent", 320, 0, 2000));
    }

    public static long joinWarmupMs() {
        return longValue("safeguards.joinWarmupMs", 15000L, 0L, 300000L);
    }

    public static int joinWarmupRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 12));
        return clampInt(intValue("safeguards.joinWarmupRenderDistanceCap", 12, 2, 24), minCap, renderDistanceMax());
    }

    public static long burstGuardMs() {
        return longValue("safeguards.burstGuardMs", 20000L, 0L, 300000L);
    }

    public static int burstGuardRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 10));
        return clampInt(intValue("safeguards.burstGuardRenderDistanceCap", 10, 2, 24), minCap, renderDistanceMax());
    }

    public static int hostRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 16));
        return clampInt(intValue("safeguards.integratedHostCap", 16, 2, 24), minCap, renderDistanceMax());
    }

    public static int recoveryStableTicks() {
        return intValue("safeguards.recoveryStableTicks", 4, 1, 32);
    }

    public static int hostRecoveryStableTicks() {
        return Math.max(30, intValue("safeguards.hostRecoveryStableTicks", 30, 1, 120));
    }

    private static int rawRenderDistanceMin() {
        return intValue("renderDistance.min", 4, 2, 32);
    }

    private static int rawRenderDistanceMax() {
        return intValue("renderDistance.max", 32, 2, 32);
    }

    private static boolean booleanValue(String key, boolean defaultValue) {
        initIfNeeded();
        String value = PROPERTIES.getProperty(key, DEFAULTS.getOrDefault(key, String.valueOf(defaultValue)));
        return Boolean.parseBoolean(value);
    }

    private static int intValue(String key, int defaultValue, int min, int max) {
        initIfNeeded();
        String value = PROPERTIES.getProperty(key, DEFAULTS.getOrDefault(key, String.valueOf(defaultValue)));
        try {
            return clampInt(Integer.parseInt(value.trim()), min, max);
        } catch (Throwable throwable) {
            return clampInt(defaultValue, min, max);
        }
    }

    private static long longValue(String key, long defaultValue, long min, long max) {
        initIfNeeded();
        String value = PROPERTIES.getProperty(key, DEFAULTS.getOrDefault(key, String.valueOf(defaultValue)));
        try {
            return clampLong(Long.parseLong(value.trim()), min, max);
        } catch (Throwable throwable) {
            return clampLong(defaultValue, min, max);
        }
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static long clampLong(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static void loadProperties() {
        PROPERTIES.clear();
        PROPERTIES.putAll(DEFAULTS);

        if (configPath == null) {
            return;
        }

        boolean shouldWriteBack = !Files.isRegularFile(configPath);
        if (!shouldWriteBack) {
            Properties loaded = new Properties();
            try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                loaded.load(reader);
                for (String key : loaded.stringPropertyNames()) {
                    PROPERTIES.setProperty(key, loaded.getProperty(key));
                }
            } catch (IOException ignored) {
                shouldWriteBack = true;
            }
        }

        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (!PROPERTIES.containsKey(entry.getKey())) {
                PROPERTIES.setProperty(entry.getKey(), entry.getValue());
                shouldWriteBack = true;
            }
        }

        if (shouldWriteBack) {
            writeProperties();
        }
    }

    private static void writeProperties() {
        if (configPath == null) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            StringBuilder builder = new StringBuilder();
            builder.append("# WellNet Fabric client config").append('\n');
            for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
                String value = PROPERTIES.getProperty(entry.getKey(), entry.getValue());
                builder.append(entry.getKey()).append('=').append(value).append('\n');
            }
            Files.writeString(
                configPath,
                builder.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> createDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("general.enabled", "true");
        defaults.put("general.debug", "false");
        defaults.put("sampling.snapshotIntervalTicks", "20");
        defaults.put("sampling.decisionIntervalMs", "5000");
        defaults.put("renderDistance.min", "4");
        defaults.put("renderDistance.max", "32");
        defaults.put("renderDistance.cooldownMs", "30000");
        defaults.put("renderDistance.restoreOnExit", "true");
        defaults.put("renderDistance.afkTimeoutMs", "60000");
        defaults.put("renderDistance.afkMaxRD", "10");
        defaults.put("qualityThresholds.pingExcellentMaxMs", "80");
        defaults.put("qualityThresholds.pingGoodMaxMs", "140");
        defaults.put("qualityThresholds.pingFairMaxMs", "220");
        defaults.put("qualityThresholds.jitterExcellentMaxMs", "12");
        defaults.put("qualityThresholds.jitterGoodMaxMs", "25");
        defaults.put("qualityThresholds.jitterFairMaxMs", "45");
        defaults.put("qualityThresholds.spikeExcellentMaxPct", "3");
        defaults.put("qualityThresholds.spikeGoodMaxPct", "8");
        defaults.put("qualityThresholds.spikeFairMaxPct", "15");
        defaults.put("qualityThresholds.spikePingThresholdMs", "250");
        defaults.put("qualityThresholds.spikeDeltaThresholdMs", "80");
        defaults.put("qualityThresholds.lateThresholdMs", "250");
        defaults.put("clientPerformance.frameSpikeThresholdMs", "80");
        defaults.put("clientPerformance.frameStallThresholdMs", "200");
        defaults.put("clientPerformance.frameSpikeBadPct", "15");
        defaults.put("clientPerformance.frameStallBadPct", "3");
        defaults.put("bpsThresholds.targetInBps", "5000000");
        defaults.put("bpsThresholds.targetOutBps", "5000000");
        defaults.put("bpsThresholds.maxBurstinessPercent", "220");
        defaults.put("bpsThresholds.severeBurstinessPercent", "320");
        defaults.put("safeguards.joinWarmupMs", "15000");
        defaults.put("safeguards.joinWarmupRenderDistanceCap", "12");
        defaults.put("safeguards.burstGuardMs", "20000");
        defaults.put("safeguards.burstGuardRenderDistanceCap", "10");
        defaults.put("safeguards.integratedHostCap", "16");
        defaults.put("safeguards.recoveryStableTicks", "4");
        defaults.put("safeguards.hostRecoveryStableTicks", "30");
        return defaults;
    }
}
