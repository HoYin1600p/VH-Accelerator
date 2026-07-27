package dev.hoyin1600p.vhaccelerator.mixin.compat.ironfurnaces;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.ironfurnaces.IronFurnacesRecipeCache;
import ironfurnaces.Config;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "ironfurnaces.jei.IronFurnacesJEIPlugin", remap = false)
public abstract class IronFurnacesJeiPluginMixin {
    @Inject(method = "registerRecipes", at = @At("HEAD"), cancellable = true, require = 1)
    private void vhaccelerator$useCachedRecipes(
            IRecipeRegistration registration,
            CallbackInfo callback
    ) {
        if (VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES.cacheIronFurnacesJeiRecipes.get()
                && Config.enableJeiPlugin.get()
                && IronFurnacesRecipeCache.registerRecipes(registration)) {
            callback.cancel();
        }
    }
}
