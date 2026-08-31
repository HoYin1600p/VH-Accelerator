package dev.hoyin1600p.vhaccelerator.mixin.compat.modernfix;

import dev.hoyin1600p.vhaccelerator.client.model.TextureAtlasWorkerPool;
import java.util.concurrent.ExecutorService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes ModernFix's public reload executor accessor texture-phase aware.
 *
 * <p>The wrapper delegates every non-texture submission to ModernFix's original
 * executor. Only work submitted synchronously while TextureAtlas is collecting
 * sprite metadata is routed to VHA's bounded pool.</p>
 */
@Pseudo
@Mixin(targets = "org.embeddedt.modernfix.ModernFix", remap = false)
public abstract class ModernFixResourceReloadExecutorMixin {
    @Inject(
            method = "resourceReloadExecutor",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void vhaccelerator$routeTextureMetadataWork(
            CallbackInfoReturnable<ExecutorService> callback
    ) {
        callback.setReturnValue(
                TextureAtlasWorkerPool.wrapModernFixService(
                        callback.getReturnValue()
                )
        );
    }
}
