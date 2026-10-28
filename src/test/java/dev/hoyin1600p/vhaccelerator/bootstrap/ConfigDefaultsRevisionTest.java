package dev.hoyin1600p.vhaccelerator.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class ConfigDefaultsRevisionTest {
    private static final String BG = "[compatibility]\n\tcompactBuildingGadgetsTemplates = ";

    @TempDir
    Path directory;

    private Path common() {
        return directory.resolve(ConfigMigration.COMMON_CONFIG);
    }

    private Path marker() {
        return directory.resolve(ConfigDefaultsRevision.MARKER);
    }

    @Test
    void movesOldDefaultsOnceAndKeepsLaterChoices() throws Exception {
        Path common = common();
        Files.writeString(common, "[backports]\n\tdeduplicateResourceLocationNamespaces = false\n");
        Path client = directory.resolve(ConfigMigration.CLIENT_CONFIG);
        Files.writeString(client, "[optimizations]\n\tdeferItemModelBaking = true\n\tskipBlockStateGraphLoading = false\n");

        ConfigDefaultsRevision.apply(directory, true);

        assertTrue(Files.readString(common).contains("deduplicateResourceLocationNamespaces = true"));
        assertTrue(Files.readString(client).contains("skipBlockStateGraphLoading = true"));
        assertTrue(Files.readString(client).contains("deferItemModelBaking = true"));
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));

        // A player turning it off afterwards is respected.
        Files.writeString(common, "[backports]\n\tdeduplicateResourceLocationNamespaces = false\n");
        ConfigDefaultsRevision.apply(directory, true);
        assertTrue(Files.readString(common).contains("deduplicateResourceLocationNamespaces = false"));
    }

    @Test
    void freshInstallOnlyRecordsTheRevision() {
        ConfigDefaultsRevision.apply(directory, true);
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));
        assertFalse(Files.exists(common()));
    }

    @Test
    void buildingGadgetsOldDefaultFlipsWhenNoMarkerExists() throws Exception {
        Files.writeString(common(), BG + "false\n");
        ConfigDefaultsRevision.apply(directory, false);
        assertTrue(Files.readString(common()).contains("compactBuildingGadgetsTemplates = true"));
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void revisionOneInstallsOnlyGetRevisionTwoChanges() throws Exception {
        Files.writeString(marker(), "1");
        Files.writeString(common(), "[backports]\n\tdeduplicateResourceLocationNamespaces = false\n"
                + "[compatibility]\n\tcompactBuildingGadgetsTemplates = false\n");
        ConfigDefaultsRevision.apply(directory, false);
        String text = Files.readString(common());
        assertTrue(text.contains("compactBuildingGadgetsTemplates = true"));
        // Revision 1 was already applied, so a later deliberate false stays false.
        assertTrue(text.contains("deduplicateResourceLocationNamespaces = false"));
    }

    @Test
    void userValueOtherThanTheOldDefaultIsKept() throws Exception {
        Files.writeString(common(), BG + "true\n");
        ConfigDefaultsRevision.apply(directory, false);
        assertTrue(Files.readString(common()).contains("compactBuildingGadgetsTemplates = true"));
        // A wrong-typed value is not an old default either.
        Files.writeString(common(), BG + "\"false\"\n");
        Files.delete(marker());
        ConfigDefaultsRevision.apply(directory, false);
        assertTrue(Files.readString(common()).contains("\"false\""));
    }

    @Test
    void alreadyCurrentRevisionLeavesTheFileUntouched() throws Exception {
        Files.writeString(marker(), Integer.toString(ConfigDefaultsRevision.REVISION));
        String original = BG + "false\n";
        Files.writeString(common(), original);
        ConfigDefaultsRevision.apply(directory, true);
        assertEquals(original, Files.readString(common()));
    }

    @Test
    void newerMarkerThanThisBuildIsNeverLowered() throws Exception {
        Files.writeString(marker(), "99");
        Files.writeString(common(), BG + "false\n");
        ConfigDefaultsRevision.apply(directory, true);
        assertTrue(Files.readString(common()).contains("= false"));
        assertEquals(99, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void garbageMarkerIsTreatedAsRevisionZero() throws Exception {
        Files.writeString(marker(), "not a number");
        assertEquals(0, ConfigDefaultsRevision.readRevision(marker()));
        Files.writeString(common(), BG + "false\n");
        ConfigDefaultsRevision.apply(directory, false);
        assertTrue(Files.readString(common()).contains("= true"));
    }

    @Test
    void secondRunIsIdempotent() throws Exception {
        Files.writeString(common(), BG + "false\n");
        ConfigDefaultsRevision.apply(directory, false);
        String once = Files.readString(common());
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(once, Files.readString(common()));
        // Even with the marker lost, a flipped value stays flipped.
        Files.delete(marker());
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(once, Files.readString(common()));
    }

    @Test
    void dedicatedServerNeverTouchesTheClientFile() throws Exception {
        Path client = directory.resolve(ConfigMigration.CLIENT_CONFIG);
        String original = "[optimizations]\n\tdeferItemModelBaking = false\n";
        Files.writeString(client, original);
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(original, Files.readString(client));
    }

    @Test
    void malformedFileIsLeftAloneAndRetriedNextLaunch() throws Exception {
        String garbage = "[compatibility\n\tcompactBuildingGadgetsTemplates = = false\n";
        Files.writeString(common(), garbage);
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(garbage, Files.readString(common()));
        assertFalse(Files.exists(marker()), "no marker so the next launch retries");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void readOnlyFileKeepsTheOldValueAndDoesNotRecordTheRevision() throws Exception {
        Path file = common();
        Files.writeString(file, BG + "false\n");
        assertTrue(file.toFile().setReadOnly());
        try {
            Assumptions.assumeFalse(file.toFile().canWrite(), "read-only not enforced here");
            ConfigDefaultsRevision.apply(directory, false);
            assertTrue(Files.readString(file).contains("= false"));
            assertFalse(Files.exists(marker()));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    void firstBootGatingSnapshotSeesTheMigratedValue() throws Exception {
        Files.writeString(common(), BG + "false\n");
        // The same capture the launch snapshot runs, before any other reader of the common file.
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertTrue(snapshot.bool("compatibility", "compactBuildingGadgetsTemplates", false));
        assertTrue(Files.readString(common()).contains("compactBuildingGadgetsTemplates = true"));
    }

    @Test
    void firstBootGatingKeepsADeliberateValueAfterTheMigration() throws Exception {
        Files.writeString(marker(), Integer.toString(ConfigDefaultsRevision.REVISION));
        Files.writeString(common(), BG + "false\n");
        BootstrapCommonConfig.Snapshot snapshot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertFalse(snapshot.bool("compatibility", "compactBuildingGadgetsTemplates", true));
    }

    private static final String ZONES = "[compatibility]\ncompactWorldZonesMemberships = ";

    @Test
    void worldZonesRevisionTwoUpgradeLeavesEarlierOptOutsAlone() throws Exception {
        Files.writeString(marker(), "2");
        Files.writeString(common(), "[backports]\ndeduplicateResourceLocationNamespaces = false\n"
                + ZONES + "false\ncompactBuildingGadgetsTemplates = false\n");
        ConfigDefaultsRevision.apply(directory, false);
        String text = Files.readString(common());
        assertTrue(text.contains("compactWorldZonesMemberships = true"));
        assertTrue(text.contains("compactBuildingGadgetsTemplates = false"));
        assertTrue(text.contains("deduplicateResourceLocationNamespaces = false"));
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void worldZonesFirstBootUpgradesOnceAndLaterOptOutIsKept(int revision) throws Exception {
        if (revision > 0) Files.writeString(marker(), Integer.toString(revision));
        Files.writeString(common(), ZONES + "false\n");
        var firstBoot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertTrue(firstBoot.bool("compatibility", "compactWorldZonesMemberships", false));
        assertTrue(Files.readString(common()).contains("compactWorldZonesMemberships = true"));
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));
        Files.writeString(common(), ZONES + "false\n");
        var laterBoot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertFalse(laterBoot.bool("compatibility", "compactWorldZonesMemberships", true));
        assertEquals(ZONES + "false\n", Files.readString(common()));
    }

    @Test
    void missingWorldZonesValueUsesNewDefaultWithoutRewritingTheFile() throws Exception {
        Files.writeString(marker(), "2");
        String original = "[compatibility]\ncompactBuildingGadgetsTemplates = false\n";
        Files.writeString(common(), original);
        var snapshot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertTrue(snapshot.bool("compatibility", "compactWorldZonesMemberships", true));
        assertEquals(original, Files.readString(common()));
        assertEquals(ConfigDefaultsRevision.REVISION, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void malformedWorldZonesValueIsLeftForForgeCorrection() throws Exception {
        Files.writeString(marker(), "2");
        String original = ZONES + "\"false\"\n";
        Files.writeString(common(), original);
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(original, Files.readString(common()));
    }

    private static final String TREES = "[optimizations]\nreleaseMixinClassTrees = ";

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void mixinTreesUpgradeOnceAndKeepLaterOptOuts(int revision) throws Exception {
        if (revision > 0) Files.writeString(marker(), Integer.toString(revision));
        Files.writeString(common(), TREES + "false\n");
        var firstBoot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertTrue(firstBoot.bool("optimizations", "releaseMixinClassTrees", false));
        assertTrue(Files.readString(common()).contains("releaseMixinClassTrees = true"));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));

        Files.writeString(common(), TREES + "false\n");
        var laterBoot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertFalse(laterBoot.bool("optimizations", "releaseMixinClassTrees", true));
        assertEquals(TREES + "false\n", Files.readString(common()));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void revisionFourDoesNotReplayEarlierDefaultChanges() throws Exception {
        Files.writeString(marker(), "3");
        Files.writeString(common(), "[backports]\ndeduplicateResourceLocationNamespaces = false\n"
                + "[compatibility]\ncompactBuildingGadgetsTemplates = false\n"
                + "compactWorldZonesMemberships = false\n" + TREES + "false\n");
        ConfigDefaultsRevision.apply(directory, false);
        String text = Files.readString(common());
        assertTrue(text.contains("releaseMixinClassTrees = true"));
        assertTrue(text.contains("deduplicateResourceLocationNamespaces = false"));
        assertTrue(text.contains("compactBuildingGadgetsTemplates = false"));
        assertTrue(text.contains("compactWorldZonesMemberships = false"));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void missingMixinTreesValueUsesNewDefaultWithoutRewriting() throws Exception {
        Files.writeString(marker(), "3");
        String original = "[optimizations]\nenableCommonOptimizations = false\n";
        Files.writeString(common(), original);
        var snapshot = new BootstrapCommonConfig.Snapshot(
                () -> BootstrapCommonConfig.captureFrom(directory, false));
        assertTrue(snapshot.bool("optimizations", "releaseMixinClassTrees", true));
        assertFalse(snapshot.bool("optimizations", "enableCommonOptimizations", true));
        assertEquals(original, Files.readString(common()));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void alreadyEnabledMixinTreesAreNotRewritten() throws Exception {
        Files.writeString(marker(), "3");
        String original = TREES + "true\n";
        Files.writeString(common(), original);
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(original, Files.readString(common()));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));
    }

    @Test
    void malformedMixinTreesValueIsLeftForForgeCorrection() throws Exception {
        Files.writeString(marker(), "3");
        String original = TREES + "\"false\"\n";
        Files.writeString(common(), original);
        ConfigDefaultsRevision.apply(directory, false);
        assertEquals(original, Files.readString(common()));
        assertEquals(4, ConfigDefaultsRevision.readRevision(marker()));
    }
}
