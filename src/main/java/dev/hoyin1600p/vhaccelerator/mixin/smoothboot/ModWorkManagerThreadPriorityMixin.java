package dev.hoyin1600p.vhaccelerator.mixin.smoothboot;

import dev.hoyin1600p.vhaccelerator.compat.smoothboot.SmoothBootThreadPriorities;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import net.minecraftforge.fml.ModWorkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores normal priority for Forge's {@code modloading-worker} threads.
 *
 * <p>Smooth Boot (Reloaded) overwrites {@code newForkJoinWorkerThread} with a
 * copy that adds {@code setPriority(config.threadPriority.modLoading)}
 * (default 1). Applying after it (priority 1100) at {@code RETURN} raises each
 * worker before the pool starts it, whichever body the method ends up with.
 * Forge's thread count from {@code fml.toml} is untouched.
 */
@Mixin(value = ModWorkManager.class, priority = 1100, remap = false)
public abstract class ModWorkManagerThreadPriorityMixin {
    @Inject(method = "newForkJoinWorkerThread", at = @At("RETURN"), remap = false)
    private static void vhaccelerator$restoreModLoadingPriority(
            ForkJoinPool pool,
            CallbackInfoReturnable<ForkJoinWorkerThread> callback
    ) {
        SmoothBootThreadPriorities.raise("modloading-worker", callback.getReturnValue());
    }
}
