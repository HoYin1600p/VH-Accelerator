package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.filter.AndFilter;
import dev.latvian.mods.kubejs.recipe.filter.OrFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A per-event lookup that lets KubeJS 1802's {@code event.remove(filter)} and
 * {@code forEachRecipe(filter, ...)} skip the full recipe scan when the filter names a recipe id or
 * an output item.
 *
 * <p>KubeJS runs every such call as {@code originalRecipes.stream().filter(filter).forEach(consumer)}:
 * hundreds of calls over tens of thousands of recipes. This class answers a call in two steps.
 * First it computes a <em>candidate superset</em> of the recipes the filter can accept, in
 * original list order, from lookup tables built once per event (id to recipe, output item to
 * recipes). Then it runs the unchanged KubeJS filter on those candidates only and hands the accepted
 * ones to the consumer one at a time, exactly as the stream did. The tables only ever narrow the
 * set; the real filter always makes the final decision, so a recipe is accepted if and only if
 * KubeJS would have accepted it, and in the same order.</p>
 *
 * <p>Only these shapes narrow: an {@code IDFilter} (exact id), an {@code OutputFilter} over one
 * plain item, an {@code AndFilter} with at least one such part (the smallest part wins) and an
 * {@code OrFilter} whose every part is such a shape (their union). Anything else (regex, tag, mod,
 * input, group, type, NOT, wildcards, script functions) returns {@code false} so the caller keeps
 * KubeJS's own path, or the parallel filter.</p>
 *
 * <p>The tables are checked against the live recipes on every lookup (one cheap pass comparing the
 * fields the tables were built from) and rebuilt if a script changed them, for example after
 * {@code replaceOutput}. A table that keeps changing is abandoned for the rest of the event.</p>
 */
public final class KubeJsRecipeIndex {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final int MAX_BUILDS_PER_LANE = 5;
    private static final int PARALLEL_CHECK_FROM = 8_192;
    private static final int[] NONE = new int[0];
    private static final String INTERNAL_CONSUMER = "dev.latvian.mods.kubejs.recipe.RecipeEventJS$$Lambda";

    /** Receives what a {@link Source} knows about each recipe while a table is built. */
    public interface Sink<K> {
        /** Recipe number {@code ordinal} (its position in the recipe list) can be found under {@code key}. */
        void put(int ordinal, K key);

        /** Recipe number {@code ordinal} cannot be classified; it stays a candidate for every lookup. */
        void unknown(int ordinal);
    }

    /** One kind of lookup key (recipe id, output item) with the code that reads it. */
    public interface Source<K> {
        String name();

        /** The key a filter is a leaf for (an exact, superset-safe key), or {@code null} if it is not one. */
        K keyOf(RecipeFilter filter);

        /** Reads the keys of every recipe, calling {@code sink} in ascending ordinal order. */
        void snapshot(List<RecipeJS> recipes, Sink<K> sink);

        /** True when every recipe still has exactly the state {@link #snapshot} read. */
        boolean isCurrent(List<RecipeJS> recipes);
    }

    private static final class IntList {
        int[] data = new int[2];
        int size;

