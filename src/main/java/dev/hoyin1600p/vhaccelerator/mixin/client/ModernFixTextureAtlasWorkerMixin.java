package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.TextureAtlasWorkerPool;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adapts ModernFix's faster-texture-loading path to VHA's bounded worker pool.
 *
 * <p>ModernFix replaces TextureAtlas#getBasicSpriteInfos and submits its eager
 * image loads from a private handler merged into TextureAtlas. This mixin is
 * selected only when ModernFix is present and runs after its default-priority
 * mixin, so packs without ModernFix retain the vanilla adapter.</p>
 */
@Mixin(value = TextureAtlas.class, priority = 900)
public abstract class ModernFixTextureAtlasWorkerMixin {
    @Redirect(
            method = "skipIteration",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/embeddedt/modernfix/ModernFix;"
                            + "resourceReloadExecutor()"
                            + "Ljava/util/concurrent/ExecutorService;",
                    remap = false
            ),
            remap = false
    )
    private ExecutorService vhaccelerator$routeModernFixTextureWork() {
        return TextureAtlasWorkerPool.selectModernFixService();
    }
}
