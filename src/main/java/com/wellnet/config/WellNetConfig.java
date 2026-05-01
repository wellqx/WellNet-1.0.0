package com.wellnet.config;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class WellNetConfig {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue DEBUG;
    private static final ForgeConfigSpec.IntValue SNAPSHOT_INTERVAL_TICKS;
    private static final ForgeConfigSpec.LongValue DECISION_INTERVAL_MS;
    private static final ForgeConfigSpec.IntValue RENDER_DISTANCE_MIN;
    private static final ForgeConfigSpec.IntValue RENDER_DISTANCE_MAX;
    private static final ForgeConfigSpec.LongValue RENDER_DISTANCE_COOLDOWN_MS;
    private static final ForgeConfigSpec.BooleanValue RESTORE_ON_EXIT;
    private static final ForgeConfigSpec.LongValue AFK_TIMEOUT_MS;
    private static final ForgeConfigSpec.IntValue AFK_MAX_RENDER_DISTANCE;
    private static final ForgeConfigSpec.IntValue PING_EXCELLENT_MAX_MS;
    private static final ForgeConfigSpec.IntValue PING_GOOD_MAX_MS;
    private static final ForgeConfigSpec.IntValue PING_FAIR_MAX_MS;
    private static final ForgeConfigSpec.IntValue JITTER_EXCELLENT_MAX_MS;
    private static final ForgeConfigSpec.IntValue JITTER_GOOD_MAX_MS;
    private static final ForgeConfigSpec.IntValue JITTER_FAIR_MAX_MS;
    private static final ForgeConfigSpec.IntValue SPIKE_EXCELLENT_MAX_PCT;
    private static final ForgeConfigSpec.IntValue SPIKE_GOOD_MAX_PCT;
    private static final ForgeConfigSpec.IntValue SPIKE_FAIR_MAX_PCT;
    private static final ForgeConfigSpec.IntValue SPIKE_PING_THRESHOLD_MS;
    private static final ForgeConfigSpec.IntValue SPIKE_DELTA_THRESHOLD_MS;
    private static final ForgeConfigSpec.IntValue LATE_THRESHOLD_MS;
    private static final ForgeConfigSpec.IntValue FRAME_SPIKE_THRESHOLD_MS;
    private static final ForgeConfigSpec.IntValue FRAME_STALL_THRESHOLD_MS;
    private static final ForgeConfigSpec.IntValue FRAME_SPIKE_BAD_PCT;
    private static final ForgeConfigSpec.IntValue FRAME_STALL_BAD_PCT;
    private static final ForgeConfigSpec.LongValue TARGET_INBOUND_BPS;
    private static final ForgeConfigSpec.LongValue TARGET_OUTBOUND_BPS;
    private static final ForgeConfigSpec.IntValue MAX_BURSTINESS_PERCENT;
    private static final ForgeConfigSpec.IntValue SEVERE_BURSTINESS_PERCENT;
    private static final ForgeConfigSpec.LongValue JOIN_WARMUP_MS;
    private static final ForgeConfigSpec.IntValue JOIN_WARMUP_RENDER_DISTANCE_CAP;
    private static final ForgeConfigSpec.LongValue BURST_GUARD_MS;
    private static final ForgeConfigSpec.IntValue BURST_GUARD_RENDER_DISTANCE_CAP;
    private static final ForgeConfigSpec.IntValue HOST_RENDER_DISTANCE_CAP;
    private static final ForgeConfigSpec.IntValue RECOVERY_STABLE_TICKS;
    private static final ForgeConfigSpec.IntValue HOST_RECOVERY_STABLE_TICKS;

    private static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("general");
        ENABLED = BUILDER.comment("Master toggle for WellNet adaptive safeguards.")
            .define("enabled", true);
        DEBUG = BUILDER.comment("Enables periodic status logging from the core.")
            .define("debug", false);
        BUILDER.pop();

        BUILDER.push("sampling");
        SNAPSHOT_INTERVAL_TICKS = BUILDER.comment("How often the client state snapshot is sampled.")
            .defineInRange("snapshotIntervalTicks", 20, 1, 200);
        DECISION_INTERVAL_MS = BUILDER.comment("Minimum time between adaptive decisions.")
            .defineInRange("decisionIntervalMs", 5000L, 250L, 60000L);
        BUILDER.pop();

        BUILDER.push("renderDistance");
        RENDER_DISTANCE_MIN = BUILDER.comment("Lower bound for adaptive render distance.")
            .defineInRange("min", 4, 2, 32);
        RENDER_DISTANCE_MAX = BUILDER.comment("Upper bound for adaptive render distance.")
            .defineInRange("max", 32, 2, 32);
        RENDER_DISTANCE_COOLDOWN_MS = BUILDER.comment("Cooldown between non-emergency render-distance changes.")
            .defineInRange("cooldownMs", 30000L, 0L, 600000L);
        RESTORE_ON_EXIT = BUILDER.comment("Restore the original render distance on disconnect.")
            .define("restoreOnExit", true);
        AFK_TIMEOUT_MS = BUILDER.comment("How long the player must stay mostly idle before AFK capping starts.")
            .defineInRange("afkTimeoutMs", 60000L, 5000L, 3600000L);
        AFK_MAX_RENDER_DISTANCE = BUILDER.comment("Maximum render distance while the player is AFK.")
            .defineInRange("afkMaxRD", 10, 2, 24);
        BUILDER.pop();

        BUILDER.push("qualityThresholds");
        PING_EXCELLENT_MAX_MS = BUILDER.defineInRange("pingExcellentMaxMs", 80, 1, 5000);
        PING_GOOD_MAX_MS = BUILDER.defineInRange("pingGoodMaxMs", 140, 1, 10000);
        PING_FAIR_MAX_MS = BUILDER.defineInRange("pingFairMaxMs", 220, 1, 20000);
        JITTER_EXCELLENT_MAX_MS = BUILDER.defineInRange("jitterExcellentMaxMs", 12, 0, 5000);
        JITTER_GOOD_MAX_MS = BUILDER.defineInRange("jitterGoodMaxMs", 25, 0, 10000);
        JITTER_FAIR_MAX_MS = BUILDER.defineInRange("jitterFairMaxMs", 45, 0, 20000);
        SPIKE_EXCELLENT_MAX_PCT = BUILDER.defineInRange("spikeExcellentMaxPct", 3, 0, 100);
        SPIKE_GOOD_MAX_PCT = BUILDER.defineInRange("spikeGoodMaxPct", 8, 0, 100);
        SPIKE_FAIR_MAX_PCT = BUILDER.defineInRange("spikeFairMaxPct", 15, 0, 100);
        SPIKE_PING_THRESHOLD_MS = BUILDER.defineInRange("spikePingThresholdMs", 250, 1, 20000);
        SPIKE_DELTA_THRESHOLD_MS = BUILDER.defineInRange("spikeDeltaThresholdMs", 80, 0, 20000);
        LATE_THRESHOLD_MS = BUILDER.defineInRange("lateThresholdMs", 250, 1, 20000);
        BUILDER.pop();

        BUILDER.push("clientPerformance");
        FRAME_SPIKE_THRESHOLD_MS = BUILDER.defineInRange("frameSpikeThresholdMs", 80, 0, 10000);
        FRAME_STALL_THRESHOLD_MS = BUILDER.defineInRange("frameStallThresholdMs", 200, 0, 60000);
        FRAME_SPIKE_BAD_PCT = BUILDER.defineInRange("frameSpikeBadPct", 15, 0, 100);
        FRAME_STALL_BAD_PCT = BUILDER.defineInRange("frameStallBadPct", 3, 0, 100);
        BUILDER.pop();

        BUILDER.push("bpsThresholds");
        TARGET_INBOUND_BPS = BUILDER.comment("Target inbound traffic level in bits per second.")
            .defineInRange("targetInBps", 5_000_000L, 0L, 1_000_000_000L);
        TARGET_OUTBOUND_BPS = BUILDER.comment("Target outbound traffic level in bits per second.")
            .defineInRange("targetOutBps", 5_000_000L, 0L, 1_000_000_000L);
        MAX_BURSTINESS_PERCENT = BUILDER.comment("Burstiness level that starts pressure handling.")
            .defineInRange("maxBurstinessPercent", 220, 0, 2000);
        SEVERE_BURSTINESS_PERCENT = BUILDER.comment("Burstiness level that arms the severe burst guard.")
            .defineInRange("severeBurstinessPercent", 320, 0, 2000);
        BUILDER.pop();

        BUILDER.push("safeguards");
        JOIN_WARMUP_MS = BUILDER.comment("Duration of the conservative join warmup.")
            .defineInRange("joinWarmupMs", 15000L, 0L, 300000L);
        JOIN_WARMUP_RENDER_DISTANCE_CAP = BUILDER.comment("Render-distance cap during join warmup.")
            .defineInRange("joinWarmupRenderDistanceCap", 12, 2, 24);
        BURST_GUARD_MS = BUILDER.comment("How long severe burst pressure keeps the policy in safe mode.")
            .defineInRange("burstGuardMs", 20000L, 0L, 300000L);
        BURST_GUARD_RENDER_DISTANCE_CAP = BUILDER.comment("Render-distance cap while burst guard is active.")
            .defineInRange("burstGuardRenderDistanceCap", 10, 2, 24);
        HOST_RENDER_DISTANCE_CAP = BUILDER.comment("Hard cap used while the player hosts an integrated server.")
            .defineInRange("integratedHostCap", 16, 2, 24);
        RECOVERY_STABLE_TICKS = BUILDER.comment("How many strong policy ticks are needed before recovering render distance.")
            .defineInRange("recoveryStableTicks", 4, 1, 32);
        HOST_RECOVERY_STABLE_TICKS = BUILDER.comment("Recovery ticks required while hosting an integrated server.")
            .defineInRange("hostRecoveryStableTicks", 30, 1, 120);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private WellNetConfig() {
    }

    @SuppressWarnings("removal")
    public static void init() {
        if (INITIALIZED.compareAndSet(false, true)) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "wellnet-client.toml");
        }
    }

    public static void initIfNeeded() {
        if (!INITIALIZED.get()) {
            init();
        }
    }

    public static ForgeConfigSpec specOrNull() {
        return SPEC;
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static boolean debug() {
        return DEBUG.get();
    }

    public static int snapshotIntervalTicks() {
        return SNAPSHOT_INTERVAL_TICKS.get();
    }

    public static long decisionIntervalMs() {
        return DECISION_INTERVAL_MS.get();
    }

    public static int renderDistanceMin() {
        return Math.min(RENDER_DISTANCE_MIN.get(), RENDER_DISTANCE_MAX.get());
    }

    public static int renderDistanceMax() {
        return Math.max(RENDER_DISTANCE_MIN.get(), RENDER_DISTANCE_MAX.get());
    }

    public static long renderDistanceCooldownMs() {
        return RENDER_DISTANCE_COOLDOWN_MS.get();
    }

    public static boolean restoreOnExit() {
        return RESTORE_ON_EXIT.get();
    }

    public static long afkTimeoutMs() {
        return AFK_TIMEOUT_MS.get();
    }

    public static int afkMaxRenderDistance() {
        return clampInt(AFK_MAX_RENDER_DISTANCE.get(), renderDistanceMin(), renderDistanceMax());
    }

    public static int pingExcellentMaxMs() {
        return PING_EXCELLENT_MAX_MS.get();
    }

    public static int pingGoodMaxMs() {
        return PING_GOOD_MAX_MS.get();
    }

    public static int pingFairMaxMs() {
        return PING_FAIR_MAX_MS.get();
    }

    public static int jitterExcellentMaxMs() {
        return JITTER_EXCELLENT_MAX_MS.get();
    }

    public static int jitterGoodMaxMs() {
        return JITTER_GOOD_MAX_MS.get();
    }

    public static int jitterFairMaxMs() {
        return JITTER_FAIR_MAX_MS.get();
    }

    public static int spikeExcellentMaxPct() {
        return SPIKE_EXCELLENT_MAX_PCT.get();
    }

    public static int spikeGoodMaxPct() {
        return SPIKE_GOOD_MAX_PCT.get();
    }

    public static int spikeFairMaxPct() {
        return SPIKE_FAIR_MAX_PCT.get();
    }

    public static int spikePingThresholdMs() {
        return SPIKE_PING_THRESHOLD_MS.get();
    }

    public static int spikeDeltaThresholdMs() {
        return SPIKE_DELTA_THRESHOLD_MS.get();
    }

    public static int lateThresholdMs() {
        return LATE_THRESHOLD_MS.get();
    }

    public static int frameSpikeThresholdMs() {
        return FRAME_SPIKE_THRESHOLD_MS.get();
    }

    public static int frameStallThresholdMs() {
        return FRAME_STALL_THRESHOLD_MS.get();
    }

    public static int frameSpikeBadPct() {
        return FRAME_SPIKE_BAD_PCT.get();
    }

    public static int frameStallBadPct() {
        return FRAME_STALL_BAD_PCT.get();
    }

    public static long targetInboundBps() {
        return TARGET_INBOUND_BPS.get();
    }

    public static long targetOutboundBps() {
        return TARGET_OUTBOUND_BPS.get();
    }

    public static int maxBurstinessPercent() {
        return MAX_BURSTINESS_PERCENT.get();
    }

    public static int severeBurstinessPercent() {
        return Math.max(maxBurstinessPercent(), SEVERE_BURSTINESS_PERCENT.get());
    }

    public static long joinWarmupMs() {
        return JOIN_WARMUP_MS.get();
    }

    public static int joinWarmupRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 12));
        return clampInt(JOIN_WARMUP_RENDER_DISTANCE_CAP.get(), minCap, renderDistanceMax());
    }

    public static long burstGuardMs() {
        return BURST_GUARD_MS.get();
    }

    public static int burstGuardRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 10));
        return clampInt(BURST_GUARD_RENDER_DISTANCE_CAP.get(), minCap, renderDistanceMax());
    }

    public static int hostRenderDistanceCap() {
        int minCap = Math.max(renderDistanceMin(), Math.min(renderDistanceMax(), 16));
        return clampInt(HOST_RENDER_DISTANCE_CAP.get(), minCap, renderDistanceMax());
    }

    public static int recoveryStableTicks() {
        return RECOVERY_STABLE_TICKS.get();
    }

    public static int hostRecoveryStableTicks() {
        return Math.max(30, HOST_RECOVERY_STABLE_TICKS.get());
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
}
