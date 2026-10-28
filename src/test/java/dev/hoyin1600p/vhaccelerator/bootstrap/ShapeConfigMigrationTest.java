package dev.hoyin1600p.vhaccelerator.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;

class ShapeConfigMigrationTest {
    @TempDir Path directory;

    private Path client() { return directory.resolve(ConfigMigration.CLIENT_CONFIG); }
    private Path common() { return directory.resolve(ConfigMigration.COMMON_CONFIG); }

    @Test
    void disabledClientPreferencesMoveToTheCommonConfigAndTheClientFileIsUntouched() throws Exception {
        String original = "# keep\n[optimizations]\ncacheLaunchVoxelShapes = false\noptimizeVoxelShapeMerging = false\n";
        Files.writeString(client(), original);
        assertTrue(ConfigMigration.migrateShapeSettings(directory, true));
        String common = Files.readString(common());
        assertTrue(common.contains("cacheLaunchVoxelShapes = false"), common);
        assertTrue(common.contains("optimizeVoxelShapeMerging = false"), common);
        assertEquals(original, Files.readString(client()));
        assertFalse(ConfigMigration.migrateShapeSettings(directory, true), "promotion happens once");
    }

    @Test
    void eachSettingMigratesIndependently() throws Exception {
        Files.writeString(client(), "[optimizations]\noptimizeVoxelShapeMerging = false\n");
        assertTrue(ConfigMigration.migrateShapeSettings(directory, true));
        String common = Files.readString(common());
        assertTrue(common.contains("optimizeVoxelShapeMerging = false"));
        assertFalse(common.contains("cacheLaunchVoxelShapes"));
    }

    @Test
    void anExplicitCommonValueWinsOverTheClientValue() throws Exception {
        Files.writeString(client(), "[optimizations]\ncacheLaunchVoxelShapes = false\noptimizeVoxelShapeMerging = false\n");
        String original = "# authoritative\n[optimizations]\ncacheLaunchVoxelShapes = true\noptimizeVoxelShapeMerging = true\n";
        Files.writeString(common(), original);
        assertFalse(ConfigMigration.migrateShapeSettings(directory, true));
        assertEquals(original, Files.readString(common()));
    }

    @Test
    void oneExplicitCommonValueDoesNotBlockTheOtherSetting() throws Exception {
        Files.writeString(client(), "[optimizations]\ncacheLaunchVoxelShapes = false\noptimizeVoxelShapeMerging = false\n");
        Files.writeString(common(), "[optimizations]\ncacheLaunchVoxelShapes = true\nenableCommonOptimizations = true\n");
        assertTrue(ConfigMigration.migrateShapeSettings(directory, true));
        String common = Files.readString(common());
        assertTrue(common.contains("cacheLaunchVoxelShapes = true"), common);
        assertTrue(common.contains("optimizeVoxelShapeMerging = false"), common);
        assertTrue(common.contains("enableCommonOptimizations = true"), common);
    }

    @Test
    void missingOrNonBooleanClientValuesWriteNothingAndServersIgnoreClientFiles() throws Exception {
        assertFalse(ConfigMigration.migrateShapeSettings(directory, true));
        Files.writeString(client(), "[optimizations]\ncacheLaunchVoxelShapes = \"no\"\nother = false\n");
        assertFalse(ConfigMigration.migrateShapeSettings(directory, true));
        Files.writeString(client(), "[optimizations]\ncacheLaunchVoxelShapes = false\n");
        assertFalse(ConfigMigration.migrateShapeSettings(directory, false));
        assertFalse(Files.exists(common()));
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readOnlyCommonConfigFailsInsteadOfHangingAndKeepsItsContent() throws Exception {
        Files.writeString(client(), "[optimizations]\ncacheLaunchVoxelShapes = false\n");
        String original = "# read-only\n[optimizations]\n";
        Files.writeString(common(), original);
        assertTrue(common().toFile().setReadOnly());
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(common().toFile().canWrite(), "read-only not enforced here");
            try {
                assertFalse(ConfigMigration.migrateShapeSettings(directory, true));
            } catch (RuntimeException expected) {
                // The caller logs and carries on; what matters is that it returned.
            }
            assertEquals(original, Files.readString(common()));
        } finally {
            common().toFile().setWritable(true);
        }
    }
}
