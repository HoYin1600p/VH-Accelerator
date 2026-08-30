/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/blockstate/FakeStateMap.java
 * Upstream commit: 49464451ddc6b174740b2fd14057611441d26f40
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retained array-first population while providing a complete read-only Map fallback.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.world.level.block.state.properties.Property;

public final class GracefulStateMap<S>
        extends AbstractMap<Map<Property<?>, Comparable<?>>, S> {
    private final Map<Property<?>, Comparable<?>>[] keys;
    private final Object[] values;
    private int usedSlots;
    private Map<Map<Property<?>, Comparable<?>>, S> fastLookup;

    @SuppressWarnings("unchecked")
    public GracefulStateMap(int expectedStates) {
        keys = new Map[expectedStates];
        values = new Object[expectedStates];
    }

    @Override
    public int size() {
        return usedSlots;
    }

    @Override
    public boolean containsKey(Object key) {
        return fastLookup().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return fastLookup().containsValue(value);
    }

    @Override
    public S get(Object key) {
        return fastLookup().get(key);
    }

    @Override
    public S put(Map<Property<?>, Comparable<?>> key, S value) {
        if (fastLookup != null) {
            throw new IllegalStateException(
                    "Cannot populate the state map after random lookup begins"
            );
        }
        if (usedSlots >= keys.length) {
            throw new IllegalStateException(
                    "State definition produced more states than calculated"
            );
        }
        keys[usedSlots] = key;
        values[usedSlots] = value;
        usedSlots++;
        return null;
    }

    @Override
    public void clear() {
        for (int index = 0; index < usedSlots; index++) {
            keys[index] = null;
            values[index] = null;
        }
        usedSlots = 0;
        fastLookup = null;
    }

    @Override
    public Set<Entry<Map<Property<?>, Comparable<?>>, S>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return usedSlots;
            }

            @Override
            public Iterator<Entry<Map<Property<?>, Comparable<?>>, S>> iterator() {
                return new Iterator<>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index < usedSlots;
                    }

                    @Override
                    public Entry<Map<Property<?>, Comparable<?>>, S> next() {
                        if (!hasNext()) {
                            throw new NoSuchElementException();
                        }
                        Entry<Map<Property<?>, Comparable<?>>, S> entry =
                                new SimpleImmutableEntry<>(
                                        keys[index],
                                        valueAt(index)
                                );
                        index++;
                        return entry;
                    }
                };
            }
        };
    }

    private Map<Map<Property<?>, Comparable<?>>, S> fastLookup() {
        if (fastLookup == null) {
            Map<Map<Property<?>, Comparable<?>>, S> lookup =
                    new HashMap<>(Math.max(1, usedSlots * 2));
            for (int index = 0; index < usedSlots; index++) {
                lookup.put(keys[index], valueAt(index));
            }
            fastLookup = lookup;
        }
        return fastLookup;
    }

    @SuppressWarnings("unchecked")
    private S valueAt(int index) {
        return (S) values[index];
    }
}
