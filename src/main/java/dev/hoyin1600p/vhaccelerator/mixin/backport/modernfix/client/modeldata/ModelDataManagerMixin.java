/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/bugfix/model_data_manager_cme/ModelDataManagerMixin.java
 * Upstream commit: e253833b685e0bb4ee5de62860ce81b7d886a311
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: retargeted Forge 40 static APIs, added exact
 * option ownership, and retained the legacy Rubidium exclusion.
 * Modified: 2026-08-30
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.modeldata;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.model.ModelDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Forge model-data refresh requests safe when chunk/model work queries
 * model data from worker threads.
 */
@Mixin(ModelDataManager.class)
public abstract class ModelDataManagerMixin {
    @Shadow
    protected static void refreshModelData(Level level, ChunkPos chunk) {
        throw new AssertionError();
    }

    @Shadow
    @Final
    private static Map<ChunkPos, Set<BlockPos>> needModelDataRefresh;

    @ModifyArg(
            method = "requestModelDataRefresh",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;",
                    ordinal = 0
            ),
            index = 1,
            remap = false
    )
    private static Function<ChunkPos, Set<BlockPos>> vha$useConcurrentSet(
            Function<ChunkPos, Set<BlockPos>> original
    ) {
        return ignored -> ConcurrentHashMap.newKeySet();
    }

    @Redirect(
            method = "getModelData(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Map;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/model/ModelDataManager;refreshModelData(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)V"
            ),
            remap = false
    )
    private static void vha$refreshOnlyOnMainThread(
            Level level,
            ChunkPos chunk
    ) {
        if (!Minecraft.getInstance().isSameThread()
                || needModelDataRefresh.isEmpty()) {
            return;
        }

        // Model-data consumers can inspect neighboring blocks while building
        // a chunk model, so drain the requested chunk and its neighbors while
        // safely back on the client thread.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                refreshModelData(
                        level,
                        new ChunkPos(chunk.x + x, chunk.z + z)
                );
            }
        }
    }
}
