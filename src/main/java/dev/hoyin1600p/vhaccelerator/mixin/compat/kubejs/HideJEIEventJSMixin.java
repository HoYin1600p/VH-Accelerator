package dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs;

import dev.hoyin1600p.vhaccelerator.compat.kubejs.KubeJsParallelFilters;
import dev.latvian.mods.kubejs.integration.forge.jei.HideJEIEventJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Evaluates KubeJS's {@code jei.hide.items} data-only ingredients across the
 * common pool when JEI exposes a large item list, after warming lazy KubeJS
 * ingredient caches on the calling thread. Events built with a custom matcher
 * function (outside KubeJS's own {@code JEIPlugin}) keep the original loop.
 */
@Mixin(value = HideJEIEventJS.class, remap = false)
public abstract class HideJEIEventJSMixin<T> {
    @Unique
    private static final String KUBEJS_JEI_PLUGIN_LAMBDA =
            "dev.latvian.mods.kubejs.integration.forge.jei.JEIPlugin$$Lambda";

    @Shadow
    @Final
    private IIngredientType<T> type;

    @Shadow
    @Final
    private Function<Object, Predicate<T>> function;

    @Shadow
    @Final
    private HashSet<T> hidden;

    @Shadow
    @Final
    private Collection<T> allIngredients;

    @Inject(method = "hide", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void vhaccelerator$hideWithOneParse(Object argument, CallbackInfo callback) {
        // JEI 9 and 10 name the item type differently; compare its class, and only
        // handle KubeJS's own JEIPlugin item matcher so custom HideJEIEventJS functions stay intact.
        if (type.getIngredientClass() != ItemStack.class
                || function == null
                || !function.getClass().getName().startsWith(KUBEJS_JEI_PLUGIN_LAMBDA)) {
            return;
        }
        IngredientJS ingredient = IngredientJS.of(argument);
        List<T> matches;
        if (allIngredients.size() >= 2_048 && KubeJsParallelFilters.prepareForParallel(ingredient)) {
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
