package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.TextureAtlasWorkerPool;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasWorkerMixin {
    @ModifyArg(
            method = "getBasicSpriteInfos",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;"
                            + "runAsync(Ljava/lang/Runnable;"
                            + "Ljava/util/concurrent/Executor;)"
                            + "Ljava/util/concurrent/CompletableFuture;"
            ),
            index = 1
    )
    private Executor vhaccelerator$routeMetadataWork(
            Executor vanillaExecutor
    ) {
        return TextureAtlasWorkerPool.select(vanillaExecutor);
    }

    @ModifyArg(
            method = "getLoadedSprites",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;"
                            + "runAsync(Ljava/lang/Runnable;"
                            + "Ljava/util/concurrent/Executor;)"
                            + "Ljava/util/concurrent/CompletableFuture;"
            ),
            index = 1
    )
    private Executor vhaccelerator$routeSpriteLoading(
            Executor vanillaExecutor
    ) {
        return TextureAtlasWorkerPool.select(vanillaExecutor);
    }
}
