/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_handshake_stall/HandshakeHandlerMixin.java
 * Upstream commit: c2f585da9551d925c01b391ddd151e02c5037382
 * Original copyright: embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-29; replaced MixinExtras wrappers with standard Mixin redirects and delegated the progress loop to a tested helper.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.network;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.network.ForgeHandshakeBatcher;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HandshakeHandler.class, remap = false)
public abstract class HandshakeHandlerMixin {
    @Shadow
    private int packetPosition;

    @Shadow
    private List<NetworkRegistry.LoginPayload> messageList;

    @Shadow
    private List<Integer> sentMessages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void vha$synchronizeSentMessages(CallbackInfo callback) {
        this.sentMessages = Collections.synchronizedList(this.sentMessages);
    }

    @Redirect(
            method = "tickLogin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/network/HandshakeHandler;tickServer()Z"
            )
    )
    private static boolean vha$batchProgressingPackets(
            HandshakeHandler handler
    ) {
        HandshakeHandlerAccess access =
                (HandshakeHandlerAccess) (Object) handler;
        return ForgeHandshakeBatcher.tickUntilBlocked(
                access::vha$getPacketPosition,
                handler::tickServer
        );
    }

    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;isEmpty()Z",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z",
                            ordinal = 0
                    )
            )
    )
    private boolean vha$preventEarlyExit(List<?> instance) {
        if (instance != this.sentMessages) {
            throw new AssertionError("VH Accelerator handshake injector moved");
        }
        return instance.isEmpty()
                && this.packetPosition >= this.messageList.size();
    }
}
