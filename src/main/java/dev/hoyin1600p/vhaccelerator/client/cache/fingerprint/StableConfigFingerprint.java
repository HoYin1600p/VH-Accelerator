package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.util.PathNames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Hashes the config and default-config directories into the stable view the
 * fingerprint uses: an early snapshot taken at launch start, validated against
 * filesystem events (or a complete rescan) once Forge has loaded its configs.
 */
final class StableConfigFingerprint {
    private static final long MAX_CONFIG_HASH_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_STABILITY_ATTEMPTS = 3;
    private static final long CONFIG_EVENT_QUIET_MILLIS = 100L;
    private static final long MAX_CONFIG_EVENT_WAIT_MILLIS = 750L;
    private static final long POST_VALIDATION_QUIET_MILLIS = 50L;
    private static final long MAX_POST_VALIDATION_WAIT_MILLIS = 100L;

    private StableConfigFingerprint() {
    }

    static EarlyConfigSnapshot captureEarlyConfigSnapshot() {
        Map<String, EarlyConfigFile> files = new HashMap<>();
        TreeSet<String> missingDirectories = new TreeSet<>();
        TreeSet<ConfigFingerprintMonitor.ChangedPath>
                requiredValidation = new TreeSet<>();
        Map<String, Path> roots = new TreeMap<>();
        roots.put(
                "config",
                FMLPaths.CONFIGDIR.get()
                        .toAbsolutePath()
                        .normalize()
        );
        roots.put(
                "default-config",
                FMLPaths.GAMEDIR.get()
                        .resolve("defaultconfigs")
                        .toAbsolutePath()
                        .normalize()
        );
        ConfigFingerprintMonitor monitor =
                ConfigFingerprintMonitor.open(roots);
        boolean complete = monitor != null;
        if (Files.notExists(roots.get("config"))) {
            missingDirectories.add("config");
        }
        if (Files.notExists(roots.get("default-config"))) {
            missingDirectories.add("default-config");
        }
        complete &= captureEarlyConfigDirectory(
                files,
                requiredValidation,
                roots.get("config"),
                "config"
        );
        complete &= captureEarlyConfigDirectory(
                files,
                requiredValidation,
                roots.get("default-config"),
                "default-config"
        );
        return new EarlyConfigSnapshot(
                Map.copyOf(files),
                Set.copyOf(missingDirectories),
                Set.copyOf(requiredValidation),
                monitor,
                complete
        );
    }

    static boolean captureEarlyConfigDirectory(
            Map<String, EarlyConfigFile> output,
            Set<ConfigFingerprintMonitor.ChangedPath>
                    requiredValidation,
            Path directory,
            String label
    ) {
        if (Files.notExists(directory)) {
            return true;
        }
        if (!Files.isDirectory(directory)) {
            return false;
        }

        List<ConfigFileStamp> files;
        try {
            files = snapshotConfigFiles(directory);
        } catch (UnstableFingerprintInputException ignored) {
            return false;
        }
        for (ConfigFileStamp stamp : files) {
            if (stamp.size > MAX_CONFIG_HASH_BYTES) {
                output.put(
                        configKey(label, stamp.relative),
                        new EarlyConfigFile(
                                stamp.size,
                                stamp.modifiedMillis,
                                null
                        )
                );
                continue;
            }
            Path path = directory.resolve(stamp.relative);
            try {
                String digest = FingerprintDigests.digestFile(path);
                if (stamp.equals(inspectConfigFile(
                        directory,
                        path
                ))) {
                    output.put(
                            configKey(label, stamp.relative),
                            new EarlyConfigFile(
                                    stamp.size,
                                    stamp.modifiedMillis,
                                    digest
                            )
                    );
                } else {
                    requiredValidation.add(
                            new ConfigFingerprintMonitor.ChangedPath(
                                    label,
                                    stamp.relative
                            )
                    );
                }
            } catch (UnstableFingerprintInputException ignored) {
                requiredValidation.add(
                        new ConfigFingerprintMonitor.ChangedPath(
                                label,
                                stamp.relative
                        )
                );
            }
        }
        return true;
    }

