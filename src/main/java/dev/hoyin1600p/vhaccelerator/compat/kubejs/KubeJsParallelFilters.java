package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.item.ingredient.TagIngredientJS;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.server.ServerSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Decides whether a KubeJS recipe filter may be evaluated on several threads.
 *
 * <p>KubeJS 1802 evaluates {@code event.remove(filter)} and
 * {@code event.forEachRecipe(filter, ...)} sequentially over every recipe, once
 * per call; a pack with hundreds of removes scans the whole recipe list
 * hundreds of times while a world opens. KubeJS itself already evaluates the
 * same filter types in parallel for {@code replaceInput}. Only filters built
 * entirely from KubeJS's data-only filter and ingredient classes qualify; a
 * filter or ingredient that may call back into a script (a JavaScript function
 * or custom predicate) is never evaluated off the calling thread.</p>
 *
 * <p>Before parallel evaluation begins, this helper also guards against KubeJS's
 * non-thread-safe lazy caches: {@code UtilsJS.getUniqueId()} shares an
 * unsynchronized static MD5 {@code MessageDigest} when a recipe has a null id,
 * {@code ServerSettings.useOriginalRecipeForFilters} invokes arbitrary modded
 * {@code Recipe} methods, {@code ItemStackJS.getList()} lazily populates a
 * static cache via {@code Item.fillItemCategory}, and {@code TagIngredientJS}
 * lazily caches its tag holders in a non-volatile field.</p>
 */
public final class KubeJsParallelFilters {
    private static final String FILTER = "dev.latvian.mods.kubejs.recipe.filter.";
    private static final String INGREDIENT = "dev.latvian.mods.kubejs.item.ingredient.";
    private static final Set<String> SAFE_CLASSES = Set.of(
            FILTER + "IDFilter",
            FILTER + "RegexIDFilter",
            FILTER + "ModFilter",
            FILTER + "TypeFilter",
            FILTER + "GroupFilter",
            FILTER + "OutputFilter",
            FILTER + "InputFilter",
            FILTER + "AndFilter",
            FILTER + "OrFilter",
            FILTER + "NotFilter",
            "dev.latvian.mods.kubejs.item.ItemStackJS",
            INGREDIENT + "TagIngredientJS",
            INGREDIENT + "ModIngredientJS",
            INGREDIENT + "RegexIngredientJS",
            INGREDIENT + "MatchAllIngredientJS",
            INGREDIENT + "IgnoreNBTIngredientJS",
            INGREDIENT + "WeakNBTIngredientJS",
            INGREDIENT + "IngredientStackJS",
            INGREDIENT + "GroupIngredientJS",
            INGREDIENT + "MatchAnyIngredientJS",
            INGREDIENT + "NotIngredientJS"
    );
    private static final Set<String> ID_READING_FILTERS = Set.of(
            FILTER + "IDFilter",
            FILTER + "RegexIDFilter",
            FILTER + "ModFilter"
    );
    private static final Set<String> ITEM_MATCH_FILTERS = Set.of(
            FILTER + "InputFilter",
            FILTER + "OutputFilter"
    );
    private static final Set<String> ITEM_LIST_INGREDIENTS = Set.of(
            INGREDIENT + "ModIngredientJS",
            INGREDIENT + "RegexIngredientJS",
            INGREDIENT + "MatchAllIngredientJS",
            INGREDIENT + "GroupIngredientJS",
            INGREDIENT + "NotIngredientJS"
    );

    private KubeJsParallelFilters() {
    }

    /**
     * The elements {@code filter} accepts, in list order, evaluated on the common pool. The caller
     * applies its consumer to the result on its own thread, so the consumer sees what the sequential
     * {@code stream().filter(filter).forEach(consumer)} would hand it.
     */
    public static <T> List<T> matchesInOrder(List<T> items, Predicate<? super T> filter) {
        return items.parallelStream().filter(filter).toList();
    }

    /**
     * True when {@code filter} can be safely evaluated in parallel over {@code recipes}, after
     * warming any lazy KubeJS ingredient caches on the calling thread.
     */
    public static boolean canParallelizeRecipeFilter(List<RecipeJS> recipes, Object filter) {
        FilterShape shape = inspect(filter);
        if (shape == null) {
            return false;
        }
        if (shape.readsOriginalRecipeCandidate && usesOriginalRecipeFilters()) {
            return false;
        }
        if (shape.readsRecipeId && hasUnassignedRecipeId(recipes)) {
            return false;
        }
        return warmUp(shape);
    }

