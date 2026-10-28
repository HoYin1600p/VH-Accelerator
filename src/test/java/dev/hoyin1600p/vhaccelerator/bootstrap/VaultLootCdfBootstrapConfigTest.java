package dev.hoyin1600p.vhaccelerator.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultLootCdfBootstrapConfigTest {
    @TempDir Path directory;

    @Test
    void migrationAndTomlParsingRunOnceAcrossThousandsOfDistributionFallbackReads() throws Exception {
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = false\n");
        AtomicInteger captures = new AtomicInteger();
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(() -> {
            captures.incrementAndGet();
            ConfigMigration.migrateVaultLootCdf(directory, true);
            return BootstrapCommonConfig.read(directory.resolve(ConfigMigration.COMMON_CONFIG));
        });
        for (int i = 0; i < 2000; i++) {
            assertTrue(snapshot.bool("optimizations", "enableCommonOptimizations", true));
            assertFalse(snapshot.bool("compatibility", "optimizeVaultLootCdf", true));
        }
        assertEquals(1, captures.get());
        Files.writeString(directory.resolve(ConfigMigration.COMMON_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = true\n");
        assertFalse(snapshot.bool("compatibility", "optimizeVaultLootCdf", true), "bootstrap fallback is a launch snapshot");
        assertEquals(1, captures.get());
    }

    @Test
    void explicitCommonFalseWinsBeforeTheSnapshotCapturesMigratedClientTrue() throws Exception {
        Files.writeString(directory.resolve(ConfigMigration.CLIENT_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = true\n");
        Files.writeString(directory.resolve(ConfigMigration.COMMON_CONFIG),
                "[compatibility]\noptimizeVaultLootCdf = false\n");
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(() -> {
            ConfigMigration.migrateVaultLootCdf(directory, true);
            return BootstrapCommonConfig.read(directory.resolve(ConfigMigration.COMMON_CONFIG));
        });
        assertFalse(snapshot.bool("compatibility", "optimizeVaultLootCdf", true));
    }

    @Test
    void missingAndWrongTypedValuesKeepCallersDefaultsWithoutRepeatedReads() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Path common = directory.resolve(ConfigMigration.COMMON_CONFIG);
        BootstrapCommonConfig.Snapshot missing = new BootstrapCommonConfig.Snapshot(() -> {
            reads.incrementAndGet();
            return BootstrapCommonConfig.read(common);
        });
        assertTrue(missing.bool("compatibility", "optimizeVaultLootCdf", true));
        Files.writeString(common, "[compatibility]\noptimizeVaultLootCdf = false\n");
        assertTrue(missing.bool("compatibility", "optimizeVaultLootCdf", true));
        assertFalse(missing.bool("compatibility", "optimizeVaultLootCdf", false));
        assertEquals(1, reads.get());
        Files.writeString(common, "[compatibility]\noptimizeVaultLootCdf = \"not a boolean\"\nother = false\n");
        BootstrapCommonConfig.Snapshot typed = new BootstrapCommonConfig.Snapshot(() -> BootstrapCommonConfig.read(common));
        assertTrue(typed.bool("compatibility", "optimizeVaultLootCdf", true));
        assertFalse(typed.bool("compatibility", "other", true));
    }

    @Test
    void malformedSnapshotFallsBackOnceEvenIfTheFileIsLaterCorrected() throws Exception {
        Path common = directory.resolve(ConfigMigration.COMMON_CONFIG);
        Files.writeString(common, "[unterminated\n");
        AtomicInteger reads = new AtomicInteger();
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(() -> {
            reads.incrementAndGet();
            return BootstrapCommonConfig.read(common);
        });
        assertTrue(snapshot.bool("compatibility", "optimizeVaultLootCdf", true));
        Files.writeString(common, "[compatibility]\noptimizeVaultLootCdf = false\n");
        assertTrue(snapshot.bool("compatibility", "optimizeVaultLootCdf", true));
        assertEquals(1, reads.get());
    }

    @Test
    void concurrentReadersShareOneImmutableSnapshot() throws Exception {
        Map<List<String>, Boolean> mutable = new HashMap<>();
        mutable.put(List.of("compatibility", "optimizeVaultLootCdf"), false);
        AtomicInteger reads = new AtomicInteger();
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(() -> {
            reads.incrementAndGet();
            return mutable;
        });
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = java.util.stream.IntStream.range(0, 1000)
                    .mapToObj(i -> (java.util.concurrent.Callable<Boolean>) () ->
                            snapshot.bool("compatibility", "optimizeVaultLootCdf", true)).toList();
            for (var result : executor.invokeAll(tasks)) assertFalse(result.get());
        } finally { executor.shutdownNow(); }
        mutable.put(List.of("compatibility", "optimizeVaultLootCdf"), true);
        assertFalse(snapshot.bool("compatibility", "optimizeVaultLootCdf", true));
        assertEquals(1, reads.get());
    }

    @Test
    void loadedSpecAndLiveChangesOverrideTheCachedFallbackAndCompareModeStillWins() throws Exception {
        var child = ForgeConfigSpec.class.getDeclaredField("childConfig");
        child.setAccessible(true);
        CommentedConfig previousConfig = (CommentedConfig) child.get(VHAcceleratorConfig.COMMON_SPEC);
        var compare = BootstrapCompareMode.class.getDeclaredField("launchValue");
        compare.setAccessible(true);
        Object previousCompare = compare.get(null);
        var launch = BootstrapCommonConfig.class.getDeclaredField("LAUNCH");
        launch.setAccessible(true);
        Object snapshot = launch.get(null);
        var values = BootstrapCommonConfig.Snapshot.class.getDeclaredField("values");
        values.setAccessible(true);
        Object previousValues = values.get(snapshot);
        try {
            BootstrapCompareMode.set(false);
            values.set(snapshot, Map.of(List.of("optimizations", "enableCommonOptimizations"), false,
                    List.of("compatibility", "optimizeVaultLootCdf"), false));
            VHAcceleratorConfig.COMMON_SPEC.setConfig(null);
            assertFalse(VHAcceleratorConfig.optimizeVaultLootCdfEnabled());
            CommentedConfig loaded = CommentedConfig.inMemory();
            VHAcceleratorConfig.COMMON_SPEC.correct(loaded);
            VHAcceleratorConfig.COMMON_SPEC.setConfig(loaded);
            assertTrue(VHAcceleratorConfig.optimizeVaultLootCdfEnabled(), "loaded true overrides cached false");
            VHAcceleratorConfig.COMMON.optimizeVaultLootCdf.set(false);
            assertFalse(VHAcceleratorConfig.optimizeVaultLootCdfEnabled());
            VHAcceleratorConfig.COMMON.optimizeVaultLootCdf.set(true);
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(false);
            assertFalse(VHAcceleratorConfig.optimizeVaultLootCdfEnabled());
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(true);
            assertTrue(VHAcceleratorConfig.optimizeVaultLootCdfEnabled());
            BootstrapCompareMode.set(true);
            assertFalse(VHAcceleratorConfig.optimizeVaultLootCdfEnabled());
        } finally {
            VHAcceleratorConfig.COMMON_SPEC.setConfig(previousConfig);
            values.set(snapshot, previousValues);
            compare.set(null, previousCompare);
        }
    }
}
