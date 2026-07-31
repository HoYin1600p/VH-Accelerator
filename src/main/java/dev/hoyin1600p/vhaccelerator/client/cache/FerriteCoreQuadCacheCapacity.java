package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Learns the per-pack size of FerriteCore's temporary baked-quad
 * deduplication table. Only table capacity is persisted; model data never
 * leaves FerriteCore's normal launch-local cache.
 */
public final class FerriteCoreQuadCacheCapacity {
    private static final int MAGIC = 0x56484146;
    private static final int FORMAT_VERSION = 1;
    private static final int MIN_EXPECTED_ENTRIES = 1_024;
    private static final int MAX_EXPECTED_ENTRIES = 16_000_000;
    private static final int MAX_RECORDED_ENTRIES = 100_000_000;
    private static final String DEDUPLICATOR =
            "malte0811.ferritecore.impl.Deduplicator";
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("client-assets");
    private static final Path CACHE_FILE =
            DIRECTORY.resolve("ferritecore-quad-capacity-v1.bin");
    private static final Executor WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator FerriteCore capacity writer"
                );
                thread.setDaemon(true);
                return thread;
            });

    private static CompletableFuture<CachedCapacity> preload;
    private static boolean preloadStarted;
    private static volatile int currentTopLevelEstimate;
    private static boolean reflectionFailureReported;

    private FerriteCoreQuadCacheCapacity() {
    }

    public static synchronized void prewarm() {
        if (!enabled() || preloadStarted) {
            return;
        }
        preloadStarted = true;
        preload = CompletableFuture.supplyAsync(
                FerriteCoreQuadCacheCapacity::read,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "VH Accelerator FerriteCore capacity reader"
                    );
                    thread.setDaemon(true);
                    thread.start();
                }
        );
    }

    public static void prepare(int topLevelEstimate) {
        currentTopLevelEstimate = topLevelEstimate;
        if (!enabled()) {
            return;
        }

        prewarm();
        CachedCapacity cached;
        synchronized (FerriteCoreQuadCacheCapacity.class) {
            cached = preload == null ? null : preload.join();
            preload = CompletableFuture.completedFuture(cached);
        }
        if (cached == null
                || !cached.ferriteVersion.equals(ferriteVersion())
                || cached.topLevelEstimate <= 0
                || cached.quadCount < MIN_EXPECTED_ENTRIES) {
            return;
        }

        long scaled = divideRoundUp(
                (long) cached.quadCount * topLevelEstimate,
                cached.topLevelEstimate
        );
        long headroom = Math.max(65_536L, scaled / 50L);
        int expectedEntries = (int) Math.min(
                MAX_EXPECTED_ENTRIES,
                scaled + headroom
        );
        if (expectedEntries < MIN_EXPECTED_ENTRIES) {
            return;
        }

        try {
            Object cache = quadCache();
            Method ensureCapacity = cache.getClass()
                    .getDeclaredMethod("ensureCapacity", int.class);
            ensureCapacity.setAccessible(true);
            synchronized (cache) {
                if (!((Map<?, ?>) cache).isEmpty()) {
                    return;
                }
                ensureCapacity.invoke(cache, expectedEntries);
            }
            VHAccelerator.LOGGER.info(
                    "Pre-sized FerriteCore's baked-quad deduplication "
                            + "table for {} learned entries",
                    expectedEntries
            );
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            reportReflectionFailure(
                    "Could not pre-size FerriteCore's baked-quad "
                            + "deduplication table; FerriteCore will use "
                            + "its normal growth path",
                    failure
            );
        }
    }

    public static void record() {
        if (!enabled() || currentTopLevelEstimate <= 0) {
            return;
        }

        try {
            Object cache = quadCache();
            int quadCount;
            synchronized (cache) {
                quadCount = ((Map<?, ?>) cache).size();
            }
            if (quadCount < MIN_EXPECTED_ENTRIES
                    || quadCount > MAX_RECORDED_ENTRIES) {
                return;
            }
            CachedCapacity learned = new CachedCapacity(
                    ferriteVersion(),
                    currentTopLevelEstimate,
                    quadCount
            );
            CompletableFuture.runAsync(
                    () -> write(learned),
                    WRITER
            );
            VHAccelerator.LOGGER.info(
                    "Learned FerriteCore baked-quad capacity from {} "
                            + "unique launch-local entries",
                    quadCount
            );
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            reportReflectionFailure(
                    "Could not learn FerriteCore's baked-quad table "
                            + "capacity; no capacity data was saved",
                    failure
            );
        }
    }

    private static Object quadCache()
            throws ReflectiveOperationException {
        Class<?> deduplicator = Class.forName(
                DEDUPLICATOR,
                false,
                FerriteCoreQuadCacheCapacity.class.getClassLoader()
        );
        Field field = deduplicator.getDeclaredField("BAKED_QUAD_CACHE");
        field.setAccessible(true);
        Object cache = field.get(null);
        if (!(cache instanceof Map<?, ?>)) {
            throw new ReflectiveOperationException(
                    "FerriteCore's baked-quad cache is not a map"
            );
        }
        return cache;
    }

    private static long divideRoundUp(long value, long divisor) {
        return value / divisor
                + (value % divisor == 0L ? 0L : 1L);
    }

    private static synchronized void reportReflectionFailure(
            String message,
            Throwable failure
    ) {
        if (reflectionFailureReported) {
            return;
        }
        reflectionFailureReported = true;
        VHAccelerator.LOGGER.warn(message, failure);
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .preSizeFerriteCoreQuadCache
                )
                && ModList.get().isLoaded("ferritecore");
    }

    private static String ferriteVersion() {
        return ModList.get()
                .getModContainerById("ferritecore")
                .map(container -> container.getModInfo()
                        .getVersion()
                        .toString())
                .orElse("unknown");
    }

    private static CachedCapacity read() {
        if (!Files.isRegularFile(CACHE_FILE)) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(
                        Files.newInputStream(CACHE_FILE)
                )
        )) {
            if (input.readInt() != MAGIC
                    || input.readInt() != FORMAT_VERSION) {
                return null;
            }
            CachedCapacity cached = new CachedCapacity(
                    input.readUTF(),
                    input.readInt(),
                    input.readInt()
            );
            if (cached.topLevelEstimate <= 0
                    || cached.topLevelEstimate
                    > MAX_RECORDED_ENTRIES
                    || cached.quadCount <= 0
                    || cached.quadCount > MAX_RECORDED_ENTRIES) {
                return null;
            }
            return cached;
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not read the FerriteCore baked-quad capacity "
                            + "cache; it will be learned again",
                    failure
            );
            return null;
        }
    }

    private static void write(CachedCapacity capacity) {
        Path temporary = CACHE_FILE.resolveSibling(
                CACHE_FILE.getFileName() + ".tmp"
        );
        try {
            Files.createDirectories(DIRECTORY);
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(temporary)
                    )
            )) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(capacity.ferriteVersion);
                output.writeInt(capacity.topLevelEstimate);
                output.writeInt(capacity.quadCount);
            }
            try {
                Files.move(
                        temporary,
                        CACHE_FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        CACHE_FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the FerriteCore baked-quad capacity "
                            + "cache",
                    failure
            );
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A stale temporary file is safe to replace next launch.
            }
        }
    }

    private record CachedCapacity(
            String ferriteVersion,
            int topLevelEstimate,
            int quadCount
    ) {
    }
}