    static StableConfigResult resolveStableConfigContents(
            EarlyConfigSnapshot earlyConfigs
    ) {
        ConfigFingerprintMonitor monitor = earlyConfigs.monitor;
        if (!earlyConfigs.complete || monitor == null) {
            if (monitor != null) {
                monitor.close();
            }
            return scanAllStableConfigContents(earlyConfigs);
        }

        Map<String, String> manifest =
                new TreeMap<>();
        for (Map.Entry<String, EarlyConfigFile> entry :
                earlyConfigs.files.entrySet()) {
            manifest.put(
                    entry.getKey(),
                    entry.getValue().manifestValue()
            );
        }

        TreeSet<String> changedDuringLaunch = new TreeSet<>();
        TreeSet<ConfigFingerprintMonitor.ChangedPath> pending =
                new TreeSet<>();
        TreeSet<ConfigFingerprintMonitor.ChangedPath> observed =
                new TreeSet<>();
        pending.addAll(earlyConfigs.requiredValidation);
        observed.addAll(earlyConfigs.requiredValidation);
        UnstableFingerprintInputException lastFailure = null;
        try {
            for (int attempt = 1;
                    attempt <= MAX_STABILITY_ATTEMPTS;
                    attempt++) {
                ConfigFingerprintMonitor.ChangeSet changes =
                        monitor.drain();
                if (changes.fullRescan()) {
                    throw new UnstableFingerprintInputException(
                            "Configuration filesystem events overflowed "
                                    + "or could not be tracked safely"
                    );
                }
                pending.addAll(changes.paths());
                observed.addAll(changes.paths());
                if (pending.isEmpty()) {
                    VHAccelerator.LOGGER.info(
                            "Validated stable client asset configuration "
                                    + "from filesystem changes (0 paths)"
                    );
                    return stableConfigResult(
                            manifest,
                            changedDuringLaunch,
                            earlyConfigs.missingDirectories
                    );
                }

                ConfigFingerprintMonitor.ChangeSet settled =
                        monitor.awaitQuietChanges(
                                CONFIG_EVENT_QUIET_MILLIS,
                                MAX_CONFIG_EVENT_WAIT_MILLIS
                        );
                if (settled.fullRescan()) {
                    throw new UnstableFingerprintInputException(
                            "Configuration filesystem events overflowed "
                                    + "or could not be tracked safely"
                    );
                }
                pending.addAll(settled.paths());
                observed.addAll(settled.paths());

                Map<String, String> candidate =
                        new TreeMap<>(manifest);
                TreeSet<String> candidateLaunchChanges =
                        new TreeSet<>(changedDuringLaunch);
                try {
                    for (ConfigFingerprintMonitor.ChangedPath changed :
                            pending) {
                        applyMonitoredConfigChange(
                                candidate,
                                candidateLaunchChanges,
                                earlyConfigs,
                                monitor,
                                changed
                        );
                    }
                } catch (UnstableFingerprintInputException failure) {
                    lastFailure = failure;
                    continue;
                }

                ConfigFingerprintMonitor.ChangeSet after =
                        monitor.awaitQuietChanges(
                                POST_VALIDATION_QUIET_MILLIS,
                                MAX_POST_VALIDATION_WAIT_MILLIS
                        );
                if (after.fullRescan()) {
                    throw new UnstableFingerprintInputException(
                            "Configuration filesystem events overflowed "
                                    + "or could not be tracked safely"
                    );
                }
                manifest = candidate;
                changedDuringLaunch = candidateLaunchChanges;
                pending.clear();
                pending.addAll(after.paths());
                observed.addAll(after.paths());
                if (pending.isEmpty()) {
                    VHAccelerator.LOGGER.info(
                            "Validated stable client asset configuration "
                                    + "from {} changed path(s)",
                            observed.size()
                    );
                    return stableConfigResult(
                            manifest,
                            changedDuringLaunch,
                            earlyConfigs.missingDirectories
                    );
                }
            }
        } catch (RuntimeException failure) {
            lastFailure = failure
                    instanceof UnstableFingerprintInputException unstable
                    ? unstable
                    : new UnstableFingerprintInputException(
                            "Configuration filesystem monitoring failed",
                            failure
                    );
        } finally {
            monitor.close();
        }

        VHAccelerator.LOGGER.info(
                "Falling back to a complete stable client asset config "
                        + "scan because change tracking was inconclusive",
                lastFailure
        );
        return scanAllStableConfigContents(earlyConfigs);
    }

