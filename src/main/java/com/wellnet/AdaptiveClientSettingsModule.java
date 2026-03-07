package com.wellnet;

import com.wellnet.config.WellNetConfig;
import com.wellnet.core.ActionPlan;
import com.wellnet.core.ClientSnapshot;
import com.wellnet.core.ClientStateCache;
import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.WellNetCore;
import com.wellnet.policy.AdaptivePolicyConfig;
import com.wellnet.policy.AdaptivePolicyContext;
import com.wellnet.policy.AdaptivePolicyDecision;
import com.wellnet.policy.AdaptivePolicyEngine;
import com.wellnet.policy.AdaptivePolicyState;
import com.wellnet.policy.PolicyTraceSnapshot;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdaptiveClientSettingsModule extends WellNetCore.Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveClientSettingsModule.class);

    private final WellNetCore core;
    private final NetDiagnosticsModule netDiag;
    private final AdaptivePolicyEngine policyEngine;
    private final AdaptivePolicyState policyState;
    private ScheduledFuture<?> future;

    public AdaptiveClientSettingsModule(WellNetCore core, NetDiagnosticsModule netDiag) {
        super("AdaptiveClientSettings", core);
        this.core = core;
        this.netDiag = netDiag;
        this.policyEngine = new AdaptivePolicyEngine();
        this.policyState = new AdaptivePolicyState();
        this.policyState.reset(System.currentTimeMillis());
    }

    @Override
    public void initialize() {
        this.policyState.reset(System.currentTimeMillis());
        this.setStatus(WellNetCore.Status.OK);
    }

    @Override
    public void start() {
        if (this.isRunning()) {
            return;
        }
        this.policyState.reset(System.currentTimeMillis());
        this.setRunning(true);
        this.future = this.core.getScheduler().scheduleAtFixedRate(this::tickSafe, 2000L, 2000L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        this.setRunning(false);
        if (this.future != null) {
            this.future.cancel(false);
            this.future = null;
        }
        this.policyState.reset(System.currentTimeMillis());
    }

    public boolean isHostProfile() {
        return this.policyState.isHostProfile();
    }

    private void tickSafe() {
        try {
            this.tickAdaptiveLogic();
        } catch (Throwable throwable) {
            LOGGER.warn("Adaptive policy tick failed", throwable);
            this.setStatus(WellNetCore.Status.ERROR);
        }
    }

    private void tickAdaptiveLogic() {
        if (!this.isRunning() || !WellNetConfig.enabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        ClientSnapshot snapshot = ClientStateCache.getLast();
        if (snapshot == null || !snapshot.inWorld || !snapshot.hasPlayer) {
            this.policyState.reset(now);
            this.setStatus(WellNetCore.Status.OK);
            return;
        }
        if (snapshot.paused) {
            this.setStatus(WellNetCore.Status.OK);
            return;
        }

        NetDiagnosticsModule.WindowStats shortStats = this.netDiag.snapshotStatsShort();
        NetDiagnosticsModule.WindowStats mediumStats = this.netDiag.snapshotStats();
        if (shortStats == null || mediumStats == null) {
            this.setStatus(WellNetCore.Status.OK);
            return;
        }

        AdaptivePolicyContext context = new AdaptivePolicyContext(
            now,
            snapshot,
            shortStats,
            mediumStats,
            this.core.getNetworkStats(),
            AdaptivePolicyConfig.current()
        );
        AdaptivePolicyDecision decision = this.policyEngine.evaluate(context, this.policyState);
        ActionPlan appliedPlan = null;
        if (decision.hasChange()) {
            appliedPlan = decision.toActionPlan(now, context.serverId());
            this.core.publishActionPlan(appliedPlan);
        }
        ActionPlan tracePlan = decision.hasChange()
            ? appliedPlan
            : this.core.getLastPublishedActionPlan();
        this.core.recordPolicyTrace(new PolicyTraceSnapshot(
            now,
            context.serverId(),
            this.policyState.lastTrafficPhase(),
            this.policyState.lastTrend(),
            this.policyState.lastBlendedScore(),
            this.policyState.lastShortQuality().name(),
            tracePlan == null ? ActionPlan.Reason.UNKNOWN : tracePlan.reason,
            tracePlan == null ? ActionPlan.NO_RENDER_DISTANCE_CHANGE : tracePlan.targetRenderDistance,
            decision.hasChange(),
            this.policyState.lastPhaseNote()
        ));

        this.setStatus(WellNetCore.Status.OK);
    }
}
