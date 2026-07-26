package dev.hoyin1600p.launchfastertoo.mixin.compat.jei;

import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import java.util.Collection;
import java.util.stream.Stream;
import mezz.jei.ingredients.IListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Parallelizes only JEI's pre-sort traversal. The caller still waits for the
 * complete sorted list and assigns every stable index before JEI is published.
 */
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Redirect(
            method = "getIngredientListPreSort",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"
            )
    )
    private Stream<IListElementInfo<?>> launchfastertoo$selectSortStream(
            Collection<IListElementInfo<?>> ingredients
    ) {
        if (LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                && LaunchFasterTooClientConfig.VALUES.parallelJeiIngredientSorting.get()
                && ingredients.size() >= 512) {
            return ingredients.parallelStream();
        }
        return ingredients.stream();
    }
}
