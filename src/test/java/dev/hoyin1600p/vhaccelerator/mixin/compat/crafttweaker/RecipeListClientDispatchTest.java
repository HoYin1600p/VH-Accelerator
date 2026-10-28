package dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker;

import static org.junit.jupiter.api.Assertions.*;

import com.blamejared.crafttweaker.api.fixture.RemovalPredicateFixture;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RecipeListClientDispatchTest {
    @Test
    void declinedIndexRetainsLegacyParallelRemovalAndOriginalPredicate() throws Throwable {
        Map<Integer, Integer> recipes = recipes();
        Map<Integer, Integer> byName = new LinkedHashMap<>(recipes);
        AtomicInteger indexedCalls = new AtomicInteger();
        AtomicInteger predicateCalls = new AtomicInteger();
        Predicate<Integer> original = new RemovalPredicateFixture<>(value -> {
            predicateCalls.incrementAndGet();
            return value % 2 == 0;
        });
        assertTrue(dispatch(recipes, byName, original, () -> {
            indexedCalls.incrementAndGet();
            assertEquals(2_048, recipes.size());
            return false;
        }, () -> true));
        assertEquals(1, indexedCalls.get());
        assertEquals(2_048, predicateCalls.get());
        assertEquals(1_024, recipes.size());
        assertEquals(recipes, byName);
        assertTrue(recipes.values().stream().allMatch(value -> value % 2 != 0));
    }

    @Test
    void indexedOwnerRunsOnceWithoutLegacyConfigurationOrPredicateEvaluation() throws Throwable {
        Map<Integer, Integer> recipes = recipes();
        Map<Integer, Integer> byName = new LinkedHashMap<>(recipes);
        AtomicInteger calls = new AtomicInteger();
        Predicate<Integer> original = new RemovalPredicateFixture<>(value -> {
            fail("Legacy matching must not run after indexed ownership");
            return false;
        });
        assertTrue(dispatch(recipes, byName, original, () -> {
            calls.incrementAndGet();
            recipes.remove(7);
            byName.remove(7);
            return true;
        }, () -> { throw new AssertionError("Legacy configuration must remain unevaluated"); }));
        assertEquals(1, calls.get());
        assertEquals(2_047, recipes.size());
        assertEquals(recipes, byName);
    }

    @Test
    void indexedFailurePropagatesUnchangedWithoutRetryingOrRollingBack() {
        Map<Integer, Integer> recipes = recipes();
        Map<Integer, Integer> byName = new LinkedHashMap<>(recipes);
        RuntimeException expected = new IllegalStateException("indexed callback failed");
        assertSame(expected, assertThrows(IllegalStateException.class, () -> dispatch(recipes, byName,
                new RemovalPredicateFixture<>(value -> false), () -> {
                    recipes.remove(7);
                    byName.remove(7);
                    throw expected;
                }, () -> { throw new AssertionError("A failed operation must never be retried"); })));
        assertFalse(recipes.containsKey(7));
        assertEquals(recipes, byName);
    }

    @Test
    void disabledLegacySmallMapsAndScriptPredicatesLeaveOriginalLoopUntouched() throws Throwable {
        Predicate<Integer> original = new RemovalPredicateFixture<>(value -> {
            fail("The original loop owns this predicate");
            return false;
        });
        Map<Integer, Integer> recipes = recipes();
        Map<Integer, Integer> byName = new LinkedHashMap<>(recipes);
        assertFalse(dispatch(recipes, byName, original, () -> false, () -> false));
        assertFalse(dispatch(Map.of(1, 1), Map.of(1, 1), original, () -> false, () -> true));
        assertFalse(dispatch(recipes, byName, value -> {
            fail("Script predicates retain sequential execution");
            return false;
        }, () -> false, () -> true));
        assertEquals(2_048, recipes.size());
        assertEquals(recipes, byName);
    }

    private static Map<Integer, Integer> recipes() {
        Map<Integer, Integer> recipes = new LinkedHashMap<>();
        for (int i = 0; i < 2_048; i++) recipes.put(i, i);
        return recipes;
    }

    private static <T> boolean dispatch(Map<Integer, T> recipes, Map<Integer, T> byName,
            Predicate<T> predicate, BooleanSupplier indexed, BooleanSupplier legacy) throws Throwable {
        var method = RecipeListParallelRemovalMixin.class.getDeclaredMethod("vhaccelerator$dispatch",
                Map.class, Map.class, Predicate.class, BooleanSupplier.class, BooleanSupplier.class);
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(null, recipes, byName, predicate, indexed, legacy);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
