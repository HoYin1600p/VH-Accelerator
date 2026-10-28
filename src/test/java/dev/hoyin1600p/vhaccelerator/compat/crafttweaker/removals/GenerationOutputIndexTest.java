package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;

import com.blamejared.crafttweaker.api.recipe.RecipeList;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Uses CT's real unmodified removal loop as the mutation/ordering/exception oracle. */
class GenerationOutputIndexTest {
    private static final Object APPLE = new Object();
    private static final Object STONE = new Object();
    private static final Object OTHER = new Object();

    @Test
    void thirtyThousandRecipesAreClassifiedOnceAndWarmRemovalVisitsOnlyCandidatesAndUnknowns() {
        HashMap<Integer, Value> raw = new HashMap<>(65_536);
        for (int id = 0; id < 30_000; id++) raw.put(id, new Value(id < 2 ? APPLE : STONE, id < 29_990));
        TrackedRecipeMap<Integer, Value> map = new TrackedRecipeMap<>(raw);
        Map<Integer, Value> byName = new HashMap<>(raw);
        GenerationOutputIndex index = new GenerationOutputIndex();
        assertTrue(index.tryRemove(map, byName, value -> false, () -> OTHER, value -> value.known ? value.item : null));
        assertEquals(30_000, index.classifications());
        assertEquals(10, index.visits());
        long before = index.visits();
        assertTrue(index.tryRemove(map, byName, value -> value.item == APPLE, () -> APPLE, value -> value.known ? value.item : null));
        assertEquals(12, index.visits() - before);
        assertEquals(30_000, index.classifications());
        assertEquals(1, index.orderRefreshes());
        // An overwrite keeps its rank; one new key causes one rank repair without reclassifying the old recipes.
        map.put(3, new Value(APPLE, true));
        map.put(40_000, new Value(APPLE, true));
        map.put(40_001, new Value(STONE, true));
        before = index.classifications();
        assertTrue(index.tryRemove(map, byName, value -> value.item == APPLE, () -> APPLE, value -> value.known ? value.item : null));
        assertEquals(3, index.classifications() - before);
        assertEquals(2, index.orderRefreshes());
        assertFalse(map.containsKey(3));
        assertFalse(map.containsKey(40_000));
        assertTrue(map.containsKey(40_001));
    }

    @Test
    void futurePriorAndCurrentOverwritesAreObservedWithoutRevisitingEarlierRecipes() {
        World original = new World(false, 4), indexed = new World(true, 4);
        for (World world : List.of(original, indexed)) {
            var ids = world.order();
            world.replace(ids.get(1), STONE, false);
            world.custom = () -> {
                world.replace(ids.get(0), APPLE, true);
                world.replace(ids.get(1), APPLE, true);
                world.replace(ids.get(2), APPLE, true);
            };
        }
        original.remove(APPLE, null);
        GenerationOutputIndex index = new GenerationOutputIndex();
        indexed.remove(APPLE, index);
        assertWorlds(original, indexed);
        assertEquals(3, indexed.recipes.size());
        assertEquals(7, index.classifications()); // Only the three replaced objects are reclassified.
    }

    @Test
    void unknownFailureKeepsTheSameEarlierRemovalsAndDoesNotProbeUnknownGettersAhead() {
        World original = new World(false, 4), indexed = new World(true, 4);
        for (World world : List.of(original, indexed)) {
            var ids = world.order();
            world.replace(ids.get(0), APPLE, true);
            world.replace(ids.get(1), STONE, false);
            world.custom = () -> { throw new IllegalArgumentException("getter failed"); };
        }
        assertThrows(IllegalArgumentException.class, () -> original.remove(APPLE, null));
        assertThrows(IllegalArgumentException.class, () -> indexed.remove(APPLE, new GenerationOutputIndex()));
        assertWorlds(original, indexed);
        assertEquals(3, indexed.recipes.size());
    }

