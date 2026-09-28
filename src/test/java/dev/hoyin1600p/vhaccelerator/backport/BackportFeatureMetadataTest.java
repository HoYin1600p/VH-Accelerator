package dev.hoyin1600p.vhaccelerator.backport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BackportFeatureMetadataTest {
    /**
     * Implemented features that add work to the launch path and ship as a
     * measured opt-in until in-pack A/B results justify enabling them.
     */
    private static final java.util.Set<BackportFeature> MEASURED_OPT_IN =
            java.util.Set.of();

    @Test
    void implementedFeaturesDefaultOnAndUnavailableFeaturesDefaultOff() {
        for (BackportFeature feature : BackportFeature.values()) {
            assertEquals(
                    feature.implemented() && !MEASURED_OPT_IN.contains(feature),
                    feature.defaultEnabled(),
                    feature.id()
            );
        }
    }

    @Test
    void attachCapabilitiesRequiresTheExactModernFixMixin() {
        assertEquals(
                List.of(
                        "org.embeddedt.modernfix.common.mixin.perf."
                                + "forge_cap_retrieval.AttachCapabilitiesEventMixin"
                ),
                BackportFeature.ATTACH_CAPABILITIES_DISPATCH
                        .modernFixMarkerClasses()
        );
    }
}
