/*
 * Decompiled with CFR 0.152.
 */
package com.wellnet.core;

import com.wellnet.core.ClientSnapshot;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientStateCache {
    private static final AtomicReference<ClientSnapshot> LAST = new AtomicReference();
    private static final AtomicLong LAST_UPDATE_MS = new AtomicLong(0L);

    private ClientStateCache() {
    }

    public static void update(ClientSnapshot clientSnapshot) {
        if (clientSnapshot == null) {
            return;
        }
        LAST.set(clientSnapshot);
        LAST_UPDATE_MS.set(clientSnapshot.timestampMs);
    }

    public static void reset() {
        LAST.set(null);
        LAST_UPDATE_MS.set(0L);
    }

    public static ClientSnapshot getLast() {
        return LAST.get();
    }

    public static String getServerId() {
        ClientSnapshot clientSnapshot = LAST.get();
        return clientSnapshot == null ? "" : (clientSnapshot.serverId == null ? "" : clientSnapshot.serverId);
    }

    public static int getCurrentRenderDistance() {
        ClientSnapshot clientSnapshot = LAST.get();
        return clientSnapshot == null ? -1 : clientSnapshot.currentRenderDistance;
    }

    public static boolean isIntegratedHost() {
        ClientSnapshot clientSnapshot = LAST.get();
        return clientSnapshot != null && clientSnapshot.isIntegratedHost;
    }

    public static double getLastDistanceMoved() {
        ClientSnapshot clientSnapshot = LAST.get();
        return clientSnapshot == null ? Double.NaN : clientSnapshot.distanceMoved;
    }

    public static long getLastUpdateMs() {
        return LAST_UPDATE_MS.get();
    }
}

