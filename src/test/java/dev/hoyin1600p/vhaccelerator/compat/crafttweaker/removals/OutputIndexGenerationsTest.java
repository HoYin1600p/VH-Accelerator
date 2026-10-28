package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputIndexGenerationsTest {
    @Test
    void successfulFailedSuccessfulRunsDetachEveryObserverAndNeverShareGenerationState() throws Exception {
        List<GenerationOutputIndex> generations = new ArrayList<>();
        TrackedRecipeMap<Integer, Object> map = new TrackedRecipeMap<>(new HashMap<>(Map.of(1, new Object())));
        for (int run = 0; run < 3; run++) {
            int current = run;
            var body = (OutputIndexGenerations.Run) () -> {
                GenerationOutputIndex index = OutputIndexGenerations.current();
                generations.add(index);
                assertTrue(index.tryRemove(map, new HashMap<>(), ignored -> false, Object::new, ignored -> null));
                assertEquals(2, map.observerCount());
                if (current == 1) throw new IllegalArgumentException("failed script");
            };
            if (current == 1) assertThrows(IllegalArgumentException.class, () -> OutputIndexGenerations.run(body));
            else OutputIndexGenerations.run(body);
            assertNull(OutputIndexGenerations.current());
            assertEquals(0, map.observerCount());
        }
        assertEquals(3, generations.stream().distinct().count());
        assertTrue(generations.stream().noneMatch(GenerationOutputIndex::usable));
    }
    @Test
    void nestedRunsRestoreOuterOwnershipAndFinallyDetachInnerState() throws Exception {
        OutputIndexGenerations.run(() -> {
            GenerationOutputIndex outer = OutputIndexGenerations.current();
            assertThrows(IllegalStateException.class, () -> OutputIndexGenerations.run(() -> {
                assertNotSame(outer, OutputIndexGenerations.current());
                throw new IllegalStateException("nested");
            }));
            assertSame(outer, OutputIndexGenerations.current());
            assertTrue(outer.usable());
        });
        assertNull(OutputIndexGenerations.current());
    }
    @Test
    void contextRestoresAfterNestedFailure() {
        boolean outer = OutputRemovalContext.enter(true);
        try {
            assertThrows(IllegalStateException.class, () -> {
                boolean nested = OutputRemovalContext.enter(false);
                try { assertFalse(OutputRemovalContext.active()); throw new IllegalStateException("nested"); }
                finally { OutputRemovalContext.restore(nested); }
            });
            assertTrue(OutputRemovalContext.active());
        } finally { OutputRemovalContext.restore(outer); }
        assertFalse(OutputRemovalContext.active());
    }
}
