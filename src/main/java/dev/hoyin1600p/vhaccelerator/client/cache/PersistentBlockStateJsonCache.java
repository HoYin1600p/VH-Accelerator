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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists only ordered raw blockstate resource bytes. Definitions are parsed
 * fresh against the active block registry on every launch.
 */
public final class PersistentBlockStateJsonCache {
    private static final int MAGIC = 0x56484142;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_LOCATIONS = 100_000;
    private static final int MAX_STACK_DEPTH = 128;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES =
            256L * 1024L * 1024L;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("client-assets");
    private static final Path CACHE_FILE =
            DIRECTORY.resolve("blockstate-json-v1.bin.gz");
    private static final Executor WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator blockstate cache writer"
                );
                thread.setDaemon(true);
                return thread;
            });

    private static CompletableFuture<CachedFile> preload;
    private static boolean preloadStarted;

    private PersistentBlockStateJsonCache() {
    }

    public static synchronized void prewarm() {
        ClientAssetFingerprint.prewarm();
        if (preloadStarted) {
            return;
        }
        preloadStarted = true;
        preload = CompletableFuture.supplyAsync(
                PersistentBlockStateJsonCache::read,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "VH Accelerator blockstate cache reader"
                    );
                    thread.setDaemon(true);
                    thread.start();
                }
        );
    }

    @Nullable
    public static Session begin(
            net.minecraft.server.packs.resources.ResourceManager
                    resourceManager
    ) {
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
        synchronized (PersistentBlockStateJsonCache.class) {
            cached = preload == null ? null : preload.join();
            preload = CompletableFuture.completedFuture(null);
        }
        if (cached != null
                && cached.fingerprint.equals(fingerprint)) {
            VHAccelerator.LOGGER.info(
                    "Restored {} blockstate resource stacks from the "
                            + "persistent client asset cache",
                    cached.resources.size()
            );
            return new Session(
                    fingerprint,
                    cached.resources,
                    true
            );
        }
        return new Session(
                fingerprint,
                new ConcurrentHashMap<>(),
                false
        );
    }

    public static void finish(@Nullable Session session) {
        if (session == null
                || session.restored
                || !session.complete.get()) {
            return;
        }
        CachedFile cached = new CachedFile(
                session.fingerprint,
                Map.copyOf(session.resources)
        );
        CompletableFuture.runAsync(
                () -> write(cached),
                WRITER
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
            int locationCount = input.readInt();
            if (locationCount < 0
                    || locationCount > MAX_LOCATIONS) {
                throw new IOException(
                        "Invalid blockstate cache location count "
                                + locationCount
                );
            }

            long totalBytes = 0L;
            Map<ResourceLocation, List<RawResource>> resources =
                    new java.util.HashMap<>(
                            Math.max(16, locationCount * 2)
                    );
            for (int locationIndex = 0;
                 locationIndex < locationCount;
                 locationIndex++) {
                ResourceLocation location =
                        ResourceLocation.tryParse(input.readUTF());
                if (location == null) {
                    throw new IOException(
                            "Invalid cached blockstate location"
                    );
                }
                int stackDepth = input.readInt();
                if (stackDepth < 0
                        || stackDepth > MAX_STACK_DEPTH) {
                    throw new IOException(
                            "Invalid blockstate resource stack depth "
                                    + stackDepth
                    );
                }
                List<RawResource> stack =
                        new ArrayList<>(stackDepth);
                for (int stackIndex = 0;
                     stackIndex < stackDepth;
                     stackIndex++) {
                    String sourceName = input.readUTF();
                    int length = input.readInt();
                    long expectedCrc = input.readLong();
                    if (length < 0 || length > MAX_ENTRY_BYTES) {
                        throw new IOException(
                                "Invalid blockstate cache entry size "
                                        + length
                        );
                    }
                    totalBytes += length;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new IOException(
                                "Blockstate cache exceeds its "
                                        + "decompressed size limit"
                        );
                    }
                    byte[] bytes = input.readNBytes(length);
                    if (bytes.length != length) {
                        throw new EOFException(
                                "Truncated blockstate cache entry"
                        );
                    }
                    CRC32 crc = new CRC32();
                    crc.update(bytes);
                    if (crc.getValue() != expectedCrc) {
                        throw new IOException(
                                "Blockstate cache entry checksum mismatch"
                        );
                    }
                    stack.add(new RawResource(
                            sourceName,
                            bytes
                    ));
                }
                resources.put(location, List.copyOf(stack));
            }
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent blockstate resource "
                            + "stacks in {} ms",
                    resources.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return new CachedFile(
                    fingerprint,
                    Map.copyOf(resources)
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not read the persistent blockstate cache; "
                            + "resources will be loaded normally",
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
            List<Map.Entry<ResourceLocation, List<RawResource>>>
                    entries =
                    new ArrayList<>(cached.resources.entrySet());
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
                for (Map.Entry<
                        ResourceLocation,
                        List<RawResource>> entry : entries) {
                    output.writeUTF(entry.getKey().toString());
                    output.writeInt(entry.getValue().size());
                    for (RawResource resource : entry.getValue()) {
                        CRC32 crc = new CRC32();
                        crc.update(resource.bytes);
                        output.writeUTF(resource.sourceName);
                        output.writeInt(resource.bytes.length);
                        output.writeLong(crc.getValue());
                        output.write(resource.bytes);
                    }
                }
            }
            moveAtomically(temporary, CACHE_FILE);
            VHAccelerator.LOGGER.info(
                    "Saved {} blockstate resource stacks to the "
                            + "persistent client asset cache",
                    entries.size()
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the persistent blockstate cache",
                    failure
            );
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The incomplete temporary file is harmless.
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

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .persistentBlockStateJsonCache
                        .get();
    }

    public record RawResource(
            String sourceName,
            byte[] bytes
    ) {
    }

    public static final class Session {
        private final String fingerprint;
        private final Map<
                ResourceLocation,
                List<RawResource>> resources;
        private final boolean restored;
        private final AtomicBoolean complete =
                new AtomicBoolean(true);
        private final AtomicLong totalBytes = new AtomicLong();

        private Session(
                String fingerprint,
                Map<ResourceLocation, List<RawResource>> resources,
                boolean restored
        ) {
            this.fingerprint = fingerprint;
            this.resources = resources;
            this.restored = restored;
        }

        @Nullable
        public List<RawResource> restored(
                ResourceLocation location
        ) {
            return restored ? resources.get(location) : null;
        }

        public void record(
                ResourceLocation location,
                List<RawResource> stack
        ) {
            if (restored) {
                return;
            }
            if (stack.size() > MAX_STACK_DEPTH
                    || resources.size() >= MAX_LOCATIONS) {
                complete.set(false);
                return;
            }
            long stackBytes = 0L;
            for (RawResource resource : stack) {
                if (resource.bytes.length > MAX_ENTRY_BYTES) {
                    complete.set(false);
                    return;
                }
                stackBytes += resource.bytes.length;
            }
            if (totalBytes.addAndGet(stackBytes)
                    > MAX_TOTAL_BYTES) {
                complete.set(false);
                return;
            }
            resources.put(location, List.copyOf(stack));
        }

        public void markIncomplete() {
            complete.set(false);
        }
    }

    private record CachedFile(
            String fingerprint,
            Map<ResourceLocation, List<RawResource>> resources
    ) {
    }
}
