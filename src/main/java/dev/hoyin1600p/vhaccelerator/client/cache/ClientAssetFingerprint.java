package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

/**
 * Identifies inputs that can affect the initial client model resource view.
 */
public final class ClientAssetFingerprint {
    private static final int SCHEMA_VERSION = 5;
    private static final long MAX_CONFIG_HASH_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_STABILITY_ATTEMPTS = 3;
    private static final long CONFIG_EVENT_QUIET_MILLIS = 30L;
    private static final long MAX_CONFIG_EVENT_WAIT_MILLIS = 750L;
    private static final long POST_VALIDATION_QUIET_MILLIS = 10L;
    private static final long MAX_POST_VALIDATION_WAIT_MILLIS = 100L;
    private static final int MAX_MANIFEST_ENTRIES = 100_000;
    private static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;
    private static final int CHANGE_REPORT_LIMIT = 24;
    private static final Path MANIFEST_DIRECTORY =
            FMLPaths.GAMEDIR.get()
                    .resolve("cache")
                    .resolve("vhaccelerator")
                    .resolve("client-assets");
    private static final Path CONFIG_MANIFEST =
            MANIFEST_DIRECTORY.resolve(
                    "config-fingerprint-manifest-v1.txt"
            );
    private static final Executor MANIFEST_WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator config manifest writer"
                );
                thread.setDaemon(true);
                return thread;
            });
    private static volatile CompletableFuture<EarlyConfigSnapshot>
            earlyConfigSnapshot;
    private static volatile CompletableFuture<BaseFingerprint>
            baseFingerprint;

    private ClientAssetFingerprint() {
    }

    /**
     * Hashes the launch-start config view off the critical model-loading path.
     * Files rewritten later by Forge are detected by their metadata and read
     * again after config loading; unchanged files retain this early digest.
     */
    public static void prewarm() {
        if (earlyConfigSnapshot != null) {
            return;
        }
        synchronized (ClientAssetFingerprint.class) {
            if (earlyConfigSnapshot == null) {
                earlyConfigSnapshot = CompletableFuture.supplyAsync(
                        ClientAssetFingerprint
                                ::captureEarlyConfigSnapshot,
                        runnable -> startDaemon(
                                runnable,
                                "VH Accelerator early config fingerprint"
                        )
                );
            }
        }
    }

    private static void startStableScan() {
        prewarm();
        if (baseFingerprint != null) {
            return;
        }
        synchronized (ClientAssetFingerprint.class) {
            if (baseFingerprint == null) {
                baseFingerprint = earlyConfigSnapshot.thenApplyAsync(
                        ClientAssetFingerprint::buildBaseFingerprint,
                        runnable -> {
                            startDaemon(
                                    runnable,
                                    "VH Accelerator asset fingerprint"
                            );
                        }
                );
            }
        }
    }

    private static void startDaemon(
            Runnable runnable,
            String name
    ) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
    }

    public static String current(ResourceManager resourceManager) {
        startStableScan();
        try {
            BaseFingerprint base = baseFingerprint.join();
            List<String> activePacks = new ArrayList<>();
            int[] index = {0};
            try (Stream<PackResources> packs =
                         resourceManager.listPacks()) {
                packs.forEachOrdered(pack -> activePacks.add(
                        "pack="
                                + index[0]++
                                + ":"
                                + pack.getClass().getName()
                                + ":"
                                + pack.getName()
                ));
            }
            return encode(
                    base,
                    digestStrings(activePacks)
            );
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not fingerprint the initial client asset packs; "
                            + "the persistent model cache will stay off",
                    failure
            );
            return null;
        }
    }

    public static void reportMismatch(
            String cacheName,
            String cachedFingerprint,
            String currentFingerprint
    ) {
        if (cachedFingerprint == null
                || currentFingerprint == null
                || cachedFingerprint.equals(currentFingerprint)) {
            return;
        }

        ParsedFingerprint cached = parse(cachedFingerprint);
        ParsedFingerprint current = parse(currentFingerprint);
        if (cached == null || current == null) {
            VHAccelerator.LOGGER.info(
                    "{} cache fingerprint used an older schema; "
                            + "rebuilding it once",
                    cacheName
            );
            return;
        }

        List<String> changed = new ArrayList<>();
        if (!cached.installation.equals(current.installation)) {
            changed.add("installed mods");
        }
        if (!cached.configs.equals(current.configs)) {
            changed.add("asset-affecting configuration");
        }
        if (!cached.resourceFiles.equals(current.resourceFiles)) {
            changed.add("resource-pack files");
        }
        if (!cached.activePacks.equals(current.activePacks)) {
            changed.add("active resource-pack order");
        }
        VHAccelerator.LOGGER.info(
                "{} cache fingerprint changed ({}); rebuilding safely",
                cacheName,
                changed.isEmpty()
                        ? "fingerprint format"
                        : String.join(", ", changed)
        );
    }

    private static BaseFingerprint buildBaseFingerprint(
            EarlyConfigSnapshot earlyConfigs
    ) {
        long started = System.nanoTime();
        List<String> installation = new ArrayList<>();
        installation.add(
                "minecraft="
                        + SharedConstants.getCurrentVersion().getName()
        );
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .map(mod -> "mod="
                        + mod.getModId()
                        + "@"
                        + mod.getVersion())
                .forEach(installation::add);

        Path gameDirectory = FMLPaths.GAMEDIR.get();
        appendFlatMetadata(
                installation,
                gameDirectory.resolve("mods"),
                "mod-file"
        );

        StableConfigResult stableConfigs =
                resolveStableConfigContents(earlyConfigs);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            reportConfigChanges(
                    earlyConfigs,
                    stableConfigs.manifest,
                    stableConfigs.changedDuringLaunch
            );
        }

        List<String> resourceFiles = new ArrayList<>();
        appendResourcePackMetadata(
                resourceFiles,
                gameDirectory.resolve("resourcepacks")
        );
        BaseFingerprint fingerprint = new BaseFingerprint(
                digestStrings(installation),
                digestStrings(stableConfigs.fingerprintInputs),
                digestStrings(resourceFiles)
        );
        VHAccelerator.LOGGER.info(
                "Prepared stable client asset fingerprint in {} ms",
                (System.nanoTime() - started) / 1_000_000L
        );
        return fingerprint;
    }

    private static String encode(
            BaseFingerprint base,
            String activePacks
    ) {
        return "v"
                + SCHEMA_VERSION
                + "|"
                + base.installation
                + "|"
                + base.configs
                + "|"
                + base.resourceFiles
                + "|"
                + activePacks;
    }

    private static ParsedFingerprint parse(String encoded) {
        String[] components = encoded.split("\\|", -1);
        if (components.length != 5
                || !components[0].equals("v" + SCHEMA_VERSION)) {
            return null;
        }
        return new ParsedFingerprint(
                components[1],
                components[2],
                components[3],
                components[4]
        );
    }

    private static void appendFlatMetadata(
            List<String> inputs,
            Path directory,
            String label
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add(label + "-directory-missing");
            return;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".jar"))
                    .sorted(Comparator.comparing(path ->
                            path.getFileName().toString()))
                    .forEach(path -> appendMetadata(
                            inputs,
                            directory,
                            path,
                            label
                    ));
        } catch (IOException exception) {
            inputs.add(label + "-directory-read-failed");
        }
    }

    private static EarlyConfigSnapshot captureEarlyConfigSnapshot() {
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

    private static boolean captureEarlyConfigDirectory(
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
                String digest = digestFile(path);
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

    private static StableConfigResult resolveStableConfigContents(
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

    private static void applyMonitoredConfigChange(
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
            String digest = digestFile(path);
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

    private static StableConfigResult stableConfigResult(
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

    private static StableConfigResult scanAllStableConfigContents(
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

    private static void appendStableConfigContents(
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
                                : digestFile(path);
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

    private static void reportConfigChanges(
            EarlyConfigSnapshot earlyConfigs,
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

    private static void reportChangeDetails(
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

    private static Map<String, String> readConfigManifest() {
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

    private static void writeConfigManifest(
            Map<String, String> manifest
    ) {
        if (manifest.size() > MAX_MANIFEST_ENTRIES) {
            return;
        }
        Path temporary = CONFIG_MANIFEST.resolveSibling(
                CONFIG_MANIFEST.getFileName() + ".tmp"
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
            moveAtomically(temporary, CONFIG_MANIFEST);
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

    private static void moveAtomically(
            Path source,
            Path target
    ) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static String displayConfigKey(String key) {
        return key.replace('\u0000', '/');
    }

    private static String configKey(
            String label,
            String relative
    ) {
        return label + '\u0000' + relative;
    }

    private static ConfigFileStamp inspectConfigFile(
            Path directory,
            Path path
    ) {
        try {
            return new ConfigFileStamp(
                    normalizeRelative(directory, path),
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

    private static List<ConfigFileStamp> snapshotConfigFiles(
            Path directory
    ) {
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(directory)) {
            paths = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            normalizeRelative(directory, path)))
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
            String relative = normalizeRelative(directory, path);
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
    private static boolean isVolatileNonAssetConfig(String relative) {
        String normalized =
                relative.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("distanthorizons.toml")
                || normalized.equals("embeddium-options.json")
                || normalized.equals("forge-client.toml")
                || normalized.equals("oculus.properties")
                || normalized.equals("powah.json5")
                || normalized.equals("vaultlootbeams.json")
                || normalized.equals(
                        "modernstartupqol/startup_times.json"
                )
                || normalized.startsWith("xaerominimap")
                || normalized.startsWith("xaeroworldmap")
                || normalized.startsWith("voicechat/")
                || normalized.startsWith("konkrete/locals/");
    }

    private static void appendResourcePackMetadata(
            List<String> inputs,
            Path directory
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add("resourcepack-directory-missing");
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            normalizeRelative(directory, path)))
                    .forEach(path -> appendMetadata(
                            inputs,
                            directory,
                            path,
                            "resourcepack"
                    ));
        } catch (IOException exception) {
            inputs.add("resourcepack-directory-read-failed");
        }
    }

    private static void appendMetadata(
            List<String> inputs,
            Path root,
            Path path,
            String label
    ) {
        try {
            inputs.add(
                    label
                            + "="
                            + normalizeRelative(root, path)
                            + ":"
                            + Files.size(path)
                            + ":"
                            + Files.getLastModifiedTime(path).toMillis()
            );
        } catch (IOException exception) {
            inputs.add(
                    label
                            + "-read-failed="
                            + normalizeRelative(root, path)
            );
        }
    }

    private static String normalizeRelative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static String digestFile(Path path) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(path)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(
                    digest.digest()
            );
        } catch (IOException exception) {
            throw new UnstableFingerprintInputException(
                    "Could not read asset-affecting configuration",
                    exception
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private static String digestStrings(List<String> values) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] encoded =
                        value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (encoded.length >>> 24));
                digest.update((byte) (encoded.length >>> 16));
                digest.update((byte) (encoded.length >>> 8));
                digest.update((byte) encoded.length);
                digest.update(encoded);
            }
            return java.util.HexFormat.of().formatHex(
                    digest.digest()
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private record BaseFingerprint(
            String installation,
            String configs,
            String resourceFiles
    ) {
    }

    private record ParsedFingerprint(
            String installation,
            String configs,
            String resourceFiles,
            String activePacks
    ) {
    }

    private record ConfigFileStamp(
            String relative,
            long size,
            long modifiedMillis
    ) {
    }

    private record EarlyConfigFile(
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

    private record EarlyConfigSnapshot(
            Map<String, EarlyConfigFile> files,
            Set<String> missingDirectories,
            Set<ConfigFingerprintMonitor.ChangedPath>
                    requiredValidation,
            ConfigFingerprintMonitor monitor,
            boolean complete
    ) {
    }

    private record StableConfigResult(
            List<String> fingerprintInputs,
            Map<String, String> manifest,
            TreeSet<String> changedDuringLaunch
    ) {
    }

    private static final class UnstableFingerprintInputException
            extends RuntimeException {
        private UnstableFingerprintInputException(String message) {
            super(message);
        }

        private UnstableFingerprintInputException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}
