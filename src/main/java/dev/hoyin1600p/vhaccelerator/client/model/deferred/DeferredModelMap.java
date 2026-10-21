package dev.hoyin1600p.vhaccelerator.client.model.deferred;

import dev.hoyin1600p.vhaccelerator.client.model.StructurallyVersioned;
import java.util.Map;

/** Shared lifecycle and publication contract for deferred model registries. */
public interface DeferredModelMap<K, V> extends Map<K, V>, StructurallyVersioned {
    void retire();
    boolean isRetired();
    boolean isDeferred(Object key);
    boolean isUnresolvedDeferred(Object key);
    /** True only for a published real model, never a fallback or an in-flight result. */
    boolean isPublished(Object key, V value);
    int initialDeferred();
    int unresolvedDeferred();
    int removedDeferred();
    int bakedOnDemand();
    int failedBakes();
    int retiredLookups();

    default boolean allowsOffThreadBakes() {
        return false;
    }

    default int offThreadLookups() {
        return 0;
    }

    default int sharedWaits() {
        return 0;
    }
}
