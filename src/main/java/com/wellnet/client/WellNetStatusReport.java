package com.wellnet.client;

import com.wellnet.core.ActionPlan;
import com.wellnet.core.NetworkStats;
import com.wellnet.core.WellNetCore;
import com.wellnet.net.TrafficManager;
import com.wellnet.policy.PolicyTraceSnapshot;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class WellNetStatusReport {
    private static final long DECISION_CONTEXT_TTL_MS = 300000L;

    private WellNetStatusReport() {
    }

    public static void show() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return;
            }

            WellNetCore core = WellNetCore.getInstance();
            if (core == null) {
                send(minecraft.player, Component.literal("WellNet: core not initialized."));
                return;
            }

            Options options = minecraft.options;
            int currentRenderDistance = options == null ? -1 : options.renderDistance().get();
            PlayerFacingStatus playerFacingStatus = PlayerFacingStatusFactory.create(minecraft, core, currentRenderDistance);
            send(minecraft.player, playerFacingStatus.header());
            send(minecraft.player, playerFacingStatus.summary());
            send(minecraft.player, playerFacingStatus.action());
            send(minecraft.player, playerFacingStatus.reason());
            send(minecraft.player, playerFacingStatus.tip());
            send(minecraft.player, playerFacingStatus.footer());
        } catch (Throwable ignored) {
        }
    }

    public static void showTechnical() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return;
            }

            WellNetCore core = WellNetCore.getInstance();
            if (core == null) {
                send(minecraft.player, Component.literal("WellNet: core not initialized."));
                return;
            }

            long uptimeSeconds = computeSessionUptimeSeconds(core);
            NetworkStats networkStats = core.getNetworkStats();
            String netLine = formatNetLine(networkStats);
            String trafficLine = "Traffic: in=" + formatBps(TrafficManager.getInboundBps())
                + " out=" + formatBps(TrafficManager.getOutboundBps());

            Options options = minecraft.options;
            int currentRenderDistance = options == null ? -1 : options.renderDistance().get();
            ActionPlan plan = choosePlanToShow(core);
            String target = "-";
            String reason = "-";
            String confidence = "-";
            String note = "-";
            if (plan != null && plan.targetRenderDistance != ActionPlan.NO_RENDER_DISTANCE_CHANGE) {
                target = String.valueOf(plan.targetRenderDistance);
                reason = String.valueOf(plan.reason);
                confidence = String.format("%.2f", plan.confidence);
                if (plan.note != null && !plan.note.isBlank()) {
                    note = plan.note;
                }
                note = enrichDecisionNote(note, plan, networkStats);
            }

            send(minecraft.player, Component.literal("WellNet technical status"));
            send(minecraft.player, Component.literal("Session uptime: " + uptimeSeconds + "s"));
            send(minecraft.player, Component.literal(netLine));
            send(minecraft.player, Component.literal(trafficLine));
            send(minecraft.player, Component.literal("RenderDistance: current=" + currentRenderDistance + " target=" + target + " reason=" + reason + " conf=" + confidence));
            send(minecraft.player, Component.literal("Decision note: " + note));

            List<PolicyTraceSnapshot> traces = core.recentPolicyTraces(3);
            if (!traces.isEmpty()) {
                PolicyTraceSnapshot latestTrace = traces.get(traces.size() - 1);
                send(minecraft.player, Component.literal(
                    "Trend: " + latestTrace.trend()
                        + " phase=" + latestTrace.phase()
                        + " score=" + latestTrace.blendedScore()
                        + " q=" + latestTrace.shortQuality()
                ));
                send(minecraft.player, Component.literal("Recent policy trace:"));
                for (PolicyTraceSnapshot trace : traces) {
                    send(minecraft.player, Component.literal(" - " + trace.compactSummary()));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void showGuide(String topic) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return;
            }

            String normalizedTopic = normalizeTopic(topic);
            List<String> lines = WellNetGuideRepository.topicLines(minecraft, normalizedTopic);
            if (lines.isEmpty()) {
                send(minecraft.player, Component.translatable("wellnet.status.guide.empty"));
                return;
            }

            send(minecraft.player, Component.translatable("wellnet.status.guide.header", normalizedTopic));
            for (String line : lines) {
                send(minecraft.player, Component.literal(line));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void showTrace() {
        WellNetTraceReport.show();
    }

    private static String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return "overview";
        }
        return topic.trim().toLowerCase();
    }

    private static ActionPlan choosePlanToShow(WellNetCore core) {
        ActionPlan current = core.getActionPlan();
        if (current != null) {
            return current;
        }

        ActionPlan last = core.getLastPublishedActionPlan();
        if (last == null) {
            return null;
        }

        long ageMs = System.currentTimeMillis() - last.timestampMs;
        return ageMs <= DECISION_CONTEXT_TTL_MS ? last : null;
    }

    private static long computeSessionUptimeSeconds(WellNetCore core) {
        long connectStart = core.getConnectStartTimeSeconds();
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (connectStart > 0L && nowSeconds >= connectStart) {
            return nowSeconds - connectStart;
        }
        return core.uptimeSeconds();
    }

    private static void send(LocalPlayer player, Component message) {
        try {
            player.sendSystemMessage(message);
        } catch (Throwable ignored) {
        }
    }

    private static String formatNetLine(NetworkStats networkStats) {
        if (networkStats == null) {
            return "Net: (no stats yet)";
        }
        if (networkStats.avgPingMillis >= 0L) {
            return "Net: avg=" + networkStats.avgPingMillis + "ms jitter=" + formatMillis(networkStats.jitterMillis)
                + " late=" + formatPercent(networkStats.latePercent()) + " p95=" + formatMillis(networkStats.p95PingMillis)
                + " p99=" + formatMillis(networkStats.p99PingMillis) + " spikes=" + formatPercent(networkStats.spikeRatePercent);
        }
        return "Net: frame/traffic mode frameAvg=" + formatMillis(networkStats.avgFrameTimeMs)
            + " frameP95=" + formatMillis(networkStats.p95FrameTimeMs)
            + " frameP99=" + formatMillis(networkStats.p99FrameTimeMs)
            + " frameSpikes=" + formatPercent(networkStats.frameSpikeRatePercent)
            + " stalls=" + formatPercent(networkStats.frameStallRatePercent);
    }

    private static String formatMillis(long value) {
        return value >= 0L ? value + "ms" : "-";
    }

    private static String formatPercent(int value) {
        return value >= 0 ? value + "%" : "-";
    }

    private static String formatBps(long bps) {
        if (bps < 0L) {
            return "-";
        }
        double value = bps;
        if (value < 1000.0) {
            return String.format("%.0f bps", value);
        }
        value /= 1000.0;
        if (value < 1000.0) {
            return String.format("%.1f Kbps", value);
        }
        value /= 1000.0;
        if (value < 1000.0) {
            return String.format("%.1f Mbps", value);
        }
        value /= 1000.0;
        return String.format("%.1f Gbps", value);
    }

    private static String enrichDecisionNote(String note, ActionPlan plan, NetworkStats networkStats) {
        StringBuilder builder = new StringBuilder();
        if (note != null && !note.isBlank()) {
            builder.append(note);
        } else {
            builder.append("-");
        }

        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - plan.timestampMs) / 1000L);
        builder.append(" age=").append(ageSeconds).append("s");
        if (networkStats != null && networkStats.qualityScore >= 0 && networkStats.quality != null && !networkStats.quality.isBlank()) {
            builder.append(" liveScore=").append(networkStats.qualityScore);
            builder.append(" liveQ=").append(networkStats.quality);
        }
        return builder.toString();
    }
}
