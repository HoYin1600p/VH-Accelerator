/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fast_forge_dummies/NamespacedHolderHelperMixin.java
 * Upstream commit: ba3d418260932a3f621bc0e995ffa2432598ed39 (introduction)
 *                  a06a46b498bac472685adef9ebbcb95c77c7511f (audited state)
 *                  b26ab375b56d9ec34bb1aa51a8b3cc2f78b2b939 (current location)
 * Original copyright: Copyright (c) 2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; retargeted from the newer NamespacedWrapper to Forge
 * 40's NamespacedHolderHelper, which owns the holder maps in 1.18.2, and
 * mirrored its intrusive-holder check over the holders map.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.registry.freeze;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the stream pipelines in Forge 40's
 * {@code NamespacedHolderHelper#freeze} with plain loops on the success path.
 *
 * <p>Forge 40.3.11 freezes every Forge registry's holder helper at load
 * completion and again after each registry snapshot injection and restore.
 * {@code freeze} sets {@code frozen}, streams {@code holdersByName} for
 * unbound holders (sorting and joining their names into an exception), and
 * for intrusive registries streams {@code holders} for unbound intrusive
 * holders. Both streams exist only to build the failure messages. This
 * injection runs after {@code frozen = true}, at the first read of
 * {@code holdersByName}, and returns {@code self} when a bound-check loop
 * over the same maps with the same predicates finds nothing; any unbound
 * holder falls through to the untouched Forge code, which throws the same
 * exception with the same sorted message.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedHolderHelper")
public abstract class NamespacedHolderHelperFreezeMixin<T> {
    @Shadow(remap = false)
    @Final
    private Registry<T> self;

    @Shadow(remap = false)
    @Final
    private Function<T, Holder.Reference<T>> holderLookup;

    @Shadow(remap = false)
    private Map<ResourceLocation, Holder.Reference<T>> holdersByName;

    @Shadow(remap = false)
    private Map<T, Holder.Reference<T>> holders;

    /**
     * @author embeddedt, HoYin1600p
     * @reason Check for unbound holders without allocating streams; the
     * original code is kept for the failing case and its message.
     */
    @Inject(
            method = "freeze",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraftforge/registries/NamespacedHolderHelper;"
                            + "holdersByName:Ljava/util/Map;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0,
                    remap = false
            ),
            cancellable = true,
            remap = false
    )
    private void vha$freezeWithoutStreams(CallbackInfoReturnable<Registry<T>> cir) {
        for (Holder.Reference<T> holder : this.holdersByName.values()) {
            if (!holder.isBound()) {
                return;
            }
        }
        if (this.holderLookup != null) {
            for (Holder.Reference<T> holder : this.holders.values()) {
                if (holder.getType() == Holder.Reference.Type.INTRUSIVE && !holder.isBound()) {
                    return;
                }
            }
        }
        cir.setReturnValue(this.self);
    }
}
