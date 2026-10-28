package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import com.blamejared.crafttweaker.api.action.base.IAction;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.ingredient.type.TagIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.item.MCItemStack;
import com.blamejared.crafttweaker.api.item.MCItemStackMutable;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.resources.ResourceLocation;

/** Exact CraftTweaker 9.1.213 output actions, with original sequential removal. */
public final class IndexedOutputRemovals {
    private static final String MIXIN = "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals.RecipeListIndexedOutputMixin";
    private static final String TYPED = "com.blamejared.crafttweaker.api.action.recipe.ActionRemoveRecipeByOutput";
    private static final String GENERIC = "com.blamejared.crafttweaker.api.action.recipe.generic.ActionRemoveGenericRecipeByOutput";
    private static final ThreadLocal<Request> INGREDIENT = new ThreadLocal<>();

    /**
     * The ingredient of the action being applied. A tag output is expanded to its item identities once, when
     * the action starts; an item output re-reads its (final) item on every use, as before.
     */
    private record Request(Object ingredient, RequestedKeys tagKeys) {
        Object key() { return tagKeys != null ? tagKeys : requestedItem(ingredient); }
    }

    // Initialized only by the exact-version action hook. The legacy marker
    // accessor lives in another class and cannot trigger optional linkage.
    private static final ClassValue<Field> OUTPUT = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Field field = type.getDeclaredField("output");
                if (field.getType() != IIngredient.class || !Modifier.isPrivate(field.getModifiers())
                        || !Modifier.isFinal(field.getModifiers()) || !field.trySetAccessible()) return null;
                return field;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return null;
            }
        }
    };

    private IndexedOutputRemovals() { }

    public static void applyAction(IAction action) throws Throwable {
        String name = action.getClass().getName();
        boolean outputAction = TYPED.equals(name) || GENERIC.equals(name);
        applyAction(action, outputAction, IndexedOutputRemovals::configured);
    }

    /** Narrow seam for testing unavailable common config without initializing Forge globals. */
    static void applyAction(IAction action, boolean outputAction, BooleanSupplier configuration) throws Throwable {
        GenerationOutputIndex generation = OutputIndexGenerations.current();
        Request previousIngredient = INGREDIENT.get();
        boolean previousSuspension = !OutputRemovalContext.indexAllowed();
        boolean trustedOutput = false;
        boolean previousContext = OutputRemovalContext.enter(outputAction);
        try {
            Object ingredient = outputAction && enabled(configuration) ? outputIngredient(action) : null;
            Object key = outputAction ? requestKey(ingredient) : null;
            trustedOutput = key != null;
            OutputRemovalContext.suspend(!trustedOutput);
            if (ingredient == null) INGREDIENT.remove();
            else INGREDIENT.set(new Request(ingredient, key instanceof RequestedKeys many ? many : null));
            action.apply();
        } catch (Throwable failure) {
            // CT catches and logs action failures itself. Stop using this
            // generation's index even if the enclosing runner later succeeds.
            if (generation != null) generation.fail();
            throw failure;
        } finally {
            OutputRemovalContext.restore(previousContext);
            OutputRemovalContext.restoreSuspension(previousSuspension);
            if (previousIngredient == null) INGREDIENT.remove();
            else INGREDIENT.set(previousIngredient);
        }
    }

    private static Object outputIngredient(IAction action) {
        try {
            Field field = OUTPUT.get(action.getClass());
            return field == null ? null : field.get(action);
        } catch (IllegalAccessException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /**
     * The candidate key of a supported output request: one item, or the expanded items of a tag. Wrapped,
     * conditioned and custom ingredients stay with CraftTweaker.
     */
    private static Object requestKey(Object ingredient) {
        try {
            if (ingredient != null && ingredient.getClass() == TagIngredient.class) {
                return tagKeys(((TagIngredient) ingredient).getItems());
            }
            return requestedItem(ingredient);
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static Object requestedItem(Object ingredient) {
        try {
            ItemStack stack;
            if (ingredient != null && ingredient.getClass() == MCItemStack.class) {
                stack = ((MCItemStack) ingredient).getInternal();
            } else if (ingredient != null && ingredient.getClass() == MCItemStackMutable.class) {
                stack = ((MCItemStackMutable) ingredient).getInternal();
            } else return null;
            // IItemStack.matches rejects a different item, an empty/non-empty mismatch and a larger count
            // before it reads any NBT or damage, so the item alone selects every possible match. A requested
            // stack that carries NBT (every damageable item's default stack does) is therefore still indexed;
            // CraftTweaker keeps the count, damage and NBT checks. Empty requests stay with CraftTweaker.
            return stack == null || stack.getClass() != ItemStack.class || stack.isEmpty() ? null : stack.getItem();
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /**
     * TagIngredient.matches is "any default stack of the tag matches"; each default stack must be a plain
     * CraftTweaker item stack, whose item identity is checked first. Anything else is not indexed.
     */
    static RequestedKeys tagKeys(IItemStack[] items) {
        if (items == null) return null;
        List<Object> keys = new ArrayList<>(items.length);
        for (IItemStack item : items) {
            if (item == null || (item.getClass() != MCItemStack.class && item.getClass() != MCItemStackMutable.class)) return null;
            ItemStack stack = item.getInternal();
            if (stack == null || stack.getClass() != ItemStack.class) return null;
            keys.add(stack.getItem());
        }
        return new RequestedKeys(keys);
    }

    public static <T extends Recipe<?>> boolean tryRemove(Map<ResourceLocation, T> recipes,
            Map<ResourceLocation, Recipe<?>> byName, Predicate<T> original) {
        GenerationOutputIndex generation = OutputIndexGenerations.current();
        Request request = INGREDIENT.get();
        if (generation == null || request == null || !OutputRemovalContext.active()
                || !OutputRemovalContext.indexAllowed()
                || !enabled() || request.key() == null
                || byName.getClass() != java.util.HashMap.class || (Object) recipes == byName) return false;
        boolean handled = generation.tryRemove(recipes, byName, original,
                request::key, IndexedOutputRemovals::stableOutputItem);
        if (handled) CompatMixinGroups.recordOptimizationUse(MIXIN);
        return handled;
    }

    private static boolean enabled() {
        return enabled(IndexedOutputRemovals::configured);
    }

    static boolean enabled(BooleanSupplier configuration) {
        try {
            return configuration.getAsBoolean();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean configured() {
        return settingEnabled()
                && CompatMixinGroups.activationAllowed(MIXIN);
    }

    static boolean settingEnabled() {
        return enabled(() -> VHAcceleratorConfig.commonOptimizationsEnabled()
                && VHAcceleratorConfig.COMMON.indexCraftTweakerRecipeRemovals.get());
    }

    private static Object stableOutputItem(Recipe<?> recipe) {
        // Audited exact 1.18.2 fixed-result implementations only. Subclasses,
        // custom recipes and custom getters never run just to probe eligibility.
        if (recipe == null || (recipe.getClass() != ShapedRecipe.class
                && recipe.getClass() != ShapelessRecipe.class)) return null;
        ItemStack stack = recipe.getResultItem();
        // Both result fields and ItemStack's item/delegate are final in this
        // layout. Count/tag/damage mutation cannot change an initially nonempty
        // item's identity. A later empty stack is only a false-positive candidate
        // and still goes through CT. Never cache an initially empty/AIR key:
        // setCount can resurrect the original item without a new action.
        return stack == null || stack.getClass() != ItemStack.class || stack.isEmpty() ? null : stack.getItem();
    }
}
