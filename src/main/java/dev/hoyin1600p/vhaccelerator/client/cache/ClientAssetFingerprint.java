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
import java.util.List;
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
    private static final int SCHEMA_VERSION = 3;
    private static final long MAX_CONFIG_HASH_BYTES = 32L * 1024L * 1024L;
    private static volatile CompletableFuture<BaseFingerprint>
            baseFingerprint;

    private ClientAssetFingerprint() {
    }

    /**
     * Starts the filesystem portion only after Forge has constructed the
     * initial resource reload. Starting this during mod construction can
     * snapshot configuration files before Forge finishes creating and
     * normalizing them, causing a false cache miss on every launch.
     */
    public static void prepareStable() {
        if (baseFingerprint != null) {
            return;
        }
        synchronized (ClientAssetFingerprint.class) {
            if (baseFingerprint == null) {
                baseFingerprint = CompletableFuture.supplyAsync(
                        ClientAssetFingerprint::buildBaseFingerprint,
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "VH Accelerator asset fingerprint"
                            );
                            thread.setDaemon(true);
                            thread.start();
                        }
                );
            }
        }
    }

    public static String current(ResourceManager resourceManager) {
        prepareStable();
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

    private static BaseFingerprint buildBaseFingerprint() {
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
        appendConfigContents(
                configs,
                FMLPaths.CONFIGDIR.get(),
                "config"
        );
        appendConfigContents(
                configs,
                gameDirectory.resolve("defaultconfigs"),
                "default-config"
        );

        List<String> resourceFiles = new ArrayList<>();
        appendResourcePackMetadata(
                resourceFiles,
                gameDirectory.resolve("resourcepacks")
        );
        return new BaseFingerprint(
                digestStrings(installation),
                digestStrings(configs),
                digestStrings(resourceFiles)
        );
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

    private static void appendConfigContents(
            List<String> inputs,
            Path directory,
            String label
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add(label + "-directory-missing");
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            normalizeRelative(directory, path)))
                    .forEach(path -> {
                        try {
                            long size = Files.size(path);
                            String relative =
                                    normalizeRelative(directory, path);
                            if (isVolatileNonAssetConfig(relative)) {
                                return;
                            }
                            if (size <= MAX_CONFIG_HASH_BYTES) {
                                inputs.add(
                                        label
                                                + "="
                                                + relative
                                                + ":"
                                                + digestFile(path)
                                );
                            } else {
                                appendMetadata(
                                        inputs,
                                        directory,
                                        path,
                                        label
                                );
                            }
                        } catch (IOException exception) {
                            inputs.add(
                                    label
                                            + "-read-failed="
                                            + normalizeRelative(
                                            directory,
                                            path
                                    )
                            );
                        }
                    });
        } catch (IOException exception) {
            inputs.add(label + "-directory-read-failed");
        }
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
            return "read-failed";
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
}
