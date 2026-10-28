package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The exact item identities a tag-valued output request can match, expanded once for one action.
 * A recipe is a candidate when its stable output key is one of them; recipes whose key is unknown
 * stay must-check entries, exactly as for a single-item request.
 */
public final class RequestedKeys {
    private final Object[] keys;

    public RequestedKeys(Collection<?> requested) {
        Set<Object> distinct = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object key : requested) {
            if (key == null) throw new IllegalArgumentException("A requested key cannot be null");
            distinct.add(key);
        }
        keys = distinct.toArray();
    }

    Object[] keys() { return keys; }

    public int size() { return keys.length; }

    public boolean contains(Object key) {
        for (Object candidate : keys) if (candidate == key) return true;
        return false;
    }
}
