package dev.hoyin1600p.vhaccelerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BootstrapBackportConfigTest {
    @Test
    void missingValuesUseSafeDefaults() {
        Map<BackportFeature, Boolean> values =
                BootstrapBackportConfig.resolveValues(path -> null);

        for (BackportFeature feature : BackportFeature.values()) {
            assertFalse(values.get(feature), feature.id());
        }
    }

    @Test
    void eachConfiguredValueIsResolvedIndependently() {
        BackportFeature selected = BackportFeature.CHUNK_MESHING;
        Map<BackportFeature, Boolean> values =
                BootstrapBackportConfig.resolveValues(path ->
                        matches(path, selected) ? Boolean.TRUE : null
                );

        assertTrue(values.get(selected));
        assertFalse(values.get(BackportFeature.FORGE_HANDSHAKE_BATCHING));
    }

    private static boolean matches(
            List<String> path,
            BackportFeature feature
    ) {
        return path.equals(List.of("backports", feature.configKey()));
    }
}
