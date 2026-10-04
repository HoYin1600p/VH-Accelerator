package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.ClientAssetFingerprint.BaseFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.ClientAssetFingerprint.ParsedFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.FingerprintFixtureReport.ConfigResult;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.StableConfigFingerprint.EarlyConfigSnapshot;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.StableConfigFingerprint.StableConfigResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;
import org.junit.jupiter.api.Test;

/**
 * Runs each stage of the client asset fingerprint over a fixed game
 * directory and compares the digests, manifests and encoded fingerprint with
 * a snapshot recorded from the implementation before it was split into
 * helper classes. A mismatch means the fingerprint (and therefore every
 * persistent cache key) changed.
 */
class ClientAssetFingerprintFixtureTest {
    @Test
    void fingerprintStagesMatchTheRecordedSnapshot() throws IOException {
        Path gameDir = Files.createTempDirectory("vha-fingerprint");
        FMLPaths.loadAbsolutePaths(gameDir);
        FingerprintFixtureReport.populate(gameDir);

        List<String> actual = FingerprintFixtureReport.render(new Production(), gameDir);

        try (InputStream stream = getClass().getResourceAsStream("fingerprint-fixture-snapshot.txt")) {
            List<String> expected = List.of(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
            assertEquals(expected, actual);
        }
    }

    private static final class Production implements FingerprintFixtureReport.Stages {
        @Override
        public Object captureEarlyConfigSnapshot() {
            return StableConfigFingerprint.captureEarlyConfigSnapshot();
        }

        @Override
        public ConfigResult resolveStableConfigContents(Object earlyConfigs) {
            return result(StableConfigFingerprint.resolveStableConfigContents((EarlyConfigSnapshot) earlyConfigs));
        }

        @Override
        public ConfigResult scanAllStableConfigContents(Object earlyConfigs) {
            return result(StableConfigFingerprint.scanAllStableConfigContents((EarlyConfigSnapshot) earlyConfigs));
        }

        private static ConfigResult result(StableConfigResult stable) {
            return new ConfigResult(stable.fingerprintInputs(), stable.manifest(), stable.changedDuringLaunch());
        }

        @Override
        public void closeEarlyConfigMonitor(Object earlyConfigs) {
            ConfigFingerprintMonitor monitor = ((EarlyConfigSnapshot) earlyConfigs).monitor();
            if (monitor != null) {
                monitor.close();
            }
        }

        @Override
        public String digestStrings(List<String> values) {
            return FingerprintDigests.digestStrings(values);
        }

        @Override
        public void appendModFileMetadata(List<String> inputs, Path modsDirectory) {
            FileMetadataInputs.appendFlatMetadata(inputs, modsDirectory, "mod-file");
        }

        @Override
        public void appendResourcePackMetadata(List<String> inputs, Path directory) {
            FileMetadataInputs.appendResourcePackMetadata(inputs, directory);
        }

        @Override
        public String encode(String installation, String configs, String resourceFiles, String activePacks) {
            return ClientAssetFingerprint.encode(
                    new BaseFingerprint(installation, configs, resourceFiles), activePacks);
        }

        @Override
        public List<String> parse(String encoded) {
            ParsedFingerprint parsed = ClientAssetFingerprint.parse(encoded);
            return parsed == null ? null : List.of(
                    parsed.installation(), parsed.configs(), parsed.resourceFiles(), parsed.activePacks());
        }

        @Override
        public void writeConfigManifest(Map<String, String> manifest) {
            ConfigChangeReport.writeConfigManifest(manifest);
        }

        @Override
        public Map<String, String> readConfigManifest() {
            return ConfigChangeReport.readConfigManifest();
        }

        @Override
        public Path manifestFile() {
            return ConfigChangeReport.CONFIG_MANIFEST;
        }
    }
}
