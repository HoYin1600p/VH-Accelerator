package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;

import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.RecipeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * A tag output expands to several items. CT's real unmodified removal loop is the oracle: the indexed
 * removal must remove the same recipes in the same order, leave the same maps, and visit exactly the
 * recipes of the requested items plus every must-check entry, in the original encounter order.
 */
class RequestedKeysIndexTest {
    private static final Object[] ITEMS = {new Object(), new Object(), new Object(), new Object(), new Object(), new Object()};

    @Test
    void randomTagRequestsMatchTheOriginalLoopAndVisitOnlyCandidatesAndMustCheckEntries() {
        int removedTotal = 0;
        long skippedTotal = 0;
        for (long seed = 1; seed <= 60; seed++) {
            Random random = new Random(seed);
            int size = 5 + random.nextInt(400);
            World original = new World(false), indexed = new World(true);
            for (int i = 0; i < size; i++) {
                ResourceLocation id = new ResourceLocation("test", "r" + i);
                Object item = ITEMS[random.nextInt(ITEMS.length)];
                boolean known = random.nextInt(10) != 0;
                original.put(id, item, known);
                indexed.put(id, item, known);
            }
            GenerationOutputIndex index = new GenerationOutputIndex();
            for (int round = 0; round < 8; round++) {
                Object request = request(random);
                int before = original.order().size();
                original.remove(request, null);
                indexed.remove(request, index);
                removedTotal += before - original.order().size();
                skippedTotal += original.visits.size() - indexed.visits.size();
                assertEquals(original.order(), indexed.order(), "order after round " + round + " seed " + seed);
                assertEquals(new ArrayList<>(original.byName.keySet()), new ArrayList<>(indexed.byName.keySet()));
                assertEquals(expectedVisits(original), indexed.visits, "visits round " + round + " seed " + seed);
                // New recipes between actions, like scripts adding the replacements for what they removed.
                int additions = random.nextInt(4);
                for (int a = 0; a < additions; a++) {
                    ResourceLocation id = new ResourceLocation("test", "add" + seed + "_" + round + "_" + a);
                    Object item = ITEMS[random.nextInt(ITEMS.length)];
                    boolean known = random.nextBoolean();
                    original.put(id, item, known);
                    indexed.put(id, item, known);
                }
                original.visits.clear();
                indexed.visits.clear();
            }
            index.close();
        }
        assertTrue(removedTotal > 500, "the trials must actually remove recipes: " + removedTotal);
        assertTrue(skippedTotal > 5_000, "the index must skip non-candidates: " + skippedTotal);
    }

    @Test
    void anEmptyTagVisitsOnlyMustCheckEntriesAndRemovesNothingElse() {
        World original = new World(false), indexed = new World(true);
        for (int i = 0; i < 50; i++) {
            ResourceLocation id = new ResourceLocation("test", "r" + i);
            original.put(id, ITEMS[i % ITEMS.length], i % 7 != 0);
            indexed.put(id, ITEMS[i % ITEMS.length], i % 7 != 0);
        }
        RequestedKeys none = new RequestedKeys(List.of());
        original.remove(none, null);
        GenerationOutputIndex index = new GenerationOutputIndex();
        indexed.remove(none, index);
        assertEquals(original.order(), indexed.order());
        assertEquals(50, indexed.order().size());
        assertEquals(8, indexed.visits.size());
        assertEquals(expectedVisits(original), indexed.visits);
    }

    @Test
    void duplicateKeysAreCollapsedAndNullKeysRejected() {
        Object a = new Object();
        RequestedKeys keys = new RequestedKeys(List.of(a, a, a));
        assertEquals(1, keys.size());
        assertTrue(keys.contains(a));
        assertFalse(keys.contains(new Object()));
        assertThrows(IllegalArgumentException.class, () -> new RequestedKeys(java.util.Arrays.asList(a, null)));
    }

