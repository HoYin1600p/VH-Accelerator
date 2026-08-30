/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/world/gen/PositionalBiomeGetter.java
 * Upstream commit: 2193aa11a408251b7b5b5e03ecfadc3d166c291c
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; generalized the value type and preserved lazy null-value memoization.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.worldgen;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

public final class PositionMemoizedSupplier<T> implements Supplier<T> {
    private final Function<BlockPos, T> resolver;
    private final BlockPos.MutableBlockPos mutablePosition;

    private int nextX;
    private int nextY;
    private int nextZ;
    private T value;
    private volatile boolean resolved;

    public PositionMemoizedSupplier(
            Function<BlockPos, T> resolver,
            BlockPos.MutableBlockPos mutablePosition
    ) {
        this.resolver = resolver;
        this.mutablePosition = mutablePosition;
    }

    public void update(int x, int y, int z) {
        this.nextX = x;
        this.nextY = y;
        this.nextZ = z;
        this.resolved = false;
    }

    @Override
    public T get() {
        if (!this.resolved) {
            this.value = this.resolver.apply(
                    this.mutablePosition.set(this.nextX, this.nextY, this.nextZ)
            );
            this.resolved = true;
        }
        return this.value;
    }
}
