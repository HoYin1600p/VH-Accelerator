package dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs;

import dev.hoyin1600p.vhaccelerator.compat.kubejs.KubeJsRecipeIndex;
import dev.latvian.mods.kubejs.recipe.RecipeEventJS;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Answers {@code remove} / {@code stage} filters that name a recipe id or an output item from
 * per-event lookup tables instead of scanning every recipe. Runs before the parallel filter (lower
 * priority number); a filter the tables cannot narrow falls through to it and then to KubeJS's own
 * loop. See KubeJsRecipeIndex.
 */
@Mixin(value = RecipeEventJS.class, remap = false, priority = 900)
public abstract class RecipeEventJSRemoveIndexMixin {
    @Shadow
    @Final
    private List<RecipeJS> originalRecipes;

    @Unique
    private KubeJsRecipeIndex vhaccelerator$index;

    @Inject(method = "forEachRecipe", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$answerFromIndex(
            RecipeFilter filter,
            Consumer<RecipeJS> consumer,
            CallbackInfo callback
    ) {
        if (filter == RecipeFilter.ALWAYS_TRUE
                || filter == RecipeFilter.ALWAYS_FALSE
                || originalRecipes.size() < 1_024) {
            return;
        }
        if (vhaccelerator$index == null) {
            vhaccelerator$index = KubeJsRecipeIndex.forEvent(originalRecipes);
        }
        if (vhaccelerator$index.forEach(filter, consumer, KubeJsRecipeIndex.isInternalConsumer(consumer))) {
            callback.cancel();
        }
    }
}
