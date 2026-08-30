/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/model_optimizations/SelectorMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated the 1.18.2 selector cache behind VHA ownership.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.model.selector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Selector.class)
public abstract class SelectorMixin {
    @Unique
    private final ConcurrentHashMap<
            StateDefinition<Block, BlockState>,
            Predicate<BlockState>
            > vha$predicateCache = new ConcurrentHashMap<>();

    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void vha$reusePredicate(
            StateDefinition<Block, BlockState> definition,
            CallbackInfoReturnable<Predicate<BlockState>> callback
    ) {
        Predicate<BlockState> cached = this.vha$predicateCache.get(definition);
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "getPredicate", at = @At("RETURN"))
    private void vha$rememberPredicate(
            StateDefinition<Block, BlockState> definition,
            CallbackInfoReturnable<Predicate<BlockState>> callback
    ) {
        this.vha$predicateCache.putIfAbsent(
                definition,
                callback.getReturnValue()
        );
    }
}
