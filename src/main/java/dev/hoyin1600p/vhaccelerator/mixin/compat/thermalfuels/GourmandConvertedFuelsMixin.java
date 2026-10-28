package dev.hoyin1600p.vhaccelerator.mixin.compat.thermalfuels;

import dev.hoyin1600p.vhaccelerator.compat.thermalfuels.DeferredConvertedFuels;
import dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Builds Gourmand's converted-fuel list when something reads it, not at every tag update. */
@Pseudo
@Mixin(targets = "cofh.thermal.core.util.managers.dynamo.GourmandFuelManager", remap = false)
public abstract class GourmandConvertedFuelsMixin {
    @Unique
    private final DeferredConvertedFuels<RecipeManager> vhaccelerator$deferred = new DeferredConvertedFuels<>();

    @Shadow(remap = false)
    protected abstract void createConvertedRecipes(RecipeManager recipeManager);

    @Inject(method = "createConvertedRecipes(Lnet/minecraft/world/item/crafting/RecipeManager;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void vhaccelerator$deferScan(RecipeManager recipeManager, CallbackInfo callback) {
        if (vhaccelerator$deferred != null
                && ThermalConvertedFuels.active(ThermalConvertedFuels.GOURMAND_MIXIN)
                && vhaccelerator$deferred.tryDefer(recipeManager)) {
            ThermalConvertedFuels.deferred(ThermalConvertedFuels.GOURMAND_MIXIN, "Gourmand");
            callback.cancel();
        }
    }

    @Inject(method = "clear()V", at = @At("HEAD"), require = 0, remap = false)
    private void vhaccelerator$dropOwedScan(CallbackInfo callback) {
        if (vhaccelerator$deferred != null) vhaccelerator$deferred.discard();
    }

    @Inject(method = "getConvertedFuels()Ljava/util/List;", at = @At("HEAD"), require = 0, remap = false)
    private void vhaccelerator$scanOnFirstRead(CallbackInfoReturnable<java.util.List<?>> callback) {
        if (vhaccelerator$deferred == null) return;
        RecipeManager owed = vhaccelerator$deferred.begin();
        if (owed == null) return;
        try {
            createConvertedRecipes(owed);
        } finally {
            vhaccelerator$deferred.end();
        }
    }
}