    @Test
    void structuralCallbackMutationPreservesFailFastTimingAndByNameSideEffects() {
        for (boolean matches : List.of(false, true)) {
            World original = new World(false, 4), indexed = new World(true, 4);
            for (World world : List.of(original, indexed)) {
                var ids = world.order();
                world.replace(ids.get(1), matches ? APPLE : STONE, false);
                world.custom = () -> {
                    world.recipes.remove(ids.get(2));
                    world.replace(new ResourceLocation("test:new_entry"), APPLE, true);
                };
            }
            ResourceLocation failingId = indexed.order().get(1);
            assertThrows(ConcurrentModificationException.class, () -> original.remove(APPLE, null));
            assertThrows(ConcurrentModificationException.class, () -> indexed.remove(APPLE, new GenerationOutputIndex()));
            assertWorlds(original, indexed);
            assertEquals(!matches, indexed.byName.containsKey(failingId));
        }
    }

    @Test
    void unsuccessfulLastOriginalPredicateCanMutateWithoutASecondFailFastCheck() {
        World original = new World(false, 4), indexed = new World(true, 4);
        for (World world : List.of(original, indexed)) {
            var ids = world.order();
            world.replace(ids.get(3), STONE, false);
            world.custom = () -> world.replace(new ResourceLocation("test:new_entry"), APPLE, true);
        }
        original.remove(APPLE, null);
        indexed.remove(APPLE, new GenerationOutputIndex());
        assertWorlds(original, indexed);
        assertEquals(5, indexed.recipes.size());
    }

    @Test
    void unsupportedRequestAfterCallbackUsesTheOriginalRemainderWithoutRepeatingEarlierCallbacks() {
        World original = new World(false, 4), indexed = new World(true, 4);
        for (World world : List.of(original, indexed)) {
            world.replace(world.order().get(1), STONE, false);
            world.custom = () -> world.requested.set(null);
        }
        original.remove(APPLE, null);
        indexed.remove(APPLE, new GenerationOutputIndex());
        assertWorlds(original, indexed);
        assertEquals(1, indexed.recipes.size());
    }

    @Test
    void nestedRemovalUsesTheOriginalOuterIteratorsConcurrentModificationBehavior() {
        World original = new World(false, 4), indexed = new World(true, 4);
        GenerationOutputIndex index = new GenerationOutputIndex();
        for (World world : List.of(original, indexed)) {
            var ids = world.order();
            world.replace(ids.get(1), STONE, false);
            world.replace(ids.get(2), OTHER, true);
            world.custom = () -> {
                Runnable saved = world.custom;
                world.custom = null;
                try { world.remove(OTHER, world == indexed ? index : null); }
                finally { world.custom = saved; }
            };
        }
        assertThrows(ConcurrentModificationException.class, () -> original.remove(APPLE, null));
        assertThrows(ConcurrentModificationException.class, () -> indexed.remove(APPLE, index));
        assertWorlds(original, indexed);
    }

