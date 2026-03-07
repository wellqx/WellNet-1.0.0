package com.wellnet.policy;

public record TrafficPhaseSnapshot(
    TrafficPhase phase,
    int baselineScore,
    int recoveryScoreFloor,
    boolean fastHostRecovery,
    boolean trustedBaseline,
    int stabilityConfidence,
    String note
) {
}
