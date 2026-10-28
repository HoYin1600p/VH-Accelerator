package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.RecipeTypeJS;
import dev.latvian.mods.kubejs.recipe.filter.AndFilter;
import dev.latvian.mods.kubejs.recipe.filter.NotFilter;
import dev.latvian.mods.kubejs.recipe.filter.OrFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import dev.latvian.mods.kubejs.recipe.minecraft.ShapelessRecipeJS;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Differential oracle for the KubeJS removal index. Real {@code RecipeJS} objects and KubeJS's real
 * filter classes (built the way a script's {@code event.remove({...})} builds them) are filtered
 * twice: by the stream KubeJS itself runs, and by {@link KubeJsRecipeIndex}. The two must hand the
 * same recipes to the consumer in the same order.
 *
 * <p>The id table is the production {@link KubeJsRecipeIdSource}. Output filters need Minecraft's
 * item registry, which cannot be bootstrapped here, so the output side is exercised through a
 * stand-in item source (an output table over strings, with a filter class of the same shape as
 * {@code OutputFilter}); the production {@code KubeJsRecipeOutputSource} is covered by the server
 * A/B recipe-dump comparison.</p>
 */
class KubeJsRecipeIndexEqualityTest {
    private static final int RECIPES = 41_000;
    private static final String[] TYPES = {"minecraft:crafting_shapeless", "minecraft:smelting",
            "botania:orechid", "create:milling", "the_vault:vault_forge"};

    private List<RecipeJS> recipes;
    private Map<RecipeJS, Set<String>> outputs;
    private FakeOutputSource outputSource;
    private KubeJsRecipeIndex index;

    /** The stand-in for OutputFilter: accepts the recipes that list the item among their outputs. */
    private static final class FakeOutputFilter implements RecipeFilter {
        private final Map<RecipeJS, Set<String>> outputs;
        final String item;

        FakeOutputFilter(Map<RecipeJS, Set<String>> outputs, String item) {
            this.outputs = outputs;
            this.item = item;
        }

        @Override
        public boolean test(RecipeJS recipe) {
            return outputs.getOrDefault(recipe, Set.of()).contains(item);
        }
    }

    private static final class FakeOutputSource implements KubeJsRecipeIndex.Source<String> {
        private final Map<RecipeJS, Set<String>> outputs;
        private final Set<RecipeJS> unclassified = new HashSet<>();
        private Map<RecipeJS, Set<String>> seen = new IdentityHashMap<>();
        int snapshots;

        FakeOutputSource(Map<RecipeJS, Set<String>> outputs) {
            this.outputs = outputs;
        }

        @Override
        public String name() {
            return "fake output";
        }

        @Override
        public String keyOf(RecipeFilter filter) {
            return filter instanceof FakeOutputFilter fake ? fake.item : null;
        }

        @Override
        public void snapshot(List<RecipeJS> list, KubeJsRecipeIndex.Sink<String> sink) {
            snapshots++;
            seen = new IdentityHashMap<>();
            for (int i = 0; i < list.size(); i++) {
                RecipeJS recipe = list.get(i);
                Set<String> items = outputs.getOrDefault(recipe, Set.of());
                seen.put(recipe, Set.copyOf(items));
                if (unclassified.contains(recipe)) {
                    sink.unknown(i);
                }
                for (String item : items) {
                    sink.put(i, item);
                }
            }
        }

        @Override
        public boolean isCurrent(List<RecipeJS> list) {
            if (list.size() != seen.size()) {
                return false;
            }
            for (RecipeJS recipe : list) {
                if (!outputs.getOrDefault(recipe, Set.of()).equals(seen.get(recipe))) {
                    return false;
                }
            }
            return true;
        }
    }

    @BeforeEach
    void corpus() throws Exception {
        RecipeTypeJS[] types = new RecipeTypeJS[TYPES.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = type(TYPES[i]);
        }
        recipes = new ArrayList<>(RECIPES);
        outputs = new IdentityHashMap<>();
        for (int i = 0; i < RECIPES; i++) {
            RecipeJS recipe = new ShapelessRecipeJS();
            recipe.id = new ResourceLocation("mod" + (i % 23), "group" + (i % 5) + "/recipe_" + i);
            recipe.type = types[i % types.length];
            recipe.json = new JsonObject();
            recipes.add(recipe);
            Set<String> items = new LinkedHashSet<>();
            items.add("item" + (i % 700));
            if (i % 11 == 0) {
                items.add("item" + ((i + 5) % 700));
            }
            outputs.put(recipe, items);
        }
        outputSource = new FakeOutputSource(outputs);
        // A few recipes the output table cannot classify (a custom recipe whose result throws).
        for (int i = 0; i < RECIPES; i += 997) {
            outputSource.unclassified.add(recipes.get(i));
        }
        index = new KubeJsRecipeIndex(recipes, List.of(new KubeJsRecipeIdSource(), outputSource));
    }

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

    private static AndFilter and(RecipeFilter... parts) {
        AndFilter filter = new AndFilter();
        filter.list.addAll(List.of(parts));
        return filter;
    }

    private static OrFilter or(RecipeFilter... parts) {
        OrFilter filter = new OrFilter();
        filter.list.addAll(List.of(parts));
        return filter;
    }

    private List<RecipeJS> serial(RecipeFilter filter) {
        return recipes.stream().filter(filter).toList();
    }

    /** Runs the filter through the index; null when the index declined (the caller would scan). */
    private List<RecipeJS> indexed(RecipeFilter filter) {
        List<RecipeJS> seen = new ArrayList<>();
        return index.forEach(filter, seen::add, true) ? seen : null;
    }

    private void assertIndexedEqualsSerial(String label, RecipeFilter filter) {
        List<RecipeJS> expected = serial(filter);
        List<RecipeJS> actual = indexed(filter);
        assertNotNull(actual, label + " should be answered from the tables: " + filter);
        assertEquals(expected.size(), actual.size(), label);
        for (int i = 0; i < expected.size(); i++) {
            assertSame(expected.get(i), actual.get(i), label + " match " + i);
        }
    }

    private RecipeFilter fakeOutput(String item) {
        return new FakeOutputFilter(outputs, item);
    }

    @Test
    void idFiltersAsScriptsBuildThemMatchTheScan() {
        assertIndexedEqualsSerial("one id", RecipeFilter.of(map("id", "mod8:group3/recipe_123")));
        assertIndexedEqualsSerial("one id, a middle recipe", RecipeFilter.of(map("id", "mod0:group0/recipe_20000")));
        assertIndexedEqualsSerial("a missing id", RecipeFilter.of(map("id", "mod8:group3/nothing")));
        assertIndexedEqualsSerial("a list of ids (OR)", RecipeFilter.of(List.of(
                map("id", "mod8:group3/recipe_123"), map("id", "mod2:group2/recipe_40000"),
                map("id", "mod0:group0/recipe_0"), map("id", "mod1:none/missing"))));
        assertIndexedEqualsSerial("id AND mod (match)", RecipeFilter.of(map("id", "mod8:group3/recipe_123", "mod", "mod8")));
        assertIndexedEqualsSerial("id AND mod (no match)", RecipeFilter.of(map("id", "mod8:group3/recipe_123", "mod", "mod9")));
        assertIndexedEqualsSerial("id AND type", RecipeFilter.of(map("id", "mod8:group3/recipe_123", "type", "create:milling")));
        assertIndexedEqualsSerial("id AND NOT", RecipeFilter.of(map("id", "mod8:group3/recipe_123", "not", map("mod", "mod1"))));
        assertIndexedEqualsSerial("or key with ids", RecipeFilter.of(map("or", List.of(
                map("id", "mod8:group3/recipe_123"), map("id", "mod5:group4/recipe_9"))
                , "type", "create:milling")));
    }

    @Test
    void outputFiltersAndMixedShapesMatchTheScan() {
        assertIndexedEqualsSerial("one output", fakeOutput("item5"));
        assertIndexedEqualsSerial("an absent output", fakeOutput("itemX"));
        assertIndexedEqualsSerial("two outputs (OR)", or(fakeOutput("item5"), fakeOutput("item699")));
        RecipeFilter id = RecipeFilter.of(map("id", "mod8:group3/recipe_123"));
        RecipeFilter mod = RecipeFilter.of(map("mod", "mod3"));
        assertIndexedEqualsSerial("output AND mod", and(fakeOutput("item5"), mod));
        assertIndexedEqualsSerial("output AND NOT mod", and(fakeOutput("item5"), new NotFilter(mod)));
        assertIndexedEqualsSerial("id AND output", and(id, fakeOutput("item123")));
        assertIndexedEqualsSerial("id OR output", or(id, fakeOutput("item5")));
        assertIndexedEqualsSerial("(id OR output) AND mod", and(or(id, fakeOutput("item5")), mod));
        assertIndexedEqualsSerial("output AND (id OR output)", and(fakeOutput("item5"), or(id, fakeOutput("item6"))));
        assertIndexedEqualsSerial("two outputs AND", and(fakeOutput("item5"), fakeOutput("item16")));
        // The recipes the output table could not classify are candidates and still decided by the real test.
        assertTrue(outputSource.unclassified.size() > 10);
        for (RecipeJS unclassified : outputSource.unclassified) {
            String item = outputs.get(unclassified).iterator().next();
            List<RecipeJS> found = indexed(fakeOutput(item));
            assertTrue(found.contains(unclassified));
        }
    }

    @Test
    void filtersThatCannotBeNarrowedAreDeclinedWithoutCallingTheConsumer() {
        List<RecipeFilter> declined = List.of(
                RecipeFilter.of(map("mod", "mod5")),
                RecipeFilter.of(map("type", "minecraft:smelting")),
                RecipeFilter.of(map("group", "grp2")),
                RecipeFilter.of(map("id", "/mod1:group[0-9]\\/recipe_1.*/")),
                RecipeFilter.of(map("not", map("mod", "mod1"))),
                RecipeFilter.of(List.of(map("id", "mod8:group3/recipe_123"), map("mod", "mod5"))),
                and(RecipeFilter.of(map("mod", "mod5")), RecipeFilter.of(map("type", "create:milling"))),
                or(fakeOutput("item5"), RecipeFilter.of(map("type", "create:milling"))),
                new NotFilter(fakeOutput("item5")),
                (RecipeFilter) recipe -> recipe.getId().endsWith("7"));
        for (RecipeFilter filter : declined) {
            Consumer<RecipeJS> never = recipe -> fail("consumer must not run when the index declines " + filter);
            assertFalse(index.forEach(filter, never, true), String.valueOf(filter));
        }
        // An empty OR accepts nothing; an empty AND accepts everything and is not narrowed.
        assertEquals(List.of(), indexed(or()));
        assertFalse(index.forEach(and(), recipe -> fail("empty AND"), true));
    }

    @Test
    void aConsumerThatIsNotKubeJsOwnIsNeverAnsweredFromTheTables() {
        RecipeFilter filter = RecipeFilter.of(map("id", "mod8:group3/recipe_123"));
        assertFalse(index.forEach(filter, recipe -> fail("script callbacks stay sequential"), false));
        assertFalse(KubeJsRecipeIndex.isInternalConsumer((Consumer<RecipeJS>) recipe -> { }));
        assertFalse(KubeJsRecipeIndex.isInternalConsumer(null));
    }

    @Test
    void removeSemanticsAndOrderMatchIncludingDuplicatesAcrossFilters() {
        List<RecipeFilter> script = List.of(
                RecipeFilter.of(map("id", "mod8:group3/recipe_123")),
                RecipeFilter.of(map("id", "mod8:group3/recipe_123")),
                or(fakeOutput("item5"), fakeOutput("item6")),
                and(fakeOutput("item5"), RecipeFilter.of(map("mod", "mod3"))),
                RecipeFilter.of(List.of(map("id", "mod2:group2/recipe_40000"), map("id", "mod0:group0/recipe_0"))));
        Set<RecipeJS> removedSerial = new LinkedHashSet<>();
        List<String> logSerial = new ArrayList<>();
        Set<RecipeJS> removedIndexed = new LinkedHashSet<>();
        List<String> logIndexed = new ArrayList<>();
        int countSerial = 0;
        int countIndexed = 0;
        for (RecipeFilter filter : script) {
            for (RecipeJS recipe : serial(filter)) {
                if (removedSerial.add(recipe)) {
                    logSerial.add(recipe.getId());
                    countSerial++;
                }
            }
            for (RecipeJS recipe : indexed(filter)) {
                if (removedIndexed.add(recipe)) {
                    logIndexed.add(recipe.getId());
                    countIndexed++;
                }
            }
        }
        assertEquals(logSerial, logIndexed);
        assertEquals(List.copyOf(removedSerial), List.copyOf(removedIndexed));
        assertEquals(countSerial, countIndexed);
        assertTrue(countIndexed > 0);
    }

    @Test
    void theTablesFollowScriptEditsBetweenCalls() {
        RecipeFilter oldId = RecipeFilter.of(map("id", "mod8:group3/recipe_123"));
        RecipeFilter newId = RecipeFilter.of(map("id", "kjs:renamed"));
        assertIndexedEqualsSerial("before the edit", oldId);
        assertIndexedEqualsSerial("new id, before", newId);
        // A script renames a recipe: the old id stops matching and the new one starts.
        RecipeJS renamed = recipes.stream().filter(oldId).findFirst().orElseThrow();
        renamed.id = new ResourceLocation("kjs", "renamed");
        assertIndexedEqualsSerial("old id after rename", oldId);
        assertIndexedEqualsSerial("new id after rename", newId);
        assertSame(renamed, indexed(newId).get(0));
        // A script changes outputs (what replaceOutput does): the output table is rebuilt.
        RecipeFilter output = fakeOutput("item5");
        assertIndexedEqualsSerial("output before", output);
        int snapshots = outputSource.snapshots;
        outputs.get(recipes.get(6)).add("item5");
        outputs.get(recipes.get(7000)).add("item5");
        outputs.get(recipes.get(5)).clear();
        assertIndexedEqualsSerial("output after edit", output);
        assertTrue(outputSource.snapshots > snapshots, "the output table was rebuilt");
        // A recipe is appended to the live list.
        RecipeJS added = new ShapelessRecipeJS();
        added.id = new ResourceLocation("kjs", "appended");
        added.type = recipes.get(0).type;
        added.json = new JsonObject();
        recipes.add(added);
        outputs.put(added, new LinkedHashSet<>(Set.of("item5")));
        assertIndexedEqualsSerial("appended id", RecipeFilter.of(map("id", "kjs:appended")));
        assertIndexedEqualsSerial("appended output", output);
        // And one is removed from it.
        recipes.remove(3);
        assertIndexedEqualsSerial("after removal", oldId);
        assertIndexedEqualsSerial("output after removal", output);
    }

    @Test
    void aTableThatKeepsChangingIsAbandonedAndTheScanTakesOver() {
        int declinedAt = -1;
        for (int round = 0; round < 12; round++) {
            outputs.get(recipes.get(round)).add("churn" + round);
            RecipeFilter filter = fakeOutput("item5");
            List<RecipeJS> result = indexed(filter);
            if (result == null) {
                declinedAt = round;
                break;
            }
            assertEquals(serial(filter), result, "round " + round);
        }
        assertTrue(declinedAt > 0 && declinedAt <= 6, "abandoned after a few rebuilds: " + declinedAt);
        // The id table is a separate lane and keeps working.
        assertIndexedEqualsSerial("id after the output lane was abandoned",
                RecipeFilter.of(map("id", "mod8:group3/recipe_123")));
    }

    @Test
    void theUnionKeepsSortedOrderWithoutDuplicates() {
        assertArrayEquals(new int[] {1, 2, 3, 5, 8}, KubeJsRecipeIndex.union(new int[] {1, 3, 5}, new int[] {2, 3, 8}));
        assertArrayEquals(new int[] {4}, KubeJsRecipeIndex.union(new int[0], new int[] {4}));
        assertArrayEquals(new int[] {4}, KubeJsRecipeIndex.union(new int[] {4}, new int[] {4}));
    }

    /**
     * Timing only (no assertion): 150 id removals over 41,000 recipes with the production id table,
     * scan against index (the index run includes building the table and re-checking it on every call).
     * The recipes are allocated together here, so both sides are faster than on a server heap.
     */
    @Test
    void microbenchmarkOfAWoldsSizedIdScript() {
        List<RecipeFilter> script = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            int n = (i * 263) % RECIPES;
            script.add(RecipeFilter.of(map("id", "mod" + (n % 23) + ":group" + (n % 5) + "/recipe_" + n)));
        }
        long best = Long.MAX_VALUE;
        long bestIndexed = Long.MAX_VALUE;
        for (int round = 0; round < 8; round++) {
            long start = System.nanoTime();
            int serialMatches = 0;
            for (RecipeFilter filter : script) {
                serialMatches += recipes.stream().filter(filter).toList().size();
            }
            best = Math.min(best, System.nanoTime() - start);
            KubeJsRecipeIndex fresh = new KubeJsRecipeIndex(recipes, List.of(new KubeJsRecipeIdSource()));
            start = System.nanoTime();
            int indexedMatches = 0;
            for (RecipeFilter filter : script) {
                List<RecipeJS> seen = new ArrayList<>();
                assertTrue(fresh.forEach(filter, seen::add, true));
                indexedMatches += seen.size();
            }
            bestIndexed = Math.min(bestIndexed, System.nanoTime() - start);
            assertEquals(serialMatches, indexedMatches);
        }
        System.out.printf("KUBEJS-INDEX-BENCH ids recipes=%d removes=%d scan=%.1f ms indexed=%.1f ms (cores=%d)%n",
                RECIPES, script.size(), best / 1e6, bestIndexed / 1e6, Runtime.getRuntime().availableProcessors());
    }
}
