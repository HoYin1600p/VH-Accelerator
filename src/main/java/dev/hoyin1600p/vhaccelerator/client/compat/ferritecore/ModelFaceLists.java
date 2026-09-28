/*
 * SPDX-License-Identifier: LGPL-3.0-or-later AND MIT
 *
 * Adapted for VH Accelerator from FerriteCore.
 * Upstream repository: https://github.com/malte0811/FerriteCore
 * Upstream source: Common/src/main/java/malte0811/ferritecore/impl/ModelSidesImpl.java
 * Upstream commit: 7baeea0bd337188114f889c581a28f02b74f3364 (branch 1.21.1)
 * Original copyright: Copyright (c) 2020 malte0811 (MIT License)
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; backported to Minecraft 1.18.2, where FerriteCore
 * 4.2.2 has no equivalent option.
 */
package dev.hoyin1600p.vhaccelerator.client.compat.ferritecore;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/**
 * Exact-size immutable face lists for {@code SimpleBakedModel}: every empty
 * list is the shared {@code List.of()}, and a model with no side-specific
 * faces shares one empty side map. The builder's lists default to room for
 * ten quads, and most models have few or no faces per side.
 */
public final class ModelFaceLists {
    private static final Direction[] SIDES = Direction.values();
    private static final Map<Direction, List<BakedQuad>> EMPTY = empty();

    private ModelFaceLists() {
    }

    public static List<BakedQuad> unculled(List<BakedQuad> quads) {
        return List.copyOf(quads);
    }

    public static Map<Direction, List<BakedQuad>> culled(Map<Direction, List<BakedQuad>> quadsBySide) {
        if (quadsBySide.isEmpty()) {
            // Forge's EmptyModel passes an empty map; keep it as it is.
            return quadsBySide;
        }
        boolean allEmpty = true;
        for (Direction side : SIDES) {
            List<BakedQuad> sideQuads = quadsBySide.get(side);
            if (sideQuads == null) {
                return quadsBySide;
            }
            quadsBySide.put(side, List.copyOf(sideQuads));
            allEmpty &= sideQuads.isEmpty();
        }
        return allEmpty ? EMPTY : quadsBySide;
    }

    private static Map<Direction, List<BakedQuad>> empty() {
        Map<Direction, List<BakedQuad>> map = new EnumMap<>(Direction.class);
        for (Direction side : SIDES) {
            map.put(side, List.of());
        }
        return map;
    }
}
