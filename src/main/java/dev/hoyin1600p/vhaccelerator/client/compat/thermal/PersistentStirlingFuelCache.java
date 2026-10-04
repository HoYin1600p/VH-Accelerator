package dev.hoyin1600p.vhaccelerator.client.compat.thermal;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.cache.ServerScopedCacheMemory;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.util.AtomicFiles;
import dev.hoyin1600p.vhaccelerator.util.CacheFiles;
import dev.hoyin1600p.vhaccelerator.util.Digests;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists base furnace-fuel values used by Thermal's Stirling dynamo.
 * Thermal's explicit recipe overrides are deliberately not cached and are
 * filtered against the active RecipeManager whenever these values are used.
 */
public final class PersistentStirlingFuelCache {
    private static final int MAGIC = 0x56484154;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENTRIES = 100_000;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("thermal-stirling-fuels");

    private static volatile CompletableFuture<Map<String, CachedFuelList>>
            preload;
    private static boolean released;
    private static String retainedServerKey;

    private PersistentStirlingFuelCache() {
    }

    public static void prewarm() {
        loaded();
    }

    private static CompletableFuture<Map<String, CachedFuelList>> loaded() {
        CompletableFuture<Map<String, CachedFuelList>> current = preload;
        if (current != null) {
            return current;
        }
        synchronized (PersistentStirlingFuelCache.class) {
            if (preload == null) {
                if (released && VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                    VHAccelerator.LOGGER.info(
                            "[debug] Rereading released {} files from disk",
                            "PersistentStirlingFuelCache"
                    );
                }
                released = false;
                preload = CompletableFuture.supplyAsync(
                        PersistentStirlingFuelCache::loadAll,
                        SharedWorkers.io()
                );
            }
            return preload;
        }
    }

    /**
     * Once the client has consumed this cache, keeps only
     * {@code keepServerKey}'s entries in memory, so a reconnect or proxy
     * backend switch to the same address never rereads them, and releases
     * every other server's data. With no key, or before the preload has
     * finished, everything is released. Cache files always stay on disk.
     */
    public static synchronized void releaseMemory(String keepServerKey) {
        CompletableFuture<Map<String, CachedFuelList>> current = preload;
        if (current == null) {
            return;
        }
        if (keepServerKey == null
                || !current.isDone()
                || current.isCompletedExceptionally()) {
            released = true;
            retainedServerKey = null;
            preload = null;
            return;
        }
        preload = CompletableFuture.completedFuture(
                ServerScopedCacheMemory.retain(current.join(), keepServerKey)
        );
        retainedServerKey = keepServerKey;
    }

    /**
     * Starts reading the cache files for a new connection unless this
     * server's entries are already held in memory.
     */
    public static synchronized void prewarmFor(String serverKey) {
        if (retainedServerKey != null
                && (serverKey == null || !retainedServerKey.equals(serverKey))) {
            released = true;
            retainedServerKey = null;
            preload = null;
        }
        prewarm();
    }

    public static LookupResult find(
            String serverKey,
            LoginStateFingerprint.FuelDependencies current
    ) {
        prewarm();
        CachedFuelList cached = loaded().join().get(serverKey);
        if (cached == null) {
            return LookupResult.miss(
                    "no compatible cache exists for this server"
            );
        }
        LoginStateFingerprint.FuelDependencies stored =
                cached.dependencies();
        if (!stored.localCodeHash().equals(current.localCodeHash())) {
            return LookupResult.miss(
                    "local mods, mod files, or item registry changed"
            );
        }
        if (!stored.tagPayloadHash().equals(current.tagPayloadHash())) {
            return LookupResult.miss("the synchronized server tags changed");
        }
        if (!stored.serverConfigHash().equals(current.serverConfigHash())) {
            return LookupResult.miss(
                    "the synchronized Forge server configs changed"
            );
        }
        if (!stored.value().equals(current.value())) {
            return LookupResult.miss("the fuel-cache schema changed");
        }
        return LookupResult.hit(cached);
    }

