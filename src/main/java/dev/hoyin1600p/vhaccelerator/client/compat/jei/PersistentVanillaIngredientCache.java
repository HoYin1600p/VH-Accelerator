package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Stores only the completed item list returned by JEI's own vanilla item
 * factory. Cached stacks stay serialized until the current server-dependent
 * fingerprint has been validated, then they are reconstructed on the client
 * thread.
 */
public final class PersistentVanillaIngredientCache {
    private static final int MAGIC = 0x56484149;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_STACKS = 250_000;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("vanilla-item-ingredients");
    private static final Executor WRITER = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator ingredient cache writer"
                );
                thread.setDaemon(true);
                return thread;
            }
    );

    private static volatile CompletableFuture<Map<String, CachedIngredientList>>
            preload;
    private static final ThreadLocal<List<ItemStack>> RESTORED_RESULT =
            new ThreadLocal<>();
    private static String reportedMissKey;

    private PersistentVanillaIngredientCache() {
    }

    public static void prewarm() {
        if (preload != null) {
            return;
        }
        synchronized (PersistentVanillaIngredientCache.class) {
            if (preload == null) {
                preload = CompletableFuture.supplyAsync(
                        PersistentVanillaIngredientCache::loadAll,
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "VH Accelerator ingredient cache reader"
                            );
                            thread.setDaemon(true);
                            thread.start();
                        }
                );
            }
        }
    }

    public static synchronized void beginConnection() {
        reportedMissKey = null;
        RESTORED_RESULT.remove();
    }

    public static List<ItemStack> restore(String jeiGeneration) {
        if (!enabled()) {
            return null;
        }
        LoginStateFingerprint.Snapshot fingerprint =
                LoginStateFingerprint.current();
        if (fingerprint == null) {
            return null;
        }

        prewarm();
        String cacheKey = cacheKey(fingerprint.serverKey(), jeiGeneration);
        CachedIngredientList cached = preload.join().get(cacheKey);
        if (cached == null) {
            reportMiss(
                    fingerprint,
                    jeiGeneration,
                    "no compatible cache exists for this server and JEI generation"
            );
            return null;
        }

        LoginStateFingerprint.IngredientDependencies stored =
                cached.dependencies();
        LoginStateFingerprint.IngredientDependencies current =
                fingerprint.ingredients();
        String mismatch = mismatch(stored, current);
        if (mismatch != null) {
            reportMiss(fingerprint, jeiGeneration, mismatch);
            return null;
        }

        long started = System.nanoTime();
        try {
            List<ItemStack> restored = new ArrayList<>(cached.stacks().size());
            for (CompoundTag serialized : cached.stacks()) {
                ItemStack stack = ItemStack.of(serialized.copy());
                if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                    reportMiss(
                            fingerprint,
                            jeiGeneration,
                            "a cached item stack no longer resolves"
                    );
                    return null;
                }
                restored.add(stack);
            }
            VHAccelerator.LOGGER.info(
                    "Restored {} JEI {} vanilla item ingredients from the "
                            + "persistent cache in {} ms",
                    restored.size(),
                    jeiGeneration,
                    (System.nanoTime() - started) / 1_000_000L
            );
            RESTORED_RESULT.set(restored);
            return restored;
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not reconstruct the persistent JEI {} vanilla "
                            + "ingredient cache; running JEI's original factory",
                    jeiGeneration,
                    failure
            );
            return null;
        }
    }

    public static void record(
            String jeiGeneration,
            List<ItemStack> itemStacks
    ) {
        List<ItemStack> restored = RESTORED_RESULT.get();
        RESTORED_RESULT.remove();
        if (restored == itemStacks) {
            return;
        }
        if (!enabled()
                || itemStacks.isEmpty()
                || itemStacks.size() > MAX_STACKS) {
            return;
        }
        LoginStateFingerprint.Snapshot fingerprint =
                LoginStateFingerprint.current();
        if (fingerprint == null) {
            return;
        }

        long started = System.nanoTime();
        List<CompoundTag> serialized = new ArrayList<>(itemStacks.size());
        try {
            for (ItemStack stack : itemStacks) {
                if (stack.isEmpty() || stack.getItem() == Items.AIR) {
                    return;
                }
                serialized.add(stack.save(new CompoundTag()));
            }
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not serialize JEI {} vanilla item ingredients; "
                            + "the persistent cache will not be updated",
                    jeiGeneration,
                    failure
            );
            return;
        }

        CachedIngredientList cached = new CachedIngredientList(
                fingerprint.ingredients(),
                List.copyOf(serialized)
        );
        String cacheKey = cacheKey(fingerprint.serverKey(), jeiGeneration);
        prewarm();
        preload.join().put(cacheKey, cached);
        VHAccelerator.LOGGER.info(
                "Captured {} JEI {} vanilla item ingredients for persistent "
                        + "caching in {} ms",
                serialized.size(),
                jeiGeneration,
                (System.nanoTime() - started) / 1_000_000L
        );
        CompletableFuture.runAsync(
                () -> save(
                        fingerprint.serverKey(),
                        jeiGeneration,
                        cached
                ),
                WRITER
        );
    }

    private static boolean enabled() {
        return JeiRecoveryReload.optimizationsAllowed()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .persistentVanillaIngredientCache
                        .get();
    }

    private static String mismatch(
            LoginStateFingerprint.IngredientDependencies stored,
            LoginStateFingerprint.IngredientDependencies current
    ) {
        if (!stored.localCodeHash().equals(current.localCodeHash())) {
            return "local mods, mod files, or the item registry changed";
        }
        if (!stored.localConfigHash().equals(current.localConfigHash())) {
            return "the local configuration changed";
        }
        if (!stored.tagPayloadHash().equals(current.tagPayloadHash())) {
            return "the synchronized item tags changed";
        }
        if (!stored.serverConfigHash().equals(current.serverConfigHash())) {
            return "the synchronized Forge server configs changed";
        }
        if (!stored.value().equals(current.value())) {
            return "the ingredient-cache schema changed";
        }
        return null;
    }

    private static synchronized void reportMiss(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration,
            String reason
    ) {
        String missKey = fingerprint.serverKey()
                + ":"
                + jeiGeneration
                + ":"
                + fingerprint.ingredients().value();
        if (missKey.equals(reportedMissKey)) {
            return;
        }
        reportedMissKey = missKey;
        VHAccelerator.LOGGER.info(
                "Persistent JEI {} vanilla ingredient cache miss because {}; "
                        + "running JEI's original factory",
                jeiGeneration,
                reason
        );
    }

    private static Map<String, CachedIngredientList> loadAll() {
        Map<String, CachedIngredientList> caches = new ConcurrentHashMap<>();
        if (!Files.isDirectory(DIRECTORY)) {
            return caches;
        }
        try (var paths = Files.list(DIRECTORY)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .forEach(path -> loadOne(path, caches));
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not preload persistent vanilla ingredient caches",
                    exception
            );
        }
        if (!caches.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent vanilla ingredient cache(s)",
                    caches.size()
            );
        }
        return caches;
    }

    private static void loadOne(
            Path path,
            Map<String, CachedIngredientList> destination
    ) {
        try (InputStream input = new BufferedInputStream(
                Files.newInputStream(path)
        )) {
            CompoundTag root = NbtIo.readCompressed(input);
            if (root.getInt("magic") != MAGIC
                    || root.getInt("format") != FORMAT_VERSION) {
                return;
            }
            String serverKey = root.getString("server");
            String generation = root.getString("jei");
            if (serverKey.length() != 64
                    || !("9".equals(generation) || "10".equals(generation))) {
                throw new IOException("Invalid ingredient cache identity");
            }

            LoginStateFingerprint.IngredientDependencies dependencies =
                    new LoginStateFingerprint.IngredientDependencies(
                            root.getString("dependencies"),
                            root.getString("localCode"),
                            root.getString("localConfigs"),
                            root.getString("tags"),
                            root.getString("serverConfigs")
                    );
            ListTag stackTags = root.getList("stacks", Tag.TAG_COMPOUND);
            int count = root.getInt("count");
            if (count <= 0
                    || count != stackTags.size()
                    || count > MAX_STACKS) {
                throw new IOException(
                        "Invalid vanilla ingredient count " + count
                );
            }

            List<CompoundTag> stacks = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                CompoundTag stack = stackTags.getCompound(index);
                if (!stack.contains("id", Tag.TAG_STRING)) {
                    throw new IOException(
                            "Invalid item stack at index " + index
                    );
                }
                stacks.add(stack.copy());
            }
            destination.put(
                    cacheKey(serverKey, generation),
                    new CachedIngredientList(
                            dependencies,
                            List.copyOf(stacks)
                    )
            );
        } catch (EOFException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring truncated vanilla ingredient cache {}",
                    path.getFileName()
            );
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring invalid vanilla ingredient cache {}",
                    path.getFileName(),
                    exception
            );
        }
    }

    private static void save(
            String serverKey,
            String jeiGeneration,
            CachedIngredientList cached
    ) {
        Path temporary = null;
        try {
            Files.createDirectories(DIRECTORY);
            Path target = cachePath(serverKey, jeiGeneration);
            temporary = Files.createTempFile(
                    DIRECTORY,
                    serverKey + "-" + jeiGeneration + "-",
                    ".tmp"
            );

            CompoundTag root = new CompoundTag();
            root.putInt("magic", MAGIC);
            root.putInt("format", FORMAT_VERSION);
            root.putString("server", serverKey);
            root.putString("jei", jeiGeneration);
            LoginStateFingerprint.IngredientDependencies dependencies =
                    cached.dependencies();
            root.putString("dependencies", dependencies.value());
            root.putString("localCode", dependencies.localCodeHash());
            root.putString("localConfigs", dependencies.localConfigHash());
            root.putString("tags", dependencies.tagPayloadHash());
            root.putString("serverConfigs", dependencies.serverConfigHash());
            root.putInt("count", cached.stacks().size());
            ListTag stacks = new ListTag();
            cached.stacks().stream()
                    .map(CompoundTag::copy)
                    .forEach(stacks::add);
            root.put("stacks", stacks);

            try (OutputStream output = new BufferedOutputStream(
                    Files.newOutputStream(temporary)
            )) {
                NbtIo.writeCompressed(root, output);
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
            VHAccelerator.LOGGER.info(
                    "Persisted {} JEI {} vanilla item ingredients for future logins",
                    cached.stacks().size(),
                    jeiGeneration
            );
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not persist the JEI {} vanilla ingredient cache",
                    jeiGeneration,
                    exception
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed temporary-file cleanup is harmless.
                }
            }
        }
    }

    private static String cacheKey(
            String serverKey,
            String jeiGeneration
    ) {
        return serverKey + ":" + jeiGeneration;
    }

    private static Path cachePath(
            String serverKey,
            String jeiGeneration
    ) {
        return DIRECTORY.resolve(
                serverKey + "-" + jeiGeneration + ".nbt"
        );
    }

    private record CachedIngredientList(
            LoginStateFingerprint.IngredientDependencies dependencies,
            List<CompoundTag> stacks
    ) {
    }
}
