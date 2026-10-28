package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipeTypeJS;
import dev.latvian.mods.kubejs.recipe.filter.AndFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import dev.latvian.mods.kubejs.recipe.minecraft.ShapelessRecipeJS;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Differential oracle for the parallel recipe filter: KubeJS's real filter classes, built the way a
 * script's {@code event.remove({...})} builds them ({@code RecipeFilter.of}), run on real
 * {@code RecipeJS} objects. The sequential {@code stream().filter(filter)} that KubeJS's own
 * {@code forEachRecipe} uses is the reference; {@link KubeJsParallelFilters#matchesInOrder} is the
 * code the mixin ships.
 *
 * <p>Item-based filters (output, input) need Minecraft's item registry, which cannot be bootstrapped
 * in this test environment; they are covered by the server A/B recipe-dump comparison instead.</p>
 */
class KubeJsParallelFilterEqualityTest {
    private static final int RECIPES = 41_000;
    private static final int ROUNDS = 12;
    private static final String[] TYPES = {"minecraft:crafting_shapeless", "minecraft:smelting",
            "botania:orechid", "create:milling", "the_vault:vault_forge"};
    private static List<RecipeJS> recipes;

    @BeforeAll
    static void corpus() throws Exception {
        RecipeTypeJS[] types = new RecipeTypeJS[TYPES.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = type(TYPES[i]);
        }
        recipes = new ArrayList<>(RECIPES);
        for (int i = 0; i < RECIPES; i++) {
            RecipeJS recipe = new ShapelessRecipeJS();
            recipe.id = new ResourceLocation("mod" + (i % 23), "group" + (i % 5) + "/recipe_" + i);
            recipe.type = types[i % types.length];
            recipe.json = new JsonObject();
            if (i % 9 == 0) {
                recipe.json.addProperty("group", "grp" + (i % 4));
            }
            recipes.add(recipe);
        }
    }

    /** RecipeTypeJS's constructor asks Architectury's registry for the id; the id is all a filter reads. */
    private static RecipeTypeJS type(String id) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        RecipeTypeJS type = (RecipeTypeJS) unsafe.allocateInstance(RecipeTypeJS.class);
        Field idField = RecipeTypeJS.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(type, new ResourceLocation(id));
        return type;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** The registry-free shapes the Wolds server scripts use (ids, mods, types) and their combinators. */
    private static Map<String, Object> scripts() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("id", map("id", "mod8:group3/recipe_123"));
        filters.put("idRegex", map("id", "/mod1:group[0-9]\\/recipe_1.*/"));
        filters.put("mod", map("mod", "mod5"));
        filters.put("type", map("type", "minecraft:smelting"));
        filters.put("group", map("group", "grp2"));
        filters.put("modAndType", map("mod", "mod4", "type", "create:milling"));
        filters.put("not", map("not", map("mod", "mod1")));
        filters.put("or", List.of(map("mod", "mod1"), map("id", "mod8:group3/recipe_123"),
                map("type", "botania:orechid")));
        filters.put("orKey", map("or", List.of(map("type", "create:milling"), map("group", "grp1")),
                "not", map("mod", "mod2")));
        return filters;
    }

    @Test
    void everyDataOnlyFilterShapeMatchesTheSequentialScanInOrderAndIsAccepted() {
        for (Map.Entry<String, Object> script : scripts().entrySet()) {
            RecipeFilter filter = RecipeFilter.of(script.getValue());
            assertNotSame(RecipeFilter.ALWAYS_FALSE, filter, script.getKey());
            assertNotSame(RecipeFilter.ALWAYS_TRUE, filter, script.getKey());
            assertTrue(KubeJsParallelFilters.isParallelSafe(filter), script.getKey() + " " + filter);
            List<RecipeJS> serial = recipes.stream().filter(filter).toList();
            assertFalse(serial.isEmpty(), script.getKey() + " selects something");
            for (int round = 0; round < ROUNDS; round++) {
                List<RecipeJS> parallel = KubeJsParallelFilters.matchesInOrder(recipes, filter);
                assertEquals(serial.size(), parallel.size(), script.getKey());
                for (int i = 0; i < serial.size(); i++) {
                    assertSame(serial.get(i), parallel.get(i), script.getKey() + " match " + i);
                }
            }
        }
    }

    @Test
    void removeAndForEachSeeTheSameRecipesInTheSameOrder() {
        for (Map.Entry<String, Object> script : scripts().entrySet()) {
            RecipeFilter filter = RecipeFilter.of(script.getValue());
            // KubeJS's event.remove(): a set add per match, counting the new ones.
            Set<RecipeJS> removedSerial = new LinkedHashSet<>();
            List<String> orderSerial = new ArrayList<>();
            recipes.stream().filter(filter).forEach(recipe -> {
                if (removedSerial.add(recipe)) {
                    orderSerial.add(recipe.getId());
                }
            });
            Set<RecipeJS> removedParallel = new LinkedHashSet<>();
            List<String> orderParallel = new ArrayList<>();
            KubeJsParallelFilters.matchesInOrder(recipes, filter).forEach(recipe -> {
                if (removedParallel.add(recipe)) {
                    orderParallel.add(recipe.getId());
                }
            });
            assertEquals(orderSerial, orderParallel, script.getKey());
            assertEquals(List.copyOf(removedSerial), List.copyOf(removedParallel), script.getKey());
        }
    }

    @Test
    void scriptCallbacksAndCustomPredicatesAreNeverEvaluatedOffTheCallingThread() {
        RecipeFilter callback = recipe -> recipe.getId().endsWith("7");
        assertFalse(KubeJsParallelFilters.isParallelSafe(callback));
        AndFilter mixed = new AndFilter();
        mixed.list.add(RecipeFilter.of(map("mod", "mod1")));
        mixed.list.add(callback);
        assertFalse(KubeJsParallelFilters.isParallelSafe(mixed));
        assertTrue(KubeJsParallelFilters.isParallelSafe(RecipeFilter.of(map("mod", "mod1"))));
    }
}
