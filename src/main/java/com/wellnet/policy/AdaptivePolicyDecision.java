package com.wellnet.policy;

import com.wellnet.core.ActionPlan;

public record AdaptivePolicyDecision(
    int targetRenderDistance,
    ActionPlan.Reason reason,
    double confidence,
    String note
) {
    private static final AdaptivePolicyDecision NONE =
        new AdaptivePolicyDecision(ActionPlan.NO_RENDER_DISTANCE_CHANGE, ActionPlan.Reason.UNKNOWN, 0.0, null);

    public static AdaptivePolicyDecision none() {
        return NONE;
    }

    public boolean hasChange() {
        return this.targetRenderDistance != ActionPlan.NO_RENDER_DISTANCE_CHANGE;
    }

    public ActionPlan toActionPlan(long nowMs, String serverId) {
        return new ActionPlan(nowMs, serverId, this.targetRenderDistance, this.reason, this.confidence, this.note);
    }
}
