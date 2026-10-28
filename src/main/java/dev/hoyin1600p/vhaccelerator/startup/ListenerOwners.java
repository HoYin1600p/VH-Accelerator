package dev.hoyin1600p.vhaccelerator.startup;

import dev.hoyin1600p.vhaccelerator.startup.LifecycleListenerLedger.Described;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraftforge.eventbus.api.IEventListener;

/**
 * Names the class, method and owning mod of an EventBus listener. Runs only when a ledger snapshot is
 * taken, never while a listener executes.
 */
final class ListenerOwners {
    private static final Pattern READABLE_METHOD = Pattern.compile("([\\w$<>]+)\\(.*\\)[\\w\\[/;$]+$");
    private static final Pattern READABLE_STATIC = Pattern.compile("^ASM: class ([\\w.$]+)");
    private static final String LAMBDA_MARK = "$$Lambda";

    private final Function<Class<?>, String> modOfClass;
    private final Map<Class<?>, String> cache = new HashMap<>();

    /** @param modOfClass maps a class to a mod id, or null when unknown. */
    ListenerOwners(Function<Class<?>, String> modOfClass) {
        this.modOfClass = modOfClass;
    }

    Described describe(IEventListener listener) {
        IEventListener real = listener instanceof LifecycleListenerWrapping.TimedListener timed
                ? timed.delegate() : listener;
        String readable = String.valueOf(real);
        Class<?> owner;
        String method;
        String ownerName = null;
        if (readable.startsWith("ASM: ")) {
            Matcher name = READABLE_METHOD.matcher(readable);
            method = name.find() ? name.group(1) : null;
            owner = instanceClass(real);
            if (owner == null) {
                Matcher type = READABLE_STATIC.matcher(readable);
                if (type.find()) ownerName = type.group(1);
            }
        } else {
            owner = capturedOwner(real, 0);
            method = "lambda";
        }
        if (owner == null && ownerName == null) owner = real.getClass();
        if (ownerName == null) ownerName = hostName(owner.getName());
        String mod = owner == null ? namePrefixMod(ownerName) : modFor(owner);
        return new Described(mod, method == null ? ownerName : ownerName + "#" + method);
    }

    private String modFor(Class<?> type) {
        return cache.computeIfAbsent(type, key -> {
            String mod = null;
            try {
                mod = modOfClass.apply(key);
            } catch (RuntimeException | LinkageError failure) {
                // Falls through to the package-based guess below.
            }
            return mod != null ? mod : namePrefixMod(key.getName());
        });
    }

    private static String namePrefixMod(String name) {
        if (name.startsWith("net.minecraftforge.")) return "forge";
        if (name.startsWith("net.minecraft.") || name.startsWith("com.mojang.")) return "minecraft";
        return LifecycleListenerLedger.UNKNOWN_MOD;
    }

    /** ASMEventHandler keeps its generated invoker in a private field; that invoker holds the target. */
    private static Class<?> instanceClass(IEventListener listener) {
        try {
            Field handler = listener.getClass().getDeclaredField("handler");
            if (!handler.trySetAccessible()) return null;
            Object invoker = handler.get(listener);
            if (invoker == null) return null;
            Object target = invoker.getClass().getField("instance").get(invoker);
            return target == null ? null : target.getClass();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /** A lambda listener captures the consumer, which in turn captures the mod's own object. */
    private static Class<?> capturedOwner(Object listener, int depth) {
        if (depth > 2) return null;
        Class<?> fallback = null;
        for (Field field : listener.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            try {
                if (!field.trySetAccessible()) continue;
                Object value = field.get(listener);
                if (value == null) continue;
                String name = value.getClass().getName();
                if (name.startsWith("java.") || name.startsWith("net.minecraftforge.eventbus.")) continue;
                if (name.contains(LAMBDA_MARK)) {
                    Class<?> nested = capturedOwner(value, depth + 1);
                    if (nested != null) return nested;
                }
                if (fallback == null) fallback = value.getClass();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                // Unreadable capture: keep looking, the class name below still names a mod.
            }
        }
        return fallback;
    }

    /** Hidden lambda classes are named Host$$Lambda$N/0x...; the host class names the mod. */
    static String hostName(String className) {
        int lambda = className.indexOf(LAMBDA_MARK);
        return lambda >= 0 ? className.substring(0, lambda) : className;
    }
}
