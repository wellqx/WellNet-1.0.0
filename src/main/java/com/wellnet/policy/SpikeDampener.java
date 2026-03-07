package com.wellnet.policy;

import com.wellnet.core.NetworkStats;

public final class SpikeDampener {
    private static final long REMOTE_BURST_FREEZE_MS = 10_000L;
    private static final long HOST_BURST_FREEZE_MS = 15_000L;

    public Snapshot analyze(
        AdaptivePolicyContext context,
        TrafficPhaseSnapshot phaseSnapshot,
        boolean burstGuardActive,
        boolean framePressure,
        boolean severeFramePressure,
        boolean severeBurst
    ) {
        NetworkStats networkStats = context.networkStats();
        boolean elevatedSpikes = networkStats != null
            && ((networkStats.frameSpikeRatePercent >= 0 && networkStats.frameSpikeRatePercent >= 8)
            || (networkStats.frameStallRatePercent >= 0 && networkStats.frameStallRatePercent >= 2)
            || networkStats.p95PingMillis >= 200L);
        boolean escalateBadCondition = framePressure || severeFramePressure || severeBurst || elevatedSpikes;

        long freezeUntilMs = 0L;
        if (burstGuardActive || severeBurst || severeFramePressure) {
            long freezeMs = context.snapshot().isIntegratedHost ? HOST_BURST_FREEZE_MS : REMOTE_BURST_FREEZE_MS;
            freezeUntilMs = context.nowMs() + freezeMs;
        }

        boolean recoveryBoost = phaseSnapshot.fastHostRecovery()
            && !burstGuardActive
            && !framePressure
            && context.shortStats() != null
            && context.shortStats().qualityScore() >= phaseSnapshot.recoveryScoreFloor();
        String note = "freeze=" + (freezeUntilMs > 0L) + " elevatedSpikes=" + elevatedSpikes;
        return new Snapshot(freezeUntilMs, escalateBadCondition, recoveryBoost, note);
    }

    public record Snapshot(
        long freezeUntilMs,
        boolean escalateBadCondition,
        boolean recoveryBoost,
        String note
    ) {
    }
}
