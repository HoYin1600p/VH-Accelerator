/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/ChunkAccessMixin.java
 * Upstream commit: 8cca316fb521b96da9428beb0d2cb4da21ca0658
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: replaced the upstream empty-map snapshot with
 * a fully unmodifiable live view that reuses empty collection singletons.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An unmodifiable live map view whose empty collection views reuse JDK
 * singletons instead of allocating iterators around an empty mutable map.
 */
public final class LiveReadOnlyMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> delegate;
    private final Map<K, V> readOnly;

    public LiveReadOnlyMap(Map<K, V> delegate) {
        this.delegate = delegate;
        this.readOnly = Collections.unmodifiableMap(delegate);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return delegate.isEmpty()
                ? Collections.emptySet()
                : readOnly.entrySet();
    }

    @Override
    public Set<K> keySet() {
        return delegate.isEmpty()
                ? Collections.emptySet()
                : readOnly.keySet();
    }

    @Override
    public Collection<V> values() {
        return delegate.isEmpty()
                ? Collections.emptyList()
                : readOnly.values();
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfAbsent(
            K key,
            Function<? super K, ? extends V> function
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfPresent(
            K key,
            BiFunction<? super K, ? super V, ? extends V> function
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V compute(
            K key,
            BiFunction<? super K, ? super V, ? extends V> function
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V merge(
            K key,
            V value,
            BiFunction<? super V, ? super V, ? extends V> function
    ) {
        throw new UnsupportedOperationException();
    }
}
