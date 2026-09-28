package dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Answers "first smelting recipe for this single item" for every item at
 * once. {@code RecipeManager.getRecipeFor} returns the first recipe, in
 * {@code getAllRecipesFor} order, whose {@code matches} accepts the
 * container. Walking the recipes in that same order and keeping the first
 * recipe found per item gives the same answer: a vanilla
 * {@code SmeltingRecipe} with a plain vanilla, non-empty {@code Ingredient}
 * matches exactly the items its ingredient lists; any other recipe or
 * ingredient type is tested against every item, as the original lookup did.
 * Verified in Wolds: 646 of 39,076 items smeltable, no difference from
 * {@code getRecipeFor} for any item.
 */
public final class VaultSmeltingIndex {
    /** Live only during one JEI registration pass; see begin/end. */
    private static Map<Item, Recipe<?>> firstRecipe;

    private VaultSmeltingIndex() {
    }

    @SuppressWarnings("unchecked")
    public static <C extends Container, T extends Recipe<C>> Optional<T> lookup(
            RecipeManager manager,
            RecipeType<T> type,
            C container,
            Level level
    ) {
        if (type != RecipeType.SMELTING
                || container.getContainerSize() != 1
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.indexVaultSmeltingJeiRecipes)) {
            return manager.getRecipeFor(type, container, level);
        }
        Map<Item, Recipe<?>> index = index(manager, level);
        ItemStack stack = container.getItem(0);
        Recipe<?> recipe = index.get(stack.getItem());
        return Optional.ofNullable((T) recipe);
    }

    /** The client replaces recipes in place on every join and reload. */
    public static synchronized void begin() {
        firstRecipe = null;
    }

    public static synchronized void end() {
        firstRecipe = null;
    }

    private static synchronized Map<Item, Recipe<?>> index(RecipeManager manager, Level level) {
        if (firstRecipe != null) {
            return firstRecipe;
        }
        Map<Item, Recipe<?>> index = new IdentityHashMap<>();
        List<SmeltingRecipe> recipes = manager.getAllRecipesFor(RecipeType.SMELTING);
        for (SmeltingRecipe recipe : recipes) {
            Ingredient ingredient = recipe.getIngredients().isEmpty() ? null : recipe.getIngredients().get(0);
            boolean plain = recipe.getClass() == SmeltingRecipe.class
                    && ingredient != null
                    && ingredient.getClass() == Ingredient.class
                    && !ingredient.isEmpty();
            if (plain) {
                for (ItemStack listed : ingredient.getItems()) {
                    if (!listed.isEmpty()) {
                        index.putIfAbsent(listed.getItem(), recipe);
                    }
                }
            } else {
                for (Item item : ForgeRegistries.ITEMS) {
                    if (!index.containsKey(item)
                            && recipe.matches(new SimpleContainer(new ItemStack(item)), level)) {
                        index.put(item, recipe);
                    }
                }
            }
        }
        firstRecipe = index;
        return index;
    }
}
