package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveReadOnlyMapTest {
    @Test
    void remainsLiveAfterStartingEmpty() {
        Map<String, Integer> mutable = new HashMap<>();
        Map<String, Integer> view = new LiveReadOnlyMap<>(mutable);

        assertTrue(view.isEmpty());
        assertSame(Collections.emptySet(), view.entrySet());

        mutable.put("key", 4);
        assertEquals(4, view.get("key"));
        assertEquals(Map.of("key", 4), view);
    }

    @Test
    void rejectsEveryMutationEvenWhenTheKeyIsAbsent() {
        Map<String, Integer> view = new LiveReadOnlyMap<>(new HashMap<>());

        assertThrows(UnsupportedOperationException.class,
                () -> view.remove("missing"));
        assertThrows(UnsupportedOperationException.class,
                () -> view.put("key", 1));
        assertThrows(UnsupportedOperationException.class, view::clear);
    }
}
