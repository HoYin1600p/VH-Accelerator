package dev.hoyin1600p.vhaccelerator.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RemovedCompatibilityConfigTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forgeCorrectsTheRemovedRampageKeyWithoutChangingExistingPreferences(boolean staleValue) {
        CommentedConfig config = CommentedConfig.inMemory();
        var spec = VHAcceleratorConfig.COMMON_SPEC;
        spec.correct(config);
        config.set("compatibility.compactBuildingGadgetsTemplates", false);
        config.set("compatibility.deferWoldsRampageListenerSetup", staleValue);
        assertFalse(spec.isCorrect(config));

        assertEquals(1, assertDoesNotThrow(() -> spec.correct(config)));

        assertFalse(config.contains("compatibility.deferWoldsRampageListenerSetup"));
        assertEquals(Boolean.FALSE, config.get("compatibility.compactBuildingGadgetsTemplates"));
        assertTrue(spec.isCorrect(config));
        assertEquals(0, spec.correct(config), "correction is idempotent");
    }
}