    @Test
    void joinedForeignOverwriteOrNestedFailureResumesTheOriginalRemainderWithoutStaleSkips() {
        for (int mode = 0; mode < 3; mode++) {
            int currentMode = mode;
            World original = new World(false, 4), indexed = new World(true, 4);
            GenerationOutputIndex index = new GenerationOutputIndex();
            for (World world : List.of(original, indexed)) {
                var ids = world.order();
                world.replace(ids.get(1), STONE, false);
                world.custom = () -> {
                    Runnable replace = () -> world.replace(ids.get(2), APPLE, true);
                    if (currentMode == 1) {
                        Thread writer = new Thread(replace);
                        writer.start();
                        try { writer.join(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                    } else {
                        if (world == indexed) {
                            if (currentMode == 0) index.fail();
                            else index.invalidate();
                        }
                        replace.run();
                    }
                };
            }
            original.remove(APPLE, null);
            indexed.remove(APPLE, index);
            assertWorlds(original, indexed);
            assertEquals(3, indexed.recipes.size());
        }
    }

    @Test
    void treeBinsRetainTheEntireOriginalLoopAndItsNativeIteratorRemovals() {
        World original = World.collisions(false), indexed = World.collisions(true);
        GenerationOutputIndex index = new GenerationOutputIndex();
        for (Object output : List.of(APPLE, STONE, APPLE)) {
            original.remove(output, null);
            indexed.remove(output, index);
            assertWorlds(original, indexed);
        }
        assertEquals(0, index.visits());
    }

    @Test
    void putAllPreResizeFailureRetainsTheOriginalCachedNextEvenAfterDetachOrForeignMutation() {
        for (int mode = 0; mode < 4; mode++) {
            int currentMode = mode;
            World original = new World(false, 64), indexed = new World(true, 64);
            GenerationOutputIndex index = new GenerationOutputIndex();
            for (World world : List.of(original, indexed)) {
                ResourceLocation callback = world.order().get(5);
                world.replace(callback, STONE, false);
                world.custom = () -> {
                    world.custom = null;
                    if (world == indexed) {
                        if (currentMode == 1) index.invalidate();
                        if (currentMode == 2) index.fail();
                    }
                    Runnable putAll = () -> {
                        try { world.recipes.putAll(failingLargeSource()); }
                        catch (IllegalArgumentException expected) { assertEquals("source failed", expected.getMessage()); }
                    };
                    if (currentMode == 3) {
                        Thread foreign = new Thread(putAll);
                        foreign.start();
                        try { foreign.join(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
                    } else putAll.run();
                };
            }
            Throwable expected = failure(() -> original.remove(APPLE, null));
            Throwable actual = failure(() -> indexed.remove(APPLE, index));
            assertEquals(expected == null ? null : expected.getClass(), actual == null ? null : actual.getClass());
            assertWorlds(original, indexed);
            index.close();
            assertEquals(0, ((TrackedRecipeMap<?, ?>) indexed.recipes).observerCount());
        }
    }

    private static Throwable failure(Runnable action) {
        try { action.run(); return null; } catch (Throwable failure) { return failure; }
    }

    @Test
    void existingKeyComputeOperationsPreserveCachedNextWhenTheyResizeWithoutChangingValues() {
        for (String operation : List.of("computeIfAbsent", "compute", "merge")) {
            World original = new World(false, 96), indexed = new World(true, 96);
            GenerationOutputIndex index = new GenerationOutputIndex();
            for (World world : List.of(original, indexed)) {
                ResourceLocation deferred = new ResourceLocation("test:deferred");
                Recipe<?> recipe = new FixtureRecipe(deferred);
                world.values.put(recipe, new Value(STONE, true));
                // HashMap.compute inserts entry 97 without resizing its 128-slot, threshold-96 table.
                world.recipes.compute(deferred, (key, old) -> recipe);
                world.byName.put(deferred, recipe);
                var ids = world.order();
                world.replace(ids.get(5), STONE, false);
                world.custom = () -> {
                    world.custom = null;
                    ResourceLocation existing = ids.get(0);
                    switch (operation) {
                        case "computeIfAbsent" -> world.recipes.computeIfAbsent(existing, key -> fail("existing key must skip callback"));
                        case "compute" -> world.recipes.compute(existing, (key, value) -> value);
                        case "merge" -> world.recipes.merge(existing, recipe, (value, ignored) -> value);
                        default -> throw new AssertionError(operation);
                    }
                };
            }
            Throwable expected = failure(() -> original.remove(APPLE, null));
            Throwable actual = failure(() -> indexed.remove(APPLE, index));
            assertEquals(expected == null ? null : expected.getClass(), actual == null ? null : actual.getClass());
            assertWorlds(original, indexed);
            index.close();
            assertEquals(0, ((TrackedRecipeMap<?, ?>) indexed.recipes).observerCount());
        }
    }

    private static Map<ResourceLocation, Recipe<?>> failingLargeSource() {
        return new AbstractMap<>() {
            @Override public Set<Entry<ResourceLocation, Recipe<?>>> entrySet() {
                return new AbstractSet<>() {
                    @Override public int size() { return 1_000; }
                    @Override public Iterator<Entry<ResourceLocation, Recipe<?>>> iterator() {
                        return new Iterator<>() {
                            @Override public boolean hasNext() { return true; }
                            @Override public Entry<ResourceLocation, Recipe<?>> next() { throw new IllegalArgumentException("source failed"); }
                        };
                    }
                };
            }
        };
    }

    @Test
    void rawAndBusyMapsDeclineAndGenerationCloseDetachesObservers() throws Exception {
        Object recipe = new Object();
        Map<Integer, Object> raw = new HashMap<>(Map.of(1, recipe));
        GenerationOutputIndex index = new GenerationOutputIndex();
        assertFalse(index.tryRemove(raw, new HashMap<>(), ignored -> fail("raw map"), () -> APPLE, ignored -> STONE));
        TrackedRecipeMap<Integer, Object> tracked = new TrackedRecipeMap<>(new HashMap<>(raw));
        assertTrue(index.tryRemove(tracked, new HashMap<>(), ignored -> false, () -> APPLE, ignored -> STONE));
        assertEquals(2, tracked.observerCount());
        tracked.compute(1, (key, old) -> {
            assertFalse(index.tryRemove(tracked, new HashMap<>(), ignored -> fail("busy map"), () -> APPLE, ignored -> STONE));
            return old;
        });
        Thread foreign = new Thread(() -> tracked.put(2, new Object()));
        foreign.start(); foreign.join();
        assertFalse(tracked.eligible());
        assertFalse(index.tryRemove(tracked, new HashMap<>(), ignored -> fail("foreign mutation"), () -> APPLE, ignored -> STONE));
        index.close();
        assertEquals(0, tracked.observerCount());
        assertFalse(index.usable());
    }

    private static void assertWorlds(World expected, World actual) {
        assertEquals(expected.order(), actual.order());
        assertEquals(new ArrayList<>(expected.byName.keySet()), new ArrayList<>(actual.byName.keySet()));
        assertEquals(expected.observations, actual.observations);
    }
    private record Value(Object item, boolean known) { }
    private static final class World {
        final Map<ResourceLocation, Recipe<?>> recipes;
        final Map<ResourceLocation, Recipe<?>> byName = new HashMap<>();
        final IdentityHashMap<Recipe<?>, Value> values = new IdentityHashMap<>();
        final List<String> observations = new ArrayList<>();
        final AtomicReference<Object> requested = new AtomicReference<>();
        Runnable custom;
        World(boolean tracked, int size) {
            this(tracked, new HashMap<>(128));
            for (int i = 0; i < size; i++) replace(new ResourceLocation("test:r" + i), STONE, true);
        }
        World(boolean tracked, HashMap<ResourceLocation, Recipe<?>> raw) {
            recipes = tracked ? new TrackedRecipeMap<>(raw) : raw;
        }
        void replace(ResourceLocation id, Object output, boolean known) {
            Recipe<?> recipe = new FixtureRecipe(id);
            values.put(recipe, new Value(output, known));
            recipes.put(id, recipe);
            if (!byName.containsKey(id)) byName.put(id, recipe);
        }
        List<ResourceLocation> order() { return new ArrayList<>(recipes.keySet()); }
        void remove(Object output, GenerationOutputIndex index) {
            requested.set(output);
            Predicate<Recipe<?>> original = recipe -> {
                Value value = values.get(recipe);
                if (!value.known) {
                    observations.add(recipe.getId() + ":" + order() + ":" + new ArrayList<>(byName.keySet()));
                    if (custom != null) custom.run();
                }
                return requested.get() == null || value.item == requested.get();
            };
            boolean handled = index != null && index.tryRemove(recipes, byName, original, requested::get,
                    recipe -> values.get(recipe).known ? values.get(recipe).item : null);
            if (!handled) new RecipeList<Recipe<?>>(null, recipes, byName).removeByRecipeTest(original);
        }
        static World collisions(boolean tracked) {
            World world = new World(tracked, new HashMap<>(128));
            for (int i = 0; i < 40; i++) {
                StringBuilder id = new StringBuilder("test:");
                for (int bit = 0; bit < 6; bit++) id.append((i & (1 << bit)) == 0 ? "a0" : "_n");
                world.replace(new ResourceLocation(id.toString()), i % 3 == 0 ? APPLE : STONE, true);
            }
            assertEquals(1, world.recipes.keySet().stream().map(Object::hashCode).distinct().count());
            return world;
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
