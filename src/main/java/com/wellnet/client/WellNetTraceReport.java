package com.wellnet.client;

import com.wellnet.core.ActionPlan;
import com.wellnet.core.WellNetCore;
import com.wellnet.policy.PolicyTrend;
import com.wellnet.policy.PolicyTraceSnapshot;
import com.wellnet.policy.TrafficPhase;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class WellNetTraceReport {
    private static final int MAX_TRACE_LINES = 5;

    private WellNetTraceReport() {
    }

    public static void show() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return;
            }

            WellNetCore core = WellNetCore.getInstance();
            if (core == null) {
                send(minecraft.player, Component.literal("WellNet trace: core not initialized."));
                return;
            }

            List<PolicyTraceSnapshot> traces = core.recentPolicyTraces(MAX_TRACE_LINES);
            send(minecraft.player, Component.literal("WellNet recent decisions"));
            if (traces.isEmpty()) {
                send(minecraft.player, Component.literal("No recent decision history yet. Join a world first."));
                return;
            }

            long now = System.currentTimeMillis();
            for (int index = traces.size() - 1; index >= 0; index--) {
                send(minecraft.player, Component.literal(formatEntry(traces.get(index), now)));
            }
            send(minecraft.player, Component.literal("Use /wellnet status technical if you want the raw metrics behind these entries."));
        } catch (Throwable ignored) {
        }
    }

    private static String formatEntry(PolicyTraceSnapshot trace, long now) {
        long ageSeconds = Math.max(0L, (now - trace.timestampMs()) / 1000L);
        String phase = describePhase(trace.phase());
        String reason = describeReason(trace.reason());
        String trend = describeTrend(trace.trend());
        String quality = trace.shortQuality() == null || trace.shortQuality().isBlank() ? "unknown" : trace.shortQuality().toLowerCase();
        int score = trace.blendedScore();
        String detail = summarizeDecision(trace, phase, reason);
        String note = summarizeNote(trace.note());
        return ageSeconds + "s ago: " + detail + " Trend looked " + trend + ". Window looked " + quality + " (score " + score + "). " + note;
    }

    private static String summarizeDecision(PolicyTraceSnapshot trace, String phase, String reason) {
        if (trace.changed() && trace.targetRenderDistance() >= 0) {
            if (trace.reason() == ActionPlan.Reason.RECOVERY) {
                return "During " + phase + ", the session looked healthier, so WellNet started restoring render distance toward " + trace.targetRenderDistance() + ".";
            }
            return "During " + phase + ", " + reason + ", so WellNet held a safer render distance at " + trace.targetRenderDistance() + ".";
        }
        if (trace.reason() == ActionPlan.Reason.RECOVERY) {
            return "During " + phase + ", the session stayed calm enough for recovery, so WellNet kept climbing carefully.";
        }
        return "During " + phase + ", " + reason + ", but the mod did not need a new settings change.";
    }

    private static String summarizeNote(String note) {
        if (note == null || note.isBlank()) {
            return "No extra trace note was recorded.";
        }
        String compact = note.trim().replace('\n', ' ').replace('\r', ' ');
        if (compact.length() > 120) {
            compact = compact.substring(0, 117) + "...";
        }
        return "Trace note: " + compact;
    }

    private static String describePhase(TrafficPhase phase) {
        if (phase == null) {
            return "an unknown phase";
        }
        return switch (phase) {
            case JOIN -> "world entry";
            case SETTLING -> "session settling";
            case STEADY -> "steady play";
            case HOST_LOCAL -> "local hosting";
            case BURST -> "burst recovery";
        };
    }

    private static String describeReason(ActionPlan.Reason reason) {
        if (reason == null) {
            return "the session looked rough";
        }
        return switch (reason) {
            case HOST_PRESSURE -> "local host pressure was high";
            case BURST_GUARD -> "burst spikes stacked up";
            case CLIENT_LAG -> "frame pressure was high";
            case SPIKES -> "short instability spikes were detected";
            case HIGH_BPS -> "traffic turned uneven";
            case JOIN_WARMUP -> "the world was still settling";
            case RECOVERY -> "the session had recovered";
            case AFK -> "the session was idle";
            case POOR_NET -> "the connection looked rough";
            default -> "the session looked rough";
        };
    }

    private static String describeTrend(PolicyTrend trend) {
        if (trend == null) {
            return "unclear";
        }
        return switch (trend) {
            case IMPROVING -> "improving";
            case STABLE -> "stable";
            case WATCHING -> "mixed but watchable";
            case VOLATILE -> "uneven";
            case WORSENING -> "getting worse";
        };
    }

    private static void send(LocalPlayer player, Component message) {
        try {
            player.sendSystemMessage(message);
        } catch (Throwable ignored) {
        }
    }
}
