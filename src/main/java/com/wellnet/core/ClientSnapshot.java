/*
 * Decompiled with CFR 0.152.
 */
package com.wellnet.core;

public final class ClientSnapshot {
    public static final int UNKNOWN_INT = -1;
    public static final double UNKNOWN_DOUBLE = Double.NaN;
    public final long timestampMs;
    public final String serverId;
    public final int pingMs;
    public final double playerX;
    public final double playerY;
    public final double playerZ;
    public final double distanceMoved;
    public final boolean isIntegratedHost;
    public final int currentRenderDistance;
    public final int fps;
    public final double frameTimeMs;
    public final boolean paused;
    public final boolean inWorld;
    public final boolean hasPlayer;

    public ClientSnapshot(long l, String string, int n, double d, double d2, double d3, double d4, boolean bl, int n2, int n3, double d5, boolean bl2, boolean bl3, boolean bl4) {
        this.timestampMs = l;
        this.serverId = string == null ? "" : string;
        this.pingMs = n;
        this.playerX = d;
        this.playerY = d2;
        this.playerZ = d3;
        this.distanceMoved = d4;
        this.isIntegratedHost = bl;
        this.currentRenderDistance = n2;
        this.fps = n3;
        this.frameTimeMs = d5;
        this.paused = bl2;
        this.inWorld = bl3;
        this.hasPlayer = bl4;
    }

    public String toString() {
        return "ClientSnapshot{timestampMs=" + this.timestampMs + ", serverId='" + this.serverId + "', pingMs=" + this.pingMs + ", playerX=" + this.playerX + ", playerY=" + this.playerY + ", playerZ=" + this.playerZ + ", distanceMoved=" + this.distanceMoved + ", isIntegratedHost=" + this.isIntegratedHost + ", currentRenderDistance=" + this.currentRenderDistance + ", fps=" + this.fps + ", frameTimeMs=" + this.frameTimeMs + ", paused=" + this.paused + ", inWorld=" + this.inWorld + ", hasPlayer=" + this.hasPlayer + "}";
    }
}
