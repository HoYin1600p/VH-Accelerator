package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals;

import com.blamejared.crafttweaker.api.recipe.RecipeList;
import dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals.IndexedOutputRemovals;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Takes ownership only when candidate-only removal is proven equivalent to CT's loop. */
@Mixin(value = RecipeList.class, remap = false)
public abstract class RecipeListIndexedOutputMixin<T extends Recipe<?>> {
    @Shadow @Final private Map<ResourceLocation, T> recipes;
    @Shadow @Final private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(method = "removeByRecipeTest(Ljava/util/function/Predicate;)V", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$indexedOutput(Predicate<T> predicate, CallbackInfo callback) {
        if (IndexedOutputRemovals.tryRemove(recipes, byName, predicate)) callback.cancel();
    }
}
