package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompatibilityConfigEvidenceTest {
    @Test
    @SuppressWarnings("unchecked")
    void snapshotsCopyRawSettingsWithoutRetainingObjectsOrInventingGuardResults() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("diagnostics", Map.of("debug", true));
        input.put("optimizations", Map.of("enableCommonOptimizations", false));
        input.put("unsupported", new Object());
        input.put("nonfinite", Double.NaN);
        CompatibilityConfigEvidence.capture("COMMON", input);
        input.clear();
        Map<String, Object> snapshot = CompatibilityConfigEvidence.snapshot();
        Map<String, Map<String, Object>> files = (Map<String, Map<String, Object>>) snapshot.get("configurations");
        Map<String, Object> common = files.get("COMMON");
        assertEquals(Map.of("diagnostics.debug", true, "optimizations.enableCommonOptimizations", false), common.get("values"));
        assertEquals(2L, common.get("omittedValues"));
        assertTrue(common.get("source").toString().contains("not evaluated runtime guards"));
        CompatibilityConfigEvidence.capture("COMMON", Map.of("diagnostics", Map.of("debug", false)));
        assertEquals(Map.of("diagnostics.debug", true, "optimizations.enableCommonOptimizations", false), common.get("values"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overlongAndExcessSettingsAreExplicitlyOmitted() {
        Map<String, Object> input = new LinkedHashMap<>();
        for (int i = 0; i < 1030; i++) input.put("setting" + i, true);
        input.put("longText", "x".repeat(4097));
        CompatibilityConfigEvidence.capture("SERVER", input);
        Map<String, Map<String, Object>> files = (Map<String, Map<String, Object>>) CompatibilityConfigEvidence.snapshot().get("configurations");
        var server = files.get("SERVER");
        assertEquals(1024, ((Map<?, ?>) server.get("values")).size());
        assertEquals(7L, server.get("omittedValues"));
    }
}
