package dev.hoyin1600p.vhaccelerator.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ObjectHolderDispatchTest {
    private static final String PREFIX = ObjectHolderDispatchTest.class.getName();
    private static final ResourceLocation NAME = new ResourceLocation("test", "blocks");

    @Test
    void anAnnouncedSingleRegistryFilterIsAccepted() {
        ObjectHolderDispatch.announce(NAME);
        Predicate<ResourceLocation> filter = NAME::equals;
        ObjectHolderDispatch.enter(filter, PREFIX);
        assertEquals(NAME, ObjectHolderDispatch.activeKey());
        ObjectHolderDispatch.enter(filter, PREFIX); // no announcement left
        assertNull(ObjectHolderDispatch.activeKey());
    }

    @Test
    void aCustomFilterAfterAStaleAnnouncementUsesTheOriginalLoop() {
        // Stale announcement, then a multi-registry filter that happens to accept the name.
        ObjectHolderDispatch.announce(NAME);
        ObjectHolderDispatch.enter(rl -> rl.getNamespace().equals("test") || rl.getPath().isEmpty());
        assertNull(ObjectHolderDispatch.activeKey());
        // Even a filter that behaves like NAME::equals is refused unless it is Forge's own method reference.
        ObjectHolderDispatch.announce(NAME);
        ObjectHolderDispatch.enter(NAME::equals);
        assertNull(ObjectHolderDispatch.activeKey());
        // A behaviourally wrong filter is refused even with the right class name.
        ObjectHolderDispatch.announce(NAME);
        ObjectHolderDispatch.enter(rl -> true, PREFIX);
        assertNull(ObjectHolderDispatch.activeKey());
    }

    @Test
    void anAnnouncementIsClearedWhenTheAnnouncingCallReturns() {
        ObjectHolderDispatch.announce(NAME);
        ObjectHolderDispatch.clearAnnouncement();
        ObjectHolderDispatch.enter(NAME::equals, PREFIX);
        assertNull(ObjectHolderDispatch.activeKey());
    }

    @Test
    void withoutAnActiveKeyTheOriginalLoopRuns() {
        assertTrue(!ObjectHolderDispatch.forEach(new HashSet<>(), h -> { }));
    }

    @Test
    void anErrorFromAHandlerPropagatesAndNoHandlerRunsTwice() {
        HolderDispatch<Object> engine = new HolderDispatch<>(h -> "k", 8);
        Set<Object> set = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 10; i++) {
            set.add("handler" + i);
        }
        List<Object> visited = new ArrayList<>();
        Consumer<Object> body = h -> {
            visited.add(h);
            if (h.equals("handler4")) {
                throw new ExceptionInInitializerError("boom");
            }
        };
        assertThrows(ExceptionInInitializerError.class, () -> engine.dispatch(set, new Object(), "k", body));
        assertEquals(5, visited.size());
        assertEquals(visited.size(), new HashSet<>(visited).size());
    }

    @Test
    void releasingTheIndexKeepsDispatchCorrectBecauseTheNextPassRebuildsIt() {
        HolderDispatch<Object> engine = new HolderDispatch<>(h -> h.toString().substring(0, 1), 2);
        Set<Object> set = new java.util.LinkedHashSet<>(List.of("a1", "b1", "a2", "c1", "a3"));
        List<Object> before = new ArrayList<>();
        assertTrue(engine.dispatch(set, new Object(), "a", before::add));
        assertEquals(List.of("a1", "a2", "a3"), before);
        for (int round = 0; round < 6; round++) {
            engine.release();
            engine.release();
            assertEquals(0, engine.summary().handlers(), "released index holds nothing");
            List<Object> after = new ArrayList<>();
            assertTrue(engine.dispatch(set, new Object(), "a", after::add));
            assertEquals(before, after, "same handlers in the same order after a rebuild");
            assertTrue(!engine.disabled(), "releases never spend the rebuild budget");
        }
        assertEquals(5, engine.summary().handlers());
        ObjectHolderDispatch.releaseIndex();
    }

    @Test
    void theVerifierSkipsUnknownHandlersAndNeverThrows() {
        List<Object> junk = new ArrayList<>();
        junk.add(null);
        junk.add(new Object());
        junk.add((Consumer<Object>) o -> { });
        ObjectHolderVerifier.Result result = ObjectHolderVerifier.verify(junk);
        assertEquals(0, result.mismatches());
        assertEquals(3, result.skipped());
        assertEquals(0, result.details().size());
        List<String> lines = new ArrayList<>();
        ObjectHolderVerifier.runAndLog(lines::add, lines::add);
        assertTrue(lines.size() >= 1);
    }
}
