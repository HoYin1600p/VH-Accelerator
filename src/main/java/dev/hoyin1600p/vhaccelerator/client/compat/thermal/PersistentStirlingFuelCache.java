package dev.hoyin1600p.vhaccelerator.client.compat.thermal;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private PersistentStirlingFuelCache() {
    }

    public static void prewarm() {
        if (preload != null) {
            return;
        }
        synchronized (PersistentStirlingFuelCache.class) {
            if (preload == null) {
                preload = CompletableFuture.supplyAsync(
                        PersistentStirlingFuelCache::loadAll,
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "VH Accelerator Thermal fuel cache reader"
                            );
                            thread.setDaemon(true);
                            thread.start();
                        }
                );
            }
        }
    }

    public static LookupResult find(
            String serverKey,
            LoginStateFingerprint.FuelDependencies current
    ) {
        prewarm();
        CachedFuelList cached = preload.join().get(serverKey);
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
                    ".tmp"
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

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            preload.join().put(serverKey, cached);
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
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
