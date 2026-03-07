package com.wellnet.client;

import com.wellnet.config.WellNetConfig;
import com.wellnet.core.ActionPlan;
import com.wellnet.core.WellNetCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdaptiveActionApplier {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveActionApplier.class);
    private static final Object STATE_LOCK = new Object();
    private static int baselineRenderDistance = -1;
    private static int userPreferredMax = -1;
    private static int lastObservedRd = -1;
    private static int lastAppliedByWellNet = -1;
    private static ActionPlan pendingPlan;
    private static long lastApplyMs = 0L;
    private static long lastSaveMs = 0L;
    private static long lastChangeMs = 0L;
    private static boolean pendingSave = false;

    private AdaptiveActionApplier() {
    }

    public static void reset() {
        synchronized (STATE_LOCK) {
            resetState();
        }
    }

    public static void onDisconnectFromMinecraft() {
        synchronized (STATE_LOCK) {
            try {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null || minecraft.options == null) {
                    return;
                }

                Options options = minecraft.options;
                if (WellNetConfig.restoreOnExit() && baselineRenderDistance >= 0) {
                    int currentRenderDistance = options.renderDistance().get();
                    if (currentRenderDistance >= 0 && currentRenderDistance != baselineRenderDistance) {
                        options.renderDistance().set(baselineRenderDistance);
                        lastChangeMs = System.currentTimeMillis();
                        pendingSave = true;
                    }
                }
                forceSaveNow(options);
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to restore client settings during disconnect", throwable);
            } finally {
                resetState();
            }
        }
    }

    public static void applyFromMinecraft(WellNetCore core, ActionPlan actionPlan) {
        if (core == null) {
            return;
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return;
            }
            applyFromMinecraftInstance(minecraft, core, actionPlan);
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to apply adaptive action plan", throwable);
        }
    }

    public static void applyFromMinecraftInstance(Minecraft minecraft, WellNetCore core, ActionPlan actionPlan) {
        synchronized (STATE_LOCK) {
            if (minecraft == null || core == null || minecraft.options == null) {
                return;
            }

            if (actionPlan != null) {
                pendingPlan = actionPlan;
            }

            Options options = minecraft.options;
            if (minecraft.level == null || minecraft.player == null) {
                maybeSave(options, System.currentTimeMillis());
                return;
            }
            if (minecraft.isPaused()) {
                maybeSave(options, System.currentTimeMillis());
                return;
            }

            int currentRenderDistance = options.renderDistance().get();
            long nowMs = System.currentTimeMillis();
            if (baselineRenderDistance < 0) {
                baselineRenderDistance = currentRenderDistance;
            }
            if (userPreferredMax < 0) {
                userPreferredMax = currentRenderDistance;
            }
            if (currentRenderDistance != lastObservedRd) {
                if (lastAppliedByWellNet < 0 || currentRenderDistance != lastAppliedByWellNet) {
                    userPreferredMax = currentRenderDistance;
                    baselineRenderDistance = currentRenderDistance;
                }
                lastObservedRd = currentRenderDistance;
            }

            maybeSave(options, nowMs);
            if (!WellNetConfig.enabled()) {
                return;
            }

            ActionPlan plan = pendingPlan;
            if (plan == null) {
                return;
            }
            if (plan.targetRenderDistance == ActionPlan.NO_RENDER_DISTANCE_CHANGE) {
                pendingPlan = null;
                return;
            }

            int minRenderDistance = WellNetConfig.renderDistanceMin();
            int preferredMax = userPreferredMax > 0 ? userPreferredMax : currentRenderDistance;
            int configMax = WellNetConfig.renderDistanceMax();
            int effectiveMax = Math.min(preferredMax, configMax);
            if (effectiveMax < minRenderDistance) {
                effectiveMax = minRenderDistance;
            }

            int desiredRenderDistance = clamp(plan.targetRenderDistance, minRenderDistance, effectiveMax);
            long cooldownMs = WellNetConfig.renderDistanceCooldownMs();
            if (plan.reason == ActionPlan.Reason.RECOVERY) {
                cooldownMs = Math.max(cooldownMs, 60000L);
            } else if (plan.reason == ActionPlan.Reason.HOST_PRESSURE) {
                cooldownMs = Math.max(cooldownMs, 90000L);
            }
            long sinceLastApplyMs = lastApplyMs == 0L ? Long.MAX_VALUE : nowMs - lastApplyMs;
            boolean loweringDistance = desiredRenderDistance < currentRenderDistance;
            boolean emergencyDownshift = loweringDistance
                && (plan.reason == ActionPlan.Reason.CLIENT_LAG || plan.reason == ActionPlan.Reason.SPIKES)
                && plan.confidence >= 0.75;

            if (cooldownMs > 0L && sinceLastApplyMs < cooldownMs) {
                if (!emergencyDownshift) {
                    return;
                }
                long emergencyMinIntervalMs = Math.max(1000L, Math.min(WellNetConfig.decisionIntervalMs(), 10000L));
                if (sinceLastApplyMs < emergencyMinIntervalMs) {
                    return;
                }
            }

            boolean directLoweringReason = loweringDistance
                && (plan.reason == ActionPlan.Reason.HOST_PRESSURE
                || plan.reason == ActionPlan.Reason.BURST_GUARD
                || plan.reason == ActionPlan.Reason.AFK);

            int stepLimitedRenderDistance = desiredRenderDistance;
            if (!directLoweringReason) {
                if (stepLimitedRenderDistance > currentRenderDistance + 1) {
                    stepLimitedRenderDistance = currentRenderDistance + 1;
                }
                if (stepLimitedRenderDistance < currentRenderDistance - 2) {
                    stepLimitedRenderDistance = currentRenderDistance - 2;
                }
            }
            if (stepLimitedRenderDistance == currentRenderDistance) {
                pendingPlan = null;
                return;
            }

            options.renderDistance().set(stepLimitedRenderDistance);
            lastApplyMs = nowMs;
            lastChangeMs = nowMs;
            lastAppliedByWellNet = stepLimitedRenderDistance;
            pendingSave = true;
            if (stepLimitedRenderDistance == desiredRenderDistance) {
                pendingPlan = null;
            }
            maybeSave(options, nowMs);
        }
    }

    private static void maybeSave(Options options, long nowMs) {
        if (!pendingSave || options == null) {
            return;
        }

        long sinceLastSaveMs = lastSaveMs == 0L ? Long.MAX_VALUE : nowMs - lastSaveMs;
        long sinceLastChangeMs = lastChangeMs == 0L ? Long.MAX_VALUE : nowMs - lastChangeMs;
        boolean hitMaxInterval = sinceLastSaveMs >= 120000L;
        boolean hitSteadyInterval = sinceLastSaveMs >= 60000L && sinceLastChangeMs >= 15000L;
        if (!hitMaxInterval && !hitSteadyInterval) {
            return;
        }

        options.save();
        lastSaveMs = nowMs;
        pendingSave = false;
    }

    private static void forceSaveNow(Options options) {
        if (!pendingSave || options == null) {
            return;
        }

        options.save();
        lastSaveMs = System.currentTimeMillis();
        pendingSave = false;
    }

    private static void resetState() {
        baselineRenderDistance = -1;
        userPreferredMax = -1;
        lastObservedRd = -1;
        lastAppliedByWellNet = -1;
        pendingPlan = null;
        lastApplyMs = 0L;
        lastSaveMs = 0L;
        lastChangeMs = 0L;
        pendingSave = false;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
