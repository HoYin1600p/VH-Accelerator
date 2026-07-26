package dev.hoyin1600p.launchfastertoo.mixin.compat.powah;

import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.Marker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import owmii.powah.Powah;
import owmii.powah.lib.client.wiki.Wiki;

/**
 * Replaces Powah's item-by-item full recipe scans with two result indexes.
 */
@Mixin(value = Wiki.class, remap = false)
public abstract class WikiMixin {
    @Shadow
    @Final
    public static Marker MARKER;

    @Shadow
    @Final
    public static Map<String, Wiki> WIKIS;

    @Inject(method = "lambda$static$5", at = @At("HEAD"), cancellable = true)
    private static void launchfastertoo$indexWikiRecipes(RecipeManager recipeManager, CallbackInfo ci) {
        if (!LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get()
                || !LaunchFasterTooClientConfig.VALUES.indexPowahWikiRecipes.get()) {
            return;
        }

        ci.cancel();
        StopWatch watch = StopWatch.createStarted();
        Powah.LOGGER.info(MARKER, "Started indexed wiki recipe collection...");

        Set<Item> powahItems = new HashSet<>();
        for (Item item : Registry.ITEM) {
            ResourceLocation id = Registry.ITEM.getKey(item);
            if (id != null && Powah.MOD_ID.equals(id.getNamespace())) {
                powahItems.add(item);
            }
        }

        Map<ItemLike, List<Recipe<?>>> crafting = launchfastertoo$emptyIndex(powahItems);
        Map<ItemLike, List<Recipe<?>>> smelting = launchfastertoo$emptyIndex(powahItems);
        launchfastertoo$indexRecipes(recipeManager.getAllRecipesFor(RecipeType.CRAFTING), powahItems, crafting);
        launchfastertoo$indexRecipes(recipeManager.getAllRecipesFor(RecipeType.SMELTING), powahItems, smelting);

        for (Wiki wiki : WIKIS.values()) {
            launchfastertoo$publish(wiki.getCrafting(), crafting);
            launchfastertoo$publish(wiki.getSmelting(), smelting);
        }

        watch.stop();
        Powah.LOGGER.info(MARKER, "Indexed wiki recipe collection completed in {} ms", watch.getTime());
    }

    private static Map<ItemLike, List<Recipe<?>>> launchfastertoo$emptyIndex(Set<Item> items) {
        Map<ItemLike, List<Recipe<?>>> index = new HashMap<>();
        for (Item item : items) {
            index.put(item, new ArrayList<>());
        }
        return index;
    }

    private static void launchfastertoo$indexRecipes(
            List<? extends Recipe<?>> recipes,
            Set<Item> powahItems,
            Map<ItemLike, List<Recipe<?>>> index
    ) {
        for (Recipe<?> recipe : recipes) {
            Item result = recipe.getResultItem().getItem();
            if (powahItems.contains(result)) {
                index.get(result).add(recipe);
            }
        }
    }

    private static void launchfastertoo$publish(
            Map<ItemLike, List<Recipe<?>>> target,
            Map<ItemLike, List<Recipe<?>>> source
    ) {
        target.clear();
        source.forEach((item, recipes) -> target.put(item, new ArrayList<>(recipes)));
    }
}
