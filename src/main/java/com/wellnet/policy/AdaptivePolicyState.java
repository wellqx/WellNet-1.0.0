package com.wellnet.policy;

import com.wellnet.core.NetDiagnosticsModule;

public final class AdaptivePolicyState {
    private String lastServerId = "";
    private boolean hostProfile;
    private int badTicks;
    private int goodTicks;
    private long lastChangeTimeMs;
    private long lastActiveTimeMs;
    private long sessionStartTimeMs;
    private long burstGuardUntilMs;
    private long decisionFreezeUntilMs;
    private boolean afk;
    private final ServerSessionProfile serverProfile = new ServerSessionProfile();
    private TrafficPhase lastTrafficPhase = TrafficPhase.JOIN;
    private PolicyTrend lastTrend = PolicyTrend.WATCHING;
    private String lastPhaseNote = "";
    private int lastBlendedScore = -1;
    private NetDiagnosticsModule.Quality lastShortQuality = NetDiagnosticsModule.Quality.FAIR;

    public void reset(long nowMs) {
        this.lastServerId = "";
        this.hostProfile = false;
        this.badTicks = 0;
        this.goodTicks = 0;
        this.lastChangeTimeMs = 0L;
        this.lastActiveTimeMs = nowMs;
        this.sessionStartTimeMs = 0L;
        this.burstGuardUntilMs = 0L;
        this.decisionFreezeUntilMs = 0L;
        this.afk = false;
        this.serverProfile.reset("", false, nowMs);
        this.lastTrafficPhase = TrafficPhase.JOIN;
        this.lastTrend = PolicyTrend.WATCHING;
        this.lastPhaseNote = "";
        this.lastBlendedScore = -1;
        this.lastShortQuality = NetDiagnosticsModule.Quality.FAIR;
    }

    public void refreshSession(String serverId, boolean newHostProfile, long nowMs) {
        String normalizedServerId = serverId == null ? "" : serverId;
        if (!normalizedServerId.equals(this.lastServerId)) {
            this.lastServerId = normalizedServerId;
            this.hostProfile = newHostProfile;
            this.badTicks = 0;
            this.goodTicks = 0;
            this.lastChangeTimeMs = 0L;
            this.lastActiveTimeMs = nowMs;
            this.sessionStartTimeMs = nowMs;
            this.burstGuardUntilMs = 0L;
            this.decisionFreezeUntilMs = 0L;
            this.afk = false;
            this.serverProfile.reset(normalizedServerId, newHostProfile, nowMs);
            this.lastTrafficPhase = newHostProfile ? TrafficPhase.HOST_LOCAL : TrafficPhase.JOIN;
            this.lastTrend = PolicyTrend.WATCHING;
            this.lastPhaseNote = "";
            this.lastBlendedScore = -1;
            this.lastShortQuality = NetDiagnosticsModule.Quality.FAIR;
            return;
        }

        this.hostProfile = newHostProfile;
        if (this.sessionStartTimeMs == 0L) {
            this.sessionStartTimeMs = nowMs;
        }
        this.serverProfile.ensureSession(normalizedServerId, newHostProfile, nowMs);
    }

    public void updateActivity(double distanceMoved, long nowMs, long afkTimeoutMs, double moveEpsilon) {
        if (!Double.isNaN(distanceMoved) && distanceMoved > moveEpsilon) {
            this.lastActiveTimeMs = nowMs;
            this.afk = false;
            return;
        }

        if (nowMs - this.lastActiveTimeMs >= afkTimeoutMs) {
            this.afk = true;
        }
    }

    public void extendBurstGuardUntil(long candidateUntilMs) {
        if (candidateUntilMs > this.burstGuardUntilMs) {
            this.burstGuardUntilMs = candidateUntilMs;
        }
    }

    public boolean isWarmupActive(long nowMs, long joinWarmupMs) {
        return joinWarmupMs > 0L && this.sessionStartTimeMs > 0L && nowMs - this.sessionStartTimeMs < joinWarmupMs;
    }

    public boolean isBurstGuardActive(long nowMs) {
        return this.burstGuardUntilMs > nowMs;
    }

    public void freezeDecisionsUntil(long candidateUntilMs) {
        if (candidateUntilMs > this.decisionFreezeUntilMs) {
            this.decisionFreezeUntilMs = candidateUntilMs;
        }
    }

    public boolean isDecisionFreezeActive(long nowMs) {
        return this.decisionFreezeUntilMs > nowMs;
    }

    public long sessionAgeMs(long nowMs) {
        if (this.sessionStartTimeMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowMs - this.sessionStartTimeMs);
    }

    public void markBadCondition() {
        this.badTicks++;
        this.goodTicks = 0;
    }

    public void markExcellentCondition() {
        this.goodTicks++;
        this.badTicks = 0;
    }

    public void decayTrendCounters() {
        this.badTicks = Math.max(0, this.badTicks - 1);
        this.goodTicks = Math.max(0, this.goodTicks - 1);
    }

    public boolean shouldDeferDecision(long nowMs, long minIntervalMs) {
        return this.lastChangeTimeMs > 0L && nowMs - this.lastChangeTimeMs < Math.max(0L, minIntervalMs);
    }

    public void markDecisionApplied(long nowMs) {
        this.lastChangeTimeMs = nowMs;
    }

    public void markEvaluation(
        TrafficPhase phase,
        PolicyTrend trend,
        String note,
        int blendedScore,
        NetDiagnosticsModule.Quality shortQuality
    ) {
        this.lastTrafficPhase = phase == null ? TrafficPhase.SETTLING : phase;
        this.lastTrend = trend == null ? PolicyTrend.WATCHING : trend;
        this.lastPhaseNote = note == null ? "" : note;
        this.lastBlendedScore = blendedScore;
        if (shortQuality != null) {
            this.lastShortQuality = shortQuality;
        }
    }

    public void clearBadTicks() {
        this.badTicks = 0;
    }

    public void clearGoodTicks() {
        this.goodTicks = 0;
    }

    public boolean isHostProfile() {
        return this.hostProfile;
    }

    public int badTicks() {
        return this.badTicks;
    }

    public int goodTicks() {
        return this.goodTicks;
    }

    public boolean isAfk() {
        return this.afk;
    }

    public ServerSessionProfile serverProfile() {
        return this.serverProfile;
    }

    public TrafficPhase lastTrafficPhase() {
        return this.lastTrafficPhase;
    }

    public PolicyTrend lastTrend() {
        return this.lastTrend;
    }

    public String lastPhaseNote() {
        return this.lastPhaseNote;
    }

    public int lastBlendedScore() {
        return this.lastBlendedScore;
    }

    public NetDiagnosticsModule.Quality lastShortQuality() {
        return this.lastShortQuality;
    }
}
