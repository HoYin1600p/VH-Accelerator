package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockStateGraphLoadDiagnosticsTest {
    @Test
    void splitsCandidateTimeByCacheStateAtHead() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object uncached = new Object();
        Object cached = new Object();
        Object eager = new Object();

        diagnostics.begin(uncached, true, false, 100L);
        diagnostics.finish(uncached, 130L);
        diagnostics.begin(cached, true, true, 200L);
        diagnostics.finish(cached, 205L);
        diagnostics.begin(eager, false, false, 300L);
        diagnostics.finish(eager, 350L);

        assertEquals(85L, diagnostics.allNanos());
        assertEquals(3, diagnostics.allCalls());
        assertEquals(5L, diagnostics.cachedNanos());
        assertEquals(1, diagnostics.cachedCalls());
        assertEquals(30L, diagnostics.candidateUncachedNanos());
        assertEquals(1, diagnostics.candidateUncachedCalls());
        assertEquals(5L, diagnostics.candidateCachedNanos());
        assertEquals(1, diagnostics.candidateCachedCalls());
        assertEquals(0, diagnostics.nestedCalls());
        assertEquals(0, diagnostics.untimedCalls());
    }

    @Test
    void unknownReturnsSuchAsItemKeysDoNotDisturbOpenFrames() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object blockState = new Object();
        Object item = new Object();

        diagnostics.begin(blockState, true, false, 10L);
        // An item call interleaves; the mixin never forwards it, but a stray
        // return must not close or re-time the block-state frame.
        diagnostics.finish(item, 15L);
        diagnostics.finish(blockState, 40L);
        diagnostics.finish(blockState, 90L);

        assertEquals(1, diagnostics.allCalls());
        assertEquals(30L, diagnostics.allNanos());
        assertEquals(30L, diagnostics.candidateUncachedNanos());
        assertEquals(0, diagnostics.untimedCalls());
    }

    @Test
    void nestedCallsAreTimedInclusivelyWithoutStaleStarts() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object outer = new Object();
        Object inner = new Object();

        diagnostics.begin(outer, false, false, 0L);
        diagnostics.begin(inner, true, false, 10L);
        diagnostics.finish(inner, 25L);
        diagnostics.finish(outer, 100L);

        assertEquals(2, diagnostics.allCalls());
        assertEquals(115L, diagnostics.allNanos());
        assertEquals(15L, diagnostics.candidateUncachedNanos());
        assertEquals(1, diagnostics.nestedCalls());
        assertEquals(0, diagnostics.untimedCalls());
    }

    @Test
    void framesAbandonedByAnExceptionAreDroppedWhenTheCallerReturns() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object outer = new Object();
        Object thrown = new Object();
        Object later = new Object();

        diagnostics.begin(outer, false, false, 0L);
        diagnostics.begin(thrown, true, false, 5L);
        diagnostics.finish(outer, 50L);
        diagnostics.begin(later, true, true, 60L);
        diagnostics.finish(later, 70L);

        assertEquals(2, diagnostics.allCalls());
        assertEquals(60L, diagnostics.allNanos());
        assertEquals(0L, diagnostics.candidateUncachedNanos());
        assertEquals(10L, diagnostics.candidateCachedNanos());
        assertEquals(1, diagnostics.nestedCalls());
        assertEquals(1, diagnostics.untimedCalls());
    }

    @Test
    void overflowIsCountedAndDoesNotCorruptTheStack() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object[] keys =
                new Object[BlockStateGraphLoadDiagnostics.MAX_DEPTH + 1];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = new Object();
            diagnostics.begin(keys[index], false, false, index);
        }
        for (int index = keys.length - 1; index >= 0; index--) {
            diagnostics.finish(keys[index], 100L);
        }

        assertEquals(BlockStateGraphLoadDiagnostics.MAX_DEPTH,
                diagnostics.allCalls());
        assertEquals(1, diagnostics.untimedCalls());
    }

    @Test
    void summaryLabelsUpperBoundAndNonAdditiveTiming() {
        BlockStateGraphLoadDiagnostics diagnostics =
                new BlockStateGraphLoadDiagnostics();
        Object key = new Object();
        diagnostics.begin(key, true, false, 0L);
        diagnostics.finish(key, 2_000_000L);

        String line = diagnostics.describe(9_000_000L);

        assertTrue(line.contains("UPPER BOUND"));
        assertTrue(line.contains("not savings"));
        assertTrue(line.contains("not additive"));
        assertTrue(line.contains("deferral-eligible uncached 2.0 ms/1"));
        assertTrue(line.contains("processLoading wall 9.0 ms"));
        assertFalse(line.contains("\n"));
    }
}

