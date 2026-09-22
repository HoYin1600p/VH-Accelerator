package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeferredItemModelDiagnosticsTest {
    private DeferredModelRegistry<String, String> registry() {
        Map<String, String> eager = new HashMap<>(Map.of("minecraft:missing", "missing"));
        return new DeferredModelRegistry<>(eager, List.of("a#inventory", "b#inventory", "c#inventory"),
                key -> key.startsWith("c") ? null : "baked", "missing", (k, f) -> { });
    }

    @Test void lifecycleTransitionsAreReportedOnce() {
        var diagnostics = new DeferredItemModelDiagnostics();
        assertNull(diagnostics.observe(false, false), "loading overlay is not a menu");
        assertEquals("first menu", diagnostics.observe(true, false));
        assertNull(diagnostics.observe(true, false));
        assertEquals("first world frame", diagnostics.observe(false, true));
        assertNull(diagnostics.observe(false, true), "dimension change keeps a level");
        assertEquals("world exit", diagnostics.observe(true, false));
        assertNull(diagnostics.observe(true, false), "first menu is reported once");
        assertEquals("world rejoin", diagnostics.observe(false, true));
    }

    @Test void menuAndWorldJoinLeaveSelectedModelsUnresolved() {
        var registry = registry();
        var diagnostics = new DeferredItemModelDiagnostics();
        assertEquals("first menu", diagnostics.observe(true, false));
        var menu = diagnostics.snapshot("first menu", registry);
        assertEquals(3, menu.selected());
        assertEquals(3, menu.unresolved());
        assertEquals(0, menu.intervalBakes());
        assertEquals("first world frame", diagnostics.observe(false, true));
        var world = diagnostics.snapshot("first world frame", registry);
        assertEquals(3, world.unresolved(), "joining a world bakes nothing");
        assertEquals(0, world.baked());
    }

    @Test void snapshotsCountFirstUseBakesPerInterval() {
        var registry = registry();
        var diagnostics = new DeferredItemModelDiagnostics();
        registry.get("a#inventory");
        diagnostics.recordBake("a#inventory", 2_000_000L);
        registry.get("c#inventory");
        diagnostics.recordBake("c#inventory", 5_000_000L);
        var first = diagnostics.snapshot("first world frame", registry);
        assertEquals(1, first.unresolved());
        assertEquals(1, first.baked());
        assertEquals(1, first.failed());
        assertEquals(2, first.intervalBakes());
        assertEquals(7_000_000L, first.intervalNanos());
        assertEquals("c#inventory", first.slowestKey());
        assertTrue(first.describe().contains("1 unresolved"));
        var next = diagnostics.snapshot("reload retirement", registry);
        assertEquals(0, next.intervalBakes(), "intervals reset");
        assertNull(next.slowestKey());
        assertTrue(next.describe().contains("slowest none"));
    }

    @Test void individualBakeLoggingIsBounded() {
        var diagnostics = new DeferredItemModelDiagnostics();
        int logged = 0;
        for (int i = 0; i < DeferredItemModelDiagnostics.INDIVIDUAL_BAKE_LOG_LIMIT + 10; i++) {
            if (diagnostics.recordBake("k" + i, 1)) {
                logged++;
            }
        }
        assertEquals(DeferredItemModelDiagnostics.INDIVIDUAL_BAKE_LOG_LIMIT, logged);
    }
}
