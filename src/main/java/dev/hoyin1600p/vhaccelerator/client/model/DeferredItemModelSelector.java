package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Chooses top-level models whose bake may be deferred.
 *
 * <p>A key qualifies only when its whole dependency closure is already in the
 * bakery's unbaked cache and every node is an ordinary JSON model. Lookup is
 * cache-only, so a later bake can never read the resource manager, and every
 * texture of a qualifying graph was already collected by the eager material
 * pass before atlas stitching.
 */
public final class DeferredItemModelSelector {
    private DeferredItemModelSelector() {
    }

    public interface Graph<K, N> {
        /** Cache-only lookup; {@code null} when the location was never loaded. */
        N cached(K location);

        Collection<K> dependencies(N node);

        /** Exact vanilla JSON model without custom geometry, markers, or missing model. */
        boolean isPlain(N node);
    }

    public static <K, N> Set<K> select(
            Map<K, N> topLevelModels,
            Predicate<? super K> eligibleKey,
            Graph<K, N> graph
    ) {
        Walker<K, N> walker = new Walker<>(graph);
        Set<K> selected = new LinkedHashSet<>();
        topLevelModels.forEach((location, model) -> {
            if (model == null || !eligibleKey.test(location)) {
                return;
            }
            // ModelBakery#bake resolves the key through its unbaked cache.
            if (graph.cached(location) != model) {
                return;
            }
            if (walker.plainClosure(model)) {
                selected.add(location);
            }
        });
        return selected;
    }

    private static final class Walker<K, N> {
        private final Graph<K, N> graph;
        private final Map<N, Boolean> results = new IdentityHashMap<>();
        private final Set<N> visiting =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private Walker(Graph<K, N> graph) {
            this.graph = graph;
        }

        private boolean plainClosure(N node) {
            if (node == null) {
                return false;
            }
            Boolean known = results.get(node);
            if (known != null) {
                return known;
            }
            if (!visiting.add(node)) {
                return false; // Cycles stay on vanilla's diagnostic path.
            }
            boolean plain;
            try {
                plain = graph.isPlain(node);
                if (plain) {
                    for (K dependency : graph.dependencies(node)) {
                        if (!plainClosure(graph.cached(dependency))) {
                            plain = false;
                            break;
                        }
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                plain = false;
            }
            visiting.remove(node);
            results.put(node, plain);
            return plain;
        }
    }
}
