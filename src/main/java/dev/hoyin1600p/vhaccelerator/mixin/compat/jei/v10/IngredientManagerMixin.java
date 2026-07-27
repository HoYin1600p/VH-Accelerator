package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.compat.jei.DeferredIngredientMutations;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.common.ingredients.IngredientFilter;
import mezz.jei.common.ingredients.IngredientManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = IngredientManager.class, remap = false)
public abstract class IngredientManagerMixin {
    @Shadow
    @Final
    private IngredientFilter ingredientFilter;

    @Inject(method = "addIngredientsAtRuntime", at = @At("HEAD"), cancellable = true)
    private <V> void vhaccelerator$deferAdd(
            IIngredientType<V> ingredientType,
            Collection<V> ingredients,
            CallbackInfo ci
    ) {
        IngredientManager self = (IngredientManager) (Object) this;
        List<V> snapshot = List.copyOf(ingredients);
        if (((DeferredIngredientMutations) ingredientFilter)
                .vhaccelerator$deferIngredientMutation(
                        () -> self.addIngredientsAtRuntime(ingredientType, snapshot)
                )) {
            ci.cancel();
        }
    }

    @Inject(method = "removeIngredientsAtRuntime", at = @At("HEAD"), cancellable = true)
    private <V> void vhaccelerator$deferRemove(
            IIngredientType<V> ingredientType,
            Collection<V> ingredients,
            CallbackInfo ci
    ) {
        IngredientManager self = (IngredientManager) (Object) this;
        List<V> snapshot = List.copyOf(ingredients);
        if (((DeferredIngredientMutations) ingredientFilter)
                .vhaccelerator$deferIngredientMutation(
                        () -> self.removeIngredientsAtRuntime(ingredientType, snapshot)
                )) {
            ci.cancel();
        }
    }
}
