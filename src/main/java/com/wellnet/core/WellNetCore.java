package com.wellnet.core;

import com.wellnet.client.AdaptiveActionApplier;
import com.wellnet.client.MainThreadApplier;
import com.wellnet.config.WellNetConfig;
import com.wellnet.net.TrafficManager;
import com.wellnet.policy.PolicyTraceSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WellNetCore {
    private static final Logger LOGGER = LoggerFactory.getLogger(WellNetCore.class);
    private static final int MAX_SNAPSHOT_QUEUE = 512;
    private static final int MAX_POLICY_TRACE = 64;
    private static final long TRAFFIC_HOOK_GRACE_MS = 3000L;
    private static volatile WellNetCore INSTANCE;

    private final Instant startedAt = Instant.now();
    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService scheduler;
    private final ModuleManager moduleManager;
    private final MessageBus messageBus;
    private final ConcurrentLinkedQueue<ClientSnapshot> snapshotQueue;
    private final AtomicInteger snapshotQueueSize;
    private final AtomicReference<ActionPlan> actionPlanRef;
    private final AtomicReference<ActionPlan> lastPublishedActionPlan;
    private final AtomicLong lastActionDecisionMs;
    private final AtomicReference<NetworkStats> networkStatsRef;
    private final ConcurrentLinkedQueue<PolicyTraceSnapshot> policyTraceQueue;
    private final AtomicInteger policyTraceSize;
    private final Object actionPlanLock = new Object();

    private volatile long connectStartTime = -1L;
    private volatile long worldLoadedTime = -1L;
    private volatile long lastUptimeSeconds = 0L;
    private ScheduledFuture<?> healthFuture;

    public static WellNetCore getInstance() {
        return INSTANCE;
    }

    public static void setInstance(WellNetCore wellNetCore) {
        INSTANCE = wellNetCore;
    }

    public WellNetCore() {
        int workerThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int schedulerThreads = Math.max(1, workerThreads / 2);
        this.workerExecutor = Executors.newFixedThreadPool(workerThreads, new NamedThreadFactory("WellNetWorker"));
        this.scheduler = Executors.newScheduledThreadPool(schedulerThreads, new NamedThreadFactory("WellNetScheduler"));
        this.messageBus = new MessageBus();
        this.moduleManager = new ModuleManager(this);
        this.snapshotQueue = new ConcurrentLinkedQueue<>();
        this.snapshotQueueSize = new AtomicInteger(0);
        this.actionPlanRef = new AtomicReference<>();
        this.lastPublishedActionPlan = new AtomicReference<>();
        this.lastActionDecisionMs = new AtomicLong(0L);
        this.networkStatsRef = new AtomicReference<>();
        this.policyTraceQueue = new ConcurrentLinkedQueue<>();
        this.policyTraceSize = new AtomicInteger(0);
        WellNetCore.setInstance(this);

        try {
            WellNetConfig.initIfNeeded();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to initialize WellNet config", throwable);
        }

        this.healthFuture = this.scheduler.scheduleAtFixedRate(this::logStatusReportSafe, 60L, 60L, TimeUnit.SECONDS);
    }

    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    public MessageBus getMessageBus() {
        return this.messageBus;
    }

    public ExecutorService getWorkerExecutor() {
        return this.workerExecutor;
    }

    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }

    public void onConnectStart() {
        clearSessionData();
        this.connectStartTime = System.currentTimeMillis() / 1000L;
    }

    public void onWorldLoaded() {
        if (this.connectStartTime < 0L) {
            return;
        }

        this.worldLoadedTime = System.currentTimeMillis() / 1000L;
        this.lastUptimeSeconds = Math.max(0L, this.worldLoadedTime - this.connectStartTime);
    }

    public void onWorldUnload() {
        this.worldLoadedTime = -1L;
        this.lastUptimeSeconds = 0L;
        clearSnapshotQueue();
        this.actionPlanRef.set(null);
        this.networkStatsRef.set(null);
    }

    public void onDisconnect() {
        stopModulesSafely("disconnect");
        clearSessionData();
        AdaptiveActionApplier.onDisconnectFromMinecraft();
        ClientStateCache.reset();
        TrafficManager.reset();
    }

    public void shutdown() {
        stopModulesSafely("shutdown");
        clearSessionData();
        AdaptiveActionApplier.onDisconnectFromMinecraft();
        ClientStateCache.reset();
        TrafficManager.reset();

        if (this.healthFuture != null) {
            this.healthFuture.cancel(false);
            this.healthFuture = null;
        }

        try {
            this.scheduler.shutdownNow();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to stop WellNet scheduler", throwable);
        }

        try {
            this.workerExecutor.shutdownNow();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to stop WellNet worker executor", throwable);
        }

        WellNetCore.setInstance(null);
    }

    public long uptimeSeconds() {
        long startedAtSeconds = this.connectStartTime;
        if (startedAtSeconds <= 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() / 1000L) - startedAtSeconds);
    }

    public String debugUptimeState() {
        return String.format("connectStart=%d, worldLoaded=%d, lastUptime=%ds", this.connectStartTime, this.worldLoadedTime, this.lastUptimeSeconds);
    }

    public long getConnectStartTimeSeconds() {
        return this.connectStartTime;
    }

    public ActionPlan getLastPublishedActionPlan() {
        return this.lastPublishedActionPlan.get();
    }

    public void pushSnapshotFromMainThread(ClientSnapshot clientSnapshot) {
        if (clientSnapshot == null || !WellNetConfig.enabled()) {
            return;
        }

        this.snapshotQueue.add(clientSnapshot);
        int queueSize = this.snapshotQueueSize.incrementAndGet();
        while (queueSize > MAX_SNAPSHOT_QUEUE) {
            ClientSnapshot discardedSnapshot = this.snapshotQueue.poll();
            if (discardedSnapshot == null) {
                this.snapshotQueueSize.set(0);
                break;
            }
            queueSize = this.snapshotQueueSize.decrementAndGet();
        }

        ClientStateCache.update(clientSnapshot);
        if (this.shouldAttachTrafficHook(clientSnapshot)) {
            TrafficManager.ensureInstalledAndSampleFromMinecraft();
        }
        MainThreadApplier.applyPendingActionPlanFromMinecraft(this);
    }

    public ClientSnapshot pollSnapshot() {
        ClientSnapshot clientSnapshot = this.snapshotQueue.poll();
        if (clientSnapshot != null) {
            this.snapshotQueueSize.decrementAndGet();
        }
        return clientSnapshot;
    }

    public int snapshotQueueSize() {
        return this.snapshotQueueSize.get();
    }

    public void publishActionPlan(ActionPlan actionPlan) {
        if (!WellNetConfig.enabled() || actionPlan == null) {
            return;
        }

        synchronized (this.actionPlanLock) {
            ActionPlan previousPlan = this.lastPublishedActionPlan.get();
            if (previousPlan != null && actionPlan.semanticallyEquals(previousPlan)) {
                return;
            }

            long nowMs = System.currentTimeMillis();
            long minIntervalMs = Math.max(250L, WellNetConfig.decisionIntervalMs());
            boolean serverChanged = previousPlan != null && !Objects.equals(previousPlan.serverId, actionPlan.serverId);
            if (!serverChanged && nowMs - this.lastActionDecisionMs.get() < minIntervalMs) {
                return;
            }

            this.lastActionDecisionMs.set(nowMs);
            this.lastPublishedActionPlan.set(actionPlan);
            this.actionPlanRef.set(actionPlan);
        }
    }

    public ActionPlan getActionPlan() {
        return this.actionPlanRef.get();
    }

    public ActionPlan pollActionPlanForMainThread() {
        return this.actionPlanRef.getAndSet(null);
    }

    public void publishNetworkStats(NetworkStats networkStats) {
        this.networkStatsRef.set(networkStats);
    }

    public NetworkStats getNetworkStats() {
        return this.networkStatsRef.get();
    }

    public void recordPolicyTrace(PolicyTraceSnapshot traceSnapshot) {
        if (traceSnapshot == null) {
            return;
        }

        this.policyTraceQueue.add(traceSnapshot);
        int queueSize = this.policyTraceSize.incrementAndGet();
        while (queueSize > MAX_POLICY_TRACE) {
            PolicyTraceSnapshot discarded = this.policyTraceQueue.poll();
            if (discarded == null) {
                this.policyTraceSize.set(0);
                break;
            }
            queueSize = this.policyTraceSize.decrementAndGet();
        }
    }

    public List<PolicyTraceSnapshot> recentPolicyTraces(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        List<PolicyTraceSnapshot> traces = new ArrayList<>(this.policyTraceQueue);
        if (traces.isEmpty()) {
            return traces;
        }
        int fromIndex = Math.max(0, traces.size() - limit);
        return new ArrayList<>(traces.subList(fromIndex, traces.size()));
    }

    private void clearSessionData() {
        clearSnapshotQueue();
        this.actionPlanRef.set(null);
        this.lastPublishedActionPlan.set(null);
        this.lastActionDecisionMs.set(0L);
        this.networkStatsRef.set(null);
        this.policyTraceQueue.clear();
        this.policyTraceSize.set(0);
        this.connectStartTime = -1L;
        this.worldLoadedTime = -1L;
        this.lastUptimeSeconds = 0L;
    }

    private void stopModulesSafely(String reason) {
        try {
            this.moduleManager.stopAll();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to stop modules during {}", reason, throwable);
        }
    }

    private void clearSnapshotQueue() {
        this.snapshotQueue.clear();
        this.snapshotQueueSize.set(0);
    }

    private boolean shouldAttachTrafficHook(ClientSnapshot clientSnapshot) {
        if (clientSnapshot == null || !clientSnapshot.inWorld || !clientSnapshot.hasPlayer) {
            return false;
        }

        long loadedAtSeconds = this.worldLoadedTime;
        if (loadedAtSeconds <= 0L) {
            return false;
        }

        long elapsedSinceWorldLoadedMs = System.currentTimeMillis() - loadedAtSeconds * 1000L;
        return elapsedSinceWorldLoadedMs >= TRAFFIC_HOOK_GRACE_MS;
    }

    private void logStatusReportSafe() {
        try {
            this.moduleManager.logStatusReport();
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to log WellNet status report", throwable);
        }
    }

    static final class NamedThreadFactory implements ThreadFactory {
        private final String base;
        private final AtomicInteger idx = new AtomicInteger(1);

        NamedThreadFactory(String base) {
            this.base = base == null ? "WellNet" : base;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, this.base + "-" + this.idx.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    public static final class MessageBus {
        private final ConcurrentMap<Class<?>, CopyOnWriteArrayList<MessageHandler<?>>> handlers = new ConcurrentHashMap<>();

        public <T> Runnable subscribe(Class<T> clazz, MessageHandler<? super T> messageHandler) {
            Objects.requireNonNull(clazz, "clazz");
            Objects.requireNonNull(messageHandler, "handler");
            CopyOnWriteArrayList<MessageHandler<?>> bucket =
                this.handlers.computeIfAbsent(clazz, ignored -> new CopyOnWriteArrayList<>());
            bucket.add(messageHandler);
            return () -> bucket.remove(messageHandler);
        }

        public <T> void publish(T message) {
            if (message == null) {
                return;
            }

            CopyOnWriteArrayList<MessageHandler<?>> bucket = this.handlers.get(message.getClass());
            if (bucket == null) {
                return;
            }

            for (MessageHandler<?> rawHandler : bucket) {
                try {
                    @SuppressWarnings("unchecked")
                    MessageHandler<T> messageHandler = (MessageHandler<T>) rawHandler;
                    messageHandler.handle(message);
                } catch (Throwable throwable) {
                    WellNetCore.LOGGER.warn("Message bus handler failed for {}", message.getClass().getName(), throwable);
                }
            }
        }

        public interface MessageHandler<T> {
            void handle(T message);
        }
    }

    public static final class ModuleManager {
        private final ConcurrentMap<String, Module> modulesByName = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<Module> moduleOrder = new CopyOnWriteArrayList<>();
        private final WellNetCore core;

        public ModuleManager(WellNetCore core) {
            this.core = core;
        }

        public WellNetCore getCore() {
            return this.core;
        }

        public synchronized boolean registerModule(Module module) {
            if (module == null) {
                return false;
            }
            Module previous = this.modulesByName.putIfAbsent(module.getName(), module);
            if (previous != null) {
                return false;
            }
            this.moduleOrder.add(module);
            return true;
        }

        public synchronized boolean unregisterModule(String moduleName) {
            if (moduleName == null) {
                return false;
            }
            Module removed = this.modulesByName.remove(moduleName);
            if (removed == null) {
                return false;
            }
            this.moduleOrder.remove(removed);
            return true;
        }

        public void initializeAll() {
            for (Module module : this.getModules()) {
                invokeLifecycle(module, "initialize", module::initialize);
            }
        }

        public void startAll() {
            for (Module module : this.getModules()) {
                invokeLifecycle(module, "start", module::start);
            }
        }

        public void stopAll() {
            List<Module> modules = this.getModules();
            for (int i = modules.size() - 1; i >= 0; i--) {
                invokeLifecycle(modules.get(i), "stop", modules.get(i)::stop);
            }
        }

        public List<Module> getModules() {
            return new ArrayList<>(this.moduleOrder);
        }

        public void logStatusReport() {
            List<Module> modules = this.getModules();
            boolean unhealthy = modules.stream().anyMatch(module ->
                module.getStatus() != Status.OK && module.getStatus() != Status.FINISHED);
            if (!unhealthy && !WellNetConfig.debug()) {
                return;
            }

            String moduleStatusLine = modules.stream()
                .map(module -> module.getName() + "=" + module.getStatus() + (module.isRunning() ? "(running)" : "(idle)"))
                .collect(Collectors.joining(", "));
            ActionPlan lastPlan = this.core.getLastPublishedActionPlan();
            NetworkStats networkStats = this.core.getNetworkStats();

            String message = "startedAt=" + this.core.startedAt
                + ", uptime=" + this.core.uptimeSeconds() + "s"
                + ", queue=" + this.core.snapshotQueueSize()
                + ", lastPlan=" + (lastPlan == null ? "-" : lastPlan)
                + ", net=" + (networkStats == null ? "-" : networkStats)
                + ", modules=[" + moduleStatusLine + "]";
            if (unhealthy) {
                WellNetCore.LOGGER.warn("WellNet status report: {}", message);
            } else {
                WellNetCore.LOGGER.info("WellNet status report: {}", message);
            }
        }

        private void invokeLifecycle(Module module, String phase, Runnable action) {
            try {
                action.run();
            } catch (Throwable throwable) {
                module.setStatus(Status.ERROR);
                WellNetCore.LOGGER.warn("Module {} failed during {}", module.getName(), phase, throwable);
            }
        }
    }

    public abstract static class Module {
        private final String name;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile Status status = Status.OK;
        private volatile int lastPing = -1;
        private final WellNetCore core;

        protected Module(String name, WellNetCore core) {
            this.name = Objects.requireNonNull(name, "name");
            this.core = Objects.requireNonNull(core, "core");
        }

        public final String getName() {
            return this.name;
        }

        protected final WellNetCore getCore() {
            return this.core;
        }

        protected final WellNetCore core() {
            return this.core;
        }

        protected final void setStatus(Status status) {
            this.status = status == null ? Status.ERROR : status;
        }

        public final Status getStatus() {
            return this.status;
        }

        protected final void setLastPing(int lastPing) {
            this.lastPing = lastPing;
        }

        public final int getLastPing() {
            return this.lastPing;
        }

        protected final void setRunning(boolean running) {
            this.running.set(running);
        }

        public final boolean isRunning() {
            return this.running.get();
        }

        public abstract void initialize();

        public abstract void start();

        public abstract void stop();
    }

    public enum Status {
        OK,
        WARNING,
        ERROR,
        DEAD,
        FINISHED
    }
}
