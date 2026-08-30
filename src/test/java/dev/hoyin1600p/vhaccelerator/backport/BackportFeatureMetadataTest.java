package dev.hoyin1600p.vhaccelerator.backport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class BackportFeatureMetadataTest {
    @Test
    void implementedFeaturesDefaultOnAndUnavailableFeaturesDefaultOff() {
        for (BackportFeature feature : BackportFeature.values()) {
            assertEquals(
                    feature.implemented(),
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
