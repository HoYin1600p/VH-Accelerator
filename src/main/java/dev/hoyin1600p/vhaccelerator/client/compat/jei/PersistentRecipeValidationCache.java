package dev.hoyin1600p.vhaccelerator.client.compat.jei;

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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists only the IDs that passed JEI's structural input/output validation.
 * Recipe objects are always resolved from the active world's RecipeManager,
 * so this cache cannot retain objects from a disconnected world. Category
 * ownership is deliberately not cached because JEI category extensions are
 * live runtime state and must only be queried on the lifecycle thread.
 */
public final class PersistentRecipeValidationCache {
    public static final String CRAFTING = "crafting";
    public static final String STONECUTTING = "stonecutting";
    public static final String SMELTING = "smelting";
    public static final String SMOKING = "smoking";
    public static final String BLASTING = "blasting";
    public static final String CAMPFIRE = "campfire";
    public static final String SMITHING = "smithing";

    private static final int MAGIC = 0x56484152;
    private static final int FORMAT_VERSION = 3;
    private static final int MAX_CATEGORIES = 32;
    private static final int MAX_RECIPES_PER_CATEGORY = 250_000;
    private static final Set<String> COMPLETE_CATEGORY_SET = Set.of(
            CRAFTING,
            STONECUTTING,
            SMELTING,
            SMOKING,
            BLASTING,
            CAMPFIRE,
            SMITHING
    );
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("vanilla-recipe-validation");

    private static volatile CompletableFuture<Map<String, CachedManifest>> preload;
    private static PendingManifest pending;
    private static String reportedMissKey;

    private PersistentRecipeValidationCache() {
    }

