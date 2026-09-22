package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeferredItemModelSelectorTest {
    private record Node(String name, boolean plain, List<String> dependencies) { }

    private final Map<String, Node> cache = new HashMap<>();

    private Node node(String name, boolean plain, String... dependencies) {
        Node node = new Node(name, plain, List.of(dependencies));
        cache.put(name, node);
        return node;
    }

    private final DeferredItemModelSelector.Graph<String, Node> graph = new DeferredItemModelSelector.Graph<>() {
        @Override public Node cached(String location) { return cache.get(location); }
        @Override public Collection<String> dependencies(Node node) {
            if (node.name().equals("throws")) { throw new IllegalStateException(); }
            return node.dependencies();
        }
        @Override public boolean isPlain(Node node) { return node.plain(); }
    };

    private Set<String> select(Map<String, Node> topLevel) {
        return DeferredItemModelSelector.select(topLevel, key -> key.endsWith("#inventory"), graph);
    }

    @Test void selectsOnlyEligibleKeysWithPlainClosures() {
        node("block/cube", true);
        node("block/stone", true, "block/cube");
        node("builtin/generated", false);
        node("item/generated", true, "builtin/generated");
        Map<String, Node> top = new LinkedHashMap<>();
        top.put("stone#inventory", node("stone#inventory", true, "block/stone"));
        top.put("stick#inventory", node("stick#inventory", true, "item/generated"));
        top.put("stone#", node("stone#", true, "block/stone"));
        assertEquals(Set.of("stone#inventory"), select(top));
    }

    @Test void overridesAndUnloadedDependenciesAreRequired() {
        node("block/cube", true);
        node("bow_pulling#inventory-override", true, "block/cube");
        Map<String, Node> top = new LinkedHashMap<>();
        top.put("bow#inventory", node("bow#inventory", true, "block/cube", "bow_pulling#inventory-override"));
        top.put("lost#inventory", node("lost#inventory", true, "never/loaded"));
        top.put("custom#inventory", node("custom#inventory", true, "custom/geometry"));
        node("custom/geometry", false);
        assertEquals(Set.of("bow#inventory"), select(top));
    }

    @Test void cyclesFailuresAndCacheMismatchesStayEager() {
        node("cycle/a", true, "cycle/b");
        node("cycle/b", true, "cycle/a");
        node("throws", true);
        Map<String, Node> top = new LinkedHashMap<>();
        top.put("cyclic#inventory", node("cyclic#inventory", true, "cycle/a"));
        top.put("failing#inventory", node("failing#inventory", true, "throws"));
        Node detached = new Node("detached#inventory", true, List.of());
        node("detached#inventory", true);
        top.put("detached#inventory", detached);
        top.put("null#inventory", null);
        assertEquals(Set.of(), select(top));
    }

    @Test void sharedDependenciesAreEvaluatedOnce() {
        int[] visits = {0};
        node("block/cube", true);
        Map<String, Node> top = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            top.put(i + "#inventory", node(i + "#inventory", true, "block/cube"));
        }
        var counting = new DeferredItemModelSelector.Graph<String, Node>() {
            @Override public Node cached(String location) { return cache.get(location); }
            @Override public Collection<String> dependencies(Node node) { return node.dependencies(); }
            @Override public boolean isPlain(Node node) {
                if (node.name().equals("block/cube")) { visits[0]++; }
                return node.plain();
            }
        };
        assertEquals(5, DeferredItemModelSelector.select(top, key -> true, counting).size());
        assertEquals(1, visits[0]);
    }
}
