package com.wellnet.policy;

import com.wellnet.core.ClientSnapshot;
import com.wellnet.core.NetDiagnosticsModule;
import com.wellnet.core.NetworkStats;

public record AdaptivePolicyContext(
    long nowMs,
    ClientSnapshot snapshot,
    NetDiagnosticsModule.WindowStats shortStats,
    NetDiagnosticsModule.WindowStats mediumStats,
    NetworkStats networkStats,
    AdaptivePolicyConfig config
) {
    public String serverId() {
        return this.snapshot.serverId == null ? "" : this.snapshot.serverId;
    }
}
