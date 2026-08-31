package dev.hoyin1600p.vhaccelerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BootstrapBackportConfigTest {
    @Test
    void missingValuesUseDeclaredDefaults() {
        Map<BackportFeature, Boolean> values =
                BootstrapBackportConfig.resolveValues(path -> null);

        for (BackportFeature feature : BackportFeature.values()) {
            assertEquals(
                    feature.defaultEnabled(),
                    values.get(feature),
                    feature.id()
            );
        }
    }

    @Test
    void eachConfiguredValueIsResolvedIndependently() {
        BackportFeature selected = BackportFeature.CHUNK_MESHING;
        Map<BackportFeature, Boolean> values =
                BootstrapBackportConfig.resolveValues(path ->
                        matches(path, selected) ? Boolean.FALSE : null
                );

        assertFalse(values.get(selected));
        assertTrue(values.get(BackportFeature.FORGE_HANDSHAKE_BATCHING));
    }

    @Test
    void resourcePackIndexRetainsItsLegacyOptimizationPath() {
        Map<BackportFeature, Boolean> values =
                BootstrapBackportConfig.resolveValues(path ->
                        path.equals(List.of(
                                "optimizations",
                                "indexImmutableModResources"
                        )) ? Boolean.FALSE : null
                );

        assertFalse(values.get(BackportFeature.RESOURCE_PACK_INDEXING));
    }

    private static boolean matches(
            List<String> path,
            BackportFeature feature
    ) {
        return path.equals(List.of("backports", feature.configKey()));
    }
}
