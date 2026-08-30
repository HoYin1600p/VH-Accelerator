/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/fast_registry_validation/ForgeRegistryMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retained Forge 40 registration acceleration with
 * explicit cache-valid state and safe reset coverage.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.validation;

import java.lang.reflect.Method;
import java.util.BitSet;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin<V extends IForgeRegistryEntry<V>> {
    @Unique
    private static Method vha$bitSetTrimMethod;

    @Unique
    private static boolean vha$bitSetTrimMethodRetrieved;

    @Unique
    private int vha$expectedNextBit;

    @Unique
    private boolean vha$expectedNextBitValid;

    @Redirect(
            method = "validateContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/util/"
                            + "ObfuscationReflectionHelper;findMethod("
                            + "Ljava/lang/Class;Ljava/lang/String;"
                            + "[Ljava/lang/Class;)Ljava/lang/reflect/Method;"
            )
    )
    private Method vha$reuseBitSetTrimMethod(
            Class<?> owner,
            String name,
            Class<?>[] parameters
    ) {
        if (!vha$bitSetTrimMethodRetrieved) {
            vha$bitSetTrimMethodRetrieved = true;
            vha$bitSetTrimMethod = ObfuscationReflectionHelper.findMethod(
                    owner,
                    name,
                    parameters
            );
        }
        return vha$bitSetTrimMethod;
    }

    @Redirect(
            method = "add(ILnet/minecraftforge/registries/"
                    + "IForgeRegistryEntry;Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/BitSet;nextClearBit(I)I"
            )
    )
    private int vha$continueClearBitSearch(
            BitSet availability,
            int minimum
    ) {
        int start = this.vha$expectedNextBitValid
                ? Math.max(minimum, this.vha$expectedNextBit)
                : minimum;
        int bit = availability.nextClearBit(start);
        this.vha$expectedNextBit = bit + 1;
        this.vha$expectedNextBitValid = true;
        return bit;
    }

    @Inject(method = {"sync", "clear", "block"}, at = @At("HEAD"))
    private void vha$resetClearBitSearch(CallbackInfo callback) {
        this.vha$expectedNextBitValid = false;
    }

    @Inject(
            method = "createAndAddDummy",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/BitSet;clear(I)V"
            )
    )
    private void vha$resetClearBitSearchForDummy(CallbackInfo callback) {
        this.vha$expectedNextBitValid = false;
    }

    @Redirect(
            method = "add(ILnet/minecraftforge/registries/"
                    + "IForgeRegistryEntry;Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;trace("
                            + "Lorg/apache/logging/log4j/Marker;"
                            + "Ljava/lang/String;Ljava/lang/Object;"
                            + "Ljava/lang/Object;Ljava/lang/Object;"
                            + "Ljava/lang/Object;Ljava/lang/Object;)V"
            )
    )
    private void vha$skipPerEntryTrace(
            Logger logger,
            Marker marker,
            String message,
            Object first,
            Object second,
            Object third,
            Object fourth,
            Object fifth
    ) {
        // Per-entry trace output is intentionally suppressed by this option.
    }
}
