/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/util/blockpos/SectionBlockPosIterator.java
 * Upstream commit: 2e52db6e932abc310a3bbaa391ab492a5486847e
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; retained vanilla traversal order and added a bounds-safe fallback.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.render;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;

public final class SectionBlockPosIterator implements Iterator<BlockPos> {
    private static final int SECTION_EDGE = 16;
    private static final int SECTION_VOLUME = 4096;

    private final BlockPos.MutableBlockPos position =
            new BlockPos.MutableBlockPos();
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private int index;

    private SectionBlockPosIterator(BlockPos firstPosition) {
        this.baseX = firstPosition.getX();
        this.baseY = firstPosition.getY();
        this.baseZ = firstPosition.getZ();
    }

    public static Iterable<BlockPos> betweenClosed(
            BlockPos firstPosition,
            BlockPos secondPosition
    ) {
        if (!isOneSection(firstPosition, secondPosition)) {
            return BlockPos.betweenClosed(firstPosition, secondPosition);
        }
        return () -> new SectionBlockPosIterator(firstPosition);
    }

    static boolean isOneSection(
            BlockPos firstPosition,
            BlockPos secondPosition
    ) {
        return secondPosition.getX() - firstPosition.getX()
                == SECTION_EDGE - 1
                && secondPosition.getY() - firstPosition.getY()
                == SECTION_EDGE - 1
                && secondPosition.getZ() - firstPosition.getZ()
                == SECTION_EDGE - 1;
    }

    @Override
    public boolean hasNext() {
        return index < SECTION_VOLUME;
    }

    @Override
    public BlockPos next() {
        int currentIndex = index;
        if (currentIndex >= SECTION_VOLUME) {
            throw new NoSuchElementException();
        }
        index = currentIndex + 1;

        position.set(
                baseX + (currentIndex & 15),
                baseY + ((currentIndex >> 4) & 15),
                baseZ + ((currentIndex >> 8) & 15)
        );
        return position;
    }
}
