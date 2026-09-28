package dev.hoyin1600p.vhaccelerator.mixin.smoothboot;

import dev.hoyin1600p.vhaccelerator.compat.smoothboot.SmoothBootThreadPriorities;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Undoes the lowest thread priority Smooth Boot (Reloaded) gives Minecraft's
 * bootstrap, background and IO executors.
 *
 * <p>Smooth Boot's {@code UtilMixin} swaps each executor field at {@code HEAD}
 * of these getters on their first call, using thread factories that set the
 * priority from its config (default 1). This mixin applies after it (priority
 * 1100) and runs at {@code RETURN}, so it sees the executor Smooth Boot
 * installed before any worker exists, wraps its factory through
 * {@link SmoothBootThreadPriorities#restore} and publishes the result back to
 * the field. Only selected when Smooth Boot is present, on both physical sides.
 */
@Mixin(value = Util.class, priority = 1100)
public abstract class UtilThreadPriorityMixin {
    @Shadow @Final @Mutable private static ExecutorService BOOTSTRAP_EXECUTOR;
    @Shadow @Final @Mutable private static ExecutorService BACKGROUND_EXECUTOR;
    @Shadow @Final @Mutable private static ExecutorService IO_POOL;

    @Inject(method = "bootstrapExecutor", at = @At("RETURN"), cancellable = true)
    private static void vhaccelerator$restoreBootstrapPriority(
            CallbackInfoReturnable<ExecutorService> callback
    ) {
        ExecutorService current = callback.getReturnValue();
        ExecutorService restored = SmoothBootThreadPriorities.restore("Bootstrap", current);
        if (restored != current) {
            BOOTSTRAP_EXECUTOR = restored;
            callback.setReturnValue(restored);
        }
    }

    @Inject(method = "backgroundExecutor", at = @At("RETURN"), cancellable = true)
    private static void vhaccelerator$restoreBackgroundPriority(
            CallbackInfoReturnable<ExecutorService> callback
    ) {
        ExecutorService current = callback.getReturnValue();
        ExecutorService restored = SmoothBootThreadPriorities.restore("Main", current);
        if (restored != current) {
            BACKGROUND_EXECUTOR = restored;
            callback.setReturnValue(restored);
        }
    }

    @Inject(method = "ioPool", at = @At("RETURN"), cancellable = true)
    private static void vhaccelerator$restoreIoPriority(
            CallbackInfoReturnable<ExecutorService> callback
    ) {
        ExecutorService current = callback.getReturnValue();
        ExecutorService restored = SmoothBootThreadPriorities.restore("IO", current);
        if (restored != current) {
            IO_POOL = restored;
            callback.setReturnValue(restored);
        }
    }
}
