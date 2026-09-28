package dev.hoyin1600p.vhaccelerator.mixin.client.compat.selene;

import dev.hoyin1600p.vhaccelerator.client.cache.EveryCompatPackCache;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Selene 1.17.14/1.17.17: Every Compat's generated client pack is restored
 * from disk on the first reload when its inputs are unchanged; see
 * EveryCompatPackCache.
 */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.selene.resourcepack.RPAwareDynamicResourceProvider", remap = false)
public abstract class DynamicResourceProviderCacheMixin {
    @Inject(method = "reloadResources", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$restoreStoredPack(ResourceManager manager, CallbackInfo callback) {
        if (EveryCompatPackCache.restore(this, manager)) {
            callback.cancel();
        }
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void vhaccelerator$storeGeneratedPack(ResourceManager manager, CallbackInfo callback) {
        EveryCompatPackCache.generated(this);
    }
}