    /**
     * IItemStack's static initializer needs CraftTweaker's Mixin accessors, so real stacks cannot be built
     * here; the plain-stack checks of the tag expansion are covered by the server differential instead.
     */
    @Test
    void tagExpansionOfNothingDeclinesAndOfAnEmptyArrayIsAnEmptyKeySet() {
        assertNull(IndexedOutputRemovals.tagKeys(null));
        assertEquals(0, IndexedOutputRemovals.tagKeys(new IItemStack[0]).size());
    }

    private static Object request(Random random) {
        if (random.nextInt(4) == 0) return ITEMS[random.nextInt(ITEMS.length)];
        List<Object> keys = new ArrayList<>();
        int count = random.nextInt(ITEMS.length + 1);
        for (int i = 0; i < count; i++) keys.add(ITEMS[random.nextInt(ITEMS.length)]);
        return new RequestedKeys(keys);
    }

    private static boolean wanted(Object request, Object item) {
        return request instanceof RequestedKeys keys ? keys.contains(item) : request == item;
    }

    /** What the indexed run should have visited: the original run's visits that were wanted or must-check. */
    private static List<String> expectedVisits(World original) {
        List<String> expected = new ArrayList<>();
        for (String entry : original.visits) {
            String[] parts = entry.split("\\|");
            if (parts[1].equals("unknown") || parts[1].equals("wanted")) expected.add(parts[0]);
        }
        return expected;
    }

    private record Value(Object item, boolean known) { }

    private static final class World {
        final Map<ResourceLocation, Recipe<?>> recipes;
        final Map<ResourceLocation, Recipe<?>> byName = new HashMap<>();
        final IdentityHashMap<Recipe<?>, Value> values = new IdentityHashMap<>();
        /** "id|unknown", "id|wanted" or "id|other" for the original loop; the indexed run records ids only. */
        final List<String> visits = new ArrayList<>();
        private final boolean tracked;

        World(boolean tracked) {
            this.tracked = tracked;
            recipes = tracked ? new TrackedRecipeMap<>(new HashMap<>()) : new HashMap<>();
        }

        void put(ResourceLocation id, Object item, boolean known) {
            Recipe<?> recipe = new FixtureRecipe(id);
            values.put(recipe, new Value(item, known));
            recipes.put(id, recipe);
            byName.put(id, recipe);
        }

        List<ResourceLocation> order() { return new ArrayList<>(recipes.keySet()); }

        void remove(Object request, GenerationOutputIndex index) {
            Predicate<Recipe<?>> predicate = recipe -> {
                Value value = values.get(recipe);
                boolean match = wanted(request, value.item);
                String kind = !value.known ? "unknown" : match ? "wanted" : "other";
                visits.add(tracked ? recipe.getId().toString() : recipe.getId() + "|" + kind);
                // A must-check entry's getter reports its real item; both runs visit it either way.
                return match;
            };
            boolean handled = index != null && index.tryRemove(recipes, byName, predicate, () -> request,
                    recipe -> values.get(recipe).known ? values.get(recipe).item : null);
            if (!handled) new RecipeList<Recipe<?>>(null, recipes, byName).removeByRecipeTest(predicate);
        }
    }

    private record FixtureRecipe(ResourceLocation id) implements Recipe<Container> {
        @Override public boolean matches(Container container, Level level) { throw unexpected(); }
        @Override public ItemStack assemble(Container container) { throw unexpected(); }
        @Override public boolean canCraftInDimensions(int width, int height) { throw unexpected(); }
        @Override public ItemStack getResultItem() { throw unexpected(); }
        @Override public ResourceLocation getId() { return id; }
        @Override public RecipeSerializer<?> getSerializer() { throw unexpected(); }
        @Override public RecipeType<?> getType() { throw unexpected(); }
        private AssertionError unexpected() { return new AssertionError("Unexpected recipe method for " + id); }
    }
}
