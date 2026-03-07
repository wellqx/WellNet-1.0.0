/*
 * Decompiled with CFR 0.152.
 */
package com.wellnet.net;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrafficManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrafficManager.class);
    private static final String HANDLER_NAME = "wellnet_traffic";
    private static final Object CHANNEL_LOCK = new Object();
    private static final AtomicLong TOTAL_IN_BYTES = new AtomicLong(0L);
    private static final AtomicLong TOTAL_OUT_BYTES = new AtomicLong(0L);
    private static final AtomicLong IN_BPS = new AtomicLong(-1L);
    private static final AtomicLong OUT_BPS = new AtomicLong(-1L);
    private static final AtomicBoolean DISABLED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, Boolean> REFLECTION_LOGGED = new ConcurrentHashMap();
    private static volatile Object HANDLER_PROXY = null;
    private static volatile Object LAST_CHANNEL = null;
    private static volatile Class<?> CLS_CHANNEL = null;
    private static volatile Class<?> CLS_EVENTLOOP = null;
    private static volatile Class<?> CLS_PIPELINE = null;
    private static volatile Class<?> CLS_CHC = null;
    private static volatile Class<?> CLS_CHPROMISE = null;
    private static volatile Class<?> CLS_CHIN = null;
    private static volatile Class<?> CLS_CHOUT = null;
    private static volatile Class<?> CLS_CHHANDLER = null;
    private static final int M_FIRE_ACTIVE = 0;
    private static final int M_FIRE_INACTIVE = 1;
    private static final int M_FIRE_READ = 2;
    private static final int M_FIRE_READ_COMPLETE = 3;
    private static final int M_FIRE_USER_EVENT = 4;
    private static final int M_FIRE_EXCEPTION = 5;
    private static final int M_FIRE_WRITABILITY = 6;
    private static final int M_WRITE_3 = 7;
    private static final int M_WRITE_1 = 8;
    private static final int M_FLUSH = 9;
    private static final int M_READ = 10;
    private static final int M_BIND = 11;
    private static final int M_CONNECT_3 = 12;
    private static final int M_CONNECT_2 = 13;
    private static final int M_DISCONNECT = 14;
    private static final int M_CLOSE = 15;
    private static final int M_DEREGISTER = 16;
    private static final ConcurrentHashMap<Class<?>, Method[]> CTX_METHODS = new ConcurrentHashMap();
    private static volatile Class<?> CLS_BYTEBUF = null;
    private static volatile Class<?> CLS_BYTEBUF_HOLDER = null;
    private static volatile Method M_READABLE_BYTES = null;
    private static volatile Method M_CONTENT = null;

    private TrafficManager() {
    }

    public static long getInboundBps() {
        return IN_BPS.get();
    }

    public static long getOutboundBps() {
        return OUT_BPS.get();
    }

    public static long getTotalInboundBytes() {
        return TOTAL_IN_BYTES.get();
    }

    public static long getTotalOutboundBytes() {
        return TOTAL_OUT_BYTES.get();
    }

    public static void publishBps(long l, long l2) {
        IN_BPS.set(l);
        OUT_BPS.set(l2);
    }

    public static void reset() {
        DISABLED.set(false);
        TOTAL_IN_BYTES.set(0L);
        TOTAL_OUT_BYTES.set(0L);
        IN_BPS.set(-1L);
        OUT_BPS.set(-1L);
        Object previousChannel;
        synchronized (CHANNEL_LOCK) {
            previousChannel = LAST_CHANNEL;
            LAST_CHANNEL = null;
        }
        if (previousChannel != null) {
            TrafficManager.tryUninstall(previousChannel);
        }
    }

    public static void ensureInstalledAndSampleFromMinecraft() {
        if (DISABLED.get()) {
            return;
        }
        try {
            Object object = TrafficManager.getMinecraftInstance();
            if (object == null) {
                return;
            }
            Object object2 = TrafficManager.getClientPacketListener(object);
            TrafficManager.ensureInstalled(object2);
        }
        catch (Throwable throwable) {
            DISABLED.set(true);
            LOGGER.debug("Traffic sampling hook disabled after Minecraft lookup failure", throwable);
        }
    }

    public static void ensureInstalled(Object object) {
        if (DISABLED.get()) {
            return;
        }
        if (object == null) {
            return;
        }
        try {
            Object connection = TrafficManager.getConnectionFromListener(object);
            if (connection == null) {
                return;
            }
            Object channel = TrafficManager.getChannelFromConnection(connection);
            if (channel == null) {
                return;
            }
            if (!TrafficManager.isChannelReady(channel)) {
                return;
            }
            TrafficManager.trackChannel(channel);
            Object eventLoop = TrafficManager.invokeNoArg(channel, "eventLoop");
            if (eventLoop == null) {
                return;
            }
            Runnable runnable = () -> {
                try {
                    Object pipeline = TrafficManager.invokeNoArg(channel, "pipeline");
                    if (pipeline == null) {
                        return;
                    }
                    Object existingHandler = TrafficManager.invokeOneArg(pipeline, "get", String.class, HANDLER_NAME);
                    if (existingHandler != null) {
                        return;
                    }
                    Object handlerProxy = TrafficManager.getOrCreateHandlerProxy();
                    if (handlerProxy == null) {
                        return;
                    }
                    TrafficManager.invokeTwoArgIfPresent(pipeline, "addFirst", String.class, Object.class, HANDLER_NAME, handlerProxy);
                }
                catch (Throwable throwable) {
                    DISABLED.set(true);
                    LOGGER.debug("TrafficManager failed to install handler on channel pipeline", throwable);
                }
            };
            if (!TrafficManager.submitToEventLoop(eventLoop, runnable)) {
                DISABLED.set(true);
                LOGGER.debug("TrafficManager failed to submit install task to channel event loop");
            }
        }
        catch (Throwable throwable) {
            DISABLED.set(true);
            LOGGER.debug("TrafficManager failed while preparing channel hook", throwable);
        }
    }

    private static void trackChannel(Object channel) {
        Object previousChannel;
        synchronized (CHANNEL_LOCK) {
            previousChannel = LAST_CHANNEL;
            if (previousChannel == channel) {
                return;
            }
            LAST_CHANNEL = channel;
        }
        if (previousChannel != null) {
            TrafficManager.tryUninstall(previousChannel);
        }
    }

    private static boolean isChannelReady(Object channel) {
        if (channel == null) {
            return false;
        }

        Object activeState = TrafficManager.invokeNoArg(channel, "isActive");
        if (activeState instanceof Boolean && !((Boolean) activeState)) {
            return false;
        }

        Object openState = TrafficManager.invokeNoArg(channel, "isOpen");
        if (openState instanceof Boolean && !((Boolean) openState)) {
            return false;
        }

        return true;
    }

    private static Object getOrCreateHandlerProxy() {
        Object cachedProxy = HANDLER_PROXY;
        if (cachedProxy != null) {
            return cachedProxy;
        }
        try {
            Object proxy;
            TrafficManager.ensureNettyClassesLoaded();
            if (CLS_CHHANDLER == null || CLS_CHIN == null || CLS_CHOUT == null) {
                return null;
            }
            InvocationHandler invocationHandler = (object, method, objectArray) -> {
                try {
                    String string = method.getName();
                    if ("handlerAdded".equals(string) || "handlerRemoved".equals(string)) {
                        return null;
                    }
                    if (objectArray == null || objectArray.length == 0) {
                        return null;
                    }
                    Object context = objectArray[0];
                    if ("channelRead".equals(string) && objectArray.length >= 2) {
                        Object message = objectArray[1];
                        TrafficManager.countInbound(message);
                        TrafficManager.fire(context, 2, message);
                        return null;
                    }
                    if ("channelActive".equals(string)) {
                        TrafficManager.fire(context, 0, new Object[0]);
                        return null;
                    }
                    if ("channelInactive".equals(string)) {
                        TrafficManager.fire(context, 1, new Object[0]);
                        return null;
                    }
                    if ("channelReadComplete".equals(string)) {
                        TrafficManager.fire(context, 3, new Object[0]);
                        return null;
                    }
                    if ("userEventTriggered".equals(string) && objectArray.length >= 2) {
                        TrafficManager.fire(context, 4, objectArray[1]);
                        return null;
                    }
                    if ("exceptionCaught".equals(string) && objectArray.length >= 2) {
                        TrafficManager.fire(context, 5, objectArray[1]);
                        return null;
                    }
                    if ("channelWritabilityChanged".equals(string)) {
                        TrafficManager.fire(context, 6, new Object[0]);
                        return null;
                    }
                    if ("write".equals(string) && objectArray.length >= 3) {
                        Object message = objectArray[1];
                        Object promise = objectArray[2];
                        TrafficManager.countOutbound(message);
                        TrafficManager.write(context, message, promise);
                        return null;
                    }
                    if ("flush".equals(string)) {
                        TrafficManager.flush(context);
                        return null;
                    }
                    if ("read".equals(string)) {
                        TrafficManager.read(context);
                        return null;
                    }
                    if ("bind".equals(string) && objectArray.length >= 3) {
                        TrafficManager.bind(context, (SocketAddress)objectArray[1], objectArray[2]);
                        return null;
                    }
                    if ("connect".equals(string) && objectArray.length >= 4) {
                        TrafficManager.connect(context, (SocketAddress)objectArray[1], (SocketAddress)objectArray[2], objectArray[3]);
                        return null;
                    }
                    if ("disconnect".equals(string) && objectArray.length >= 2) {
                        TrafficManager.disconnect(context, objectArray[1]);
                        return null;
                    }
                    if ("close".equals(string) && objectArray.length >= 2) {
                        TrafficManager.close(context, objectArray[1]);
                        return null;
                    }
                    if ("deregister".equals(string) && objectArray.length >= 2) {
                        TrafficManager.deregister(context, objectArray[1]);
                        return null;
                    }
                    return null;
                }
                catch (Throwable throwable) {
                    DISABLED.set(true);
                    try {
                        String string = method.getName();
                        if (objectArray != null && objectArray.length >= 2) {
                            Object fallbackContext = objectArray[0];
                            if ("channelRead".equals(string)) {
                                TrafficManager.fire(fallbackContext, 2, objectArray[1]);
                            } else if ("write".equals(string) && objectArray.length >= 3) {
                                TrafficManager.write(fallbackContext, objectArray[1], objectArray[2]);
                            }
                        }
                    }
                    catch (Throwable throwable2) {
                        TrafficManager.logReflectionFailureOnce("handlerProxy.fallback." + method.getName(), "Fallback channel operation failed after handler proxy exception", throwable2);
                    }
                    return null;
                }
            };
            HANDLER_PROXY = proxy = Proxy.newProxyInstance(TrafficManager.class.getClassLoader(), new Class[]{CLS_CHHANDLER, CLS_CHIN, CLS_CHOUT}, invocationHandler);
            return proxy;
        }
        catch (Throwable throwable) {
            DISABLED.set(true);
            return null;
        }
    }

    private static void fire(Object object, int n, Object ... objectArray) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[n];
        if (method == null) {
            return;
        }
        method.invoke(object, objectArray);
    }

    private static void write(Object object, Object object2, Object object3) throws Throwable {
        Method[] methodArray = TrafficManager.getCtxMethods(object.getClass());
        Method method = methodArray[7];
        if (method != null) {
            method.invoke(object, object2, object3);
            return;
        }
        method = methodArray[8];
        if (method != null) {
            method.invoke(object, object2);
            return;
        }
    }

    private static void flush(Object object) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[9];
        if (method != null) {
            method.invoke(object, new Object[0]);
        }
    }

    private static void read(Object object) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[10];
        if (method != null) {
            method.invoke(object, new Object[0]);
        }
    }

    private static void bind(Object object, SocketAddress socketAddress, Object object2) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[11];
        if (method != null) {
            method.invoke(object, socketAddress, object2);
        }
    }

    private static void connect(Object object, SocketAddress socketAddress, SocketAddress socketAddress2, Object object2) throws Throwable {
        Method[] methodArray = TrafficManager.getCtxMethods(object.getClass());
        Method method = methodArray[12];
        if (method != null) {
            method.invoke(object, socketAddress, socketAddress2, object2);
            return;
        }
        method = methodArray[13];
        if (method != null) {
            method.invoke(object, socketAddress, object2);
            return;
        }
    }

    private static void disconnect(Object object, Object object2) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[14];
        if (method != null) {
            method.invoke(object, object2);
        }
    }

    private static void close(Object object, Object object2) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[15];
        if (method != null) {
            method.invoke(object, object2);
        }
    }

    private static void deregister(Object object, Object object2) throws Throwable {
        Method method = TrafficManager.getCtxMethods(object.getClass())[16];
        if (method != null) {
            method.invoke(object, object2);
        }
    }

    private static Method[] getCtxMethods(Class<?> clazz) {
        Method[] methodArray;
        block10: {
            Method[] methodArray2 = CTX_METHODS.get(clazz);
            if (methodArray2 != null) {
                return methodArray2;
            }
            TrafficManager.ensureNettyClassesLoaded();
            methodArray = new Method[17];
            try {
                if (CLS_CHC == null) break block10;
                methodArray[0] = CLS_CHC.getMethod("fireChannelActive", new Class[0]);
                methodArray[1] = CLS_CHC.getMethod("fireChannelInactive", new Class[0]);
                methodArray[2] = CLS_CHC.getMethod("fireChannelRead", Object.class);
                methodArray[3] = CLS_CHC.getMethod("fireChannelReadComplete", new Class[0]);
                methodArray[4] = CLS_CHC.getMethod("fireUserEventTriggered", Object.class);
                methodArray[5] = CLS_CHC.getMethod("fireExceptionCaught", Throwable.class);
                methodArray[6] = CLS_CHC.getMethod("fireChannelWritabilityChanged", new Class[0]);
                if (CLS_CHPROMISE != null) {
                    methodArray[7] = CLS_CHC.getMethod("write", Object.class, CLS_CHPROMISE);
                    methodArray[11] = CLS_CHC.getMethod("bind", SocketAddress.class, CLS_CHPROMISE);
                    try {
                        methodArray[12] = CLS_CHC.getMethod("connect", SocketAddress.class, SocketAddress.class, CLS_CHPROMISE);
                    }
                    catch (NoSuchMethodException noSuchMethodException) {
                        TrafficManager.logReflectionFailureOnce("ctx.connect3", "ChannelHandlerContext three-arg connect method is unavailable", noSuchMethodException);
                    }
                    try {
                        methodArray[13] = CLS_CHC.getMethod("connect", SocketAddress.class, CLS_CHPROMISE);
                    }
                    catch (NoSuchMethodException noSuchMethodException) {
                        TrafficManager.logReflectionFailureOnce("ctx.connect2", "ChannelHandlerContext two-arg connect method is unavailable", noSuchMethodException);
                    }
                    methodArray[14] = CLS_CHC.getMethod("disconnect", CLS_CHPROMISE);
                    methodArray[15] = CLS_CHC.getMethod("close", CLS_CHPROMISE);
                    methodArray[16] = CLS_CHC.getMethod("deregister", CLS_CHPROMISE);
                }
                try {
                    methodArray[8] = CLS_CHC.getMethod("write", Object.class);
                }
                catch (NoSuchMethodException noSuchMethodException) {
                    TrafficManager.logReflectionFailureOnce("ctx.write1", "ChannelHandlerContext single-arg write method is unavailable", noSuchMethodException);
                }
                methodArray[9] = CLS_CHC.getMethod("flush", new Class[0]);
                methodArray[10] = CLS_CHC.getMethod("read", new Class[0]);
            }
            catch (Throwable throwable) {
                DISABLED.set(true);
                TrafficManager.logReflectionFailureOnce("ctx.methods.init", "Failed to initialize ChannelHandlerContext methods", throwable);
            }
        }
        CTX_METHODS.put(clazz, methodArray);
        return methodArray;
    }

    private static void countInbound(Object object) {
        long l = TrafficManager.sizeOfMessage(object);
        if (l > 0L) {
            TOTAL_IN_BYTES.addAndGet(l);
        }
    }

    private static void countOutbound(Object object) {
        long l = TrafficManager.sizeOfMessage(object);
        if (l > 0L) {
            TOTAL_OUT_BYTES.addAndGet(l);
        }
    }

    private static long sizeOfMessage(Object object) {
        if (object == null) {
            return 0L;
        }
        if (object instanceof byte[]) {
            return ((byte[])object).length;
        }
        if (object instanceof ByteBuffer) {
            return ((ByteBuffer)object).remaining();
        }
        try {
            Object object2;
            Object object3;
            Class<?> clazz = object.getClass();
            if (CLS_BYTEBUF == null) {
                try {
                    CLS_BYTEBUF = Class.forName("io.netty.buffer.ByteBuf");
                    M_READABLE_BYTES = CLS_BYTEBUF.getMethod("readableBytes", new Class[0]);
                }
                catch (Throwable throwable) {
                    TrafficManager.logReflectionFailureOnce("bytebuf.class", "Failed to resolve io.netty.buffer.ByteBuf", throwable);
                }
            }
            if (CLS_BYTEBUF != null && CLS_BYTEBUF.isAssignableFrom(clazz) && M_READABLE_BYTES != null && (object3 = M_READABLE_BYTES.invoke(object, new Object[0])) instanceof Integer) {
                return ((Integer)object3).intValue();
            }
            if (CLS_BYTEBUF_HOLDER == null) {
                try {
                    CLS_BYTEBUF_HOLDER = Class.forName("io.netty.buffer.ByteBufHolder");
                    M_CONTENT = CLS_BYTEBUF_HOLDER.getMethod("content", new Class[0]);
                }
                catch (Throwable throwable) {
                    TrafficManager.logReflectionFailureOnce("bytebufholder.class", "Failed to resolve io.netty.buffer.ByteBufHolder", throwable);
                }
            }
            if (CLS_BYTEBUF_HOLDER != null && CLS_BYTEBUF_HOLDER.isAssignableFrom(clazz) && M_CONTENT != null && (object3 = M_CONTENT.invoke(object, new Object[0])) != null && CLS_BYTEBUF != null && CLS_BYTEBUF.isAssignableFrom(object3.getClass()) && M_READABLE_BYTES != null && (object2 = M_READABLE_BYTES.invoke(object3, new Object[0])) instanceof Integer) {
                return ((Integer)object2).intValue();
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("message.size." + object.getClass().getName(), "Failed to inspect message size reflectively", throwable);
        }
        return 0L;
    }

        private static void tryUninstall(Object object) {
        try {
            Object eventLoop = TrafficManager.invokeNoArg(object, "eventLoop");
            if (eventLoop == null) {
                return;
            }
            Runnable runnable = () -> {
                try {
                    Object pipeline = TrafficManager.invokeNoArg(object, "pipeline");
                    if (pipeline == null) {
                        return;
                    }
                    Object existingHandler = TrafficManager.invokeOneArg(pipeline, "get", String.class, HANDLER_NAME);
                    if (existingHandler == null) {
                        return;
                    }
                    TrafficManager.invokeOneArgIfPresent(pipeline, "remove", String.class, HANDLER_NAME);
                }
                catch (Throwable throwable) {
                    TrafficManager.logReflectionFailureOnce("pipeline.remove", "Failed to remove WellNet traffic handler from pipeline", throwable);
                }
            };
            TrafficManager.submitToEventLoop(eventLoop, runnable);
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("pipeline.uninstall", "Failed to uninstall WellNet traffic handler", throwable);
        }
    }

    private static boolean submitToEventLoop(Object object, Runnable runnable) {
        try {
            Method method = object.getClass().getMethod("submit", Runnable.class);
            Object object2 = method.invoke(object, runnable);
            return object2 != null;
        }
        catch (NoSuchMethodException noSuchMethodException) {
            try {
                Method method = object.getClass().getMethod("execute", Runnable.class);
                method.invoke(object, runnable);
                return true;
            }
            catch (Throwable throwable) {
                TrafficManager.logReflectionFailureOnce("eventloop.execute." + object.getClass().getName(), "Failed to submit task via eventLoop.execute", throwable);
                return false;
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("eventloop.submit." + object.getClass().getName(), "Failed to submit task via eventLoop.submit", throwable);
            return false;
        }
    }

    private static Object getMinecraftInstance() {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.Minecraft");
            try {
                Method method = clazz.getMethod("getInstance", new Class[0]);
                return method.invoke(null, new Object[0]);
            }
            catch (NoSuchMethodException noSuchMethodException) {
                try {
                    Method method = clazz.getMethod("m_91087_", new Class[0]);
                    return method.invoke(null, new Object[0]);
                }
                catch (NoSuchMethodException noSuchMethodException2) {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || !clazz.isAssignableFrom(method.getReturnType())) continue;
                        method.setAccessible(true);
                        return method.invoke(null, new Object[0]);
                    }
                    TrafficManager.logReflectionFailureOnce("minecraft.instance.fallback", "No compatible Minecraft singleton accessor was found", noSuchMethodException2);
                }
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("minecraft.instance", "Failed to resolve Minecraft instance reflectively", throwable);
        }
        return null;
    }

    private static Object getClientPacketListener(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Object object2 = TrafficManager.invokeNoArg(object, "getConnection");
            if (object2 != null) {
                return object2;
            }
            object2 = TrafficManager.invokeNoArg(object, "m_91403_");
            if (object2 != null) {
                return object2;
            }
            Class<?> clazz = Class.forName("net.minecraft.client.multiplayer.ClientPacketListener");
            for (Method method : object.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || !clazz.isAssignableFrom(method.getReturnType())) continue;
                return method.invoke(object, new Object[0]);
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("client.listener." + object.getClass().getName(), "Failed to resolve ClientPacketListener from Minecraft instance", throwable);
        }
        return null;
    }

    private static Object getConnectionFromListener(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Object object2;
            Class<?> clazz = Class.forName("net.minecraft.network.Connection");
            for (Field accessibleObject : object.getClass().getDeclaredFields()) {
                if (!clazz.isAssignableFrom(accessibleObject.getType())) continue;
                accessibleObject.setAccessible(true);
                object2 = accessibleObject.get(object);
                if (object2 == null) continue;
                return object2;
            }
            for (AccessibleObject accessibleObject : object.getClass().getMethods()) {
                if (((Method)accessibleObject).getParameterCount() != 0 || !clazz.isAssignableFrom(((Method)accessibleObject).getReturnType()) || (object2 = ((Method)accessibleObject).invoke(object, new Object[0])) == null) continue;
                return object2;
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("connection.from.listener." + object.getClass().getName(), "Failed to resolve Connection from listener", throwable);
        }
        return null;
    }

    private static Object getChannelFromConnection(Object object) {
        if (object == null) {
            return null;
        }
        try {
            Object object2;
            TrafficManager.ensureNettyClassesLoaded();
            if (CLS_CHANNEL == null) {
                return null;
            }
            for (Field accessibleObject : object.getClass().getDeclaredFields()) {
                if (!CLS_CHANNEL.isAssignableFrom(accessibleObject.getType())) continue;
                accessibleObject.setAccessible(true);
                object2 = accessibleObject.get(object);
                if (object2 == null) continue;
                return object2;
            }
            for (AccessibleObject accessibleObject : object.getClass().getMethods()) {
                if (((Method)accessibleObject).getParameterCount() != 0 || !CLS_CHANNEL.isAssignableFrom(((Method)accessibleObject).getReturnType()) || (object2 = ((Method)accessibleObject).invoke(object, new Object[0])) == null) continue;
                return object2;
            }
        }
        catch (Throwable throwable) {
            TrafficManager.logReflectionFailureOnce("channel.from.connection." + object.getClass().getName(), "Failed to resolve Netty channel from connection", throwable);
        }
        return null;
    }

    private static void ensureNettyClassesLoaded() {
        if (CLS_CHANNEL != null && CLS_CHC != null) {
            return;
        }
        try {
            CLS_CHANNEL = Class.forName("io.netty.channel.Channel");
            CLS_EVENTLOOP = Class.forName("io.netty.channel.EventLoop");
            CLS_PIPELINE = Class.forName("io.netty.channel.ChannelPipeline");
            CLS_CHC = Class.forName("io.netty.channel.ChannelHandlerContext");
            CLS_CHPROMISE = Class.forName("io.netty.channel.ChannelPromise");
            CLS_CHIN = Class.forName("io.netty.channel.ChannelInboundHandler");
            CLS_CHOUT = Class.forName("io.netty.channel.ChannelOutboundHandler");
            CLS_CHHANDLER = Class.forName("io.netty.channel.ChannelHandler");
        }
        catch (Throwable throwable) {
            DISABLED.set(true);
            TrafficManager.logReflectionFailureOnce("netty.class.init", "Failed to resolve required Netty classes", throwable);
        }
    }

    private static Object invokeNoArg(Object object, String string) {
        try {
            Method method = object.getClass().getMethod(string, new Class[0]);
            return method.invoke(object, new Object[0]);
        }
        catch (Throwable throwable) {
            try {
                Method method = object.getClass().getDeclaredMethod(string, new Class[0]);
                method.setAccessible(true);
                return method.invoke(object, new Object[0]);
            }
            catch (Throwable throwable2) {
                TrafficManager.logReflectionFailureOnce("invoke0." + object.getClass().getName() + "#" + string, "Failed reflective no-arg invocation for " + string, throwable2);
                return null;
            }
        }
    }

    private static Object invokeOneArg(Object object, String string, Class<?> clazz, Object object2) {
        try {
            Method method = object.getClass().getMethod(string, clazz);
            return method.invoke(object, object2);
        }
        catch (Throwable throwable) {
            try {
                Method method = object.getClass().getDeclaredMethod(string, clazz);
                method.setAccessible(true);
                return method.invoke(object, object2);
            }
            catch (Throwable throwable2) {
                TrafficManager.logReflectionFailureOnce("invoke1." + object.getClass().getName() + "#" + string, "Failed reflective one-arg invocation for " + string, throwable2);
                return null;
            }
        }
    }

    private static boolean invokeOneArgIfPresent(Object object, String string, Class<?> clazz, Object object2) {
        return TrafficManager.invokeOneArg(object, string, clazz, object2) != null;
    }

    private static boolean invokeTwoArgIfPresent(Object object, String string, Class<?> clazz, Class<?> clazz2, Object object2, Object object3) {
        try {
            Method method = object.getClass().getMethod(string, clazz, clazz2);
            method.invoke(object, object2, object3);
            return true;
        }
        catch (Throwable throwable) {
            try {
                Method method = object.getClass().getDeclaredMethod(string, clazz, clazz2);
                method.setAccessible(true);
                method.invoke(object, object2, object3);
                return true;
            }
            catch (Throwable throwable2) {
                TrafficManager.logReflectionFailureOnce("invoke2." + object.getClass().getName() + "#" + string, "Failed reflective two-arg invocation for " + string, throwable2);
                return false;
            }
        }
    }

    private static void logReflectionFailureOnce(String key, String message, Throwable throwable) {
        if (REFLECTION_LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            LOGGER.debug(message, throwable);
        }
    }
}




