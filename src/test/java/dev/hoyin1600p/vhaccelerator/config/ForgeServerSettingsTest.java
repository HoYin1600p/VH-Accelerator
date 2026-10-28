package dev.hoyin1600p.vhaccelerator.config;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class ForgeServerSettingsTest {
    @Test void authoritativeChangesPersistAndReopenAsTypedToml(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var child = ForgeConfigSpec.class.getDeclaredField("childConfig"); child.setAccessible(true);
        CommentedConfig previous = (CommentedConfig) child.get(VHAcceleratorConfig.COMMON_SPEC);
        var path = directory.resolve("common.toml");
        try (var config = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(path).sync().build()) {
            VHAcceleratorConfig.COMMON_SPEC.correct(config); config.save();
            VHAcceleratorConfig.COMMON_SPEC.setConfig(config);
            var result = ServerSettingsService.handle(() -> true, "Operator",
                    Map.of("diagnostics.timers", true, "compatibility.lazyCompactVaultLootCdf", false), new ForgeServerSettings());
            assertEquals(ServerSettingsService.Status.OK, result.status());
            try (var reopened = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(path)) {
                reopened.load();
                assertEquals(Boolean.TRUE, reopened.get("diagnostics.timers"));
                assertEquals(Boolean.FALSE, reopened.get("compatibility.lazyCompactVaultLootCdf"));
            }
        } finally { VHAcceleratorConfig.COMMON_SPEC.setConfig(previous); }
    }

    @Test void actualForgeValuesAndLiveBootstrapSnapshotsFollowTheAuthoritativeSave() throws Exception {
        var child = ForgeConfigSpec.class.getDeclaredField("childConfig"); child.setAccessible(true);
        CommentedConfig previous = (CommentedConfig) child.get(VHAcceleratorConfig.COMMON_SPEC);
        var compare = BootstrapCompareMode.class.getDeclaredField("launchValue"); compare.setAccessible(true);
        var debug = BootstrapDebugDiagnostics.class.getDeclaredField("launchValue"); debug.setAccessible(true);
        Object previousCompare = compare.get(null), previousDebug = debug.get(null);
        try {
            CommentedConfig config = CommentedConfig.inMemory();
            VHAcceleratorConfig.COMMON_SPEC.correct(config); VHAcceleratorConfig.COMMON_SPEC.setConfig(config);
            BootstrapCompareMode.set(false); BootstrapDebugDiagnostics.set(false);
            var result = ServerSettingsService.handle(() -> true, "Operator",
                    Map.of("diagnostics.compareMode", true, "diagnostics.debug", true,
                            "compatibility.lazyCompactVaultLootCdf", false), new ForgeServerSettings());
            assertEquals(ServerSettingsService.Status.OK, result.status());
            assertTrue(VHAcceleratorConfig.COMMON.compareMode.get()); assertTrue(BootstrapCompareMode.enabled());
            assertTrue(VHAcceleratorConfig.COMMON.debug.get()); assertTrue(BootstrapDebugDiagnostics.enabled());
            assertFalse(VHAcceleratorConfig.COMMON.lazyCompactVaultLootCdf.get());
            assertEquals(false, result.values().get("compatibility.lazyCompactVaultLootCdf").value());
            assertEquals(java.util.Set.of("diagnostics.compareMode", "diagnostics.debug"), java.util.Set.copyOf(result.restartKeys()));
        } finally {
            VHAcceleratorConfig.COMMON_SPEC.setConfig(previous);
            compare.set(null, previousCompare); debug.set(null, previousDebug);
        }
    }
}
