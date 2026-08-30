/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/faster_ingredients/IngredientMixin.java
 * Upstream commit: 5048e74c79724029a8ba2e3d7cdd37c83320ad4e
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; provides a non-Mixin runtime boundary for the
 * 1.18.2 tag key exposed by the companion target mixin.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public interface IngredientTagValueView {
    TagKey<Item> vha$getTag();
}
