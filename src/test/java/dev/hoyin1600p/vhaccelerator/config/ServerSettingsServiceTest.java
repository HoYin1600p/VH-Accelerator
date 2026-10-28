package dev.hoyin1600p.vhaccelerator.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerSettingsServiceTest {
    private static final class MemoryStore implements ServerSettingsService.Store {
        final Map<String, Object> values = new LinkedHashMap<>(Map.of("optimizations.flag", true, "limits.count", 5,
                "limits.fraction", 0.5));
        int reads, saves, effects, audits;
        boolean failSave;
        public boolean loaded() { return true; }
        public Map<String, ServerSettingsService.Definition> definitions() {
            return Map.of("optimizations.flag", new ServerSettingsService.Definition(true, null, null, true, v -> v instanceof Boolean),
                    "limits.count", new ServerSettingsService.Definition(5, 1, 10, false, v -> (Integer) v >= 1 && (Integer) v <= 10),
                    "limits.fraction", new ServerSettingsService.Definition(0.5, 0.0, 1.0, false, v -> (Double) v >= 0 && (Double) v <= 1));
        }
        public Object get(String key) { reads++; return values.get(key); }
        public void set(String key, Object value) { values.put(key, value); }
        public void save() { saves++; if (failSave) { failSave = false; throw new IllegalStateException("fixture failure"); } }
        public void afterSave(String key, Object value) { effects++; }
        public void audit(String player, String key, Object before, Object after) {
            assertEquals("Operator", player); assertNotEquals(before, after); audits++;
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void nonOperatorsCannotReadOrWrite(int level) {
        MemoryStore store = new MemoryStore();
        for (var changes : java.util.List.of(Map.<String, Object>of(), Map.<String, Object>of("optimizations.flag", false))) {
            var result = ServerSettingsService.handle(() -> level >= 4, "Visitor", changes, store);
            assertEquals(ServerSettingsService.Status.DENIED, result.status());
            assertTrue(result.values().isEmpty()); assertTrue(result.restartKeys().isEmpty());
        }
        assertEquals(0, store.reads); assertEquals(0, store.saves);
    }

    @Test void operatorRoundTripPersistsOnceAuditsAndReportsOnlyChangedRestartValues() {
        MemoryStore store = new MemoryStore();
        var initial = ServerSettingsService.handle(() -> true, "Operator", Map.of(), store);
        assertEquals(true, initial.values().get("optimizations.flag").value()); assertEquals(0, store.saves);
        var result = ServerSettingsService.handle(() -> true, "Operator", Map.of("optimizations.flag", false, "limits.count", 8), store);
        assertEquals(ServerSettingsService.Status.OK, result.status());
        assertEquals(false, result.values().get("optimizations.flag").value());
        assertEquals(8, result.values().get("limits.count").value());
        assertEquals(java.util.List.of("optimizations.flag"), result.restartKeys());
        assertEquals(1, store.saves); assertEquals(2, store.effects); assertEquals(2, store.audits);
        ServerSettingsService.handle(() -> true, "Operator", Map.of("limits.count", 8), store);
        assertEquals(1, store.saves);
    }

    @Test void unknownKeysAndMixedInvalidBatchesNeverPartlyApply() {
        for (String key : java.util.List.of("updates.checkForUpdates", "optimizations.flag.extra", "../config", "unknown")) {
            MemoryStore store = new MemoryStore();
            var result = ServerSettingsService.handle(() -> true, "Operator", Map.of("optimizations.flag", false, key, true), store);
            assertEquals(ServerSettingsService.Status.INVALID, result.status());
            assertEquals(true, store.values.get("optimizations.flag")); assertEquals(0, store.saves);
        }
    }

    @Test void badTypesRangesAndNonFiniteValuesAreRejected() {
        for (var change : java.util.List.of(Map.<String, Object>of("optimizations.flag", "true"),
                Map.<String, Object>of("optimizations.flag", 1), Map.<String, Object>of("limits.count", 1.0),
                Map.<String, Object>of("limits.count", 11), Map.<String, Object>of("limits.count", 0),
                Map.<String, Object>of("limits.fraction", Double.NaN),
                Map.<String, Object>of("limits.fraction", Double.POSITIVE_INFINITY),
                Map.<String, Object>of("limits.fraction", 1.1))) {
            MemoryStore store = new MemoryStore();
            assertEquals(ServerSettingsService.Status.INVALID,
                    ServerSettingsService.handle(() -> true, "Operator", change, store).status());
            assertEquals(0, store.saves);
        }
    }

    @Test void permissionIsCheckedAgainAfterOpeningAndBeforeMutation() {
        MemoryStore store = new MemoryStore(); AtomicBoolean permission = new AtomicBoolean(true);
        assertEquals(ServerSettingsService.Status.OK, ServerSettingsService.handle(permission::get, "Operator", Map.of(), store).status());
        permission.set(false);
        assertEquals(ServerSettingsService.Status.DENIED,
                ServerSettingsService.handle(permission::get, "Operator", Map.of("optimizations.flag", false), store).status());
        java.util.concurrent.atomic.AtomicInteger checks = new java.util.concurrent.atomic.AtomicInteger();
        assertEquals(ServerSettingsService.Status.DENIED, ServerSettingsService.handle(
                () -> checks.incrementAndGet() == 1, "Operator", Map.of("optimizations.flag", false), store).status());
        assertEquals(0, store.saves);
    }

    @Test void failedSaveRestoresValuesAndDoesNotRunEffectsOrAuditSuccess() {
        MemoryStore store = new MemoryStore(); store.failSave = true;
        var result = ServerSettingsService.handle(() -> true, "Operator", Map.of("optimizations.flag", false), store);
        assertEquals(ServerSettingsService.Status.SAVE_FAILED, result.status());
        assertTrue(result.values().isEmpty()); assertEquals(true, store.values.get("optimizations.flag"));
        assertEquals(2, store.saves); assertEquals(0, store.effects); assertEquals(0, store.audits);
    }
}
