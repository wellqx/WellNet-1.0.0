package com.wellnet.client;

import com.wellnet.core.ActionPlan;
import com.wellnet.core.NetworkStats;
import com.wellnet.core.WellNetCore;
import com.wellnet.policy.PolicyTrend;
import com.wellnet.policy.PolicyTraceSnapshot;
import com.wellnet.policy.TrafficPhase;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PlayerFacingStatusFactory {
    private PlayerFacingStatusFactory() {
    }

    public static PlayerFacingStatus create(Minecraft minecraft, WellNetCore core, int currentRenderDistance) {
        NetworkStats networkStats = core.getNetworkStats();
        ActionPlan plan = choosePlan(core);
        PolicyTraceSnapshot latestTrace = latestTrace(core);
        TrafficPhase phase = latestTrace == null || latestTrace.phase() == null ? TrafficPhase.SETTLING : latestTrace.phase();
        PolicyTrend trend = latestTrace == null || latestTrace.trend() == null ? PolicyTrend.WATCHING : latestTrace.trend();
        Mood mood = resolveMood(plan, networkStats, trend);

        Component header = Component.translatable("wellnet.status.player.header", Component.translatable(mood.headerKey));
        Component summary = buildSummary(networkStats, phase);
        Component action = buildAction(plan, currentRenderDistance, mood);
        Component reason = buildReason(plan, phase, networkStats);
        String tipKey = chooseTipKey(plan, phase, mood, networkStats);
        String tipText = WellNetGuideRepository.tip(minecraft, tipKey);
        if (tipText == null || tipText.isBlank()) {
            tipText = WellNetGuideRepository.tip(minecraft, "default");
        }
        Component tip = Component.literal(tipText == null ? "" : tipText);
        Component footer = Component.translatable("wellnet.status.player.footer");
        return new PlayerFacingStatus(header, summary, action, reason, tip, footer);
    }

    private static ActionPlan choosePlan(WellNetCore core) {
        ActionPlan current = core.getActionPlan();
        return current != null ? current : core.getLastPublishedActionPlan();
    }

    private static PolicyTraceSnapshot latestTrace(WellNetCore core) {
        List<PolicyTraceSnapshot> traces = core.recentPolicyTraces(1);
        if (traces.isEmpty()) {
            return null;
        }
        return traces.get(traces.size() - 1);
    }

    private static Mood resolveMood(ActionPlan plan, NetworkStats networkStats, PolicyTrend trend) {
        if (plan != null && plan.reason == ActionPlan.Reason.RECOVERY) {
            return Mood.RECOVERING;
        }
        if (plan != null
            && plan.targetRenderDistance != ActionPlan.NO_RENDER_DISTANCE_CHANGE
            && plan.reason != ActionPlan.Reason.RECOVERY
            && plan.reason != ActionPlan.Reason.UNKNOWN) {
            return Mood.PROTECTING;
        }
        if (networkStats == null) {
            return Mood.ANALYZING;
        }
        if (trend == PolicyTrend.WORSENING || trend == PolicyTrend.VOLATILE) {
            return Mood.WATCHING;
        }
        if (networkStats.avgPingMillis >= 0L && networkStats.qualityScore >= 80) {
            return Mood.STABLE;
        }
        if (networkStats.avgPingMillis < 0L && networkStats.qualityScore >= 60) {
            return Mood.STABLE;
        }
        return Mood.WATCHING;
    }

    private static Component buildSummary(NetworkStats networkStats, TrafficPhase phase) {
        if (networkStats == null) {
            return Component.translatable("wellnet.status.player.summary.analyzing");
        }
        if (networkStats.avgPingMillis >= 0L) {
            String connectionToneKey = networkStats.qualityScore >= 80
                ? "wellnet.status.player.connection.calm"
                : networkStats.qualityScore >= 60
                ? "wellnet.status.player.connection.watching"
                : "wellnet.status.player.connection.unstable";
            return Component.translatable(
                "wellnet.status.player.summary.ping",
                networkStats.avgPingMillis,
                Component.translatable(connectionToneKey),
                Component.translatable(phaseTranslationKey(phase))
            );
        }
        return Component.translatable(
            "wellnet.status.player.summary.frame",
            networkStats.avgFrameTimeMs,
            networkStats.frameSpikeRatePercent,
            networkStats.frameStallRatePercent,
            Component.translatable(phaseTranslationKey(phase))
        );
    }

    private static Component buildAction(ActionPlan plan, int currentRenderDistance, Mood mood) {
        if (plan == null || plan.targetRenderDistance == ActionPlan.NO_RENDER_DISTANCE_CHANGE) {
            if (mood == Mood.ANALYZING) {
                return Component.translatable("wellnet.status.player.action.analyzing");
            }
            return Component.translatable("wellnet.status.player.action.idle", currentRenderDistance);
        }
        if (plan.reason == ActionPlan.Reason.RECOVERY) {
            return Component.translatable("wellnet.status.player.action.recovering", currentRenderDistance, plan.targetRenderDistance);
        }
        return Component.translatable("wellnet.status.player.action.protecting", currentRenderDistance, plan.targetRenderDistance);
    }

    private static Component buildReason(ActionPlan plan, TrafficPhase phase, NetworkStats networkStats) {
        if (plan == null || plan.reason == ActionPlan.Reason.UNKNOWN) {
            return Component.translatable("wellnet.status.player.reason.default", Component.translatable(phaseTranslationKey(phase)));
        }
        return switch (plan.reason) {
            case HOST_PRESSURE -> Component.translatable("wellnet.status.player.reason.host");
            case BURST_GUARD -> Component.translatable("wellnet.status.player.reason.burst");
            case CLIENT_LAG -> Component.translatable("wellnet.status.player.reason.frames");
            case SPIKES -> Component.translatable("wellnet.status.player.reason.spikes");
            case HIGH_BPS -> Component.translatable("wellnet.status.player.reason.traffic");
            case JOIN_WARMUP -> Component.translatable("wellnet.status.player.reason.join");
            case RECOVERY -> Component.translatable("wellnet.status.player.reason.recovery");
            case AFK -> Component.translatable("wellnet.status.player.reason.afk");
            case POOR_NET -> networkStats != null && networkStats.avgPingMillis >= 0L
                ? Component.translatable("wellnet.status.player.reason.network")
                : Component.translatable("wellnet.status.player.reason.frames");
            default -> Component.translatable("wellnet.status.player.reason.default", Component.translatable(phaseTranslationKey(phase)));
        };
    }

    private static String chooseTipKey(ActionPlan plan, TrafficPhase phase, Mood mood, NetworkStats networkStats) {
        if (mood == Mood.PROTECTING && plan != null) {
            return switch (plan.reason) {
                case HOST_PRESSURE -> "host_pressure";
                case BURST_GUARD -> "burst_guard";
                case CLIENT_LAG, SPIKES -> "frame_spikes";
                case HIGH_BPS -> "traffic_pressure";
                case JOIN_WARMUP -> "join_settling";
                default -> "protecting";
            };
        }
        if (mood == Mood.RECOVERING) {
            return "recovering";
        }
        if (phase == TrafficPhase.HOST_LOCAL) {
            return "hosting";
        }
        if (networkStats == null) {
            return "analyzing";
        }
        if (networkStats.avgPingMillis >= 0L) {
            return networkStats.qualityScore >= 80 ? "stable_network" : "watching_network";
        }
        return "stable_frames";
    }

    private static String phaseTranslationKey(TrafficPhase phase) {
        return switch (phase) {
            case JOIN -> "wellnet.status.player.phase.join";
            case HOST_LOCAL -> "wellnet.status.player.phase.host";
            case BURST -> "wellnet.status.player.phase.burst";
            case STEADY -> "wellnet.status.player.phase.steady";
            default -> "wellnet.status.player.phase.settling";
        };
    }

    private enum Mood {
        ANALYZING("wellnet.status.player.mood.analyzing"),
        WATCHING("wellnet.status.player.mood.watching"),
        STABLE("wellnet.status.player.mood.stable"),
        PROTECTING("wellnet.status.player.mood.protecting"),
        RECOVERING("wellnet.status.player.mood.recovering");

        private final String headerKey;

        Mood(String headerKey) {
            this.headerKey = headerKey;
        }
    }
}
