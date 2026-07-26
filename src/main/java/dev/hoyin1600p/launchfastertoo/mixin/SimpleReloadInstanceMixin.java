package dev.hoyin1600p.launchfastertoo.mixin;

import dev.hoyin1600p.launchfastertoo.LaunchFasterTooConfig;
import dev.hoyin1600p.launchfastertoo.ParallelReloadInstance;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleReloadInstance.class)
public abstract class SimpleReloadInstanceMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$useInstrumentedReload(
            ResourceManager resourceManager,
            List<PreparableReloadListener> listeners,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> alsoWaitedFor,
            boolean profiled,
            CallbackInfoReturnable<ReloadInstance> callback
    ) {
        if (!profiled
                && LaunchFasterTooConfig.COMMON.enableCommonOptimizations.get()
                && LaunchFasterTooConfig.COMMON.parallelReloadPreparation.get()) {
            callback.setReturnValue(new ParallelReloadInstance(
                    resourceManager,
                    listeners,
                    backgroundExecutor,
                    gameExecutor,
                    alsoWaitedFor
            ));
        }
    }
}

