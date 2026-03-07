package com.wellnet.core;

import java.util.Objects;

public final class ActionPlan {
    public static final int NO_RENDER_DISTANCE_CHANGE = -1;
    public final long createdAtMs;
    public final String serverId;
    public final int proposedRenderDistance;
    public final String note;
    public final long timestampMs;
    public final int targetRenderDistance;
    public final Reason reason;
    public final double confidence;

    public ActionPlan(long timestampMs, String serverId, int targetRenderDistance, String note) {
        this(timestampMs, serverId, targetRenderDistance, Reason.fromString(note), 0.5, note);
    }

    public ActionPlan(long timestampMs, String serverId, int targetRenderDistance, Reason reason, double confidence) {
        this(timestampMs, serverId, targetRenderDistance, reason, confidence, reason == null ? null : reason.name());
    }

    public ActionPlan(long timestampMs, String serverId, int targetRenderDistance, Reason reason, double confidence, String note) {
        this.timestampMs = timestampMs;
        this.createdAtMs = timestampMs;
        this.serverId = serverId == null ? "" : serverId;
        this.targetRenderDistance = targetRenderDistance;
        this.proposedRenderDistance = targetRenderDistance;
        this.reason = reason == null ? Reason.UNKNOWN : reason;
        this.confidence = clamp01(confidence);
        this.note = note;
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

    public boolean semanticallyEquals(ActionPlan other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(this.serverId, other.serverId)
            && this.targetRenderDistance == other.targetRenderDistance
            && this.reason == other.reason;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ActionPlan other)) {
            return false;
        }
        return semanticallyEquals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.serverId, this.targetRenderDistance, this.reason);
    }

    @Override
    public String toString() {
        return "ActionPlan{ts=" + this.timestampMs
            + ", serverId='" + this.serverId + "'"
            + ", targetRD=" + this.targetRenderDistance
            + ", reason=" + this.reason
            + ", confidence=" + this.confidence
            + ", note='" + this.note + "'}";
    }

    public enum Reason {
        POOR_NET,
        HIGH_BPS,
        SPIKES,
        AFK,
        RECOVERY,
        UNKNOWN,
        CLIENT_LAG,
        JOIN_WARMUP,
        BURST_GUARD,
        HOST_PRESSURE;

        public static Reason fromString(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            String normalized = value.trim().toUpperCase();
            for (Reason reason : values()) {
                if (reason.name().equals(normalized)) {
                    return reason;
                }
            }
            if (normalized.contains("WARMUP") || normalized.contains("JOIN")) {
                return JOIN_WARMUP;
            }
            if (normalized.contains("BURST")) {
                return BURST_GUARD;
            }
            if (normalized.contains("HOST")) {
                return HOST_PRESSURE;
            }
            if (normalized.contains("AFK")) {
                return AFK;
            }
            if (normalized.contains("SPIKE")) {
                return SPIKES;
            }
            if (normalized.contains("LAG") || normalized.contains("FPS") || normalized.contains("FRAME") || normalized.contains("STUTTER")) {
                return CLIENT_LAG;
            }
            if (normalized.contains("BPS") || normalized.contains("TRAFFIC") || normalized.contains("THROUGHPUT")) {
                return HIGH_BPS;
            }
            if (normalized.contains("RECOVER")) {
                return RECOVERY;
            }
            if (normalized.contains("POOR") || normalized.contains("NET")) {
                return POOR_NET;
            }
            return UNKNOWN;
        }
    }
}
