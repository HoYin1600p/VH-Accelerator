package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

/**
 * Predicts what JEI's {@code ElementSearch#getAllIngredients()} will report
 * for a set of list elements, so a private index can be checked for
 * completeness.
 *
 * <p>JEI 9 and 10 enumerate the no-prefix suffix tree, which stores each
 * element under its lowercase display name. An empty name inserts nothing,
 * and the enumeration is an identity set, so an item with a blank name or an
 * element added twice is legitimately absent from the count. Comparing
 * against the raw list size would wrongly reject a complete index.</p>
 */
public final class JeiIndexCompleteness {
    private JeiIndexCompleteness() {
    }

    @SafeVarargs
    public static <T> int expectedIndexed(
            Function<T, String> name,
            Collection<? extends T>... elementGroups
    ) {
        Set<T> counted = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Collection<? extends T> elements : elementGroups) {
            for (T element : elements) {
                String elementName = name.apply(element);
                if (elementName != null && !elementName.isEmpty()) {
                    counted.add(element);
                }
            }
        }
        return counted.size();
    }
}
