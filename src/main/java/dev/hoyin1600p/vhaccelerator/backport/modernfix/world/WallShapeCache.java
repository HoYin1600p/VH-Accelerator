/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's wall-shape deduplication implementation.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WallShapeCache {
    private static final Map<ImmutableList<Float>, Entry> CACHE =
            new HashMap<>();

    private WallShapeCache() {
    }

    public static synchronized Map<BlockState, VoxelShape> reuse(
            StateDefinition<Block, BlockState> definition,
            float f1,
            float f2,
            float f3,
            float f4,
            float f5,
            float f6
    ) {
        Entry cached = CACHE.get(key(f1, f2, f3, f4, f5, f6));
        if (cached == null
                || !cached.definition().getProperties().equals(
                        definition.getProperties()
                )) {
            return null;
        }

        ImmutableMap.Builder<BlockState, VoxelShape> result =
                ImmutableMap.builder();
        for (BlockState state : definition.getPossibleStates()) {
            VoxelShape shape = cached.byProperties().get(state.getValues());
            if (shape == null) {
                return null;
            }
            result.put(state, shape);
        }
        return result.build();
    }

    public static synchronized void remember(
            StateDefinition<Block, BlockState> definition,
            Map<BlockState, VoxelShape> shapes,
            float f1,
            float f2,
            float f3,
            float f4,
            float f5,
            float f6
    ) {
        ImmutableList<Float> key = key(f1, f2, f3, f4, f5, f6);
        if (CACHE.containsKey(key)) {
            return;
        }

        Map<ImmutableMap<Property<?>, Comparable<?>>, VoxelShape> values =
                new HashMap<>();
        for (Map.Entry<BlockState, VoxelShape> shape : shapes.entrySet()) {
            values.put(shape.getKey().getValues(), shape.getValue());
        }
        CACHE.put(key, new Entry(Map.copyOf(values), definition));
    }

    private static ImmutableList<Float> key(
            float f1,
            float f2,
            float f3,
            float f4,
            float f5,
            float f6
    ) {
        return ImmutableList.of(f1, f2, f3, f4, f5, f6);
    }

    private record Entry(
            Map<ImmutableMap<Property<?>, Comparable<?>>, VoxelShape>
                    byProperties,
            StateDefinition<Block, BlockState> definition
    ) {
    }
}
