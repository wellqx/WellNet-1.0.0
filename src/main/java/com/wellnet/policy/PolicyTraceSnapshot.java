package com.wellnet.policy;

import com.wellnet.core.ActionPlan;

public record PolicyTraceSnapshot(
    long timestampMs,
    String serverId,
    TrafficPhase phase,
    PolicyTrend trend,
    int blendedScore,
    String shortQuality,
    ActionPlan.Reason reason,
    int targetRenderDistance,
    boolean changed,
    String note
) {
    public String compactSummary() {
        String reasonText = this.reason == null ? "NONE" : this.reason.name();
        String targetText = this.targetRenderDistance < 0 ? "-" : Integer.toString(this.targetRenderDistance);
        return "phase=" + this.phase
            + " trend=" + this.trend
            + " score=" + this.blendedScore
            + " q=" + this.shortQuality
            + " reason=" + reasonText
            + " rd=" + targetText
            + " changed=" + this.changed;
    }
}
