package com.wellnet.policy;

import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.NetworkStats;

public final class PressureAnalyzer {
    private static final long SEVERE_PING_FLOOR_MS = 350L;
    private static final int SEVERE_FRAME_MULTIPLIER = 2;
    private static final int FALLBACK_POOR_SCORE_MAX = 35;

    public PressureSnapshot analyze(AdaptivePolicyContext context) {
        AdaptivePolicyConfig config = context.config();
        boolean framePressure = false;
        double frameSeverity = 0.0;

        double frameTimeMs = context.snapshot().frameTimeMs;
        if (!Double.isNaN(frameTimeMs) && frameTimeMs > 0.0) {
            if (config.frameStallThresholdMs() > 0 && frameTimeMs >= config.frameStallThresholdMs()) {
                framePressure = true;
                frameSeverity = 1.0;
            } else if (config.frameSpikeThresholdMs() > 0 && frameTimeMs >= config.frameSpikeThresholdMs()) {
                framePressure = true;
                double top = Math.max(config.frameStallThresholdMs(), config.frameSpikeThresholdMs());
                double range = Math.max(1.0, top - config.frameSpikeThresholdMs());
                frameSeverity = clamp01((frameTimeMs - config.frameSpikeThresholdMs()) / range);
            }
        }

        boolean trafficPressure = false;
        boolean spikePressure = false;
        boolean severeBurst = false;
        boolean fallbackPoorWindow = isShortFallbackPoor(context.shortStats());

        NetworkStats networkStats = context.networkStats();
        if (networkStats != null) {
            trafficPressure = networkStats.inboundBurstinessPercent >= config.maxBurstinessPercent()
                || networkStats.outboundBurstinessPercent >= config.maxBurstinessPercent()
                || networkStats.inboundBpsAvg >= config.targetInboundBps()
                || networkStats.outboundBpsAvg >= config.targetOutboundBps();
            spikePressure = networkStats.spikeRatePercent >= config.spikeFairMaxPct()
                || networkStats.p95PingMillis >= config.spikePingThresholdMs()
                || networkStats.p99PingMillis >= config.spikePingThresholdMs();
            boolean severeBurstByTrafficOrPing = networkStats.inboundBurstinessPercent >= config.severeBurstinessPercent()
                || networkStats.outboundBurstinessPercent >= config.severeBurstinessPercent()
                || networkStats.p99PingMillis >= Math.max(config.spikePingThresholdMs() * 2L, SEVERE_PING_FLOOR_MS);
            boolean severeBurstByFrameHistory = networkStats.frameStallRatePercent >= Math.max(1, config.frameStallBadPct() * SEVERE_FRAME_MULTIPLIER)
                || networkStats.frameSpikeRatePercent >= Math.max(1, config.frameSpikeBadPct() * SEVERE_FRAME_MULTIPLIER)
                || (networkStats.p99FrameTimeMs >= Math.max(
                    (long) config.frameStallThresholdMs(),
                    (long) Math.max(config.frameSpikeThresholdMs(), 1) * 3L
                ) && (networkStats.frameStallRatePercent >= Math.max(1, config.frameStallBadPct())
                || networkStats.frameSpikeRatePercent >= Math.max(1, config.frameSpikeBadPct())));
            severeBurst = severeBurstByTrafficOrPing
                || (severeBurstByFrameHistory && (framePressure || fallbackPoorWindow));

            if (!framePressure
                && (networkStats.frameStallRatePercent >= config.frameStallBadPct()
                || networkStats.frameSpikeRatePercent >= config.frameSpikeBadPct())) {
                framePressure = true;
                frameSeverity = Math.max(frameSeverity, 0.6);
            }
        }

        boolean severeFramePressure = framePressure && frameSeverity >= 0.85;
        String note = "pressure{frame=" + framePressure
            + ",traffic=" + trafficPressure
            + ",spike=" + spikePressure
            + ",burst=" + severeBurst
            + ",fallbackPoor=" + fallbackPoorWindow
            + "}";
        return new PressureSnapshot(
            framePressure,
            severeFramePressure,
            trafficPressure,
            spikePressure,
            severeBurst,
            fallbackPoorWindow,
            frameSeverity,
            note
        );
    }

    private boolean isShortFallbackPoor(NetDiagnosticsModule.WindowStats shortStats) {
        if (shortStats == null || shortStats.avgPingMillis() >= 0L) {
            return false;
        }
        return shortStats.quality() == NetDiagnosticsModule.Quality.POOR
            || shortStats.qualityScore() <= FALLBACK_POOR_SCORE_MAX;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
