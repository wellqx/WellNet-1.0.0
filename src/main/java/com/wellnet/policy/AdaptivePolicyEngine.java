package com.wellnet.policy;

import com.wellnet.core.ActionPlan;
import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.NetworkStats;
import java.util.Objects;

public final class AdaptivePolicyEngine {
    private static final int BAD_TICKS_THRESHOLD = 3;
    private static final double AFK_MOVE_EPSILON = 0.05;
    private static final long MIN_BURST_GUARD_INTERVAL_MS = 1500L;
    private static final long HOST_STARTUP_SETTLE_MS = 75000L;
    private static final int HOST_FALLBACK_RECOVERY_SCORE_MIN = 60;
    private static final int HOST_FALLBACK_SHORT_SCORE_MIN = 55;
    private static final int HOST_FALLBACK_RECOVERY_SPIKE_MAX_PCT = 10;
    private static final int FALLBACK_DEGRADE_SCORE_MAX = 44;

    private final PressureAnalyzer pressureAnalyzer = new PressureAnalyzer();
    private final TrafficPhaseProfiler trafficPhaseProfiler = new TrafficPhaseProfiler();
    private final SpikeDampener spikeDampener = new SpikeDampener();
    private final QualityTrendAnalyzer qualityTrendAnalyzer = new QualityTrendAnalyzer();

    public AdaptivePolicyDecision evaluate(AdaptivePolicyContext context, AdaptivePolicyState state) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");

        AdaptivePolicyConfig config = context.config();
        state.refreshSession(context.serverId(), context.snapshot().isIntegratedHost, context.nowMs());
        state.updateActivity(context.snapshot().distanceMoved, context.nowMs(), config.afkTimeoutMs(), AFK_MOVE_EPSILON);

        PressureSnapshot pressure = this.pressureAnalyzer.analyze(context);
        boolean hostStartupSettleActive = state.isHostProfile() && state.sessionAgeMs(context.nowMs()) < HOST_STARTUP_SETTLE_MS;
        if ((pressure.severeFramePressure() || pressure.severeBurst())
            && (!hostStartupSettleActive || pressure.trafficPressure() || pressure.spikePressure())) {
            state.extendBurstGuardUntil(context.nowMs() + config.burstGuardMs());
        }

        boolean warmupActive = !state.isHostProfile() && state.isWarmupActive(context.nowMs(), config.joinWarmupMs());
        boolean burstGuardActive = config.burstGuardMs() > 0L && state.isBurstGuardActive(context.nowMs());
        int currentRenderDistance = context.snapshot().currentRenderDistance > 0 ? context.snapshot().currentRenderDistance : 12;
        int blendedScore = blendScore(context.shortStats(), context.mediumStats());
        TrafficPhaseSnapshot phaseSnapshot = this.trafficPhaseProfiler.analyze(
            context,
            state,
            warmupActive,
            burstGuardActive,
            pressure.framePressure(),
            pressure.severeBurst(),
            blendedScore
        );
        QualityTrendAnalyzer.Snapshot trendSnapshot = this.qualityTrendAnalyzer.analyze(context, phaseSnapshot, pressure, blendedScore);
        SpikeDampener.Snapshot dampener = this.spikeDampener.analyze(
            context,
            phaseSnapshot,
            burstGuardActive,
            pressure.framePressure(),
            pressure.severeFramePressure(),
            pressure.severeBurst()
        );
        int decisionScore = trendSnapshot.consensusScore();
        String evaluationNote = phaseSnapshot.note() + " " + trendSnapshot.note() + " " + pressure.note() + " " + dampener.note();
        state.markEvaluation(
            phaseSnapshot.phase(),
            trendSnapshot.trend(),
            evaluationNote,
            decisionScore,
            context.shortStats().quality()
        );
        if (dampener.freezeUntilMs() > 0L) {
            state.freezeDecisionsUntil(dampener.freezeUntilMs());
        }

        boolean hostSafeguardActive = isHostSafeguardActive(state, pressure, burstGuardActive, hostStartupSettleActive);
        int hardCap = deriveHardCap(config, state, warmupActive, burstGuardActive, hostSafeguardActive);

        boolean badCondition = decisionScore <= 35
            || context.shortStats().quality() == NetDiagnosticsModule.Quality.POOR
            || pressure.framePressure()
            || burstGuardActive
            || pressure.severeBurst()
            || dampener.escalateBadCondition()
            || trendSnapshot.badBias();
        boolean excellentCondition = (decisionScore >= 80
            && context.shortStats().quality() == NetDiagnosticsModule.Quality.EXCELLENT
            && !pressure.framePressure()
            && !warmupActive
            && !burstGuardActive)
            || dampener.recoveryBoost()
            || trendSnapshot.recoveryBias()
            || isHostFallbackRecoveryCandidate(
                context,
                state,
                pressure,
                Math.max(decisionScore, phaseSnapshot.recoveryScoreFloor()),
                warmupActive,
                burstGuardActive
            );

