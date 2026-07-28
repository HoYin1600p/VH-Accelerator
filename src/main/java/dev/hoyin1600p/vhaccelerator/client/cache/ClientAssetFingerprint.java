package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

        List<String> configs = new ArrayList<>();
        appendStableConfigContents(
                configs,
                FMLPaths.CONFIGDIR.get(),
                "config",
                earlyConfigs
        );
        appendStableConfigContents(
                configs,
                gameDirectory.resolve("defaultconfigs"),
                "default-config",
                earlyConfigs
        );

        List<String> resourceFiles = new ArrayList<>();
        appendResourcePackMetadata(
                resourceFiles,
                gameDirectory.resolve("resourcepacks")
        );
        BaseFingerprint fingerprint = new BaseFingerprint(
                digestStrings(installation),
                digestStrings(configs),
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
        captureEarlyConfigDirectory(
                files,
                FMLPaths.CONFIGDIR.get(),
                "config"
        );
        captureEarlyConfigDirectory(
                files,
                FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                "default-config"
        );
        return new EarlyConfigSnapshot(Map.copyOf(files));
    }

    private static void captureEarlyConfigDirectory(
            Map<String, EarlyConfigFile> output,
            Path directory,
            String label
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        List<ConfigFileStamp> files;
        try {
            files = snapshotConfigFiles(directory);
        } catch (UnstableFingerprintInputException ignored) {
            return;
        }
        for (ConfigFileStamp stamp : files) {
            if (stamp.size > MAX_CONFIG_HASH_BYTES) {
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
                }
            } catch (UnstableFingerprintInputException ignored) {
                // The stable pass rereads files that moved during prewarm.
            }
        }
    }

    private static void appendStableConfigContents(
            List<String> inputs,
            Path directory,
            String label,
            EarlyConfigSnapshot earlyConfigs
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
                for (ConfigFileStamp stamp : before) {
                    Path path = directory.resolve(stamp.relative);
                    if (stamp.size <= MAX_CONFIG_HASH_BYTES) {
                        EarlyConfigFile early = earlyConfigs.files.get(
                                configKey(label, stamp.relative)
                        );
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
                        stableInputs.add(
                                label
                                        + "="
                                        + stamp.relative
                                        + ":"
                                        + digest
                        );
                    } else {
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
     * cannot alter the resolved model JSON supplied by resource packs.
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
                || normalized.equals("vaultlootbeams.json")
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
            return size == current.size
                    && modifiedMillis == current.modifiedMillis;
        }
    }

    private record EarlyConfigSnapshot(
            Map<String, EarlyConfigFile> files
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
