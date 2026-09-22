package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.model.DeferredItemModelBaking;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolves an item left uncached for a deferred bake through the registry. */
@Mixin(ItemModelShaper.class)
public abstract class ItemModelShaperDeferredItemMixin {
    @Inject(
            method = "getItemModel(Lnet/minecraft/world/item/ItemStack;)"
                    + "Lnet/minecraft/client/resources/model/BakedModel;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void vhaccelerator$resolveDeferredItem(
            ItemStack stack,
            CallbackInfoReturnable<BakedModel> callback
    ) {
        BakedModel result = callback.getReturnValue();
        BakedModel resolved = DeferredItemModelBaking.resolveItemModel(
                (ItemModelShaper) (Object) this,
                stack,
                result
        );
        if (resolved != result) {
            callback.setReturnValue(resolved);
        }
    }
}
