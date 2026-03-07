package com.wellnet.policy;

import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.NetworkStats;

public final class ServerSessionProfile {
    private String serverId = "";
    private boolean hostProfile;
    private long sessionStartMs;
    private long lastStableSampleMs;
    private double stableScoreEwma = -1.0;
    private double volatilityEwma = 0.0;
    private int stableSamples;
    private int roughSamples;
    private TrafficPhase lastPhase = TrafficPhase.JOIN;

    public void reset(String newServerId, boolean newHostProfile, long nowMs) {
        this.serverId = newServerId == null ? "" : newServerId;
        this.hostProfile = newHostProfile;
        this.sessionStartMs = nowMs;
        this.lastStableSampleMs = 0L;
        this.stableScoreEwma = -1.0;
        this.volatilityEwma = 0.0;
        this.stableSamples = 0;
        this.roughSamples = 0;
        this.lastPhase = newHostProfile ? TrafficPhase.HOST_LOCAL : TrafficPhase.JOIN;
    }

    public void ensureSession(String newServerId, boolean newHostProfile, long nowMs) {
        String normalizedServerId = newServerId == null ? "" : newServerId;
        if (!normalizedServerId.equals(this.serverId) || this.hostProfile != newHostProfile || this.sessionStartMs == 0L) {
            reset(normalizedServerId, newHostProfile, nowMs);
        }
    }

    public void observe(
        long nowMs,
        TrafficPhase phase,
        int blendedScore,
        NetDiagnosticsModule.WindowStats shortStats,
        NetworkStats networkStats,
        boolean framePressure,
        boolean severeBurst
    ) {
        this.lastPhase = phase == null ? TrafficPhase.SETTLING : phase;
        if (shortStats == null) {
            return;
        }

        boolean acceptableFrameWindow = networkStats == null
            || (networkStats.frameSpikeRatePercent >= 0
            && networkStats.frameSpikeRatePercent <= 10
            && networkStats.frameStallRatePercent >= 0
            && networkStats.frameStallRatePercent <= 2);
        boolean stableCandidate = !framePressure
            && !severeBurst
            && acceptableFrameWindow
            && blendedScore >= 55
            && shortStats.quality() != NetDiagnosticsModule.Quality.POOR;
        double volatilitySample = 0.0;
        if (this.stableScoreEwma >= 0.0) {
            volatilitySample = Math.max(volatilitySample, Math.min(1.0, Math.abs(blendedScore - this.stableScoreEwma) / 30.0));
        }
        if (framePressure || severeBurst) {
            volatilitySample = Math.max(volatilitySample, 1.0);
        } else if (!acceptableFrameWindow) {
            volatilitySample = Math.max(volatilitySample, 0.5);
        }
        this.volatilityEwma = 0.82 * this.volatilityEwma + 0.18 * volatilitySample;
        if (!stableCandidate) {
            this.roughSamples++;
            return;
        }

        double sample = blendedScore;
        if (this.stableScoreEwma < 0.0) {
            this.stableScoreEwma = sample;
        } else {
            this.stableScoreEwma = 0.80 * this.stableScoreEwma + 0.20 * sample;
        }
        this.stableSamples++;
        this.lastStableSampleMs = nowMs;
    }

    public boolean hasStableBaseline() {
        return this.stableSamples >= 3 && this.stableScoreEwma >= 0.0;
    }

    public int baselineScoreFloor(int fallbackScore) {
        if (!hasStableBaseline()) {
            return Math.max(50, fallbackScore);
        }
        return (int) Math.round(this.stableScoreEwma);
    }

    public int stabilityConfidencePercent(long nowMs) {
        double raw = 35.0 + this.stableSamples * 8.0 - this.roughSamples * 3.5 - this.volatilityEwma * 35.0;
        long staleMs = millisSinceLastStableSample(nowMs);
        if (staleMs > (this.hostProfile ? 120_000L : 90_000L)) {
            raw -= 15.0;
        }
        return clampPercent(raw);
    }

    public boolean shouldTrustBaseline(long nowMs) {
        return hasStableBaseline() && stabilityConfidencePercent(nowMs) >= 55;
    }

    public int baselineTolerance() {
        return this.hostProfile ? 12 : 10;
    }

    public long sessionAgeMs(long nowMs) {
        if (this.sessionStartMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowMs - this.sessionStartMs);
    }

    public long millisSinceLastStableSample(long nowMs) {
        if (this.lastStableSampleMs <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, nowMs - this.lastStableSampleMs);
    }

    public TrafficPhase lastPhase() {
        return this.lastPhase;
    }

    private int clampPercent(double value) {
        int rounded = (int) Math.round(value);
        if (rounded < 0) {
            return 0;
        }
        if (rounded > 100) {
            return 100;
        }
        return rounded;
    }
}
