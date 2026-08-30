/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/compact_bit_storage/PalettedContainerMixin.java
 * Upstream commit: 4a8e0487bc8a0fd602d2b5bd83613a8ce3c4e33e
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the corrected validation to Minecraft 1.18.2 under VHA ownership.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.blockstate.palette;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate.CompactPaletteGuard;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {
    @Shadow
    private volatile PalettedContainer.Data<T> data;

    @Invoker("createOrReuseData")
    protected abstract PalettedContainer.Data<T> vha$createOrReuseData(
            @Nullable PalettedContainer.Data<T> previous,
            int bits
    );

    @Inject(
            method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/chunk/PalettedContainer;"
                            + "data:Lnet/minecraft/world/level/chunk/"
                            + "PalettedContainer$Data;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            require = 1
    )
    private void vha$compactOversizedEmptyStorage(
            FriendlyByteBuf buffer,
            CallbackInfo callback,
            int bits
    ) {
        if (bits <= 1) {
            return;
        }
        long[] storage = data.storage().getRaw();
        if (storage.length == 0
                || !CompactPaletteGuard.storageIsEmpty(storage)) {
            return;
        }

        Optional<T> firstValue = CompactPaletteGuard.firstValue(
                data.palette()::valueFor
        );
        if (firstValue.isEmpty()) {
            return;
        }
        data = vha$createOrReuseData(null, 0);
        data.palette().idFor(firstValue.get());
    }
}
