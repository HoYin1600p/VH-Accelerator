/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/fix_loop_spin_waiting/MinecraftServerMixin.java
 * Upstream commit: a643170426cfe57cdfffddde87229b5506c49de5
 * Original copyright: Copyright (c) 2025 embeddedt, HaHaWTH, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaced MixinExtras with a standard redirect and added focused deadline testing.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.server;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.server.ServerEventLoopWait;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MinecraftServer.class, priority = 500)
public abstract class MinecraftServerEventLoopMixin
        extends BlockableEventLoop<Runnable> {
    @Shadow
    protected long nextTickTime;

    @Unique
    private boolean vha$waitingForNextTick;

    protected MinecraftServerEventLoopMixin(String name) {
        super(name);
    }

    @Redirect(
            method = "waitUntilNextTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;"
                            + "managedBlock(Ljava/util/function/BooleanSupplier;)V"
            )
    )
    private void vha$markBetweenTickWait(
            MinecraftServer server,
            BooleanSupplier finished
    ) {
        try {
            this.vha$waitingForNextTick = true;
            server.managedBlock(finished);
        } finally {
            this.vha$waitingForNextTick = false;
        }
    }

    @Override
    protected void waitForTasks() {
        if (!this.vha$waitingForNextTick) {
            super.waitForTasks();
            return;
        }

        long remainingNanos = ServerEventLoopWait.remainingNanos(
                this.nextTickTime,
                Util.getNanos()
        );
        if (remainingNanos > 0L) {
            LockSupport.parkNanos("waiting for server tasks", remainingNanos);
        }
    }
}
