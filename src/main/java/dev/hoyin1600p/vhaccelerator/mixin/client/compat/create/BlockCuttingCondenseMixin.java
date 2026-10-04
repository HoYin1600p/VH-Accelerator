package dev.hoyin1600p.vhaccelerator.mixin.client.compat.create;

import com.simibubi.create.compat.jei.category.BlockCuttingCategory;
import com.simibubi.create.foundation.item.ItemHelper;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create groups every stonecutting recipe by its ingredient for its JEI
 * "block cutting" category by comparing it with each group found so far, which
 * is quadratic (Wolds: about 12,800 recipes, 1.3 s during JEI's start).
 * {@code ItemHelper.matchIngredients} holds exactly when both ingredients list
 * the same items in the same order (no empty stacks involved), so each
 * recipe's group is found through a map keyed by that item list. The groups,
 * their order and their outputs are identical; an ingredient that lists an
 * empty stack keeps the original scan. Verified identical to Create's own
 * grouping in Wolds (1,958 groups from 12,844 recipes).
 */
@Mixin(value = BlockCuttingCategory.CondensedBlockCuttingRecipe.class, remap = false)
public abstract class BlockCuttingCondenseMixin {
    @Inject(method = "condenseRecipes", at = @At("HEAD"), cancellable = true)
    private static void vhaccelerator$condenseByItems(
            List<Recipe<?>> stoneCuttingRecipes,
            CallbackInfoReturnable<List<BlockCuttingCategory.CondensedBlockCuttingRecipe>> callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .indexCreateBlockCuttingRecipes)) {
            return;
        }
        List<BlockCuttingCategory.CondensedBlockCuttingRecipe> condensed = new ArrayList<>();
        Map<List<Item>, BlockCuttingCategory.CondensedBlockCuttingRecipe> byItems = new HashMap<>();
        List<BlockCuttingCategory.CondensedBlockCuttingRecipe> unkeyed = new ArrayList<>();
        for (Recipe<?> recipe : stoneCuttingRecipes) {
            Ingredient ingredient = recipe.getIngredients().get(0);
            List<Item> key = itemKey(ingredient);
            BlockCuttingCategory.CondensedBlockCuttingRecipe group = key != null ? byItems.get(key) : null;
            if (group == null && key == null) {
                // Empty stacks only match by identity: the original scan.
                for (BlockCuttingCategory.CondensedBlockCuttingRecipe candidate : condensed) {
                    if (ItemHelper.matchIngredients(ingredient, candidate.getIngredients().get(0))) {
                        group = candidate;
                        break;
                    }
                }
            } else if (group == null) {
                // A keyed ingredient can still match an unkeyed group by identity.
                for (BlockCuttingCategory.CondensedBlockCuttingRecipe candidate : unkeyed) {
                    if (ItemHelper.matchIngredients(ingredient, candidate.getIngredients().get(0))) {
                        group = candidate;
                        break;
                    }
                }
            }
            if (group == null) {
                group = new BlockCuttingCategory.CondensedBlockCuttingRecipe(ingredient);
                condensed.add(group);
                if (key != null) {
                    byItems.put(key, group);
                } else {
                    unkeyed.add(group);
                }
            }
            group.addOutput(recipe.getResultItem());
        }
        callback.setReturnValue(condensed);
    }

    private static List<Item> itemKey(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        List<Item> items = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                return null;
            }
            items.add(stack.getItem());
        }
        return items;
    }
}
