/*
 * Decompiled with CFR 0.152.
 */
package com.wellnet.client;

import com.wellnet.client.AdaptiveActionApplier;
import com.wellnet.core.ActionPlan;
import com.wellnet.core.WellNetCore;

public final class MainThreadApplier {
    private MainThreadApplier() {
    }

    public static void applyPendingActionPlanFromMinecraft(WellNetCore wellNetCore) {
        if (wellNetCore == null) {
            return;
        }
        ActionPlan actionPlan = wellNetCore.pollActionPlanForMainThread();
        AdaptiveActionApplier.applyFromMinecraft(wellNetCore, actionPlan);
    }
}

