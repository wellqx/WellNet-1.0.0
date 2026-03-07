package com.wellnet.policy;

import com.wellnet.core.NetDiagnosticsModule;

public final class QualityTrendAnalyzer {
    private static final int IMPROVING_DELTA_MIN = 10;
    private static final int WORSENING_DELTA_MIN = -14;
    private static final int VOLATILE_DELTA_MIN = 18;

    public Snapshot analyze(
        AdaptivePolicyContext context,
        TrafficPhaseSnapshot phaseSnapshot,
        PressureSnapshot pressure,
        int blendedScore
    ) {
        int shortScore = context.shortStats().qualityScore();
        int mediumScore = context.mediumStats().qualityScore();
        int delta = shortScore - mediumScore;
        int baselineDelta = shortScore - phaseSnapshot.baselineScore();
        boolean shortReliable = context.shortStats().quality() != NetDiagnosticsModule.Quality.POOR;
        boolean roughSignals = pressure.framePressure() || pressure.trafficPressure() || pressure.spikePressure();
        boolean recoveryReady = !pressure.framePressure()
            && !pressure.severeBurst()
            && shortReliable
            && shortScore >= Math.max(phaseSnapshot.recoveryScoreFloor() - 4, 58);

        PolicyTrend trend = resolveTrend(
            phaseSnapshot,
            pressure,
            shortScore,
            mediumScore,
            delta,
            baselineDelta,
            roughSignals,
            recoveryReady
        );
        int consensusScore = computeConsensusScore(shortScore, mediumScore, blendedScore, phaseSnapshot, trend, pressure);
        boolean badBias = trend == PolicyTrend.WORSENING
            || (trend == PolicyTrend.VOLATILE
            && consensusScore < Math.max(55, phaseSnapshot.baselineScore() - 8));
        boolean recoveryBias = recoveryReady
            && (trend == PolicyTrend.IMPROVING
            || (trend == PolicyTrend.STABLE && phaseSnapshot.trustedBaseline()));
        boolean aggressiveDegrade = trend == PolicyTrend.WORSENING
            || (trend == PolicyTrend.VOLATILE && pressure.framePressure());
        int recoveryTickAdjustment = switch (trend) {
            case IMPROVING -> -1;
            case STABLE -> 0;
            case WATCHING -> 1;
            case VOLATILE -> 2;
            case WORSENING -> 3;
        };

        String note = "trend=" + trend
            + " short=" + shortScore
            + " medium=" + mediumScore
            + " delta=" + delta
            + " baselineDelta=" + baselineDelta
            + " consensus=" + consensusScore;
        return new Snapshot(trend, consensusScore, badBias, recoveryBias, aggressiveDegrade, recoveryTickAdjustment, note);
    }

    private PolicyTrend resolveTrend(
        TrafficPhaseSnapshot phaseSnapshot,
        PressureSnapshot pressure,
        int shortScore,
        int mediumScore,
        int delta,
        int baselineDelta,
        boolean roughSignals,
        boolean recoveryReady
    ) {
        if (pressure.severeBurst() || pressure.severeFramePressure()) {
            return PolicyTrend.WORSENING;
        }
        if (delta <= WORSENING_DELTA_MIN && (roughSignals || baselineDelta <= -12)) {
            return PolicyTrend.WORSENING;
        }
        if (Math.abs(delta) >= VOLATILE_DELTA_MIN && roughSignals) {
            return PolicyTrend.VOLATILE;
        }
        if (recoveryReady && delta >= IMPROVING_DELTA_MIN) {
            return PolicyTrend.IMPROVING;
        }
        int stableFloor = Math.max(55, phaseSnapshot.baselineScore() - 10);
        if (Math.abs(delta) <= 6 && shortScore >= stableFloor && mediumScore >= stableFloor - 4) {
            return PolicyTrend.STABLE;
        }
        return roughSignals ? PolicyTrend.WATCHING : PolicyTrend.STABLE;
    }

    private int computeConsensusScore(
        int shortScore,
        int mediumScore,
        int blendedScore,
        TrafficPhaseSnapshot phaseSnapshot,
        PolicyTrend trend,
        PressureSnapshot pressure
    ) {
        int baselineContribution = phaseSnapshot.trustedBaseline()
            ? clampToRange(phaseSnapshot.baselineScore(), blendedScore - 12, blendedScore + 10)
            : blendedScore;

        double score = 0.55 * shortScore + 0.25 * mediumScore + 0.20 * baselineContribution;
        if (trend == PolicyTrend.IMPROVING) {
            score += 3.0;
        } else if (trend == PolicyTrend.WORSENING) {
            score -= 5.0;
        } else if (trend == PolicyTrend.VOLATILE) {
            score -= 3.0;
        }
        if (pressure.severeBurst()) {
            score -= 6.0;
        } else if (pressure.framePressure()) {
            score -= 4.0;
        } else if (pressure.trafficPressure() || pressure.spikePressure()) {
            score -= 2.0;
        }

        return clampScore(score);
    }

    private int clampToRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private int clampScore(double score) {
        int rounded = (int) Math.round(score);
        if (rounded < 0) {
            return 0;
        }
        if (rounded > 100) {
            return 100;
        }
        return rounded;
    }

    public record Snapshot(
        PolicyTrend trend,
        int consensusScore,
        boolean badBias,
        boolean recoveryBias,
        boolean aggressiveDegrade,
        int recoveryTickAdjustment,
        String note
    ) {
    }
}
