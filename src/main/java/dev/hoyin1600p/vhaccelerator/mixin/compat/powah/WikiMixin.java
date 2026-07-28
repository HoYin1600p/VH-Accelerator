package dev.hoyin1600p.vhaccelerator.mixin.compat.powah;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
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
    private static void vhaccelerator$indexWikiRecipes(RecipeManager recipeManager, CallbackInfo ci) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES.indexPowahWikiRecipes.get()) {
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

        Map<ItemLike, List<Recipe<?>>> crafting = vhaccelerator$emptyIndex(powahItems);
        Map<ItemLike, List<Recipe<?>>> smelting = vhaccelerator$emptyIndex(powahItems);
        vhaccelerator$indexRecipes(recipeManager.getAllRecipesFor(RecipeType.CRAFTING), powahItems, crafting);
        vhaccelerator$indexRecipes(recipeManager.getAllRecipesFor(RecipeType.SMELTING), powahItems, smelting);

        for (Wiki wiki : WIKIS.values()) {
            vhaccelerator$publish(wiki.getCrafting(), crafting);
            vhaccelerator$publish(wiki.getSmelting(), smelting);
        }

        watch.stop();
        Powah.LOGGER.info(MARKER, "Indexed wiki recipe collection completed in {} ms", watch.getTime());
    }

    private static Map<ItemLike, List<Recipe<?>>> vhaccelerator$emptyIndex(Set<Item> items) {
        Map<ItemLike, List<Recipe<?>>> index = new HashMap<>();
        for (Item item : items) {
            index.put(item, new ArrayList<>());
        }
        return index;
    }

    private static void vhaccelerator$indexRecipes(
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

    private static void vhaccelerator$publish(
            Map<ItemLike, List<Recipe<?>>> target,
            Map<ItemLike, List<Recipe<?>>> source
    ) {
        target.clear();
        source.forEach((item, recipes) -> target.put(item, new ArrayList<>(recipes)));
    }
}
