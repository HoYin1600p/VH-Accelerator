package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MaterialSessionTest {
    @Test void overlayDoesNotCopyOrMutateRestoredDataAndCapturesNewKeys() {
        var original = Map.of("model", "material");
        var session = new SessionOverlay<>(original);
        assertSame(original, session.snapshot());
        assertEquals("material", session.get("model"));
        session.putNew("other", "new");
        assertEquals(2, session.size());
        assertEquals(Map.of("model", "material", "other", "new"), session.snapshot());
        assertEquals(1, original.size());
        assertThrows(IllegalArgumentException.class, () -> session.putNew("model", "changed"));
    }

    @Test void namesTrackIdentityRenamesNullsAndSessionBoundaries() {
        var names = new SessionNameLookup<Object, String>(1);
        Object model = new Object();
        var calls = new AtomicInteger();
        java.util.function.Function<String, String> parse = s -> { calls.incrementAndGet(); return s; };
        assertEquals("a", names.resolve(model, "a", parse));
        assertEquals("a", names.resolve(model, new String("a"), parse));
        assertEquals(1, calls.get());
        assertEquals("b", names.resolve(model, "b", parse));
        assertNull(names.resolve(model, null, parse));
        assertNull(names.resolve(model, null, parse));
        assertEquals(3, calls.get());
        Object excess = new Object();
        names.resolve(excess, "x", parse);
        names.resolve(excess, "x", parse);
        assertEquals(5, calls.get()); // Limit does not change results, only reuse.
        new SessionNameLookup<Object, String>(1).resolve(model, "b", parse);
        assertEquals(6, calls.get());
    }
}
