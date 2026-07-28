package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchEventProfiler;
import net.minecraftforge.fml.IModLoadingState;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModWorkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/**
 * Times Forge's existing mod-loading state transitions without changing their
 * executors, task order, or error handling.
 */
@Mixin(value = ModLoader.class, remap = false)
public abstract class ModLoadingStateProfilerMixin {
    @Unique
    private long vhaccelerator$stateStartedNanos;

    @Inject(
            method = "dispatchAndHandleError("
                    + "Lnet/minecraftforge/fml/IModLoadingState;"
                    + "Lnet/minecraftforge/fml/"
                    + "ModWorkManager$DrivenExecutor;"
                    + "Ljava/util/concurrent/Executor;"
                    + "Ljava/lang/Runnable;)V",
            at = @At("HEAD")
    )
    private void vhaccelerator$beginStateProfile(
            IModLoadingState state,
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            Runnable ticker,
            CallbackInfo callback
    ) {
        vhaccelerator$stateStartedNanos =
                LaunchEventProfiler.enabled()
                        ? System.nanoTime()
                        : 0L;
    }

    @Inject(
            method = "dispatchAndHandleError("
                    + "Lnet/minecraftforge/fml/IModLoadingState;"
                    + "Lnet/minecraftforge/fml/"
                    + "ModWorkManager$DrivenExecutor;"
                    + "Ljava/util/concurrent/Executor;"
                    + "Ljava/lang/Runnable;)V",
            at = @At("RETURN")
    )
    private void vhaccelerator$finishStateProfile(
            IModLoadingState state,
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            Runnable ticker,
            CallbackInfo callback
    ) {
        long started = vhaccelerator$stateStartedNanos;
        vhaccelerator$stateStartedNanos = 0L;
        if (started != 0L) {
            LaunchEventProfiler.recordStage(
                    state.name(),
                    System.nanoTime() - started
            );
        }
    }
}
