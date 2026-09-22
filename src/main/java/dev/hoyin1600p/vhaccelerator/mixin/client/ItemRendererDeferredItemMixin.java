package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps Forge's reload-time item cache from baking every deferred model. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererDeferredItemMixin {
    @Redirect(
            method = "onResourceManagerReload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/"
                            + "ItemModelShaper;rebuildCache()V"
            )
    )
    private void vhaccelerator$rebuildWithDeferredItems(ItemModelShaper shaper) {
        if (!DeferredItemModelBaking.rebuildItemCache(shaper)) {
            shaper.rebuildCache();
        }
    }
}
