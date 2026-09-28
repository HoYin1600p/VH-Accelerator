package dev.hoyin1600p.vhaccelerator.mixin.client.compat.geckolib;

import dev.hoyin1600p.vhaccelerator.client.compat.geckolib.LazyGeckoLibCache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** GeckoLib 3.0.57: loads animation and geo files on first use; see LazyGeckoLibCache. */
@Pseudo
@Mixin(targets = "software.bernie.geckolib3.resource.GeckoLibCache", remap = false)
public abstract class GeckoLibCacheLazyMixin {
    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$listOnly(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager manager,
            ProfilerFiller preparationsProfiler,
            ProfilerFiller reloadProfiler,
            Executor background,
            Executor game,
            CallbackInfoReturnable<CompletableFuture<Void>> callback
    ) {
        CompletableFuture<Void> lazy = LazyGeckoLibCache.reload(this, barrier, manager, background, game);
        if (lazy != null) {
            callback.setReturnValue(lazy);
        }
    }
}
