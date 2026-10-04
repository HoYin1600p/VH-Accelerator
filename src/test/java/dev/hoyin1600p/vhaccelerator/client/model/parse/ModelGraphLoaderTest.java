package dev.hoyin1600p.vhaccelerator.client.model.parse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelGraphLoaderTest {
    @Test
    void runsLoadsOneAtATimeOnTheLoaderThread() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        List<String> threads = Collections.synchronizedList(new ArrayList<>());
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                int value = index;
                results.add(callers.submit(() -> ModelGraphLoader.call(() -> {
                    maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                    threads.add(Thread.currentThread().getName());
                    Thread.sleep(1);
                    inside.decrementAndGet();
                    return value;
                })));
            }
            for (int index = 0; index < results.size(); index++) {
                assertEquals(index, results.get(index).get());
            }
        } finally {
            callers.shutdownNow();
        }
        assertEquals(1, maxInside.get(), "Loads never overlap");
        assertTrue(threads.stream().allMatch("VH Accelerator model graph loader"::equals));
    }

    @Test
    void nestedCallsRunInlineAndFailuresReachTheCaller() {
        assertEquals("inner", ModelGraphLoader.call(() -> {
            assertTrue(ModelGraphLoader.isLoaderThread());
            return ModelGraphLoader.call(() -> "inner");
        }));
        assertFalse(ModelGraphLoader.isLoaderThread());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModelGraphLoader.call(() -> {
                    throw new IllegalStateException("broken graph");
                }));
        assertEquals("broken graph", failure.getMessage());
    }
}
