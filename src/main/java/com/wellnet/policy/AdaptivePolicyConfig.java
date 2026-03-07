package com.wellnet.policy;

import com.wellnet.config.WellNetConfig;

public record AdaptivePolicyConfig(
    int minRenderDistance,
    int maxRenderDistance,
    long decisionIntervalMs,
    long afkTimeoutMs,
    int afkMaxRenderDistance,
    long targetInboundBps,
    long targetOutboundBps,
    int maxBurstinessPercent,
    int severeBurstinessPercent,
    long joinWarmupMs,
    int joinWarmupRenderDistanceCap,
    long burstGuardMs,
    int burstGuardRenderDistanceCap,
    int hostRenderDistanceCap,
    int recoveryStableTicks,
    int hostRecoveryStableTicks,
    int frameSpikeThresholdMs,
    int frameStallThresholdMs,
    int frameSpikeBadPct,
    int frameStallBadPct,
    int spikeFairMaxPct,
    int spikePingThresholdMs
) {
    public static AdaptivePolicyConfig current() {
        return new AdaptivePolicyConfig(
            WellNetConfig.renderDistanceMin(),
            WellNetConfig.renderDistanceMax(),
            Math.max(250L, WellNetConfig.decisionIntervalMs()),
            WellNetConfig.afkTimeoutMs(),
            WellNetConfig.afkMaxRenderDistance(),
            WellNetConfig.targetInboundBps(),
            WellNetConfig.targetOutboundBps(),
            WellNetConfig.maxBurstinessPercent(),
            WellNetConfig.severeBurstinessPercent(),
            WellNetConfig.joinWarmupMs(),
            WellNetConfig.joinWarmupRenderDistanceCap(),
            WellNetConfig.burstGuardMs(),
            WellNetConfig.burstGuardRenderDistanceCap(),
            WellNetConfig.hostRenderDistanceCap(),
            WellNetConfig.recoveryStableTicks(),
            WellNetConfig.hostRecoveryStableTicks(),
            WellNetConfig.frameSpikeThresholdMs(),
            WellNetConfig.frameStallThresholdMs(),
            WellNetConfig.frameSpikeBadPct(),
            WellNetConfig.frameStallBadPct(),
            WellNetConfig.spikeFairMaxPct(),
            WellNetConfig.spikePingThresholdMs()
        );
    }
}
