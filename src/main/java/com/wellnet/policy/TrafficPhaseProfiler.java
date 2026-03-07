package com.wellnet.policy;

import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.NetworkStats;

public final class TrafficPhaseProfiler {
    private static final long REMOTE_SETTLING_MS = 45_000L;
    private static final long HOST_SETTLING_MS = 120_000L;
    private static final int REMOTE_RECOVERY_SCORE_FLOOR = 78;
    private static final int HOST_RECOVERY_SCORE_FLOOR = 60;

    public TrafficPhaseSnapshot analyze(
        AdaptivePolicyContext context,
        AdaptivePolicyState state,
        boolean warmupActive,
        boolean burstGuardActive,
        boolean framePressure,
        boolean severeBurst,
        int blendedScore
    ) {
        ServerSessionProfile profile = state.serverProfile();
        profile.ensureSession(context.serverId(), state.isHostProfile(), context.nowMs());

        TrafficPhase phase = resolvePhase(context, state, warmupActive, burstGuardActive, framePressure, severeBurst, profile);
        profile.observe(context.nowMs(), phase, blendedScore, context.shortStats(), context.networkStats(), framePressure, severeBurst);

        int baselineScore = profile.baselineScoreFloor(blendedScore);
        int stabilityConfidence = profile.stabilityConfidencePercent(context.nowMs());
        boolean trustedBaseline = profile.shouldTrustBaseline(context.nowMs());
        int recoveryScoreFloor = state.isHostProfile()
            ? Math.max(HOST_RECOVERY_SCORE_FLOOR, baselineScore - 8)
            : Math.max(REMOTE_RECOVERY_SCORE_FLOOR, baselineScore - 6);
        boolean fastHostRecovery = state.isHostProfile()
            && phase == TrafficPhase.STEADY
            && trustedBaseline
            && profile.millisSinceLastStableSample(context.nowMs()) <= 90_000L
            && recoveryScoreFloor >= HOST_RECOVERY_SCORE_FLOOR;

        String note = "phase=" + phase
            + " baseline=" + baselineScore
            + " recoverFloor=" + recoveryScoreFloor
            + " trust=" + trustedBaseline
            + " stable=" + stabilityConfidence;
        return new TrafficPhaseSnapshot(phase, baselineScore, recoveryScoreFloor, fastHostRecovery, trustedBaseline, stabilityConfidence, note);
    }

    private TrafficPhase resolvePhase(
        AdaptivePolicyContext context,
        AdaptivePolicyState state,
        boolean warmupActive,
        boolean burstGuardActive,
        boolean framePressure,
        boolean severeBurst,
        ServerSessionProfile profile
    ) {
        int steadyScoreFloor = Math.max(55, profile.baselineScoreFloor(context.shortStats().qualityScore()) - profile.baselineTolerance());
        boolean trustedBaseline = profile.shouldTrustBaseline(context.nowMs());
        if (burstGuardActive || severeBurst) {
            return TrafficPhase.BURST;
        }
        if (state.isHostProfile()) {
            if (framePressure) {
                return TrafficPhase.HOST_LOCAL;
            }
            if (profile.sessionAgeMs(context.nowMs()) < HOST_SETTLING_MS && !trustedBaseline) {
                return TrafficPhase.HOST_LOCAL;
            }
            return isHealthySteadyWindow(context, steadyScoreFloor, true) ? TrafficPhase.STEADY : TrafficPhase.HOST_LOCAL;
        }
        if (warmupActive) {
            return TrafficPhase.JOIN;
        }
        if (framePressure) {
            return TrafficPhase.SETTLING;
        }
        if (profile.sessionAgeMs(context.nowMs()) < REMOTE_SETTLING_MS && !trustedBaseline) {
            return TrafficPhase.SETTLING;
        }
        if (isHealthySteadyWindow(context, steadyScoreFloor, false)) {
            return TrafficPhase.STEADY;
        }
        if (trustedBaseline && profile.millisSinceLastStableSample(context.nowMs()) <= 90_000L) {
            return TrafficPhase.STEADY;
        }
        return TrafficPhase.SETTLING;
    }

    private boolean isHealthySteadyWindow(AdaptivePolicyContext context, int steadyScoreFloor, boolean hostProfile) {
        if (context.shortStats() == null) {
            return false;
        }

        NetDiagnosticsModule.WindowStats shortStats = context.shortStats();
        if (shortStats.avgPingMillis() >= 0L) {
            return shortStats.quality() != NetDiagnosticsModule.Quality.POOR
                && shortStats.qualityScore() >= Math.max(65, steadyScoreFloor);
        }

        NetworkStats networkStats = context.networkStats();
        if (networkStats == null) {
            return false;
        }

        int fallbackFloor = hostProfile ? Math.max(55, steadyScoreFloor - 4) : Math.max(58, steadyScoreFloor);
        return shortStats.quality() != NetDiagnosticsModule.Quality.POOR
            && shortStats.qualityScore() >= fallbackFloor
            && networkStats.frameSpikeRatePercent >= 0
            && networkStats.frameSpikeRatePercent <= (hostProfile ? 10 : 8)
            && networkStats.frameStallRatePercent >= 0
            && networkStats.frameStallRatePercent <= (hostProfile ? 2 : 1);
    }
}
