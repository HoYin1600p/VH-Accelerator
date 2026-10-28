package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strongly reachable graph size from explicit roots, using actual Instrumentation shallow sizes. */
final class RetainedGraphWalker {
    record ClassSize(long objects, long bytes) { }
    record Result(long objects, long bytes, Map<String, ClassSize> classes) { }

    private RetainedGraphWalker() { }

    static Instrumentation instrumentation() {
        try {
            // Gradle may load test classes beneath the system loader that owns the premain agent.
            Class<?> agent = Class.forName(RetainedGraphAgent.class.getName(), true, ClassLoader.getSystemClassLoader());
            return (Instrumentation) agent.getMethod("instrumentation").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access test-only retained-graph javaagent", failure);
        }
    }

    static Result measure(Collection<?> roots, Set<?> excludedIdentities) throws IOException {
        Instrumentation sizes = instrumentation();
        IdentityHashMap<Object, Boolean> excluded = new IdentityHashMap<>();
        for (Object value : excludedIdentities) excluded.put(value, Boolean.TRUE);
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> pending = new ArrayDeque<>();
        for (Object root : roots) enqueue(root, excluded, seen, pending);
        Map<Class<?>, Field[]> fieldCache = new HashMap<>();
        TreeMap<String, ClassSize> classes = new TreeMap<>();
        long objects = 0;
        long bytes = 0;
        while (!pending.isEmpty()) {
            Object value = pending.removeFirst();
            Class<?> type = value.getClass();
            if (value instanceof Class<?> || value instanceof ClassLoader || value instanceof Module || value instanceof Thread) {
                throw new IOException("Unexpected runtime-global object in measured map graph: " + type.getName());
            }
            long shallow = sizes.getObjectSize(value);
            objects++;
            bytes += shallow;
            ClassSize previous = classes.getOrDefault(type.getName(), new ClassSize(0, 0));
            classes.put(type.getName(), new ClassSize(previous.objects + 1, previous.bytes + shallow));
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive()) {
                    for (int index = 0; index < Array.getLength(value); index++) {
                        enqueue(Array.get(value, index), excluded, seen, pending);
                    }
                }
                continue;
            }
            Field[] fields = fieldCache.get(type);
            if (fields == null) {
                ArrayList<Field> references = new ArrayList<>();
                for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
                    for (Field field : owner.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        // Weak/soft referents are not strong reachability edges.
                        if (owner == Reference.class) continue;
                        if (!field.trySetAccessible()) throw new IOException("Inaccessible graph field: " + field);
                        references.add(field);
                    }
                }
                fields = references.toArray(Field[]::new);
                fieldCache.put(type, fields);
            }
            for (Field field : fields) {
                try { enqueue(field.get(value), excluded, seen, pending); }
                catch (IllegalAccessException failure) { throw new IOException("Cannot traverse " + field, failure); }
            }
        }
        return new Result(objects, bytes, classes);
    }

    private static void enqueue(Object value, IdentityHashMap<Object, Boolean> excluded,
                                IdentityHashMap<Object, Boolean> seen, ArrayDeque<Object> pending) {
        if (value != null && !excluded.containsKey(value) && seen.put(value, Boolean.TRUE) == null) pending.add(value);
    }
}
