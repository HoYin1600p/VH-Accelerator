package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.util.AtomicFiles;
import dev.hoyin1600p.vhaccelerator.util.CacheFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Debug diagnostics that explain why the config fingerprint changed: logs the
 * differences against the manifest saved by the previous launch and saves the
 * current manifest for the next one.
 */
final class ConfigChangeReport {
    private static final int MAX_MANIFEST_ENTRIES = 100_000;
    private static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;
    private static final int CHANGE_REPORT_LIMIT = 24;
    static final Path MANIFEST_DIRECTORY =
            FMLPaths.GAMEDIR.get()
                    .resolve("cache")
                    .resolve("vhaccelerator")
                    .resolve("client-assets");
    static final Path CONFIG_MANIFEST =
            MANIFEST_DIRECTORY.resolve(
                    "config-fingerprint-manifest-v1.txt"
            );
    private static final Executor MANIFEST_WRITER =
            SharedWorkers.io();

    private ConfigChangeReport() {
    }

    static void reportConfigChanges(
            StableConfigFingerprint.EarlyConfigSnapshot earlyConfigs,
            Map<String, String> current,
            TreeSet<String> changedDuringLaunch
    ) {
        if (!changedDuringLaunch.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "{} asset-affecting config file(s) changed content "
                            + "during this launch",
                    changedDuringLaunch.size()
            );
            reportChangeDetails(
                    changedDuringLaunch,
                    "changed during launch"
            );
        }

        Map<String, String> previous = readConfigManifest();
        if (previous.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Recorded client asset config fingerprint baseline "
                            + "for {} file(s)",
                    current.size()
            );
        } else {
            TreeSet<String> changed = new TreeSet<>();
            TreeSet<String> added = new TreeSet<>();
            TreeSet<String> removed = new TreeSet<>();
            for (Map.Entry<String, String> entry :
                    current.entrySet()) {
                String oldValue = previous.get(entry.getKey());
                if (oldValue == null) {
                    added.add(entry.getKey());
                } else if (!oldValue.equals(entry.getValue())) {
                    changed.add(entry.getKey());
                }
            }
            for (String key : previous.keySet()) {
                if (!current.containsKey(key)) {
                    removed.add(key);
                }
            }
            if (!changed.isEmpty()
                    || !added.isEmpty()
                    || !removed.isEmpty()) {
                VHAccelerator.LOGGER.info(
                        "Client asset config fingerprint changed across "
                                + "launches: {} content, {} added, "
                                + "{} removed",
                        changed.size(),
                        added.size(),
                        removed.size()
                );
                reportChangeDetails(changed, "content changed");
                reportChangeDetails(added, "added");
                reportChangeDetails(removed, "removed");
            }
        }

        CompletableFuture.runAsync(
                () -> writeConfigManifest(current),
                MANIFEST_WRITER
        );
    }

    static void reportChangeDetails(
            TreeSet<String> changes,
            String kind
    ) {
        int reported = 0;
        for (String key : changes) {
            if (reported >= CHANGE_REPORT_LIMIT) {
                break;
            }
            VHAccelerator.LOGGER.info(
                    "Client asset config change [{}]: {} ({})",
                    reported + 1,
                    displayConfigKey(key),
                    kind
            );
            reported++;
        }
        if (changes.size() > reported) {
            VHAccelerator.LOGGER.info(
                    "{} additional client asset config change(s) omitted",
                    changes.size() - reported
            );
        }
    }

    static Map<String, String> readConfigManifest() {
        if (!Files.isRegularFile(CONFIG_MANIFEST)) {
            return Map.of();
        }
        try {
            if (Files.size(CONFIG_MANIFEST) > MAX_MANIFEST_BYTES) {
                return Map.of();
            }
            List<String> lines = Files.readAllLines(
                    CONFIG_MANIFEST,
                    StandardCharsets.UTF_8
            );
            if (lines.size() > MAX_MANIFEST_ENTRIES) {
                return Map.of();
            }
            Map<String, String> manifest =
                    new HashMap<>(Math.max(16, lines.size() * 2));
            for (String line : lines) {
                int separator = line.indexOf('\t');
                if (separator <= 0 || separator == line.length() - 1) {
                    return Map.of();
                }
                String key = new String(
                        Base64.getUrlDecoder().decode(
                                line.substring(0, separator)
                        ),
                        StandardCharsets.UTF_8
                );
                manifest.put(key, line.substring(separator + 1));
            }
            return Map.copyOf(manifest);
        } catch (IOException | IllegalArgumentException failure) {
            VHAccelerator.LOGGER.debug(
                    "Could not read the client asset config manifest",
                    failure
            );
            return Map.of();
        }
    }

    static void writeConfigManifest(
            Map<String, String> manifest
    ) {
        if (manifest.size() > MAX_MANIFEST_ENTRIES) {
            return;
        }
        Path temporary = CONFIG_MANIFEST.resolveSibling(
                CONFIG_MANIFEST.getFileName() + CacheFiles.TEMP_SUFFIX
        );
        try {
            Files.createDirectories(MANIFEST_DIRECTORY);
            List<String> lines =
                    new ArrayList<>(manifest.size());
            for (Map.Entry<String, String> entry :
                    new TreeMap<>(manifest).entrySet()) {
                String encodedKey = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                entry.getKey().getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );
                lines.add(
                        encodedKey
                                + '\t'
                                + entry.getValue()
                );
            }
            Files.write(
                    temporary,
                    lines,
                    StandardCharsets.UTF_8
            );
            AtomicFiles.moveIntoPlace(temporary, CONFIG_MANIFEST);
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.debug(
                    "Could not write the client asset config manifest",
                    failure
            );
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // An incomplete diagnostic file is never trusted.
            }
        }
    }

    static String displayConfigKey(String key) {
        return key.replace('\u0000', '/');
    }
}
