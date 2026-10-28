package dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs;

import dev.hoyin1600p.vhaccelerator.compat.lootjs.LootJsRegexFilters;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Answers LootJS's emptiness check of a regex filter without building the full match list. */
@Pseudo
@Mixin(targets = "com.almostreliable.lootjs.kube.LootJSPlugin", remap = false)
public abstract class LootJsItemFilterMixin {
    @Redirect(method = "ofItemFilter(Ljava/lang/Object;)Lcom/almostreliable/lootjs/filters/ItemFilter;",
            at = @At(value = "INVOKE",
                    target = "Ldev/latvian/mods/kubejs/item/ingredient/IngredientJS;isEmpty()Z", remap = false),
            require = 0, remap = false)
    private static boolean vhaccelerator$regexFilterEmpty(IngredientJS ingredient) {
        return LootJsRegexFilters.isEmpty(ingredient);
    }
}
