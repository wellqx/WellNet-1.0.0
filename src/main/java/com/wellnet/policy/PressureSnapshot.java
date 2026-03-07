package com.wellnet.policy;

public record PressureSnapshot(
    boolean framePressure,
    boolean severeFramePressure,
    boolean trafficPressure,
    boolean spikePressure,
    boolean severeBurst,
    boolean fallbackPoorWindow,
    double frameSeverity,
    String note
) {
}