    public static void prewarm() {
        if (preload != null) {
            return;
        }
        synchronized (PersistentRecipeValidationCache.class) {
            if (preload == null) {
                preload = CompletableFuture.supplyAsync(
                        PersistentRecipeValidationCache::loadAll,
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "VH Accelerator recipe cache reader"
                            );
                            thread.setDaemon(true);
                            thread.start();
                        }
                );
            }
        }
    }

    public static synchronized void beginConnection() {
        pending = null;
        reportedMissKey = null;
    }

    public static <T extends Recipe<?>> List<T> restore(
            LoginStateFingerprint.Snapshot fingerprint,
            String category,
            List<T> recipes
    ) {
        CachedManifest manifest = find(fingerprint);
        if (manifest == null) {
            return null;
        }
        CategoryEntry entry = manifest.categories().get(category);
        if (!matchesSourceSize(entry, recipes)) {
            return null;
        }
        Set<String> acceptedIds = validatedIds(entry, recipes);
        if (acceptedIds == null) {
            return null;
        }

        List<T> restored = new ArrayList<>(acceptedIds.size());
        for (T recipe : recipes) {
            if (acceptedIds.contains(recipe.getId().toString())) {
                restored.add(recipe);
            }
        }
        return List.copyOf(restored);
    }

    public static synchronized void record(
            LoginStateFingerprint.Snapshot fingerprint,
            String category,
            int sourceCount,
            List<? extends Recipe<?>> recipes
    ) {
        if (fingerprint == null) {
            return;
        }
        PendingManifest current = pendingFor(fingerprint);
        current.categories().put(
                category,
                new CategoryEntry(sourceCount, recipeIds(recipes))
        );
        saveIfComplete(current);
    }

    private static CachedManifest find(
            LoginStateFingerprint.Snapshot fingerprint
    ) {
        if (fingerprint == null) {
            return null;
        }
        prewarm();
        CachedManifest manifest = preload.join().get(fingerprint.serverKey());
        if (manifest == null) {
            reportMiss(fingerprint, "no compatible cache exists for this server");
            return null;
        }
        LoginStateFingerprint.RecipeDependencies stored =
                manifest.dependencies();
        LoginStateFingerprint.RecipeDependencies current =
                fingerprint.recipes();
        if (!stored.localCodeHash().equals(current.localCodeHash())) {
            reportMiss(
                    fingerprint,
                    "local mods, mod files, or the item registry changed"
            );
            return null;
        }
        if (!stored.recipePayloadHash().equals(current.recipePayloadHash())) {
            reportMiss(fingerprint, "the synchronized recipe payload changed");
            return null;
        }
        if (!stored.tagPayloadHash().equals(current.tagPayloadHash())) {
            reportMiss(fingerprint, "the synchronized item tags changed");
            return null;
        }
        if (!stored.serverConfigHash().equals(current.serverConfigHash())) {
            reportMiss(
                    fingerprint,
                    "the synchronized Forge server configs changed"
            );
            return null;
        }
        if (!stored.value().equals(current.value())) {
            reportMiss(fingerprint, "the recipe-cache schema changed");
            return null;
        }
        if (!manifest.categories().keySet().containsAll(
                COMPLETE_CATEGORY_SET
        )) {
            reportMiss(fingerprint, "the cached recipe groups are incomplete");
            return null;
        }
        return manifest;
    }

    private static PendingManifest pendingFor(
            LoginStateFingerprint.Snapshot fingerprint
    ) {
        if (pending != null
                && pending.serverKey().equals(fingerprint.serverKey())
                && pending.dependencies().value().equals(
                        fingerprint.recipes().value()
                )) {
            return pending;
        }

        Map<String, CategoryEntry> categories = new LinkedHashMap<>();
        CachedManifest cached = find(fingerprint);
        if (cached != null) {
            categories.putAll(cached.categories());
        }
        pending = new PendingManifest(
                fingerprint.serverKey(),
                fingerprint.recipes(),
                categories
        );
        return pending;
    }

    private static void saveIfComplete(PendingManifest current) {
        if (!current.categories().keySet().containsAll(COMPLETE_CATEGORY_SET)) {
            return;
        }
        CachedManifest manifest = new CachedManifest(
                current.dependencies(),
                Map.copyOf(current.categories())
        );
        save(current.serverKey(), manifest);
        pending = null;
    }

    private static boolean matchesSourceSize(
            CategoryEntry entry,
            List<? extends Recipe<?>> recipes
    ) {
        return entry != null && entry.sourceCount() == recipes.size();
    }

    private static synchronized void reportMiss(
            LoginStateFingerprint.Snapshot fingerprint,
            String reason
    ) {
        String missKey = fingerprint.serverKey()
                + ":"
                + fingerprint.recipes().value();
        if (missKey.equals(reportedMissKey)) {
            return;
        }
        reportedMissKey = missKey;
        VHAccelerator.LOGGER.info(
                "Persistent vanilla JEI recipe cache miss because {}; "
                        + "validating the active recipe set",
                reason
        );
    }

    private static Set<String> validatedIds(
            CategoryEntry entry,
            List<? extends Recipe<?>> recipes
    ) {
        Map<String, Recipe<?>> currentRecipes = new HashMap<>(recipes.size());
        for (Recipe<?> recipe : recipes) {
            String id = recipe.getId().toString();
            if (currentRecipes.put(id, recipe) != null) {
                return null;
            }
        }

        Set<String> accepted = new HashSet<>(entry.acceptedIds());
        if (accepted.size() != entry.acceptedIds().size()
                || !currentRecipes.keySet().containsAll(accepted)) {
            return null;
        }
        return accepted;
    }

    private static List<String> recipeIds(
            List<? extends Recipe<?>> recipes
    ) {
        return recipes.stream()
                .map(Recipe::getId)
                .map(ResourceLocation::toString)
                .toList();
    }

    private static void save(String serverKey, CachedManifest manifest) {
        prewarm();
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
                LoginStateFingerprint.RecipeDependencies dependencies =
                        manifest.dependencies();
                output.writeUTF(dependencies.value());
                output.writeUTF(dependencies.localCodeHash());
                output.writeUTF(dependencies.recipePayloadHash());
                output.writeUTF(dependencies.tagPayloadHash());
                output.writeUTF(dependencies.serverConfigHash());
                List<String> categories = manifest.categories().keySet().stream()
                        .sorted()
                        .toList();
                output.writeInt(categories.size());
                for (String category : categories) {
                    CategoryEntry entry = manifest.categories().get(category);
                    output.writeUTF(category);
                    output.writeInt(entry.sourceCount());
                    output.writeInt(entry.acceptedIds().size());
                    for (String recipeId : entry.acceptedIds()) {
                        output.writeUTF(recipeId);
                    }
                }
                output.writeUTF(manifestHash(manifest));
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
            preload.join().put(serverKey, manifest);
            int recipeCount = manifest.categories().values().stream()
                    .mapToInt(entry -> entry.acceptedIds().size())
                    .sum();
            VHAccelerator.LOGGER.info(
                    "Persisted {} validated vanilla JEI recipe IDs across {} groups",
                    recipeCount,
                    manifest.categories().size()
            );
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not persist the vanilla JEI recipe validation cache",
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

    private static Map<String, CachedManifest> loadAll() {
        Map<String, CachedManifest> caches = new ConcurrentHashMap<>();
        if (!Files.isDirectory(DIRECTORY)) {
            return caches;
        }

        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".bin"))
                    .forEach(path -> loadOne(path, caches));
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not preload persistent vanilla recipe caches",
                    exception
            );
        }
        if (!caches.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent vanilla recipe cache(s)",
                    caches.size()
            );
        }
        return caches;
    }

    private static void loadOne(
            Path path,
            Map<String, CachedManifest> destination
    ) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path))
        )) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                return;
            }
            LoginStateFingerprint.RecipeDependencies dependencies =
                    new LoginStateFingerprint.RecipeDependencies(
                            input.readUTF(),
                            input.readUTF(),
                            input.readUTF(),
                            input.readUTF(),
                            input.readUTF()
                    );
            int categoryCount = input.readInt();
            if (categoryCount < 0 || categoryCount > MAX_CATEGORIES) {
                throw new IOException("Invalid recipe category count " + categoryCount);
            }

            Map<String, CategoryEntry> categories =
                    new LinkedHashMap<>(categoryCount);
            for (int categoryIndex = 0;
                    categoryIndex < categoryCount;
                    categoryIndex++) {
                String category = input.readUTF();
                int sourceCount = input.readInt();
                int recipeCount = input.readInt();
                if (sourceCount < 0
                        || sourceCount > MAX_RECIPES_PER_CATEGORY
                        || recipeCount < 0
                        || recipeCount > MAX_RECIPES_PER_CATEGORY) {
                    throw new IOException(
                            "Invalid recipe counts for " + category
                    );
                }
                List<String> recipeIds = new ArrayList<>(recipeCount);
                for (int recipeIndex = 0;
                        recipeIndex < recipeCount;
                        recipeIndex++) {
                    String recipeId = input.readUTF();
                    if (ResourceLocation.tryParse(recipeId) == null) {
                        throw new IOException(
                                "Invalid recipe ID at index " + recipeIndex
                        );
                    }
                    recipeIds.add(recipeId);
                }
                if (categories.put(
                        category,
                        new CategoryEntry(sourceCount, List.copyOf(recipeIds))
                ) != null) {
                    throw new IOException(
                            "Duplicate recipe category " + category
                    );
                }
            }

            CachedManifest manifest = new CachedManifest(
                    dependencies,
                    Map.copyOf(categories)
            );
            if (!input.readUTF().equals(manifestHash(manifest))) {
                throw new IOException("Recipe manifest checksum mismatch");
            }

            String fileName = path.getFileName().toString();
            String serverKey = fileName.substring(0, fileName.length() - 4);
            if (serverKey.length() != 64) {
                throw new IOException("Invalid server cache key");
            }
            destination.put(serverKey, manifest);
        } catch (EOFException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring truncated vanilla recipe cache {}",
                    path.getFileName()
            );
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Ignoring invalid vanilla recipe cache {}",
                    path.getFileName(),
                    exception
            );
        }
    }

    private static Path cachePath(String serverKey) {
        return DIRECTORY.resolve(serverKey + ".bin");
    }

    private static String manifestHash(CachedManifest manifest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            LoginStateFingerprint.RecipeDependencies dependencies =
                    manifest.dependencies();
            updateDigest(digest, dependencies.value());
            updateDigest(digest, dependencies.localCodeHash());
            updateDigest(digest, dependencies.recipePayloadHash());
            updateDigest(digest, dependencies.tagPayloadHash());
            updateDigest(digest, dependencies.serverConfigHash());
            manifest.categories().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        updateDigest(digest, entry.getKey());
                        updateDigest(
                                digest,
                                Integer.toString(entry.getValue().sourceCount())
                        );
                        entry.getValue().acceptedIds().forEach(
                                recipeId -> updateDigest(digest, recipeId)
                        );
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }

    private record CategoryEntry(
            int sourceCount,
            List<String> acceptedIds
    ) {
    }

    private record CachedManifest(
            LoginStateFingerprint.RecipeDependencies dependencies,
            Map<String, CategoryEntry> categories
    ) {
    }

    private record PendingManifest(
            String serverKey,
            LoginStateFingerprint.RecipeDependencies dependencies,
            Map<String, CategoryEntry> categories
    ) {
    }
}
