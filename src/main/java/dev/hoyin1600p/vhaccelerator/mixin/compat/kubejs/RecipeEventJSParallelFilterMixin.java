package dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs;

import dev.hoyin1600p.vhaccelerator.compat.kubejs.KubeJsParallelFilters;
import dev.latvian.mods.kubejs.recipe.RecipeEventJS;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Evaluates data-only recipe filters on the common pool, then hands the
 * matches to the consumer on the calling thread in the original order, so
 * removes, stages and script callbacks see exactly what the sequential scan
 * produced. See KubeJsParallelFilters.
 */
@Mixin(value = RecipeEventJS.class, remap = false)
public abstract class RecipeEventJSParallelFilterMixin {
    @Shadow
    @Final
    private List<RecipeJS> originalRecipes;

    @Inject(method = "forEachRecipe", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$filterInParallel(
            RecipeFilter filter,
            Consumer<RecipeJS> consumer,
            CallbackInfo callback
    ) {
        if (filter == RecipeFilter.ALWAYS_TRUE
                || filter == RecipeFilter.ALWAYS_FALSE
                || originalRecipes.size() < 2_048
                || !KubeJsParallelFilters.isParallelSafe(filter)) {
            return;
        }
        List<RecipeJS> matches = originalRecipes.parallelStream().filter(filter).toList();
        matches.forEach(consumer);
        callback.cancel();
    }
}