    public static synchronized void save(
            String serverKey,
            LoginStateFingerprint.FuelDependencies dependencies,
            List<FuelEntry> entries
    ) {
        prewarm();
        CachedFuelList cached = new CachedFuelList(
                dependencies,
                List.copyOf(entries)
        );
        Path temporary = null;
        try {
            Files.createDirectories(DIRECTORY);
            Path target = cachePath(serverKey);
            temporary = Files.createTempFile(
                    DIRECTORY,
                    serverKey + "-",
                    CacheFiles.TEMP_SUFFIX
            );
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary))
            )) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(dependencies.value());
                output.writeUTF(dependencies.localCodeHash());
                output.writeUTF(dependencies.tagPayloadHash());
                output.writeUTF(dependencies.serverConfigHash());
                output.writeInt(entries.size());
                for (FuelEntry entry : entries) {
                    output.writeUTF(entry.itemId());
                    output.writeInt(entry.energy());
                }
                output.writeUTF(manifestHash(entries));
            }

            AtomicFiles.moveIntoPlace(temporary, target);
            loaded().join().put(serverKey, cached);
            VHAccelerator.LOGGER.info(
                    "Persisted {} Thermal Stirling base fuels for future logins",
                    entries.size()
            );
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not persist the Thermal Stirling fuel cache",
                    exception
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Temporary-file cleanup is harmless if the OS retries it.
                }
            }
        }
    }

    private static Map<String, CachedFuelList> loadAll() {
        Map<String, CachedFuelList> caches = new HashMap<>();
        if (!Files.isDirectory(DIRECTORY)) {
            return caches;
        }

        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            paths.filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName().toString().endsWith(".bin"))
                    .forEach(path -> loadOne(path, caches));
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not preload persistent Thermal Stirling fuel caches",
                    exception
            );
        }
        if (!caches.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent Thermal Stirling fuel cache(s)",
                    caches.size()
            );
        }
        return caches;
    }

    private static void loadOne(
            Path path,
            Map<String, CachedFuelList> destination
    ) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path))
        )) {
            if (input.readInt() != MAGIC
                    || input.readInt() != FORMAT_VERSION) {
                return;
            }
            LoginStateFingerprint.FuelDependencies dependencies =
                    new LoginStateFingerprint.FuelDependencies(
                            input.readUTF(),
                            input.readUTF(),
                            input.readUTF(),
                            input.readUTF()
                    );
            int count = input.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IOException("Invalid fuel entry count " + count);
            }

            List<FuelEntry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String itemId = input.readUTF();
                int energy = input.readInt();
                if (itemId.isBlank() || energy < 1000) {
                    throw new IOException(
                            "Invalid fuel entry at index " + index
                    );
                }
                entries.add(new FuelEntry(itemId, energy));
            }
            String storedManifestHash = input.readUTF();
            if (!storedManifestHash.equals(manifestHash(entries))) {
                throw new IOException("Fuel manifest checksum mismatch");
            }

            String fileName = path.getFileName().toString();
            String serverKey = fileName.substring(0, fileName.length() - 4);
            if (serverKey.length() != 64) {
                throw new IOException("Invalid server cache key");
            }
            destination.put(
                    serverKey,
                    new CachedFuelList(dependencies, List.copyOf(entries))
            );
        } catch (EOFException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring truncated Thermal Stirling cache {}",
                    path.getFileName()
            );
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring invalid Thermal Stirling cache {}",
                    path.getFileName(),
                    exception
            );
        }
    }

    private static Path cachePath(String serverKey) {
        return DIRECTORY.resolve(serverKey + ".bin");
    }

    private static String manifestHash(List<FuelEntry> entries) {
        MessageDigest digest = Digests.sha256();
        for (FuelEntry entry : entries) {
            byte[] item =
                    entry.itemId().getBytes(StandardCharsets.UTF_8);
            digest.update(item);
            digest.update((byte) 0);
            int energy = entry.energy();
            digest.update((byte) (energy >>> 24));
            digest.update((byte) (energy >>> 16));
            digest.update((byte) (energy >>> 8));
            digest.update((byte) energy);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public record FuelEntry(String itemId, int energy) {
    }

    public record CachedFuelList(
            LoginStateFingerprint.FuelDependencies dependencies,
            List<FuelEntry> entries
    ) {
    }

    public record LookupResult(
            CachedFuelList cached,
            String missReason
    ) {
        private static LookupResult hit(CachedFuelList cached) {
            return new LookupResult(cached, null);
        }

        private static LookupResult miss(String reason) {
            return new LookupResult(null, reason);
        }

        public boolean hit() {
            return cached != null;
        }
    }
}
