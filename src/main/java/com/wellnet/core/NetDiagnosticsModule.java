package com.wellnet.core;

import com.wellnet.config.WellNetConfig;
import com.wellnet.net.TrafficManager;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetDiagnosticsModule extends WellNetCore.Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetDiagnosticsModule.class);
    private static final int DEFAULT_SHORT_SAMPLES = 30;
    private static final int DEFAULT_MEDIUM_SAMPLES = 120;

    private final WellNetCore core;
    private final Source legacySource;
    private final long pollIntervalMillis;
    private final IntRingWindow shortPing;
    private final IntRingWindow mediumPing;
    private final IntRingWindow shortFrameMs;
    private final IntRingWindow mediumFrameMs;
    private final LongRingWindow shortInBps;
    private final LongRingWindow mediumInBps;
    private final LongRingWindow shortOutBps;
    private final LongRingWindow mediumOutBps;

    private volatile WindowComputed lastShort;
    private volatile WindowComputed lastMedium;
    private ScheduledFuture<?> future;
    private String activeServerId = "";
    private int lastPing = -1;
    private double ewmaJitter = 0.0;
    private long lastTrafficSampleNs = 0L;
    private long lastInBytes = 0L;
    private long lastOutBytes = 0L;
    private long lastInBpsSample = -1L;
    private long lastOutBpsSample = -1L;

    public NetDiagnosticsModule(WellNetCore core, Source source, long pollIntervalMillis) {
        super("NetDiagnostics", core);
        this.core = Objects.requireNonNull(core, "core");
        this.legacySource = source;
        this.pollIntervalMillis = Math.max(200L, pollIntervalMillis);
        this.shortPing = new IntRingWindow(DEFAULT_SHORT_SAMPLES);
        this.mediumPing = new IntRingWindow(DEFAULT_MEDIUM_SAMPLES);
        this.shortFrameMs = new IntRingWindow(DEFAULT_SHORT_SAMPLES);
        this.mediumFrameMs = new IntRingWindow(DEFAULT_MEDIUM_SAMPLES);
        this.shortInBps = new LongRingWindow(DEFAULT_SHORT_SAMPLES);
        this.mediumInBps = new LongRingWindow(DEFAULT_MEDIUM_SAMPLES);
        this.shortOutBps = new LongRingWindow(DEFAULT_SHORT_SAMPLES);
        this.mediumOutBps = new LongRingWindow(DEFAULT_MEDIUM_SAMPLES);
    }

    @Override
    public void initialize() {
        this.setStatus(WellNetCore.Status.OK);
    }

    @Override
    public void start() {
        if (this.isRunning()) {
            return;
        }
        this.setRunning(true);
        this.future = this.core.getScheduler().scheduleAtFixedRate(this::tickSafe, 0L, this.pollIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        this.setRunning(false);
        if (this.future != null) {
            this.future.cancel(false);
            this.future = null;
        }
        this.resetForNewServer("");
    }

    public WindowStats snapshotStats() {
        WindowComputed window = this.lastMedium;
        if (window == null) {
            return null;
        }
        return new WindowStats(window.avgPingMillis, window.jitterMillis, window.latePercent, window.quality, window.qualityScore);
    }

    public WindowStats snapshotStatsShort() {
        WindowComputed window = this.lastShort;
        if (window == null) {
            return null;
        }
        return new WindowStats(window.avgPingMillis, window.jitterMillis, window.latePercent, window.quality, window.qualityScore);
    }

    private void tickSafe() {
        try {
            this.tick();
        } catch (Throwable throwable) {
            LOGGER.warn("Net diagnostics tick failed", throwable);
            this.setStatus(WellNetCore.Status.ERROR);
        }
    }

    private void tick() {
        if (!this.isRunning() || !WellNetConfig.enabled()) {
            return;
        }

        boolean consumedSnapshot = false;
        ClientSnapshot clientSnapshot;
        while ((clientSnapshot = this.core.pollSnapshot()) != null) {
            consumedSnapshot = true;
            if (clientSnapshot.serverId != null && !clientSnapshot.serverId.equals(this.activeServerId)) {
                this.resetForNewServer(clientSnapshot.serverId);
            }

            int pingMillis = this.resolvePingMillis(clientSnapshot);
            if (pingMillis > 0) {
                if (this.lastPing > 0) {
                    int delta = Math.abs(pingMillis - this.lastPing);
                    this.ewmaJitter = 0.25 * delta + 0.75 * this.ewmaJitter;
                }
                this.lastPing = pingMillis;
                this.shortPing.add(pingMillis);
                this.mediumPing.add(pingMillis);
            }

            double frameTimeMs = clientSnapshot.frameTimeMs;
            if (!Double.isNaN(frameTimeMs) && frameTimeMs > 0.0) {
                int roundedFrameMs = (int) Math.round(frameTimeMs);
                if (roundedFrameMs > 0) {
                    this.shortFrameMs.add(roundedFrameMs);
                    this.mediumFrameMs.add(roundedFrameMs);
                }
            }
        }

        this.sampleTrafficBps();
        FrameComputed shortFrameComputed = this.computeFrame(this.shortFrameMs);
        FrameComputed mediumFrameComputed = this.computeFrame(this.mediumFrameMs);
        LongWindowStats shortInboundWindow = computeLongWindow(this.shortInBps);
        LongWindowStats mediumInboundWindow = computeLongWindow(this.mediumInBps);
        LongWindowStats shortOutboundWindow = computeLongWindow(this.shortOutBps);
        LongWindowStats mediumOutboundWindow = computeLongWindow(this.mediumOutBps);

        WindowComputed shortWindow = this.computePing(this.shortPing, Math.round(this.ewmaJitter));
        if (shortWindow == null) {
            shortWindow = this.computeFallbackWindow(shortFrameComputed, shortInboundWindow, shortOutboundWindow, this.shortFrameMs.size());
        }

        WindowComputed mediumWindow = this.computePing(this.mediumPing, Math.round(this.ewmaJitter));
        if (mediumWindow == null) {
            mediumWindow = this.computeFallbackWindow(mediumFrameComputed, mediumInboundWindow, mediumOutboundWindow, this.mediumFrameMs.size());
        }

        this.lastShort = shortWindow;
        this.lastMedium = mediumWindow;

        if (mediumWindow != null && mediumWindow.samples > 0) {
            long avgFrameMs = mediumFrameComputed == null ? -1L : mediumFrameComputed.avgFrameMs();
            long p95FrameMs = mediumFrameComputed == null ? -1L : mediumFrameComputed.p95FrameMs();
            long p99FrameMs = mediumFrameComputed == null ? -1L : mediumFrameComputed.p99FrameMs();
            int frameSpikeRate = mediumFrameComputed == null ? -1 : mediumFrameComputed.frameSpikeRatePercent();
            int frameStallRate = mediumFrameComputed == null ? -1 : mediumFrameComputed.frameStallRatePercent();

            NetworkStats networkStats = new NetworkStats(
                System.currentTimeMillis(),
                this.activeServerId,
                mediumWindow.samples,
                mediumWindow.avgPingMillis,
                mediumWindow.minPingMillis,
                mediumWindow.maxPingMillis,
                mediumWindow.jitterMillis,
                mediumWindow.latePercent,
                mediumWindow.quality.name(),
                mediumWindow.qualityScore,
                mediumWindow.p95PingMillis,
                mediumWindow.p99PingMillis,
                mediumWindow.spikeRatePercent,
                this.lastInBpsSample,
                this.lastOutBpsSample,
                mediumInboundWindow.avg(),
                mediumInboundWindow.max(),
                mediumInboundWindow.burstinessPercent(),
                mediumOutboundWindow.avg(),
                mediumOutboundWindow.max(),
                mediumOutboundWindow.burstinessPercent(),
                avgFrameMs,
                p95FrameMs,
                p99FrameMs,
                frameSpikeRate,
                frameStallRate
            );
            this.core.publishNetworkStats(networkStats);
            this.setStatus(WellNetCore.Status.OK);
            this.setLastPing((int) mediumWindow.avgPingMillis);
        } else if (consumedSnapshot) {
            this.setStatus(WellNetCore.Status.OK);
        }
    }

    private int resolvePingMillis(ClientSnapshot clientSnapshot) {
        if (clientSnapshot != null && clientSnapshot.pingMs > 0) {
            return clientSnapshot.pingMs;
        }
        if (this.legacySource == null) {
            return -1;
        }
        try {
            return this.legacySource.currentPingMillis();
        } catch (Throwable throwable) {
            LOGGER.debug("Legacy ping source failed", throwable);
            return -1;
        }
    }

    private void resetForNewServer(String serverId) {
        this.activeServerId = serverId == null ? "" : serverId;
        this.lastPing = -1;
        this.ewmaJitter = 0.0;
        this.shortPing.clear();
        this.mediumPing.clear();
        this.shortFrameMs.clear();
        this.mediumFrameMs.clear();
        this.shortInBps.clear();
        this.mediumInBps.clear();
        this.shortOutBps.clear();
        this.mediumOutBps.clear();
        this.lastShort = null;
        this.lastMedium = null;
        this.core.publishNetworkStats(null);
        this.lastTrafficSampleNs = 0L;
        this.lastInBytes = 0L;
        this.lastOutBytes = 0L;
        this.lastInBpsSample = -1L;
        this.lastOutBpsSample = -1L;
        TrafficManager.publishBps(-1L, -1L);
    }

    private void sampleTrafficBps() {
        try {
            long nowNs = System.nanoTime();
            long totalInboundBytes = TrafficManager.getTotalInboundBytes();
            long totalOutboundBytes = TrafficManager.getTotalOutboundBytes();

            if (this.lastTrafficSampleNs <= 0L) {
                this.lastTrafficSampleNs = nowNs;
                this.lastInBytes = totalInboundBytes;
                this.lastOutBytes = totalOutboundBytes;
                this.lastInBpsSample = -1L;
                this.lastOutBpsSample = -1L;
                TrafficManager.publishBps(-1L, -1L);
                return;
            }

            long elapsedNs = nowNs - this.lastTrafficSampleNs;
            if (elapsedNs <= 0L) {
                return;
            }
            if (elapsedNs > 30_000_000_000L) {
                this.lastTrafficSampleNs = nowNs;
                this.lastInBytes = totalInboundBytes;
                this.lastOutBytes = totalOutboundBytes;
                this.lastInBpsSample = -1L;
                this.lastOutBpsSample = -1L;
                TrafficManager.publishBps(-1L, -1L);
                return;
            }

            long deltaInboundBytes = totalInboundBytes - this.lastInBytes;
            long deltaOutboundBytes = totalOutboundBytes - this.lastOutBytes;
            if (deltaInboundBytes < 0L || deltaOutboundBytes < 0L) {
                this.lastTrafficSampleNs = nowNs;
                this.lastInBytes = totalInboundBytes;
                this.lastOutBytes = totalOutboundBytes;
                this.lastInBpsSample = -1L;
                this.lastOutBpsSample = -1L;
                TrafficManager.publishBps(-1L, -1L);
                this.shortInBps.clear();
                this.mediumInBps.clear();
                this.shortOutBps.clear();
                this.mediumOutBps.clear();
                return;
            }

            double elapsedSeconds = elapsedNs / 1_000_000_000.0;
            if (elapsedSeconds <= 0.0) {
                return;
            }

            long inboundBps = Math.round(deltaInboundBytes * 8.0 / elapsedSeconds);
            long outboundBps = Math.round(deltaOutboundBytes * 8.0 / elapsedSeconds);
            this.lastTrafficSampleNs = nowNs;
            this.lastInBytes = totalInboundBytes;
            this.lastOutBytes = totalOutboundBytes;
            this.lastInBpsSample = inboundBps;
            this.lastOutBpsSample = outboundBps;
            this.shortInBps.add(inboundBps);
            this.mediumInBps.add(inboundBps);
            this.shortOutBps.add(outboundBps);
            this.mediumOutBps.add(outboundBps);
            TrafficManager.publishBps(inboundBps, outboundBps);
        } catch (Throwable throwable) {
            LOGGER.debug("Traffic sampling failed", throwable);
        }
    }

    private WindowComputed computePing(IntRingWindow window, long jitterMillis) {
        int samples = window.size();
        if (samples <= 0) {
            return null;
        }

        int[] values = window.toArray();
        long total = 0L;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int lateSamples = 0;
        int spikeSamples = 0;
        int previousSample = -1;

        for (int sample : values) {
            total += sample;
            if (sample < min) {
                min = sample;
            }
            if (sample > max) {
                max = sample;
            }
            if (sample >= WellNetConfig.lateThresholdMs()) {
                lateSamples++;
            }
            if (previousSample > 0) {
                int delta = Math.abs(sample - previousSample);
                if (sample >= WellNetConfig.spikePingThresholdMs() || delta >= WellNetConfig.spikeDeltaThresholdMs()) {
                    spikeSamples++;
                }
            }
            previousSample = sample;
        }

        long avgPingMillis = Math.round((double) total / samples);
        int latePercent = (int) Math.round(lateSamples * 100.0 / samples);
        int spikeRatePercent = (int) Math.round(spikeSamples * 100.0 / Math.max(1, samples - 1));
        long p95PingMillis = percentile(values, 0.95);
        long p99PingMillis = percentile(values, 0.99);
        Quality quality = classifyQuality(avgPingMillis, jitterMillis, spikeRatePercent);
        int qualityScore = computeQualityScore(avgPingMillis, jitterMillis, spikeRatePercent);
        return new WindowComputed(
            samples,
            avgPingMillis,
            min,
            max,
            jitterMillis,
            latePercent,
            p95PingMillis,
            p99PingMillis,
            spikeRatePercent,
            quality,
            qualityScore
        );
    }

    private WindowComputed computeFallbackWindow(
        FrameComputed frameComputed,
        LongWindowStats inboundWindow,
        LongWindowStats outboundWindow,
        int frameSamples
    ) {
        int sampleCount = Math.max(frameSamples, Math.max(inboundWindow.samples(), outboundWindow.samples()));
        if (sampleCount <= 0) {
            return null;
        }

        int qualityScore = this.computeFallbackQualityScore(frameComputed, inboundWindow, outboundWindow);
        Quality quality = classifyFallbackQuality(qualityScore);
        return new WindowComputed(
            sampleCount,
            -1L,
            -1L,
            -1L,
            -1L,
            -1,
            -1L,
            -1L,
            -1,
            quality,
            qualityScore
        );
    }

    private FrameComputed computeFrame(IntRingWindow window) {
        int samples = window.size();
        if (samples <= 0) {
            return null;
        }

        int[] values = window.toArray();
        if (values.length == 0) {
            return null;
        }

        long total = 0L;
        int spikeSamples = 0;
        int stallSamples = 0;
        int frameSpikeThresholdMs = WellNetConfig.frameSpikeThresholdMs();
        int frameStallThresholdMs = WellNetConfig.frameStallThresholdMs();
        for (int value : values) {
            total += value;
            if (frameSpikeThresholdMs > 0 && value >= frameSpikeThresholdMs) {
                spikeSamples++;
            }
            if (frameStallThresholdMs > 0 && value >= frameStallThresholdMs) {
                stallSamples++;
            }
        }

        long avgFrameMs = Math.round((double) total / values.length);
        long p95FrameMs = percentile(values, 0.95);
        long p99FrameMs = percentile(values, 0.99);
        int frameSpikeRatePercent = frameSpikeThresholdMs > 0 ? (int) Math.round(spikeSamples * 100.0 / values.length) : -1;
        int frameStallRatePercent = frameStallThresholdMs > 0 ? (int) Math.round(stallSamples * 100.0 / values.length) : -1;
        return new FrameComputed(avgFrameMs, p95FrameMs, p99FrameMs, frameSpikeRatePercent, frameStallRatePercent);
    }

    private static long percentile(int[] values, double quantile) {
        if (values == null || values.length == 0) {
            return -1L;
        }

        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(quantile * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index];
    }

    private static Quality classifyQuality(long avgPingMillis, long jitterMillis, int spikeRatePercent) {
        if (avgPingMillis <= WellNetConfig.pingExcellentMaxMs()
            && jitterMillis <= WellNetConfig.jitterExcellentMaxMs()
            && spikeRatePercent <= WellNetConfig.spikeExcellentMaxPct()) {
            return Quality.EXCELLENT;
        }
        if (avgPingMillis <= WellNetConfig.pingGoodMaxMs()
            && jitterMillis <= WellNetConfig.jitterGoodMaxMs()
            && spikeRatePercent <= WellNetConfig.spikeGoodMaxPct()) {
            return Quality.GOOD;
        }
        if (avgPingMillis <= WellNetConfig.pingFairMaxMs()
            && jitterMillis <= WellNetConfig.jitterFairMaxMs()
            && spikeRatePercent <= WellNetConfig.spikeFairMaxPct()) {
            return Quality.FAIR;
        }
        return Quality.POOR;
    }

    private static int computeQualityScore(long avgPingMillis, long jitterMillis, int spikeRatePercent) {
        double score = 100.0;
        score -= Math.max(0.0, (avgPingMillis - 40.0) * 0.25);
        score -= Math.max(0.0, jitterMillis * 0.9);
        score -= Math.max(0.0, spikeRatePercent * 1.5);
        return clampScore(score);
    }

    private int computeFallbackQualityScore(
        FrameComputed frameComputed,
        LongWindowStats inboundWindow,
        LongWindowStats outboundWindow
    ) {
        double score = 100.0;

        if (frameComputed != null) {
            if (frameComputed.avgFrameMs() > 0L) {
                score -= Math.max(0.0, frameComputed.avgFrameMs() - 25.0) * 1.2;
            }
            if (frameComputed.p95FrameMs() > 0L && WellNetConfig.frameSpikeThresholdMs() > 0) {
                score -= Math.max(0.0, frameComputed.p95FrameMs() - WellNetConfig.frameSpikeThresholdMs()) * 0.35;
            }
            if (frameComputed.p99FrameMs() > 0L && WellNetConfig.frameSpikeThresholdMs() > 0) {
                score -= Math.max(0.0, frameComputed.p99FrameMs() - WellNetConfig.frameSpikeThresholdMs()) * 0.20;
            }
            if (frameComputed.frameSpikeRatePercent() >= 0) {
                score -= frameComputed.frameSpikeRatePercent() * 1.4;
            }
            if (frameComputed.frameStallRatePercent() >= 0) {
                score -= frameComputed.frameStallRatePercent() * 4.0;
            }
        }

        score -= this.computeBurstinessPenalty(inboundWindow);
        score -= this.computeBurstinessPenalty(outboundWindow);
        score -= this.computeThroughputPenalty(inboundWindow.avg(), WellNetConfig.targetInboundBps());
        score -= this.computeThroughputPenalty(outboundWindow.avg(), WellNetConfig.targetOutboundBps());

        return clampScore(score);
    }

    private double computeBurstinessPenalty(LongWindowStats window) {
        if (window == null || window.burstinessPercent() <= 0) {
            return 0.0;
        }
        return Math.max(0.0, (window.burstinessPercent() - 100.0) * 0.08);
    }

    private double computeThroughputPenalty(long averageBps, long targetBps) {
        if (averageBps < 0L || targetBps <= 0L || averageBps <= targetBps) {
            return 0.0;
        }
        return Math.min(25.0, (averageBps - targetBps) * 25.0 / targetBps);
    }

    private static Quality classifyFallbackQuality(int qualityScore) {
        if (qualityScore >= 85) {
            return Quality.EXCELLENT;
        }
        if (qualityScore >= 70) {
            return Quality.GOOD;
        }
        if (qualityScore >= 45) {
            return Quality.FAIR;
        }
        return Quality.POOR;
    }

    private static int clampScore(double score) {
        int rounded = (int) Math.round(score);
        if (rounded < 0) {
            return 0;
        }
        if (rounded > 100) {
            return 100;
        }
        return rounded;
    }

    private static LongWindowStats computeLongWindow(LongRingWindow window) {
        int samples = window.size();
        if (samples <= 0) {
            return new LongWindowStats(-1L, -1L, -1, 0);
        }

        long[] values = window.toArray();
        long total = 0L;
        long max = Long.MIN_VALUE;
        int validSamples = 0;
        for (long value : values) {
            if (value < 0L) {
                continue;
            }
            validSamples++;
            total += value;
            if (value > max) {
                max = value;
            }
        }

        if (validSamples <= 0) {
            return new LongWindowStats(-1L, -1L, -1, 0);
        }

        long avg = Math.round((double) total / validSamples);
        int burstinessPercent = avg > 0L ? (int) Math.round(max * 100.0 / avg) : -1;
        return new LongWindowStats(avg, max, burstinessPercent, validSamples);
    }

    public interface Source {
        int currentPingMillis();
    }

    private static final class IntRingWindow {
        private final int[] buffer;
        private int writeIndex = 0;
        private int size = 0;

        IntRingWindow(int size) {
            this.buffer = new int[Math.max(1, size)];
        }

        void add(int value) {
            this.buffer[this.writeIndex] = value;
            this.writeIndex = (this.writeIndex + 1) % this.buffer.length;
            if (this.size < this.buffer.length) {
                this.size++;
            }
        }

        void clear() {
            this.writeIndex = 0;
            this.size = 0;
        }

        int size() {
            return this.size;
        }

        int[] toArray() {
            int[] values = new int[this.size];
            if (this.size == 0) {
                return values;
            }

            int start = this.writeIndex - this.size;
            while (start < 0) {
                start += this.buffer.length;
            }
            for (int i = 0; i < this.size; i++) {
                values[i] = this.buffer[(start + i) % this.buffer.length];
            }
            return values;
        }
    }

    private static final class LongRingWindow {
        private final long[] buffer;
        private int writeIndex = 0;
        private int size = 0;

        LongRingWindow(int size) {
            this.buffer = new long[Math.max(1, size)];
        }

        void add(long value) {
            this.buffer[this.writeIndex] = value;
            this.writeIndex = (this.writeIndex + 1) % this.buffer.length;
            if (this.size < this.buffer.length) {
                this.size++;
            }
        }

        void clear() {
            this.writeIndex = 0;
            this.size = 0;
        }

        int size() {
            return this.size;
        }

        long[] toArray() {
            long[] values = new long[this.size];
            if (this.size == 0) {
                return values;
            }

            int start = this.writeIndex - this.size;
            while (start < 0) {
                start += this.buffer.length;
            }
            for (int i = 0; i < this.size; i++) {
                values[i] = this.buffer[(start + i) % this.buffer.length];
            }
            return values;
        }
    }

    private static final class WindowComputed {
        final int samples;
        final long avgPingMillis;
        final long minPingMillis;
        final long maxPingMillis;
        final long jitterMillis;
        final int latePercent;
        final long p95PingMillis;
        final long p99PingMillis;
        final int spikeRatePercent;
        final Quality quality;
        final int qualityScore;

        WindowComputed(
            int samples,
            long avgPingMillis,
            long minPingMillis,
            long maxPingMillis,
            long jitterMillis,
            int latePercent,
            long p95PingMillis,
            long p99PingMillis,
            int spikeRatePercent,
            Quality quality,
            int qualityScore
        ) {
            this.samples = samples;
            this.avgPingMillis = avgPingMillis;
            this.minPingMillis = minPingMillis;
            this.maxPingMillis = maxPingMillis;
            this.jitterMillis = jitterMillis;
            this.latePercent = latePercent;
            this.p95PingMillis = p95PingMillis;
            this.p99PingMillis = p99PingMillis;
            this.spikeRatePercent = spikeRatePercent;
            this.quality = quality;
            this.qualityScore = qualityScore;
        }
    }

    public record WindowStats(long avgPingMillis, long jitterMillis, int avgLossPercent, Quality quality, int qualityScore) {
        public int latePercent() {
            return this.avgLossPercent;
        }
    }

    public enum Quality {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }

    private record FrameComputed(long avgFrameMs, long p95FrameMs, long p99FrameMs, int frameSpikeRatePercent, int frameStallRatePercent) {
    }

    private record LongWindowStats(long avg, long max, int burstinessPercent, int samples) {
    }
}
