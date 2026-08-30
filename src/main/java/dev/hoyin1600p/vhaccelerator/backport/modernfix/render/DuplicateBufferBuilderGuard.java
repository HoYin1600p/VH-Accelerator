/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/bugfix/buffer_builder_leak/RenderBuffersMixin.java
 * Upstream commit: d51b0f60a23b167b6ee8459073c706ab8b20a6fe
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; extracted the allocation decision for focused testing.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.render;

import java.util.Map;

public final class DuplicateBufferBuilderGuard {
    private DuplicateBufferBuilderGuard() {
    }

    public static boolean shouldCreate(Map<?, ?> builders, Object renderType) {
        return !builders.containsKey(renderType);
    }
}
