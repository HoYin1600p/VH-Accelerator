package dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs;

import dev.hoyin1600p.vhaccelerator.compat.kubejs.KubeJsParallelFilters;
import dev.latvian.mods.kubejs.integration.forge.jei.HideJEIEventJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KubeJS's item hide predicate parses the script's argument
 * ({@code IngredientJS.of(o)}) again for every one of JEI's ingredients, once
 * per {@code event.hide(...)} call; Wolds' 206 hides test about 54,000 items
 * each. The argument is parsed once per call; a data-only ingredient is then
 * tested on the common pool (see KubeJsParallelFilters), anything else on the
 * calling thread. Matches are added to the hidden set on the calling thread.
 */
@Mixin(value = HideJEIEventJS.class, remap = false)
public abstract class HideJEIEventJSMixin<T> {
    @Shadow
    @Final
    private IIngredientType<T> type;

    @Shadow
    @Final
    private HashSet<T> hidden;

    @Shadow
    @Final
    private Collection<T> allIngredients;

    @Inject(method = "hide", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void vhaccelerator$hideWithOneParse(Object argument, CallbackInfo callback) {
        // JEI 9 and 10 name the item type differently; compare its class.
        if (type.getIngredientClass() != ItemStack.class) {
            return;
        }
        IngredientJS ingredient = IngredientJS.of(argument);
        List<T> matches;
        if (allIngredients.size() >= 2_048 && KubeJsParallelFilters.isParallelSafe(ingredient)) {
            matches = allIngredients.parallelStream()
                    .filter(value -> ingredient.testVanilla((ItemStack) value))
                    .toList();
        } else {
            matches = allIngredients.stream()
                    .filter(value -> ingredient.testVanilla((ItemStack) value))
                    .toList();
        }
        hidden.addAll(matches);
        callback.cancel();
    }
}
