package dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Collection;
import java.util.stream.Stream;
import mezz.jei.common.ingredients.IListElementInfo;
import mezz.jei.common.ingredients.IngredientFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Parallelizes only JEI's pre-sort traversal. The caller still waits for the
 * complete sorted list and assigns every stable index before JEI is published.
 */
@Pseudo
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Redirect(
            method = "getIngredientListPreSort",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;"
            )
    )
    private Stream<IListElementInfo<?>> vhaccelerator$selectSortStream(
            Collection<IListElementInfo<?>> ingredients
    ) {
        if (VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES.parallelJeiIngredientSorting.get()
                && ingredients.size() >= 512) {
            return ingredients.parallelStream();
        }
        return ingredients.stream();
    }
}