    static void applyMonitoredConfigChange(
            Map<String, String> manifest,
            TreeSet<String> changedDuringLaunch,
            EarlyConfigSnapshot earlyConfigs,
            ConfigFingerprintMonitor monitor,
            ConfigFingerprintMonitor.ChangedPath changed
    ) {
        String key = configKey(changed.label(), changed.relative());
        if (isVolatileNonAssetConfig(changed.relative())) {
            manifest.remove(key);
            return;
        }

        Path root = monitor.root(changed.label());
        if (root == null) {
            throw new UnstableFingerprintInputException(
                    "A configuration change had no registered root"
            );
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path path = normalizedRoot.resolve(changed.relative())
                .normalize();
        if (!path.startsWith(normalizedRoot)) {
            throw new UnstableFingerprintInputException(
                    "A configuration change escaped its registered root"
            );
        }

        EarlyConfigFile early = earlyConfigs.files.get(key);
        if (!Files.isRegularFile(path)) {
            manifest.remove(key);
            if (early != null) {
                changedDuringLaunch.add(key);
            }
            return;
        }

        ConfigFileStamp stamp =
                inspectConfigFile(normalizedRoot, path);
        String value;
        if (stamp.size <= MAX_CONFIG_HASH_BYTES) {
            String digest = FingerprintDigests.digestFile(path);
            if (!stamp.equals(inspectConfigFile(
                    normalizedRoot,
                    path
            ))) {
                throw new UnstableFingerprintInputException(
                        "Asset-affecting configuration changed while "
                                + "it was read"
                );
            }
            value = "sha256:" + digest;
        } else {
            value = "metadata:"
                    + stamp.size
                    + ":"
                    + stamp.modifiedMillis;
        }
        manifest.put(key, value);
        if (early != null
                && !early.manifestValue().equals(value)) {
            changedDuringLaunch.add(key);
        }
    }

    static StableConfigResult stableConfigResult(
            Map<String, String> manifest,
            TreeSet<String> changedDuringLaunch,
            Set<String> missingDirectories
    ) {
        List<String> inputs = new ArrayList<>(manifest.size());
        Map<String, String> sorted = new TreeMap<>(manifest);
        for (String label :
                List.of("config", "default-config")) {
            if (missingDirectories.contains(label)) {
                inputs.add(label + "-directory-missing");
                continue;
            }
            String prefix = label + '\u0000';
            for (Map.Entry<String, String> entry :
                    sorted.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(prefix)
                        || key.length() == prefix.length()) {
                    continue;
                }
                String relative = key.substring(prefix.length());
                String value = entry.getValue();
                if (value.startsWith("sha256:")) {
                    inputs.add(
                            label
                                    + "="
                                    + relative
                                    + ":"
                                    + value.substring(
                                            "sha256:".length()
                                    )
                    );
                } else if (value.startsWith("metadata:")) {
                    inputs.add(
                            label
                                    + "="
                                    + relative
                                    + ":"
                                    + value.substring(
                                            "metadata:".length()
                                    )
                    );
                } else {
                    throw new UnstableFingerprintInputException(
                            "A configuration manifest value was malformed"
                    );
                }
            }
        }
        return new StableConfigResult(
                List.copyOf(inputs),
                Map.copyOf(manifest),
                new TreeSet<>(changedDuringLaunch)
        );
    }

    static StableConfigResult scanAllStableConfigContents(
            EarlyConfigSnapshot earlyConfigs
    ) {
        List<String> inputs = new ArrayList<>();
        Map<String, String> manifest = new HashMap<>();
        TreeSet<String> changedDuringLaunch = new TreeSet<>();
        appendStableConfigContents(
                inputs,
                FMLPaths.CONFIGDIR.get(),
                "config",
                earlyConfigs,
                manifest,
                changedDuringLaunch
        );
        appendStableConfigContents(
                inputs,
                FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                "default-config",
                earlyConfigs,
                manifest,
                changedDuringLaunch
        );
        return new StableConfigResult(
                List.copyOf(inputs),
                Map.copyOf(manifest),
                changedDuringLaunch
        );
    }

    static void appendStableConfigContents(
            List<String> inputs,
            Path directory,
            String label,
            EarlyConfigSnapshot earlyConfigs,
            Map<String, String> manifest,
            TreeSet<String> changedDuringLaunch
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add(label + "-directory-missing");
            return;
        }

