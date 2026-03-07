/*
 * Decompiled with CFR 0.152.
 */
package com.wellnet.core;

public final class NetworkStats {
    public static final long UNKNOWN_LONG = -1L;
    public final long timestampMs;
    public final String serverId;
    public final int samples;
    public final long avgPingMillis;
    public final long minPingMillis;
    public final long maxPingMillis;
    public final long jitterMillis;
    public final int avgLossPercent;
    public final String quality;
    public final int qualityScore;
    public final long p95PingMillis;
    public final long p99PingMillis;
    public final int spikeRatePercent;
    public final long inboundBps;
    public final long outboundBps;
    public final long inboundBpsAvg;
    public final long inboundBpsMax;
    public final int inboundBurstinessPercent;
    public final long outboundBpsAvg;
    public final long outboundBpsMax;
    public final int outboundBurstinessPercent;
    public final long avgFrameTimeMs;
    public final long p95FrameTimeMs;
    public final long p99FrameTimeMs;
    public final int frameSpikeRatePercent;
    public final int frameStallRatePercent;

    public NetworkStats(long l, String string, int n, long l2, long l3, long l4, long l5, int n2, String string2, int n3) {
        this(l, string, n, l2, l3, l4, l5, n2, string2, n3, -1L, -1L, -1, -1L, -1L, -1L, -1L, -1, -1L, -1L, -1);
    }

    public NetworkStats(long l, String string, int n, long l2, long l3, long l4, long l5, int n2, String string2, int n3, long l6, long l7, int n4) {
        this(l, string, n, l2, l3, l4, l5, n2, string2, n3, l6, l7, n4, -1L, -1L, -1L, -1L, -1, -1L, -1L, -1);
    }

    public NetworkStats(long l, String string, int n, long l2, long l3, long l4, long l5, int n2, String string2, int n3, long l6, long l7, int n4, long l8, long l9) {
        this(l, string, n, l2, l3, l4, l5, n2, string2, n3, l6, l7, n4, l8, l9, -1L, -1L, -1, -1L, -1L, -1);
    }

    public NetworkStats(long l, String string, int n, long l2, long l3, long l4, long l5, int n2, String string2, int n3, long l6, long l7, int n4, long l8, long l9, long l10, long l11, int n5, long l12, long l13, int n6) {
        this(l, string, n, l2, l3, l4, l5, n2, string2, n3, l6, l7, n4, l8, l9, l10, l11, n5, l12, l13, n6, -1L, -1L, -1L, -1, -1);
    }

    public NetworkStats(long l, String string, int n, long l2, long l3, long l4, long l5, int n2, String string2, int n3, long l6, long l7, int n4, long l8, long l9, long l10, long l11, int n5, long l12, long l13, int n6, long l14, long l15, long l16, int n7, int n8) {
        this.timestampMs = l;
        this.serverId = string;
        this.samples = n;
        this.avgPingMillis = l2;
        this.minPingMillis = l3;
        this.maxPingMillis = l4;
        this.jitterMillis = l5;
        this.avgLossPercent = n2;
        this.quality = string2;
        this.qualityScore = n3;
        this.p95PingMillis = l6;
        this.p99PingMillis = l7;
        this.spikeRatePercent = n4;
        this.inboundBps = l8;
        this.outboundBps = l9;
        this.inboundBpsAvg = l10;
        this.inboundBpsMax = l11;
        this.inboundBurstinessPercent = n5;
        this.outboundBpsAvg = l12;
        this.outboundBpsMax = l13;
        this.outboundBurstinessPercent = n6;
        this.avgFrameTimeMs = l14;
        this.p95FrameTimeMs = l15;
        this.p99FrameTimeMs = l16;
        this.frameSpikeRatePercent = n7;
        this.frameStallRatePercent = n8;
    }

    public int latePercent() {
        return this.avgLossPercent;
    }

    public String toString() {
        return "NetworkStats{ts=" + this.timestampMs + ", serverId='" + this.serverId + "', samples=" + this.samples + ", avgPing=" + this.avgPingMillis + ", jitter=" + this.jitterMillis + ", late%=" + this.avgLossPercent + ", quality='" + this.quality + "', score=" + this.qualityScore + ", p95=" + this.p95PingMillis + ", p99=" + this.p99PingMillis + ", spike%=" + this.spikeRatePercent + ", inBps=" + this.inboundBps + ", outBps=" + this.outboundBps + ", inAvgBps=" + this.inboundBpsAvg + ", inMaxBps=" + this.inboundBpsMax + ", inBurst%=" + this.inboundBurstinessPercent + ", outAvgBps=" + this.outboundBpsAvg + ", outMaxBps=" + this.outboundBpsMax + ", outBurst%=" + this.outboundBurstinessPercent + ", avgFrameMs=" + this.avgFrameTimeMs + ", p95FrameMs=" + this.p95FrameTimeMs + ", p99FrameMs=" + this.p99FrameTimeMs + ", frameSpike%=" + this.frameSpikeRatePercent + ", frameStall%=" + this.frameStallRatePercent + "}";
    }
}

