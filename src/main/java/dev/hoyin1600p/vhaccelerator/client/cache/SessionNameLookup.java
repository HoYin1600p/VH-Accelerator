package dev.hoyin1600p.vhaccelerator.client.cache;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Function;

/** Memoizes only identifier parsing, never model safety or material results. */
final class SessionNameLookup<T, K> {
    private final IdentityHashMap<T, Entry<K>> names = new IdentityHashMap<>();
    private final int limit;
    private long parses;
    SessionNameLookup(int limit) { this.limit = limit; }
    K resolve(T model, String name, Function<String, K> parser) {
        Entry<K> previous = names.get(model);
        if (previous != null && Objects.equals(previous.name, name)) { return previous.key; }
        K key = parser.apply(name);
        parses++;
        if (previous != null || names.size() < limit) { names.put(model, new Entry<>(name, key)); }
        return key;
    }
    long parses() { return parses; }
    private record Entry<K>(String name, K key) { }
}