        UnstableFingerprintInputException lastFailure = null;
        for (int attempt = 1;
                attempt <= MAX_STABILITY_ATTEMPTS;
                attempt++) {
            try {
                List<ConfigFileStamp> before =
                        snapshotConfigFiles(directory);
                List<String> stableInputs =
                        new ArrayList<>(before.size());
                Map<String, String> stableManifest =
                        new HashMap<>(before.size());
                TreeSet<String> stableLaunchChanges =
                        new TreeSet<>();
                for (ConfigFileStamp stamp : before) {
                    Path path = directory.resolve(stamp.relative);
                    String key = configKey(label, stamp.relative);
                    if (stamp.size <= MAX_CONFIG_HASH_BYTES) {
                        EarlyConfigFile early =
                                earlyConfigs.files.get(key);
                        String digest = early != null
                                && early.matches(stamp)
                                ? early.digest
                                : FingerprintDigests.digestFile(path);
                        if (!stamp.equals(inspectConfigFile(
                                directory,
                                path
                        ))) {
                            throw new UnstableFingerprintInputException(
                                    "Asset-affecting configuration changed "
                                            + "while it was read"
                            );
                        }
                        if (early != null
                                && early.digest != null
                                && !early.digest.equals(digest)) {
                            stableLaunchChanges.add(key);
                        }
                        stableManifest.put(key, "sha256:" + digest);
                        stableInputs.add(
                                label
                                        + "="
                                        + stamp.relative
                                        + ":"
                                        + digest
                        );
                    } else {
                        String metadata = "metadata:"
                                + stamp.size
                                + ":"
                                + stamp.modifiedMillis;
                        stableManifest.put(key, metadata);
                        stableInputs.add(
                                label
                                        + "="
                                        + stamp.relative
                                        + ":"
                                        + stamp.size
                                        + ":"
                                        + stamp.modifiedMillis
                        );
                    }
                }

                List<ConfigFileStamp> after =
                        snapshotConfigFiles(directory);
                if (!before.equals(after)) {
                    throw new UnstableFingerprintInputException(
                            "Asset-affecting configuration changed while "
                                    + "it was being fingerprinted"
                    );
                }
                inputs.addAll(stableInputs);
                manifest.putAll(stableManifest);
                changedDuringLaunch.addAll(stableLaunchChanges);
                return;
            } catch (UnstableFingerprintInputException failure) {
                lastFailure = failure;
            }
        }

        throw new UnstableFingerprintInputException(
                "Asset-affecting configuration did not stabilize after "
                        + MAX_STABILITY_ATTEMPTS
                        + " validation attempts",
                lastFailure
        );
    }

    static String configKey(
            String label,
            String relative
    ) {
        return label + '\u0000' + relative;
    }

    static ConfigFileStamp inspectConfigFile(
            Path directory,
            Path path
    ) {
        try {
            return new ConfigFileStamp(
                    PathNames.relative(directory, path),
                    Files.size(path),
                    Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException exception) {
            throw new UnstableFingerprintInputException(
                    "Could not inspect asset-affecting configuration",
                    exception
            );
        }
    }

    static List<ConfigFileStamp> snapshotConfigFiles(
            Path directory
    ) {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(directory)) {
            paths = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            PathNames.relative(directory, path)))
                    .toList();
        } catch (IOException exception) {
            throw new UnstableFingerprintInputException(
                    "Could not enumerate asset-affecting configuration",
                    exception
            );
        }

        List<ConfigFileStamp> stamps =
                new ArrayList<>(paths.size());
        for (Path path : paths) {
            String relative = PathNames.relative(directory, path);
            if (isVolatileNonAssetConfig(relative)) {
                continue;
            }
            stamps.add(inspectConfigFile(directory, path));
        }
        return List.copyOf(stamps);
    }

    /**
     * Excludes files that are rewritten with client session/UI state and
     * files whose settings cannot alter resolved model JSON supplied by
     * resource packs.
     *
     * <p>All other configuration remains fingerprinted. This avoids making
     * the cache globally insensitive to mod configuration while preventing
     * map, voice, shader, and renderer state from invalidating it on every
     * launch.</p>
     */
    static boolean isVolatileNonAssetConfig(String relative) {
        return AssetConfigPathPolicy.isVolatileNonAssetConfig(relative);
    }

    record ConfigFileStamp(
            String relative,
            long size,
            long modifiedMillis
    ) {
    }

    record EarlyConfigFile(
            long size,
            long modifiedMillis,
            String digest
    ) {
        private boolean matches(ConfigFileStamp current) {
            return digest != null
                    && size == current.size
                    && modifiedMillis == current.modifiedMillis;
        }

        private String manifestValue() {
            return digest != null
                    ? "sha256:" + digest
                    : "metadata:"
                            + size
                            + ":"
                            + modifiedMillis;
        }
    }

    record EarlyConfigSnapshot(
            Map<String, EarlyConfigFile> files,
            Set<String> missingDirectories,
            Set<ConfigFingerprintMonitor.ChangedPath>
                    requiredValidation,
            ConfigFingerprintMonitor monitor,
            boolean complete
    ) {
    }

    record StableConfigResult(
            List<String> fingerprintInputs,
            Map<String, String> manifest,
            TreeSet<String> changedDuringLaunch
    ) {
    }
}
