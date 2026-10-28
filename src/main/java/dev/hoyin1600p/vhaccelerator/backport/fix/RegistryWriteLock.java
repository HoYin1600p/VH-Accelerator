package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.util.Map;
import java.util.function.Function;

/**
 * The monitors that serialise writes to vanilla registries.
 *
 * <p>Forge 40 constructs mods on several threads, and vanilla's
 * {@code MappedRegistry} keeps its lookup tables in plain hash maps and its
 * next id in a plain field. Two mods that create or fill registries at the same
 * time (for example one calling {@code Registry.registerDefaulted} while another
 * calls {@code Registry.registerSimple}, both writing the root registry) can
 * corrupt those tables or fail with a {@code ConcurrentModificationException}.
 *
 * <p>Each registry is its own monitor, so writes to unrelated registries never
 * contend. It is the same monitor the tag backport takes in
 * {@code getOrCreateTag}; neither path calls the other while holding it, and
 * monitors are reentrant, so {@code register} calling {@code registerMapping}
 * is safe.
 */
public final class RegistryWriteLock {
    /** Guards the static {@code Registry.LOADERS} map that {@code internalRegister} fills. */
    public static final Object LOADERS = new Object();

    private RegistryWriteLock() {
    }

    public static Object monitor(Object registry) {
        return registry;
    }

    /** Forge's intrusive holder table is separate from MappedRegistry's tables but has the same owner. */
    public static <K, V> V computeHolder(Object registry, Map<K, V> holders, K key,
                                       Function<? super K, ? extends V> factory) {
        synchronized (monitor(registry)) {
            return holders.computeIfAbsent(key, factory);
        }
    }
}
