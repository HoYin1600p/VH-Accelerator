package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Stores the resolved initial model JSON resource view as one validated,
 * sequentially-readable file. Parsed or baked model objects are never
 * persisted, so Forge custom geometry and dynamic models still execute their
 * normal loaders on every launch.
 */
public final class PersistentModelJsonCache {
    private static final int MAGIC = 0x5648414D;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENTRIES = 500_000;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024L * 1024L;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("client-assets");
    private static final Path CACHE_FILE =
            DIRECTORY.resolve("model-json-v1.bin.gz");
    private static final Executor WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator model cache writer"
                );
                thread.setDaemon(true);
                return thread;
            });

    private static CompletableFuture<CachedFile> preload;
    private static boolean preloadStarted;

    private PersistentModelJsonCache() {
    }

    public static synchronized void prewarm() {
        ClientAssetFingerprint.prewarm();
        if (preloadStarted) {
            return;
        }
        preloadStarted = true;
        preload = CompletableFuture.supplyAsync(
                PersistentModelJsonCache::read,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "VH Accelerator model cache reader"
                    );
                    thread.setDaemon(true);
                    thread.start();
                }
        );
    }

    public static Session prepare(ResourceManager resourceManager) {
        if (!enabled() || LaunchTimer.isFinished()) {
            return null;
        }

        prewarm();
        String fingerprint =
                ClientAssetFingerprint.current(resourceManager);
        if (fingerprint == null) {
            return null;
        }

        CachedFile cached;
        synchronized (PersistentModelJsonCache.class) {
            cached = preload == null ? null : preload.join();
            preload = CompletableFuture.completedFuture(null);
        }
        if (cached != null
                && cached.fingerprint.equals(fingerprint)) {
            VHAccelerator.LOGGER.info(
                    "Restored {} model JSON resources from the persistent "
                            + "client asset cache",
                    cached.models.size()
            );
            return new Session(
                    fingerprint,
                    cached.models,
                    true,
                    true
            );
        }

        return capture(resourceManager, fingerprint);
    }

    public static void finish(Session session) {
        if (session == null) {
            return;
        }
        if (!session.valid.get()) {
            if (session.restored) {
                CompletableFuture.runAsync(
                        PersistentModelJsonCache::discardInvalidFile,
                        WRITER
                );
            }
            return;
        }
        if (session.restored || !session.complete) {
            return;
        }

        CachedFile cached = new CachedFile(
                session.fingerprint,
                session.models
        );
        CompletableFuture.runAsync(() -> write(cached), WRITER);
    }

    private static Session capture(
            ResourceManager resourceManager,
            String fingerprint
    ) {
        long started = System.nanoTime();
        Collection<ResourceLocation> listed;
        try {
            listed = resourceManager.listResources(
                    "models",
                    path -> path.endsWith(".json")
            );
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not enumerate model JSON resources for the "
                            + "persistent client asset cache",
                    failure
            );
            return null;
        }
        if (listed.size() > MAX_ENTRIES) {
            VHAccelerator.LOGGER.warn(
                    "Found {} model JSON resources, above the cache safety "
                            + "limit of {}; using normal resource loading",
                    listed.size(),
                    MAX_ENTRIES
            );
            return null;
        }

        List<ResourceLocation> locations =
                new ArrayList<>(listed);
        Map<ResourceLocation, String> models =
                new ConcurrentHashMap<>();
        AtomicBoolean complete = new AtomicBoolean(true);
        AtomicLong totalBytes = new AtomicLong();
        runBatched(locations, location -> {
            try (Resource resource =
                         resourceManager.getResource(location)) {
                byte[] bytes =
                        resource.getInputStream().readAllBytes();
                if (bytes.length > MAX_ENTRY_BYTES) {
                    complete.set(false);
                    VHAccelerator.LOGGER.warn(
                            "Model JSON {} is too large for persistent "
                                    + "caching; it will use normal loading",
                            location
                    );
                    return;
                }
                if (totalBytes.addAndGet(bytes.length)
                        > MAX_TOTAL_BYTES) {
                    complete.set(false);
                    return;
                }
                models.put(
                        location,
                        new String(bytes, StandardCharsets.UTF_8)
                );
            } catch (IOException | RuntimeException failure) {
                complete.set(false);
                VHAccelerator.LOGGER.debug(
                        "Could not capture model JSON {} for persistent "
                                + "caching",
                        location,
                        failure
                );
            }
        });

        Map<ResourceLocation, String> stable =
                Map.copyOf(models);
        VHAccelerator.LOGGER.info(
                "Captured {} of {} model JSON resources for the client "
                        + "asset cache in {} ms{}",
                stable.size(),
                locations.size(),
                (System.nanoTime() - started) / 1_000_000L,
                complete.get() ? "" : " (cache file will not be updated)"
        );
        return new Session(
                fingerprint,
                stable,
                false,
                complete.get()
        );
    }

    private static CachedFile read() {
        if (!Files.isRegularFile(CACHE_FILE)) {
            return null;
        }
        long started = System.nanoTime();
        try (DataInputStream input = new DataInputStream(
                new GZIPInputStream(
                        new BufferedInputStream(
                                Files.newInputStream(CACHE_FILE)
                        )
                )
        )) {
            if (input.readInt() != MAGIC
                    || input.readInt() != FORMAT_VERSION) {
                return null;
            }
            String fingerprint = input.readUTF();
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException(
                        "Invalid model cache entry count " + count
                );
            }

            Map<ResourceLocation, String> models =
                    new java.util.HashMap<>(Math.max(16, count * 2));
            long totalBytes = 0L;
            for (int index = 0; index < count; index++) {
                ResourceLocation location =
                        new ResourceLocation(input.readUTF());
                int length = input.readInt();
                long expectedCrc = input.readLong();
                if (length < 0 || length > MAX_ENTRY_BYTES) {
                    throw new IOException(
                            "Invalid model cache entry size " + length
                    );
                }
                totalBytes += length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IOException(
                            "Model cache exceeds its decompressed size limit"
                    );
                }
                byte[] bytes = input.readNBytes(length);
                if (bytes.length != length) {
                    throw new EOFException(
                            "Truncated model cache entry"
                    );
                }
                CRC32 crc = new CRC32();
                crc.update(bytes);
                if (crc.getValue() != expectedCrc) {
                    throw new IOException(
                            "Model cache entry checksum mismatch"
                    );
                }
                models.put(
                        location,
                        new String(bytes, StandardCharsets.UTF_8)
                );
            }
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent model JSON resources in {} ms",
                    models.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return new CachedFile(
                    fingerprint,
                    Map.copyOf(models)
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not read the persistent model JSON cache; "
                            + "the current resources will be loaded normally",
                    failure
            );
            return null;
        }
    }

    private static void write(CachedFile cached) {
        Path temporary = CACHE_FILE.resolveSibling(
                CACHE_FILE.getFileName() + ".tmp"
        );
        try {
            Files.createDirectories(DIRECTORY);
            List<Map.Entry<ResourceLocation, String>> entries =
                    new ArrayList<>(cached.models.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            try (DataOutputStream output = new DataOutputStream(
                    new GZIPOutputStream(
                            new BufferedOutputStream(
                                    Files.newOutputStream(temporary)
                            )
                    )
            )) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(cached.fingerprint);
                output.writeInt(entries.size());
                for (Map.Entry<ResourceLocation, String> entry :
                        entries) {
                    byte[] bytes = entry.getValue().getBytes(
                            StandardCharsets.UTF_8
                    );
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    output.writeUTF(entry.getKey().toString());
                    output.writeInt(bytes.length);
                    output.writeLong(crc.getValue());
                    output.write(bytes);
                }
            }
            moveAtomically(temporary, CACHE_FILE);
            VHAccelerator.LOGGER.info(
                    "Saved {} model JSON resources to the persistent "
                            + "client asset cache",
                    entries.size()
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the persistent model JSON cache",
                    failure
            );
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The incomplete temporary file is harmless.
            }
        }
    }

    private static void discardInvalidFile() {
        try {
            Files.deleteIfExists(CACHE_FILE);
            VHAccelerator.LOGGER.warn(
                    "Discarded a persistent model JSON cache entry that "
                            + "could not be parsed"
            );
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not discard an invalid persistent model cache",
                    exception
            );
        }
    }

    private static void moveAtomically(Path source, Path target)
            throws IOException {
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

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .persistentModelJsonCache
                        .get();
    }

    private static <T> void runBatched(
            List<T> values,
            java.util.function.Consumer<T> action
    ) {
        if (values.isEmpty()) {
            return;
        }
        int parallelism = Math.max(1, Math.min(
                Runtime.getRuntime().availableProcessors(),
                values.size()
        ));
        int batchSize = Math.max(
                1,
                (values.size() + parallelism - 1) / parallelism
        );
        List<CompletableFuture<Void>> tasks =
                new ArrayList<>(parallelism);
        for (int start = 0; start < values.size(); start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, values.size());
            tasks.add(CompletableFuture.runAsync(() -> {
                for (int index = from; index < to; index++) {
                    action.accept(values.get(index));
                }
            }, Util.backgroundExecutor()));
        }
        CompletableFuture.allOf(
                tasks.toArray(CompletableFuture[]::new)
        ).join();
    }

    public static final class Session {
        private final String fingerprint;
        private final Map<ResourceLocation, String> models;
        private final boolean restored;
        private final boolean complete;
        private final AtomicBoolean valid = new AtomicBoolean(true);

        private Session(
                String fingerprint,
                Map<ResourceLocation, String> models,
                boolean restored,
                boolean complete
        ) {
            this.fingerprint = fingerprint;
            this.models = models;
            this.restored = restored;
            this.complete = complete;
        }

        public Map<ResourceLocation, String> models() {
            return models;
        }

        public void invalidate() {
            valid.set(false);
        }
    }

    private record CachedFile(
            String fingerprint,
            Map<ResourceLocation, String> models
    ) {
    }
}
