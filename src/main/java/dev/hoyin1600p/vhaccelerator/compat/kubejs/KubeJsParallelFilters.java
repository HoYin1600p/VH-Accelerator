package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

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

    private KubeJsParallelFilters() {
    }

    /** True when every filter and ingredient reachable from {@code filter} is data-only. */
    public static boolean isParallelSafe(Object filter) {
        try {
            return safe(filter, new IdentityHashMap<>(), 0);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean safe(Object value, Map<Object, Boolean> seen, int depth)
            throws ReflectiveOperationException {
        if (value == null || depth > 32) {
            return value == null;
        }
        String name = value.getClass().getName();
        boolean filterOrIngredient = name.startsWith(FILTER) || name.startsWith(INGREDIENT)
                || name.equals("dev.latvian.mods.kubejs.item.ItemStackJS");
        if (!filterOrIngredient) {
            // Plain data held by a safe filter or ingredient.
            return !(value instanceof java.util.function.Predicate<?>)
                    && !name.startsWith("dev.latvian.mods.rhino.");
        }
        if (!SAFE_CLASSES.contains(name)) {
            return false;
        }
        if (seen.put(value, Boolean.TRUE) != null || name.equals("dev.latvian.mods.kubejs.item.ItemStackJS")) {
            return true;
        }
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object child = field.get(value);
                if (child instanceof Iterable<?> iterable) {
                    for (Object element : iterable) {
                        if (!safe(element, seen, depth + 1)) {
                            return false;
                        }
                    }
                } else if (!(child instanceof String || child instanceof Number || child instanceof Boolean
                        || child instanceof java.util.regex.Pattern || child instanceof Map<?, ?>
                        || child instanceof net.minecraft.resources.ResourceLocation)
                        && !safe(child, seen, depth + 1)) {
                    return false;
                }
            }
        }
        return true;
    }
}
