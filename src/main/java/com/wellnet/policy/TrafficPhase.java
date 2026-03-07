package com.wellnet.policy;

public enum TrafficPhase {
    JOIN,
    SETTLING,
    STEADY,
    HOST_LOCAL,
    BURST;

    public boolean isEarlySession() {
        return this == JOIN || this == SETTLING;
    }

    public boolean isProtective() {
        return this == BURST || this == HOST_LOCAL;
    }
}
