package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Compares the snapshot dispatch with Vault's Event.invoke, copied below. */
@SuppressWarnings({"rawtypes", "unchecked"})
final class VaultEventDispatchTest {
    private static final class FakeEvent {
        final Map<Integer, Map<Object, List<Consumer>>> listeners = Collections.synchronizedMap(new TreeMap<>());
        final VaultEventDispatch.State state = new VaultEventDispatch.State(false);

        FakeEvent() {
            listeners.put(0, Collections.synchronizedMap(new LinkedHashMap<>()));
        }

        void register(Object reference, Consumer listener, int priority) {
            listeners.computeIfAbsent(priority, p -> Collections.synchronizedMap(new LinkedHashMap<>()));
            listeners.get(priority).computeIfAbsent(reference, r -> new ArrayList<>()).add(listener);
            state.changed();
        }

        void release(Object reference) {
            listeners.values().forEach(map -> map.remove(reference));
            state.changed();
        }

        TreeMap<Integer, Map<Object, List<Consumer>>> getListeners() {
            TreeMap<Integer, Map<Object, List<Consumer>>> map = new TreeMap<>(Collections.reverseOrder());
            map.putAll(listeners);
            return map;
        }

        void original(Object data) {
            for (Integer priority : getListeners().keySet()) {
                new ArrayList<>(getListeners().get(priority).values()).forEach(list -> {
                    for (Object consumer : new ArrayList<>(list)) {
                        try {
                            ((Consumer) consumer).accept(data);
                        } catch (Exception e) {
                            // Vault prints the stack trace and continues.
                        }
                    }
                });
            }
        }

        void snapshot(Object data) {
            VaultEventDispatch.invoke(state, listeners, data);
        }
    }

    /** Builds the same scenario on a fresh event and returns the call log of one post. */
    private static List<String> run(boolean snapshot, int posts) {
        List<String> log = new ArrayList<>();
        FakeEvent event = new FakeEvent();
        event.register("a", d -> log.add("a0"), 0);
        event.register("b", d -> log.add("b5"), 5);
        event.register("a", d -> log.add("a0'"), 0);
        event.register("c", d -> {
            log.add("c5");
            // Mid-post changes: a later priority and a later group see them, as in Vault.
            event.register("late-low", x -> log.add("late-1"), -1);
            event.register("a", x -> log.add("a0-late"), 0);
            event.release("d");
        }, 5);
        event.register("d", d -> log.add("d0"), 0);
        event.register("e", d -> {
            log.add("e-throws");
            throw new IllegalStateException("listener failure");
        }, 0);
        event.register("f", d -> log.add("f-1"), -1);
        for (int i = 0; i < posts; i++) {
            log.add("post" + i);
            if (snapshot) {
                event.snapshot(null);
            } else {
                event.original(null);
            }
        }
        return log;
    }

    @Test
    void matchesVaultOrderIncludingChangesDuringAPost() {
        assertEquals(run(false, 3), run(true, 3));
    }

    @Test
    void listenerAddedStraightIntoTheTableRunsOnTheNextPost() {
        // Vault's ForgeEvent children write into the parent's table without
        // Event.register; a vault opened in game registered its tick that way.
        List<String> log = new ArrayList<>();
        FakeEvent event = new FakeEvent();
        event.register("existing", d -> log.add("existing"), 0);
        event.snapshot(null);
        event.listeners.get(0).computeIfAbsent("new vault", r -> new ArrayList<>()).add(d -> log.add("new vault"));
        event.snapshot(null);
        event.listeners.computeIfAbsent(-3, p -> Collections.synchronizedMap(new LinkedHashMap<>()))
                .computeIfAbsent("late priority", r -> new ArrayList<>()).add(d -> log.add("late priority"));
        event.snapshot(null);
        assertEquals(List.of("existing", "existing", "new vault", "existing", "new vault", "late priority"), log);
    }

    @Test
    void stableTableReusesTheSnapshot() {
        List<String> log = new ArrayList<>();
        FakeEvent event = new FakeEvent();
        event.register("x", d -> log.add("x"), 0);
        event.register("y", d -> log.add("y"), 1);
        event.snapshot(null);
        event.snapshot(null);
        event.release("y");
        event.snapshot(null);
        assertEquals(List.of("y", "x", "y", "x", "x"), log);
    }
}
