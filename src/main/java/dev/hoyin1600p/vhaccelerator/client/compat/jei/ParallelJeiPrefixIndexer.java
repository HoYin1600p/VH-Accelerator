package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import mezz.jei.core.search.ISearchStorage;
import mezz.jei.core.search.PrefixedSearchable;
import mezz.jei.core.search.SearchMode;

/**
 * Populates a private JEI ElementSearch one prefix at a time in parallel.
 *
 * <p>Every task owns one PrefixedSearchable and its corresponding storage.
 * Ingredients remain ordered within each prefix, and the ElementSearch is not
 * published until all prefix tasks have completed.
 *
 * @author hoyin1600p
 */
public final class ParallelJeiPrefixIndexer {
    private static final String PREFIXED_SEARCHABLES_FIELD = "prefixedSearchables";

    private ParallelJeiPrefixIndexer() {
    }

    public static <T> Result populate(Object privateIndex, List<T> ingredients) {
        long started = System.nanoTime();
        List<PrefixedSearchable<T>> prefixes = activePrefixes(privateIndex);
        if (prefixes.isEmpty()) {
            throw new IllegalStateException("JEI private search index has no active prefixes");
        }

        AdaptiveJeiWorkScheduler.invokeParallel(() -> {
            prefixes.parallelStream().forEach(prefix ->
                    populatePrefix(prefix, ingredients)
            );
            return null;
        });

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        Result result = new Result(
                ingredients.size(),
                prefixes.size(),
                AdaptiveJeiWorkScheduler.currentParallelism(),
                elapsedMillis
        );
        VHAccelerator.LOGGER.info(
                "Built {} JEI search prefixes for {} ingredients with up to {} workers in {} ms",
                result.prefixCount(),
                result.ingredientCount(),
                result.workerCount(),
                result.elapsedMillis()
        );
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<PrefixedSearchable<T>> activePrefixes(Object privateIndex) {
        try {
            Field field = privateIndex.getClass().getDeclaredField(
                    PREFIXED_SEARCHABLES_FIELD
            );
            field.setAccessible(true);
            Object value = field.get(privateIndex);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalStateException(
                        "JEI prefixedSearchables field is not a map"
                );
            }

            List<PrefixedSearchable<T>> prefixes = new ArrayList<>();
            for (Object searchable : map.values()) {
                if (!(searchable instanceof PrefixedSearchable<?> prefix)) {
                    throw new IllegalStateException(
                            "JEI prefix map contains an unsupported value"
                    );
                }
                PrefixedSearchable<T> typed = (PrefixedSearchable<T>) prefix;
                if (typed.getMode() != SearchMode.DISABLED) {
                    prefixes.add(typed);
                }
            }
            return List.copyOf(prefixes);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "JEI ElementSearch prefix layout is unsupported",
                    exception
            );
        }
    }

    private static <T> void populatePrefix(
            PrefixedSearchable<T> prefix,
            List<T> ingredients
    ) {
        ISearchStorage<T> storage = prefix.getSearchStorage();
        for (T ingredient : ingredients) {
            Collection<String> strings = prefix.getStrings(ingredient);
            for (String string : strings) {
                storage.put(string, ingredient);
            }
        }
    }

    public record Result(
            int ingredientCount,
            int prefixCount,
            int workerCount,
            long elapsedMillis
    ) {
    }
}
