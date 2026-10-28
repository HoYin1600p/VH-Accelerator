package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartupOperationMetricsTest {
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(StartupOperationMetrics metrics) {
        return (List<Map<String, Object>>) metrics.snapshot().get("rows");
    }

    @Test
    void nestedWorkAndThreadsKeepInclusiveTotalsAndPayloadEvidenceSeparate() throws Exception {
        StartupOperationMetrics metrics = new StartupOperationMetrics();
        metrics.enter("script", "fixture.Script", "reload-task-1", 2L);
        metrics.enter("action", "fixture.Action");
        metrics.exit("action", 12L);
        metrics.exit("script", null, 3L);
        Thread worker = new Thread(() -> {
            metrics.enter("action", "fixture.Action");
            metrics.exit("action", 0L);
        }, "fixture-worker");
        worker.start();
        worker.join();
        List<Map<String, Object>> rows = rows(metrics);
        assertEquals(3, rows.size());
        assertEquals(3L, rows.get(0).get("reportedItems"));
        assertEquals("reload-task-1", rows.get(0).get("parentSpanId"));
        assertEquals(2L, rows.get(0).get("reloadGeneration"));
        assertEquals("script", rows.get(1).get("parentOperation"));
        assertNull(rows.get(0).get("reportedPayloadBytes"));
        assertEquals(12L, rows.get(1).get("reportedPayloadBytes"));
        assertEquals(0L, rows.get(2).get("reportedPayloadBytes"));
        assertNotEquals(rows.get(1).get("threadId"), rows.get(2).get("threadId"));
        assertTrue((long) rows.get(0).get("inclusiveWallNanos") >= (long) rows.get(1).get("inclusiveWallNanos"));
        assertEquals(true, metrics.snapshot().get("complete"));
    }

    @Test
    void missingReturnsAndBoundsInvalidateMeasurementsWithoutThrowing() {
        StartupOperationMetrics metrics = new StartupOperationMetrics();
        metrics.enter("script", "fixture.Script");
        metrics.enter("action", "fixture.Action");
        metrics.exit("script", null);
        assertEquals(false, metrics.snapshot().get("complete"));
        assertEquals(2, metrics.snapshot().get("unfinishedCount"));
        assertEquals(1L, metrics.snapshot().get("mismatchedReturns"));
        StartupOperationMetrics bounded = new StartupOperationMetrics();
        for (int i = 0; i < 300; i++) {
            bounded.enter("action", "fixture.Owner" + i);
            bounded.exit("action", null);
        }
        assertEquals(256, rows(bounded).size());
        assertEquals(44L, bounded.snapshot().get("droppedOperations"));
        assertEquals(false, bounded.snapshot().get("complete"));
    }
}
