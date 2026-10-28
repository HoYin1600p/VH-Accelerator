package dev.hoyin1600p.vhaccelerator.mixin.compat.startup.kubejs;

import dev.hoyin1600p.vhaccelerator.startup.StartupWorkObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact-version dedicated diagnostics; target behaviour and arguments are unchanged. */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipeEventJS", remap = false)
public abstract class KubeRecipeObserverMixin {
    @Inject(method = "post(Lnet/minecraft/world/item/crafting/RecipeManager;Ljava/util/Map;)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart0(CallbackInfo ci) {
        StartupWorkObserver.enter("kubejs.recipe.post", this);
    }

    @Inject(method = "post(Lnet/minecraft/world/item/crafting/RecipeManager;Ljava/util/Map;)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd0(CallbackInfo ci) {
        StartupWorkObserver.exit("kubejs.recipe.post");
    }

    @Inject(method = "forEachRecipe(Ldev/latvian/mods/kubejs/recipe/filter/RecipeFilter;Ljava/util/function/Consumer;)V", at = @At("HEAD"), require = 0)
    private void vha$observeStart1(@org.spongepowered.asm.mixin.injection.Coerce Object filter, java.util.function.Consumer<?> consumer, CallbackInfo ci) {
        StartupWorkObserver.enter("kubejs.recipe.filter", filter);
    }

    @Inject(method = "forEachRecipe(Ldev/latvian/mods/kubejs/recipe/filter/RecipeFilter;Ljava/util/function/Consumer;)V", at = @At("RETURN"), require = 0)
    private void vha$observeEnd1(@org.spongepowered.asm.mixin.injection.Coerce Object filter, java.util.function.Consumer<?> consumer, CallbackInfo ci) {
        StartupWorkObserver.exit("kubejs.recipe.filter");
    }

}
