package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;
import com.blamejared.crafttweaker.api.action.base.IAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class IndexedOutputRemovalsTest {
    @Test
    void unavailableConfigurationRunsOriginalActionOnceWithoutLeakingContext() throws Exception {
        BooleanSupplier[] unavailable = {() -> { throw new IllegalStateException("not loaded"); },
                () -> { throw new NoClassDefFoundError("unavailable"); }};
        for (BooleanSupplier configuration : unavailable) {
            assertFalse(IndexedOutputRemovals.enabled(configuration));
            AtomicInteger calls = new AtomicInteger();
            OutputIndexGenerations.run(() -> {
                assertDoesNotThrow(() -> IndexedOutputRemovals.applyAction(new Action(() -> {
                    calls.incrementAndGet();
                    assertTrue(OutputRemovalContext.active());
                    assertFalse(OutputRemovalContext.indexAllowed());
                }), true, configuration));
                assertEquals(1, calls.get());
                assertTrue(OutputIndexGenerations.current().usable());
                assertFalse(OutputRemovalContext.active());
                assertTrue(OutputRemovalContext.indexAllowed());
            });
        }
    }
    @Test
    void actionFailureIsRethrownUnchangedAndDiscardsTheGeneration() throws Exception {
        RuntimeException failure = new IllegalArgumentException("action failed");
        OutputIndexGenerations.run(() -> {
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> IndexedOutputRemovals.applyAction(new Action(() -> { throw failure; }))));
            assertFalse(OutputIndexGenerations.current().usable());
            assertFalse(OutputRemovalContext.active());
            assertTrue(OutputRemovalContext.indexAllowed());
        });
    }
    @Test
    void unknownActionsRestoreTheirCallersMarkerWithoutInvalidatingUnrelatedIndexedRecipes() throws Exception {
        OutputIndexGenerations.run(() -> {
            GenerationOutputIndex index = OutputIndexGenerations.current();
            boolean previous = OutputRemovalContext.enter(true);
            try {
                assertDoesNotThrow(() -> IndexedOutputRemovals.applyAction(new Action(() -> {
                    assertFalse(OutputRemovalContext.active());
                    assertFalse(OutputRemovalContext.indexAllowed());
                })));
                assertTrue(OutputRemovalContext.active());
                assertTrue(index.usable());
                assertEquals(0, index.orderRefreshes());
            } finally { OutputRemovalContext.restore(previous); }
        });
    }
    private record Action(Runnable body) implements IAction {
        @Override public void apply() { body.run(); }
        @Override public String describe() { return "fixture action"; }
    }
}
