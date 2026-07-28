package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.cache.ClientAssetFingerprint;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Starts the persistent asset fingerprint once Forge has assembled the
 * initial client resource reload and finished its early configuration setup.
 */
@Mixin(value = ClientModLoader.class, remap = false)
public abstract class ClientAssetFingerprintLifecycleMixin {
    @Inject(method = "onResourceReload", at = @At("HEAD"))
    private static void vhaccelerator$prepareStableAssetFingerprint(
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager resourceManager,
            ProfilerFiller preparationProfiler,
            ProfilerFiller reloadProfiler,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> callback
    ) {
        ClientAssetFingerprint.prepareStable();
    }
}