        if (badCondition) {
            state.markBadCondition();
        } else if (excellentCondition) {
            state.markExcellentCondition();
        } else {
            state.decayTrendCounters();
        }

        long minChangeIntervalMs = burstGuardActive
            ? Math.min(config.decisionIntervalMs(), MIN_BURST_GUARD_INTERVAL_MS)
            : config.decisionIntervalMs();
        if (state.shouldDeferDecision(context.nowMs(), minChangeIntervalMs)) {
            return AdaptivePolicyDecision.none();
        }
        if (state.isDecisionFreezeActive(context.nowMs()) && currentRenderDistance <= hardCap) {
            return AdaptivePolicyDecision.none();
        }

        AdaptivePolicyDecision decision = maybeEnforceHardCap(
            context,
            state,
            pressure,
            phaseSnapshot,
            warmupActive,
            burstGuardActive,
            hostSafeguardActive,
            currentRenderDistance,
            hardCap,
            decisionScore,
            trendSnapshot
        );
        if (decision.hasChange()) {
            state.markDecisionApplied(context.nowMs());
            return decision;
        }

        if (state.badTicks() >= BAD_TICKS_THRESHOLD) {
            state.clearBadTicks();
            decision = degrade(
                context,
                state,
                pressure,
                phaseSnapshot,
                burstGuardActive,
                hostSafeguardActive,
                currentRenderDistance,
                hardCap,
                decisionScore,
                trendSnapshot
            );
            if (decision.hasChange()) {
                state.markDecisionApplied(context.nowMs());
                return decision;
            }
        }

        int recoveryStableTicks = state.isHostProfile()
            ? (phaseSnapshot.fastHostRecovery() ? 12 : Math.max(config.hostRecoveryStableTicks(), 30))
            : config.recoveryStableTicks();
        recoveryStableTicks = Math.max(2, recoveryStableTicks + trendSnapshot.recoveryTickAdjustment());
        if (state.goodTicks() >= recoveryStableTicks && !warmupActive && !burstGuardActive) {
            state.clearGoodTicks();
            decision = recover(
                context,
                phaseSnapshot,
                currentRenderDistance,
                hardCap,
                decisionScore,
                recoveryStableTicks,
                trendSnapshot
            );
            if (decision.hasChange()) {
                state.markDecisionApplied(context.nowMs());
                return decision;
            }
        }

