/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/forge_registry_alloc/DebugLevelSourceMixin.java
 * Upstream commit: 8ee85f2c1637ea1ef365b066963aa24b2e1fdde9
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; retargeted to Forge 40.3.11 and falls back to the
 * original collect if the active block registry cannot be resolved.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.blockstateview;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.registry.IdMapListView;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Backs {@code DebugLevelSource.ALL_BLOCKS} with Forge's block-state ID map
 * instead of re-streaming every block state on each block-registry bake.
 *
 * <p>Forge 40's {@code GameData.BlockCallbacks#onBake} clears the
 * {@code BLOCKSTATE_TO_ID} slave map ({@code onClear} runs on every
 * {@code sync}/{@code clear}), refills it by iterating the registry in ID
 * order and each block's {@code getPossibleStates()}, and then calls the
 * Forge-added {@code DebugLevelSource#initValidStates}, whose stream performs
 * that same traversal over the same active registry
 * ({@code Registry.BLOCK} iterates the active {@code ForgeRegistry}). The list
 * it collects therefore always equals the ID map in order and contents;
 * {@code DebugLevelSource} only reads {@code size()} and {@code get(int)}
 * from it, and {@code GRID_WIDTH}/{@code GRID_HEIGHT} are recomputed from the
 * view at the same call. Only the redundant copy is removed. The vanilla
 * static initializer that runs before any bake is untouched.
 */
@Mixin(DebugLevelSource.class)
public abstract class DebugLevelSourceStateViewMixin {
    /**
     * @author embeddedt, HoYin1600p
     * @reason Reuse the block-state list Forge just built instead of copying it.
     */
    @Redirect(
            method = "initValidStates",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private static Object vha$viewRegisteredStates(Stream<?> states, Collector<?, ?, ?> collector) {
        IdMapper<BlockState> ids;
        try {
            ids = GameData.getBlockStateIDMap();
        } catch (RuntimeException failure) {
            VHAccelerator.LOGGER.debug(
                    "Block-state ID map unavailable; DebugLevelSource keeps its own state list",
                    failure
            );
            ids = null;
        }
        if (ids == null) {
            return vha$collect(states, collector);
        }
        return new IdMapListView<>(ids);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object vha$collect(Stream<?> states, Collector<?, ?, ?> collector) {
        return states.collect((Collector) collector);
    }
}
