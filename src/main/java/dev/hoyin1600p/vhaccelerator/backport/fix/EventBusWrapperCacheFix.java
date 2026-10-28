package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.jar.Manifest;

/** Early, optional repair of EventBus 5.0.3's wrapper cache, never its dispatch path. */
public final class EventBusWrapperCacheFix {
    public static final String SETTING = "backports.eventBusWrapperCacheConcurrencyFix";
    static final String OWNER = "net.minecraftforge.eventbus.ASMEventHandler";
    static final String BODY = "cf185cf304ba5206c71be7a9ba3bf5cb4bf62a3a04719eb249347f5c4746aa47";
    static final String LOADER_BODY = "9b770e10a6483e78269c977547917e7f1eaf934fb69f462e8acfba52ae366f35";
    private static final Installation STARTUP = new Installation();

    private EventBusWrapperCacheFix() { }

    public enum State { DISABLED, APPLIED, FALLBACK }
    public record Result(State state, String reason) { }

    /** Called once by the mixin plugin, before FML starts parallel mod construction. */
    public static Result install(boolean enabled, Consumer<String> info, Consumer<String> warn) {
        return STARTUP.apply(enabled, () -> Class.forName(OWNER, false, EventBusWrapperCacheFix.class.getClassLoader()),
                EventBusWrapperCacheFix::unsafeWriter, info, warn);
    }

    public static synchronized String unavailableReason() {
        Result result = STARTUP.result;
        return result == null ? "EventBus wrapper cache installation was not attempted"
                : result.state() == State.FALLBACK ? result.reason() : null;
    }

    @FunctionalInterface interface OwnerLookup { Class<?> get() throws Throwable; }
    @FunctionalInterface interface WriterFactory { Writer get() throws Throwable; }
    @FunctionalInterface interface Writer {
        boolean replace(Field field, Object expected, Object replacement) throws Throwable;
    }

    static final class Installation {
        private volatile Result result;
        synchronized Result apply(boolean enabled, OwnerLookup lookup, WriterFactory writers,
                                  Consumer<String> info, Consumer<String> warn) {
            if (result == null) {
                result = install(enabled, lookup, writers);
                log(result, info, warn);
            }
            return result;
        }
    }

    /** A separate transaction lets tests exercise unavailable/denied access without global resets. */
    static Result install(boolean enabled, OwnerLookup lookup, WriterFactory writers) {
        if (!enabled) return new Result(State.DISABLED, "disabled by " + SETTING);
        try {
            Class<?> owner = lookup.get();
            String problem = layoutProblem(version(owner), bytes(owner, "ASMEventHandler.class"),
                    bytes(owner, "ASMEventHandler$ASMClassLoader.class"));
            if (problem != null) return fallback(problem);
            Field cache = field(owner, "cache", HashMap.class);
            Field ids = field(owner, "IDs", AtomicInteger.class);
            Field loader = field(owner, "LOADER", Class.forName(OWNER + "$ASMClassLoader", false, owner.getClassLoader()));
            if (!cache.trySetAccessible() || !ids.trySetAccessible() || !loader.trySetAccessible()) {
                return fallback("private EventBus fields are not accessible");
            }
            // These reads initialize the original class normally. Never replace a cache after use:
            // another thread or compiled createWrapper could already retain the final reference.
            Object original = cache.get(null);
            Object counter = ids.get(null);
            if (original == null || original.getClass() != HashMap.class
                    || counter == null || counter.getClass() != AtomicInteger.class
                    || loader.get(null) == null) return fallback("EventBus runtime field layout changed");
            AtomicInteger names = (AtomicInteger) counter;
            if (names.get() != 0 || !((HashMap<?, ?>) original).isEmpty()) {
                return fallback("wrapper generation already started; early installation required");
            }
            SynchronizedCache replacement = new SynchronizedCache();
            Result success = new Result(State.APPLIED, "EventBus 5.0.3 wrapper cache synchronized before first wrapper");
            Writer writer = writers.get();
            if (names.get() != 0 || !((HashMap<?, ?>) original).isEmpty()) {
                return fallback("wrapper generation started during preflight");
            }
            // CAS is the final fallible operation: no throwing post-publication verification or
            // rollback can turn a successful replacement into an advertised original fallback.
            return writer.replace(cache, original, replacement) ? success
                    : fallback("EventBus cache was replaced by another owner");
        } catch (Throwable failure) {
            if (failure instanceof java.lang.reflect.InvocationTargetException wrapped && wrapped.getCause() != null) {
                failure = wrapped.getCause();
            }
            return fallback("access unavailable: " + failure.getClass().getSimpleName());
        }
    }