        return AdaptivePolicyDecision.none();
    }

    private AdaptivePolicyDecision maybeEnforceHardCap(
        AdaptivePolicyContext context,
        AdaptivePolicyState state,
        PressureSnapshot pressure,
        TrafficPhaseSnapshot phaseSnapshot,
        boolean warmupActive,
        boolean burstGuardActive,
        boolean hostSafeguardActive,
        int currentRenderDistance,
        int hardCap,
        int decisionScore,
        QualityTrendAnalyzer.Snapshot trendSnapshot
    ) {
        if (currentRenderDistance <= hardCap) {
            return AdaptivePolicyDecision.none();
        }

        int stepDown = shouldUseAggressiveStepDown(state, pressure, burstGuardActive, trendSnapshot) ? 2 : 1;
        int targetRenderDistance = Math.max(context.config().minRenderDistance(), currentRenderDistance - stepDown);
        targetRenderDistance = Math.min(targetRenderDistance, hardCap);

        ActionPlan.Reason reason = chooseHardCapReason(state, warmupActive, burstGuardActive, hostSafeguardActive);
        double confidence = chooseHardCapConfidence(reason);
        String note = "hard-cap rd=" + hardCap
            + " score=" + decisionScore
            + " q=" + context.shortStats().quality()
            + " " + phaseSnapshot.note()
            + " " + trendSnapshot.note();
        return new AdaptivePolicyDecision(targetRenderDistance, reason, confidence, note);
    }

    private AdaptivePolicyDecision degrade(
        AdaptivePolicyContext context,
        AdaptivePolicyState state,
        PressureSnapshot pressure,
        TrafficPhaseSnapshot phaseSnapshot,
        boolean burstGuardActive,
        boolean hostSafeguardActive,
        int currentRenderDistance,
        int hardCap,
        int decisionScore,
        QualityTrendAnalyzer.Snapshot trendSnapshot
    ) {
        if (state.isHostProfile() && !hostSafeguardActive && !burstGuardActive) {
            return AdaptivePolicyDecision.none();
        }
        if ((burstGuardActive || hostSafeguardActive) && currentRenderDistance <= hardCap) {
            return AdaptivePolicyDecision.none();
        }
        if (isSoftFallbackWindow(context, pressure, burstGuardActive, hostSafeguardActive, decisionScore)) {
            state.clearBadTicks();
            return AdaptivePolicyDecision.none();
        }

        int stepDown = shouldUseAggressiveStepDown(state, pressure, burstGuardActive, trendSnapshot) ? 2 : 1;
        int lowerBound = context.config().minRenderDistance();
        if (burstGuardActive || hostSafeguardActive) {
            lowerBound = Math.max(lowerBound, hardCap);
        }
        int targetRenderDistance = Math.max(lowerBound, currentRenderDistance - stepDown);
        if (targetRenderDistance == currentRenderDistance) {
            return AdaptivePolicyDecision.none();
        }

        ActionPlan.Reason reason = chooseDegradeReason(
            burstGuardActive,
            state.isHostProfile(),
            pressure.framePressure(),
            pressure.spikePressure(),
            pressure.trafficPressure()
        );
        double confidence = chooseDegradeConfidence(reason, decisionScore, pressure.frameSeverity(), trendSnapshot);
        String note = "degrade score=" + decisionScore
            + " q=" + context.shortStats().quality()
            + " burstGuard=" + burstGuardActive
            + " " + phaseSnapshot.note()
            + " " + trendSnapshot.note();
        return new AdaptivePolicyDecision(targetRenderDistance, reason, confidence, note);
    }

    private AdaptivePolicyDecision recover(
        AdaptivePolicyContext context,
        TrafficPhaseSnapshot phaseSnapshot,
        int currentRenderDistance,
        int hardCap,
        int decisionScore,
        int recoveryStableTicks,
        QualityTrendAnalyzer.Snapshot trendSnapshot
    ) {
        int targetRenderDistance = Math.min(hardCap, currentRenderDistance + 1);
        if (targetRenderDistance == currentRenderDistance) {
            return AdaptivePolicyDecision.none();
        }

        double confidence = clamp01(decisionScore / 100.0);
        String note = "recover score=" + decisionScore
            + " q=" + context.shortStats().quality()
            + " stableTicks=" + recoveryStableTicks
            + " " + phaseSnapshot.note()
            + " " + trendSnapshot.note();
        return new AdaptivePolicyDecision(targetRenderDistance, ActionPlan.Reason.RECOVERY, confidence, note);
    }

    private int blendScore(NetDiagnosticsModule.WindowStats shortStats, NetDiagnosticsModule.WindowStats mediumStats) {
        return (int) Math.round(0.65 * shortStats.qualityScore() + 0.35 * mediumStats.qualityScore());
    }

    private int deriveHardCap(
        AdaptivePolicyConfig config,
        AdaptivePolicyState state,
        boolean warmupActive,
        boolean burstGuardActive,
        boolean hostSafeguardActive
    ) {
        int hardCap = config.maxRenderDistance();
        if (hostSafeguardActive) {
            hardCap = Math.min(hardCap, config.hostRenderDistanceCap());
        }
        if (state.isAfk()) {
            hardCap = Math.min(hardCap, config.afkMaxRenderDistance());
        }
        if (warmupActive) {
            hardCap = Math.min(hardCap, config.joinWarmupRenderDistanceCap());
        }
        if (burstGuardActive) {
            int burstCap = config.burstGuardRenderDistanceCap();
            if (state.isHostProfile()) {
                burstCap = Math.max(burstCap, config.hostRenderDistanceCap());
            }
            if (warmupActive) {
                burstCap = Math.max(burstCap, config.joinWarmupRenderDistanceCap());
            }
            hardCap = Math.min(hardCap, burstCap);
        }
        return Math.max(config.minRenderDistance(), hardCap);
    }

    private boolean isHostFallbackRecoveryCandidate(
        AdaptivePolicyContext context,
        AdaptivePolicyState state,
        PressureSnapshot pressure,
        int blendedScore,
        boolean warmupActive,
        boolean burstGuardActive
    ) {
        if (!state.isHostProfile() || warmupActive || burstGuardActive || pressure.framePressure()) {
            return false;
        }

        NetDiagnosticsModule.WindowStats shortStats = context.shortStats();
        if (shortStats == null || shortStats.avgPingMillis() >= 0L) {
            return false;
        }
        if (blendedScore < HOST_FALLBACK_RECOVERY_SCORE_MIN || shortStats.qualityScore() < HOST_FALLBACK_SHORT_SCORE_MIN) {
            return false;
        }

        NetworkStats networkStats = context.networkStats();
        if (networkStats == null) {
            return false;
        }

        int maxAllowedSpikes = Math.max(HOST_FALLBACK_RECOVERY_SPIKE_MAX_PCT, context.config().frameSpikeBadPct());
        int maxAllowedStalls = Math.max(1, context.config().frameStallBadPct());
        long maxAllowedP95FrameMs = Math.max(context.config().frameSpikeThresholdMs(), 90L);
        return networkStats.frameSpikeRatePercent >= 0
            && networkStats.frameSpikeRatePercent <= maxAllowedSpikes
            && networkStats.frameStallRatePercent >= 0
            && networkStats.frameStallRatePercent <= maxAllowedStalls
            && networkStats.p95FrameTimeMs > 0L
            && networkStats.p95FrameTimeMs <= maxAllowedP95FrameMs;
    }

    private boolean isHostSafeguardActive(
        AdaptivePolicyState state,
        PressureSnapshot pressure,
        boolean burstGuardActive,
        boolean hostStartupSettleActive
    ) {
        return state.isHostProfile()
            && !hostStartupSettleActive
            && (burstGuardActive
            || pressure.severeFramePressure()
            || pressure.severeBurst()
            || pressure.trafficPressure());
    }

    private boolean isSoftFallbackWindow(
        AdaptivePolicyContext context,
        PressureSnapshot pressure,
        boolean burstGuardActive,
        boolean hostSafeguardActive,
        int decisionScore
    ) {
        if (burstGuardActive || hostSafeguardActive || decisionScore <= FALLBACK_DEGRADE_SCORE_MAX) {
            return false;
        }
        if (context.shortStats() == null || context.mediumStats() == null) {
            return false;
        }
        boolean missingPingMetrics = context.shortStats().avgPingMillis() < 0L
            && context.mediumStats().avgPingMillis() < 0L;
        return missingPingMetrics
            && !pressure.framePressure()
            && !pressure.trafficPressure()
            && !pressure.spikePressure()
            && !pressure.severeBurst();
    }

    private boolean shouldUseAggressiveStepDown(
        AdaptivePolicyState state,
        PressureSnapshot pressure,
        boolean burstGuardActive,
        QualityTrendAnalyzer.Snapshot trendSnapshot
    ) {
        return burstGuardActive
            || pressure.severeFramePressure()
            || trendSnapshot.aggressiveDegrade()
            || (state.isHostProfile() && (pressure.framePressure() || pressure.trafficPressure()));
    }

    private ActionPlan.Reason chooseHardCapReason(
        AdaptivePolicyState state,
        boolean warmupActive,
        boolean burstGuardActive,
        boolean hostSafeguardActive
    ) {
        if (burstGuardActive) {
            return state.isHostProfile() ? ActionPlan.Reason.HOST_PRESSURE : ActionPlan.Reason.BURST_GUARD;
        }
        if (warmupActive) {
            return ActionPlan.Reason.JOIN_WARMUP;
        }
        if (state.isAfk()) {
            return ActionPlan.Reason.AFK;
        }
        if (hostSafeguardActive) {
            return ActionPlan.Reason.HOST_PRESSURE;
        }
        return ActionPlan.Reason.POOR_NET;
    }

    private double chooseHardCapConfidence(ActionPlan.Reason reason) {
        return switch (reason) {
            case BURST_GUARD -> 0.92;
            case HOST_PRESSURE -> 0.85;
            case JOIN_WARMUP -> 0.82;
            case AFK -> 0.90;
            default -> 0.72;
        };
    }

    private ActionPlan.Reason chooseDegradeReason(
        boolean burstGuardActive,
        boolean hostProfile,
        boolean framePressure,
        boolean spikePressure,
        boolean trafficPressure
    ) {
        if (burstGuardActive) {
            return hostProfile ? ActionPlan.Reason.HOST_PRESSURE : ActionPlan.Reason.BURST_GUARD;
        }
        if (hostProfile && (framePressure || trafficPressure)) {
            return ActionPlan.Reason.HOST_PRESSURE;
        }
        if (framePressure) {
            return ActionPlan.Reason.CLIENT_LAG;
        }
        if (spikePressure) {
            return ActionPlan.Reason.SPIKES;
        }
        if (trafficPressure) {
            return ActionPlan.Reason.HIGH_BPS;
        }
        return ActionPlan.Reason.POOR_NET;
    }

    private double chooseDegradeConfidence(
        ActionPlan.Reason reason,
        int score,
        double frameSeverity,
        QualityTrendAnalyzer.Snapshot trendSnapshot
    ) {
        double confidence = switch (reason) {
            case BURST_GUARD -> 0.88;
            case HOST_PRESSURE -> 0.82;
            case CLIENT_LAG -> 0.72 + 0.20 * clamp01(frameSeverity);
            case SPIKES -> 0.80;
            case HIGH_BPS -> 0.75;
            default -> 0.65;
        };
        if (trendSnapshot.trend() == PolicyTrend.WORSENING) {
            confidence += 0.08;
        } else if (trendSnapshot.trend() == PolicyTrend.VOLATILE) {
            confidence += 0.04;
        }
        confidence += clamp01((50.0 - score) / 100.0);
        return clamp01(confidence);
    }

    private double clamp01(double value) {
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
