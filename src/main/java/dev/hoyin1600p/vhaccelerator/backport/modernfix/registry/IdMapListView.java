/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/forge_registry_alloc/DebugLevelSourceMixin.java
 * Upstream commit: 8ee85f2c1637ea1ef365b066963aa24b2e1fdde9
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; moved upstream's anonymous list out of the mixin and
 * marked it random-access.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import java.util.AbstractList;
import java.util.RandomAccess;
import net.minecraft.core.IdMap;

/**
 * Read-only {@link java.util.List} view of an {@link IdMap} in ID order.
 *
 * <p>Forge 40 fills the block-state ID map in {@code GameData.BlockCallbacks
 * #onBake} by iterating the block registry and every block's possible states,
 * which is the same traversal {@code DebugLevelSource#initValidStates}
 * repeats immediately afterwards to build its own {@code ArrayList}. This view
 * exposes the map instead of copying it.
 */
public final class IdMapListView<T> extends AbstractList<T> implements RandomAccess {
    private final IdMap<T> ids;

    public IdMapListView(IdMap<T> ids) {
        this.ids = ids;
    }

    @Override
    public int size() {
        return ids.size();
    }

    @Override
    public T get(int index) {
        T value = ids.byId(index);
        if (value == null) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size());
        }
        return value;
    }
}
