package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
    private static volatile CompletableFuture<StableConfigFingerprint.EarlyConfigSnapshot>
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
                        StableConfigFingerprint
                                ::captureEarlyConfigSnapshot,
                        SharedWorkers.io()
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
                        SharedWorkers.io()
                );
            }
        }
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
                    FingerprintDigests.digestStrings(activePacks)
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

    static BaseFingerprint buildBaseFingerprint(
            StableConfigFingerprint.EarlyConfigSnapshot earlyConfigs
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
        FileMetadataInputs.appendFlatMetadata(
                installation,
                gameDirectory.resolve("mods"),
                "mod-file"
        );

        StableConfigFingerprint.StableConfigResult stableConfigs =
                StableConfigFingerprint.resolveStableConfigContents(earlyConfigs);
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            ConfigChangeReport.reportConfigChanges(
                    earlyConfigs,
                    stableConfigs.manifest(),
                    stableConfigs.changedDuringLaunch()
            );
        }

        List<String> resourceFiles = new ArrayList<>();
        FileMetadataInputs.appendResourcePackMetadata(
                resourceFiles,
                gameDirectory.resolve("resourcepacks")
        );
        List<String> scripts = LocalScriptInputs.collect(gameDirectory);
        if (scripts == null) {
            // Never reuse caches against script resources that were not read.
            resourceFiles.add("script-inputs-unavailable=" + UUID.randomUUID());
        } else {
            resourceFiles.addAll(scripts);
        }
        BaseFingerprint fingerprint = new BaseFingerprint(
                FingerprintDigests.digestStrings(installation),
                FingerprintDigests.digestStrings(stableConfigs.fingerprintInputs()),
                FingerprintDigests.digestStrings(resourceFiles)
        );
        VHAccelerator.LOGGER.info(
                "Prepared stable client asset fingerprint in {} ms",
                (System.nanoTime() - started) / 1_000_000L
        );
        return fingerprint;
    }

    static String encode(
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

    static ParsedFingerprint parse(String encoded) {
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

    record BaseFingerprint(
            String installation,
            String configs,
            String resourceFiles
    ) {
    }

    record ParsedFingerprint(
            String installation,
            String configs,
            String resourceFiles,
            String activePacks
    ) {
    }
}