        void add(int value) {
            if (size > 0 && data[size - 1] == value) {
                return;
            }
            if (size == data.length) {
                data = Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    private static final class Lane<K> {
        final Source<K> source;
        Map<K, int[]> byKey = Map.of();
        int[] unknown = NONE;
        boolean built;
        boolean disabled;
        int builds;

        Lane(Source<K> source) {
            this.source = source;
        }

        /** Makes the table match the live recipes; false when the lane is abandoned. */
        boolean refresh(List<RecipeJS> recipes) {
            if (disabled) {
                return false;
            }
            if (built && source.isCurrent(recipes)) {
                return true;
            }
            if (builds >= MAX_BUILDS_PER_LANE) {
                disabled = true;
                LOGGER.info("KubeJS recipe index: the {} table kept changing; using KubeJS's own scan from now on",
                        source.name());
                return false;
            }
            long started = System.nanoTime();
            Map<K, IntList> collected = new HashMap<>();
            IntList unclassified = new IntList();
            source.snapshot(recipes, new Sink<K>() {
                @Override
                public void put(int ordinal, K key) {
                    collected.computeIfAbsent(key, ignored -> new IntList()).add(ordinal);
                }

                @Override
                public void unknown(int ordinal) {
                    unclassified.add(ordinal);
                }
            });
            Map<K, int[]> table = new HashMap<>(collected.size() * 2);
            collected.forEach((key, list) -> table.put(key, list.toArray()));
            byKey = table;
            unknown = unclassified.toArray();
            built = true;
            builds++;
            LOGGER.info("KubeJS recipe index: built the {} table ({} recipes, {} keys, {} unclassified) in {} ms",
                    source.name(), recipes.size(), table.size(), unknown.length,
                    (System.nanoTime() - started) / 1_000_000L);
            return true;
        }

        int[] ordinals(K key) {
            int[] found = byKey.get(key);
            if (found == null) {
                found = NONE;
            }
            return unknown.length == 0 ? found : union(found, unknown);
        }
    }

    private final List<RecipeJS> recipes;
    private final List<Lane<?>> lanes = new ArrayList<>();
    private boolean broken;
    private int indexedCalls;
    private int scannedCalls;

    public KubeJsRecipeIndex(List<RecipeJS> recipes, List<? extends Source<?>> sources) {
        this.recipes = recipes;
        for (Source<?> source : sources) {
            lanes.add(new Lane<>(source));
        }
    }

    /** The index KubeJS 1802.5.5 uses: recipe id and output item. */
    public static KubeJsRecipeIndex forEvent(List<RecipeJS> recipes) {
        return new KubeJsRecipeIndex(recipes, List.of(new KubeJsRecipeIdSource(), new KubeJsRecipeOutputSource()));
    }

    /**
     * True for the consumers KubeJS builds inside {@code RecipeEventJS} itself ({@code remove},
     * {@code stage}): they touch only the recipe they are given, so computing the candidates before
     * the first call is exact. A script's own callback can edit recipes it has not visited yet and is
     * left to the sequential path.
     */
    public static boolean isInternalConsumer(Object consumer) {
        return consumer != null && consumer.getClass().getName().startsWith(INTERNAL_CONSUMER);
    }

    public int indexedCalls() {
        return indexedCalls;
    }

    public int scannedCalls() {
        return scannedCalls;
    }

    /**
     * Applies {@code consumer} to the recipes {@code filter} accepts, in list order, if the filter
     * can be narrowed by a table. Returns {@code false} without calling the consumer when it cannot;
     * the caller then runs KubeJS's own loop.
     */
    public boolean forEach(RecipeFilter filter, Consumer<RecipeJS> consumer, boolean consumerIsInternal) {
        if (broken || !consumerIsInternal) {
            return false;
        }
        int[] candidates;
        try {
            candidates = plan(filter, 0);
        } catch (RuntimeException failure) {
            broken = true;
            LOGGER.warn("KubeJS recipe index disabled for this event after an unexpected error", failure);
            return false;
        }
        if (candidates == null) {
            scannedCalls++;
            logProgress();
            return false;
        }
        for (int ordinal : candidates) {
            RecipeJS recipe = recipes.get(ordinal);
            if (filter.test(recipe)) {
                consumer.accept(recipe);
            }
        }
        indexedCalls++;
        logProgress();
        return true;
    }

    private void logProgress() {
        int calls = indexedCalls + scannedCalls;
        if (calls == 1 || calls % 100 == 0) {
            LOGGER.info("KubeJS recipe index: {} filter calls so far, {} answered from the tables, {} left to the scan",
                    calls, indexedCalls, scannedCalls);
        }
    }

    /** Sorted candidate ordinals (a superset of the accepted recipes), or null when the filter does not narrow. */
    int[] plan(Object filter, int depth) {
        if (depth > 16 || filter == null) {
            return null;
        }
        for (Lane<?> lane : lanes) {
            int[] leaf = leaf(lane, filter);
            if (leaf != null) {
                return leaf;
            }
        }
        if (filter.getClass() == AndFilter.class) {
            int[] best = null;
            for (Object part : ((AndFilter) filter).list) {
                int[] candidates = plan(part, depth + 1);
                if (candidates != null && (best == null || candidates.length < best.length)) {
                    best = candidates;
                }
            }
            return best;
        }
        if (filter.getClass() == OrFilter.class) {
            int[] all = NONE;
            for (Object part : ((OrFilter) filter).list) {
                int[] candidates = plan(part, depth + 1);
                if (candidates == null) {
                    return null;
                }
                all = union(all, candidates);
            }
            return all;
        }
        return null;
    }

    private <K> int[] leaf(Lane<K> lane, Object filter) {
        if (!(filter instanceof RecipeFilter recipeFilter)) {
            return null;
        }
        K key = lane.source.keyOf(recipeFilter);
        if (key == null || !lane.refresh(recipes)) {
            return null;
        }
        return lane.ordinals(key);
    }

    /**
     * True when {@code test} holds for every index in {@code [0, count)}. A table is checked against
     * the live recipes on every lookup; reading tens of thousands of recipe objects is spread over the
     * common pool (read-only, and the calling thread joins in), so the check stays a small fraction of
     * the scan it replaces.
     */
    static boolean allMatch(int count, IntPredicate test) {
        if (count < PARALLEL_CHECK_FROM) {
            for (int i = 0; i < count; i++) {
                if (!test.test(i)) {
                    return false;
                }
            }
            return true;
        }
        return IntStream.range(0, count).parallel().allMatch(test);
    }

    /** Sorted union without duplicates. */
    static int[] union(int[] left, int[] right) {
        if (left.length == 0) {
            return right;
        }
        if (right.length == 0) {
            return left;
        }
        int[] merged = new int[left.length + right.length];
        int size = 0;
        int i = 0;
        int j = 0;
        while (i < left.length || j < right.length) {
            int next;
            if (j >= right.length || (i < left.length && left[i] < right[j])) {
                next = left[i++];
            } else if (i >= left.length || right[j] < left[i]) {
                next = right[j++];
            } else {
                next = left[i++];
                j++;
            }
            merged[size++] = next;
        }
        return size == merged.length ? merged : Arrays.copyOf(merged, size);
    }
}