    /**
     * Pre-populates lazy KubeJS ingredient caches ({@code ItemStackJS.getList()} and
     * {@code TagIngredientJS.getHolders()}) on the calling thread before parallel matching.
     * Returns {@code false} if {@code filterOrIngredient} is not parallel-safe or warm-up fails.
     */
    public static boolean prepareForParallel(Object filterOrIngredient) {
        FilterShape shape = inspect(filterOrIngredient);
        return shape != null && warmUp(shape);
    }

    /** True when every filter and ingredient reachable from {@code filter} is data-only. */
    public static boolean isParallelSafe(Object filter) {
        return inspect(filter) != null;
    }

    static boolean hasUnassignedRecipeId(List<RecipeJS> recipes) {
        for (int i = 0, size = recipes.size(); i < size; i++) {
            RecipeJS recipe = recipes.get(i);
            if (recipe == null || recipe.id == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesOriginalRecipeFilters() {
        try {
            ServerSettings settings = ServerSettings.instance;
            return settings != null && settings.useOriginalRecipeForFilters;
        } catch (LinkageError | RuntimeException failure) {
            return true;
        }
    }

    private static boolean warmUp(FilterShape shape) {
        try {
            if (shape.needsItemList) {
                ItemStackJS.getList();
            }
            if (shape.tagIngredients != null) {
                for (TagIngredientJS tag : shape.tagIngredients) {
                    tag.getHolders();
                }
            }
            return true;
        } catch (LinkageError | RuntimeException failure) {
            return false;
        }
    }

    private static FilterShape inspect(Object filter) {
        try {
            FilterShape shape = new FilterShape();
            return safe(filter, new IdentityHashMap<>(), shape, 0) ? shape : null;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static boolean safe(Object value, Map<Object, Boolean> seen, FilterShape shape, int depth)
            throws ReflectiveOperationException {
        if (value == null || depth > 32) {
            return value == null;
        }
        String name = value.getClass().getName();
        boolean filterOrIngredient = name.startsWith(FILTER) || name.startsWith(INGREDIENT)
                || name.equals("dev.latvian.mods.kubejs.item.ItemStackJS");
        if (!filterOrIngredient) {
            // Plain data held by a safe filter or ingredient.
            return !(value instanceof Predicate<?>)
                    && !name.startsWith("dev.latvian.mods.rhino.");
        }
        if (!SAFE_CLASSES.contains(name)) {
            return false;
        }
        if (seen.put(value, Boolean.TRUE) != null) {
            return true;
        }
        if (ID_READING_FILTERS.contains(name)) {
            shape.readsRecipeId = true;
        }
        if (ITEM_MATCH_FILTERS.contains(name)) {
            shape.readsOriginalRecipeCandidate = true;
        }
        if (ITEM_LIST_INGREDIENTS.contains(name)) {
            shape.needsItemList = true;
        }
        if (value instanceof TagIngredientJS tag) {
            shape.addTagIngredient(tag);
        }
        if (name.equals("dev.latvian.mods.kubejs.item.ItemStackJS")) {
            return true;
        }
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object child = field.get(value);
                if (!safeChild(child, seen, shape, depth + 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean safeChild(Object child, Map<Object, Boolean> seen, FilterShape shape, int depth)
            throws ReflectiveOperationException {
        if (child == null) {
            return true;
        }
        if (child instanceof String || child instanceof Number || child instanceof Boolean
                || child instanceof Pattern
                || child instanceof net.minecraft.resources.ResourceLocation) {
            return true;
        }
        if (child instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (!safeChild(element, seen, shape, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (child instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!safeChild(entry.getKey(), seen, shape, depth + 1)
                        || !safeChild(entry.getValue(), seen, shape, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (child instanceof Object[] array) {
            for (Object element : array) {
                if (!safeChild(element, seen, shape, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        return safe(child, seen, shape, depth);
    }

    private static final class FilterShape {
        boolean readsRecipeId;
        boolean readsOriginalRecipeCandidate;
        boolean needsItemList;
        List<TagIngredientJS> tagIngredients;

        void addTagIngredient(TagIngredientJS tag) {
            if (tagIngredients == null) {
                tagIngredients = new java.util.ArrayList<>(2);
            }
            tagIngredients.add(tag);
        }
    }
}