    static void log(Result value, Consumer<String> info, Consumer<String> warn) {
        try {
            if (value.state() == State.APPLIED) info.accept("Applied " + SETTING + ": " + value.reason());
            else if (value.state() == State.FALLBACK) warn.accept("Skipping " + SETTING
                    + "; retained original EventBus behavior: " + value.reason());
        } catch (Throwable ignored) { /* Optional logging cannot interrupt loading or undo publication. */ }
    }

    static String layoutProblem(String version, byte[] owner, byte[] loader) throws Exception {
        if (!"5.0.3".equals(version)) return "unsupported EventBus version " + version;
        if (owner == null || !BODY.equals(hash(owner))) return "ASMEventHandler reviewed bytecode changed";
        if (loader == null || !LOADER_BODY.equals(hash(loader))) return "ASM wrapper class-loader bytecode changed";
        return null;
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static Result fallback(String reason) { return new Result(State.FALLBACK, reason); }

    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || field.getModifiers() != (Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL)) {
            throw new IllegalStateException("EventBus field changed: " + name);
        }
        return field;
    }

    private static byte[] bytes(Class<?> owner, String relative) throws Exception {
        try (InputStream stream = owner.getResourceAsStream(relative)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }

    static String version(Class<?> owner) throws Exception {
        // Named SecureJarHandler modules retain the jar version even when the manifest is unavailable.
        if (owner.getModule().isNamed() && owner.getModule().getDescriptor().rawVersion().isPresent()) {
            return owner.getModule().getDescriptor().rawVersion().get();
        }
        URL resource = owner.getResource("ASMEventHandler.class");
        if (resource == null) return null;
        Manifest manifest;
        if (resource.openConnection() instanceof JarURLConnection jar) {
            manifest = jar.getManifest();
        } else if (owner.getModule().isNamed()) {
            try (InputStream stream = owner.getModule().getResourceAsStream("META-INF/MANIFEST.MF")) {
                manifest = stream == null ? null : new Manifest(stream);
            }
        } else return null;
        if (manifest == null) return null;
        var attributes = manifest.getAttributes("net/minecraftforge/eventbus/service/");
        String value = attributes == null ? null : attributes.getValue("Implementation-Version");
        return value == null ? null : value.split("\\+", 2)[0];
    }

    static Writer unsafeWriter() throws Throwable {
        // Reflective use keeps this optional on future JVMs where sun.misc.Unsafe may be absent.
        // Java24+ can issue its one-time JEP498 warning; that is deliberately not suppressed.
        Class<?> type = Class.forName("sun.misc.Unsafe");
        Field singleton = type.getDeclaredField("theUnsafe");
        if (!singleton.trySetAccessible()) throw new IllegalAccessException("Unsafe unavailable");
        Object unsafe = singleton.get(null);
        Method base = type.getMethod("staticFieldBase", Field.class);
        Method offset = type.getMethod("staticFieldOffset", Field.class);
        Method cas = type.getMethod("compareAndSwapObject", Object.class, long.class, Object.class, Object.class);
        return (field, expected, replacement) -> {
            Object holder = base.invoke(unsafe, field);
            long position = (Long) offset.invoke(unsafe, field);
            return (Boolean) cas.invoke(unsafe, holder, position, expected, replacement);
        };
    }

    /**
     * All original cache accesses are these three virtual operations. Entries are only added:
     * synchronized contains/get need not be one transaction to keep an existing mapping visible.
     * Simultaneous misses may still generate distinct wrappers, exactly as before; AtomicInteger
     * names and the original JVM class definition remain unchanged. No event invocation uses this map.
     */
    static final class SynchronizedCache extends HashMap<Method, Class<?>> {
        @Override public synchronized boolean containsKey(Object key) { return super.containsKey(key); }
        @Override public synchronized Class<?> get(Object key) { return super.get(key); }
        @Override public synchronized Class<?> put(Method key, Class<?> value) { return super.put(key, value); }
    }
}
