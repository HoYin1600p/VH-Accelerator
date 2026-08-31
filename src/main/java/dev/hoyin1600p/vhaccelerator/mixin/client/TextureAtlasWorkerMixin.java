package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.TextureAtlasWorkerPool;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasWorkerMixin {
    @Inject(method = "getBasicSpriteInfos", at = @At("HEAD"))
    private void vhaccelerator$beginMetadataPhase(
            ResourceManager resourceManager,
            Set<ResourceLocation> spriteNames,
            CallbackInfoReturnable<Collection<TextureAtlasSprite.Info>> callback
    ) {
        TextureAtlasWorkerPool.beginMetadataPhase();
    }

    @Inject(method = "getBasicSpriteInfos", at = @At("RETURN"))
    private void vhaccelerator$endMetadataPhase(
            ResourceManager resourceManager,
            Set<ResourceLocation> spriteNames,
            CallbackInfoReturnable<Collection<TextureAtlasSprite.Info>> callback
    ) {
        TextureAtlasWorkerPool.endMetadataPhase();
    }

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

}
