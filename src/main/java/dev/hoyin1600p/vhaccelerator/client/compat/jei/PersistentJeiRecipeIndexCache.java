package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
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
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists JEI's deterministic recipe-to-ingredient-UID index plans.
 *
 * <p>Only string IDs are stored. A warm restore resolves every recipe from
 * the active world's recipe collection and publishes those active objects
 * into a newly-created JEI runtime. The cache is rejected when any relevant
 * synchronized or local input differs.</p>
 */
public final class PersistentJeiRecipeIndexCache {
    private static final int MAGIC = 0x56484A49;
    private static final int FORMAT_VERSION = 4;
    // Each index file is tens of MB in large packs; keep only recent ones.
    private static final int MAX_FILES = 8;
    /** Newest files read ahead of a connection; others are read on demand. */
    private static final int PRELOAD_FILES = 2;
    private static final int MAX_CATEGORIES = 128;
    private static final int MAX_BATCHES_PER_CATEGORY = 64;
    private static final int MAX_TOTAL_BATCHES = 512;
    private static final int MAX_RECIPES_PER_CATEGORY = 250_000;
    private static final int MAX_TOTAL_RECIPES = 500_000;
    private static final int MAX_TOTAL_UIDS = 10_000_000;
    // Per-recipe read limits only reject clearly corrupt files; the writer
    // does not bound plans, and MAX_TOTAL_UIDS caps the whole file. Wolds has
    // a recipe whose one ingredient group lists 16,645 items, which the former
    // 16,384 limit rejected, so the index was rebuilt and rewritten every join.
    private static final int MAX_ROLES_PER_RECIPE = 256;
    private static final int MAX_GROUPS_PER_ROLE = 4_096;
    private static final int MAX_UIDS_PER_GROUP = 1 << 20;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("jei-recipe-index");
    private static final Executor WRITER =
            SharedWorkers.io();

    /**
     * Manifests in memory, keyed by cache key. Only the newest files are read
     * ahead; any other key is read from its own file on first use.
     */
    private static final Map<String, Manifest> MEMORY =
            new ConcurrentHashMap<>();
    /** Keys with no usable file during the current connection. */
    private static final Set<String> ABSENT =
            ConcurrentHashMap.newKeySet();
    /** Keys hit or recorded this session; the only ones kept after use. */
    private static final Set<String> USED = ConcurrentHashMap.newKeySet();
    private static volatile CompletableFuture<Void> preload;
    private static boolean released;
    private static String retainedServerKey;
    private static String reportedMissKey;

    private PersistentJeiRecipeIndexCache() {
    }

    public static void prewarm() {
        loaded();
    }

    private static CompletableFuture<Void> loaded() {
        CompletableFuture<Void> current = preload;
        if (current != null) {
            return current;
        }
        synchronized (PersistentJeiRecipeIndexCache.class) {
            if (preload == null) {
                if (released && VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                    VHAccelerator.LOGGER.info(
                            "[debug] Rereading released {} files from disk",
                            "PersistentJeiRecipeIndexCache"
                    );
                }
                released = false;
                preload = CompletableFuture.runAsync(
                        PersistentJeiRecipeIndexCache::loadNewest,
                        SharedWorkers.io()
                );
            }
            return preload;
        }
    }

    /**
     * Once the client has consumed this cache, keeps in memory only the
     * manifests this session used for {@code keepServerKey}, so a reconnect
     * or proxy backend switch to a backend already visited never rereads
     * them, and releases everything else. With no key, or before the preload
     * has finished, everything is released. Cache files always stay on disk.
     */
    public static synchronized void releaseMemory(String keepServerKey) {
        CompletableFuture<Void> current = preload;
        if (current == null) {
            return;
        }
        if (keepServerKey == null
                || !current.isDone()
                || current.isCompletedExceptionally()) {
            released = true;
            retainedServerKey = null;
            MEMORY.clear();
            USED.clear();
            preload = null;
            return;
        }
        USED.removeIf(key -> !ServerScopedCacheMemory.belongsTo(key, keepServerKey));
        MEMORY.keySet().retainAll(USED);
        retainedServerKey = keepServerKey;
    }

    /**
     * Starts reading the newest cache files for a new connection unless this
     * server's entries are already held in memory.
     */
    public static synchronized void prewarmFor(String serverKey) {
        if (retainedServerKey != null
                && (serverKey == null || !retainedServerKey.equals(serverKey))) {
            released = true;
            retainedServerKey = null;
            MEMORY.clear();
            USED.clear();
            preload = null;
        }
        prewarm();
    }

    public static synchronized void beginConnection() {
        reportedMissKey = null;
        ABSENT.clear();
    }

    /**
     * The manifest stored under {@code cacheKey}: from memory, or else read
     * from that key's own file. The file name is the cache key, so no other
     * file is ever read for a lookup.
     */
    private static Manifest manifest(String cacheKey) {
        loaded().join();
        Manifest manifest = MEMORY.get(cacheKey);
        if (manifest != null || ABSENT.contains(cacheKey)) {
            return manifest;
        }
        synchronized (PersistentJeiRecipeIndexCache.class) {
            manifest = MEMORY.get(cacheKey);
            if (manifest != null || ABSENT.contains(cacheKey)) {
                return manifest;
            }
            Path path = DIRECTORY.resolve(cacheKey + ".bin");
            manifest = Files.isRegularFile(path) ? read(path) : null;
            if (manifest == null || !manifest.cacheKey.equals(cacheKey)) {
                ABSENT.add(cacheKey);
                return null;
            }
            MEMORY.put(cacheKey, manifest);
            return manifest;
        }
    }

    public static <T> RestoreResult<T> restore(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration,
            String categoryUid,
            Collection<T> sourceRecipes
    ) {
        if (!enabled() || fingerprint == null) {
            return null;
        }
        Manifest manifest = find(fingerprint, jeiGeneration);
        if (manifest == null) {
            return null;
        }

        Map<String, T> activeById =
                new HashMap<>(sourceRecipes.size() * 2);
        for (T candidate : sourceRecipes) {
            if (!(candidate instanceof Recipe<?> recipe)) {
                return null;
            }
            String id = recipe.getId().toString();
            if (activeById.put(id, candidate) != null) {
                return null;
            }
        }
        CategoryBatches category = manifest.categories.get(categoryUid);
        if (category == null) {
            return null;
        }
        String batchKey = JeiRecipeIndexIdentity.batchKey(
                categoryUid,
                activeById.keySet()
        );
        CategoryPlan batch = category.batches.get(batchKey);
        if (batch == null
                || batch.sourceCount != sourceRecipes.size()) {
            return null;
        }

        List<ActiveRecipe<T>> restored =
                new ArrayList<>(batch.recipes.size());
        Set<String> acceptedIds =
                new HashSet<>(batch.recipes.size() * 2);
        for (RecipePlan plan : batch.recipes) {
            if (!acceptedIds.add(plan.recipeId)) {
                return null;
            }
            T activeRecipe = activeById.get(plan.recipeId);
            if (activeRecipe == null) {
                return null;
            }
            restored.add(new ActiveRecipe<>(
                    activeRecipe,
                    plan.roleGroups
            ));
        }
        return new RestoreResult<>(
                List.copyOf(restored),
                restored.size()
        );
    }

    public static void record(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration,
            String categoryUid,
            Collection<?> sourceRecipes,
            List<? extends ActiveRecipe<?>> acceptedRecipes
    ) {
        if (!enabled()
                || fingerprint == null
                || sourceRecipes.size()
                        > MAX_RECIPES_PER_CATEGORY
                || acceptedRecipes.size()
                        > MAX_RECIPES_PER_CATEGORY) {
            return;
        }

        List<RecipePlan> recipes =
                new ArrayList<>(acceptedRecipes.size());
        for (ActiveRecipe<?> accepted : acceptedRecipes) {
            if (!(accepted.recipe instanceof Recipe<?> recipe)) {
                return;
            }
            recipes.add(new RecipePlan(
                    recipe.getId().toString(),
                    freezeRoleGroups(accepted.roleGroups)
            ));
        }
        Set<String> sourceIds = recipeIds(sourceRecipes);
        if (sourceIds == null) {
            return;
        }
        String batchKey = JeiRecipeIndexIdentity.batchKey(
                categoryUid,
                sourceIds
        );

        prewarm();
        String cacheKey = JeiRecipeIndexIdentity.cacheKey(
                fingerprint.serverKey(),
                jeiGeneration,
                fingerprint.recipes().indexValue()
        );
        Manifest next;
        synchronized (PersistentJeiRecipeIndexCache.class) {
            Manifest current = manifest(cacheKey);
            Map<String, CategoryBatches> categories =
                    new LinkedHashMap<>();
            if (current != null
                    && current.fingerprint.equals(
                            fingerprint.recipes().indexValue()
                    )) {
                categories.putAll(current.categories);
            }
            Map<String, CategoryPlan> batches = new LinkedHashMap<>();
            CategoryBatches currentCategory = categories.get(categoryUid);
            if (currentCategory != null) {
                batches.putAll(currentCategory.batches);
            }
            batches.put(batchKey, new CategoryPlan(
                    sourceRecipes.size(),
                    List.copyOf(recipes)
            ));
            if (batches.size() > MAX_BATCHES_PER_CATEGORY) {
                return;
            }
            categories.put(categoryUid, new CategoryBatches(
                    Map.copyOf(batches)
            ));
            if (categories.size() > MAX_CATEGORIES) {
                return;
            }
            int totalBatches = categories.values().stream()
                    .mapToInt(category -> category.batches.size())
                    .sum();
            if (totalBatches > MAX_TOTAL_BATCHES) {
                return;
            }
            next = new Manifest(
                    cacheKey,
                    fingerprint.recipes().indexValue(),
                    Map.copyOf(categories)
            );
            MEMORY.put(cacheKey, next);
            ABSENT.remove(cacheKey);
            USED.add(cacheKey);
        }
        CompletableFuture.runAsync(() -> write(next), WRITER);
    }

    public static <T> ActiveRecipe<T> activeRecipe(
            T recipe,
            Map<String, List<List<String>>> roleGroups
    ) {
        return new ActiveRecipe<>(
                recipe,
                freezeRoleGroups(roleGroups)
        );
    }

    private static Manifest find(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration
    ) {
        prewarm();
        String cacheKey = JeiRecipeIndexIdentity.cacheKey(
                fingerprint.serverKey(),
                jeiGeneration,
                fingerprint.recipes().indexValue()
        );
        Manifest manifest = manifest(cacheKey);
        if (manifest == null) {
            reportMiss(
                    cacheKey,
                    "no compatible recipe index exists"
            );
            return null;
        }
        if (!manifest.fingerprint.equals(
                fingerprint.recipes().indexValue()
        )) {
            reportMiss(
                    cacheKey,
                    "recipes, tags, configs, mods, or cache schema changed"
            );
            return null;
        }
        if (USED.add(cacheKey)) {
            // Keep a used file among the newest, which are the ones read
            // ahead, even when nothing in it needs rewriting.
            Path path = DIRECTORY.resolve(cacheKey + ".bin");
            CompletableFuture.runAsync(() -> {
                try {
                    Files.setLastModifiedTime(
                            path,
                            FileTime.fromMillis(
                                    System.currentTimeMillis()
                            )
                    );
                } catch (IOException ignored) {
                    // Recency only guides read-ahead; a stale time is harmless.
                }
            }, WRITER);
        }
        return manifest;
    }

    private static Set<String> recipeIds(
            Collection<?> sourceRecipes
    ) {
        Set<String> ids = new HashSet<>(sourceRecipes.size() * 2);
        for (Object candidate : sourceRecipes) {
            if (!(candidate instanceof Recipe<?> recipe)
                    || !ids.add(recipe.getId().toString())) {
                return null;
            }
        }
        return ids;
    }

    private static synchronized void reportMiss(
            String cacheKey,
            String reason
    ) {
        if (cacheKey.equals(reportedMissKey)) {
            return;
        }
        reportedMissKey = cacheKey;
        VHAccelerator.LOGGER.info(
                "Persistent JEI recipe index miss because {}; building "
                        + "from the active runtime",
                reason
        );
    }

    private static Map<String, List<List<String>>> freezeRoleGroups(
            Map<String, List<List<String>>> source
    ) {
        Map<String, List<List<String>>> frozen =
                new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<List<String>> groups =
                            entry.getValue().stream()
                                    .map(List::copyOf)
                                    .toList();
                    frozen.put(entry.getKey(), groups);
                });
        return Map.copyOf(frozen);
    }

    /**
     * Reads the most recently written files, which the next connection most
     * likely needs. Every other file is read only when its key is looked up.
     */
    private static void loadNewest() {
        if (!Files.isDirectory(DIRECTORY)) {
            return;
        }
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".bin"))
                    .sorted(Comparator
                            .comparingLong(
                                    PersistentJeiRecipeIndexCache
                                            ::lastModifiedMillis
                            )
                            .reversed())
                    .limit(PRELOAD_FILES)
                    .forEach(path -> {
                        Manifest manifest = read(path);
                        if (manifest != null) {
                            MEMORY.putIfAbsent(
                                    manifest.cacheKey,
                                    manifest
                            );
                        }
                    });
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not enumerate persistent JEI recipe indexes",
                    exception
            );
        }
    }

    private static Manifest read(Path path) {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path))
        )) {
            if (input.readInt() != MAGIC
                    || input.readInt() != FORMAT_VERSION) {
                return null;
            }
            String cacheKey = input.readUTF();
            String fingerprint = input.readUTF();
            // Recipes repeat the same roles, UIDs and ingredient groups many
            // times; share one instance of each instead of one per occurrence.
            Map<String, String> strings = new HashMap<>();
            Map<List<String>, List<String>> sharedGroups = new HashMap<>();
            int categoryCount = bounded(
                    input.readInt(),
                    MAX_CATEGORIES,
                    "category count"
            );
            Map<String, CategoryBatches> categories =
                    new LinkedHashMap<>();
            int totalRecipes = 0;
            int totalUids = 0;
            int totalBatches = 0;
            for (int categoryIndex = 0;
                 categoryIndex < categoryCount;
                 categoryIndex++) {
                String categoryUid = input.readUTF();
                int batchCount = bounded(
                        input.readInt(),
                        MAX_BATCHES_PER_CATEGORY,
                        "recipe batch count"
                );
                totalBatches += batchCount;
                if (totalBatches > MAX_TOTAL_BATCHES) {
                    throw new IOException(
                            "JEI recipe index has too many total batches"
                    );
                }
                Map<String, CategoryPlan> batches = new LinkedHashMap<>();
                for (int batchIndex = 0;
                     batchIndex < batchCount;
                     batchIndex++) {
                    String batchKey = input.readUTF();
                    int sourceCount = bounded(
                            input.readInt(),
                            MAX_RECIPES_PER_CATEGORY,
                            "source recipe count"
                    );
                    int recipeCount = bounded(
                            input.readInt(),
                            MAX_RECIPES_PER_CATEGORY,
                            "accepted recipe count"
                    );
                    totalRecipes += recipeCount;
                    if (totalRecipes > MAX_TOTAL_RECIPES) {
                        throw new IOException(
                                "JEI recipe index has too many total recipes"
                        );
                    }
                    List<RecipePlan> recipes =
                            new ArrayList<>(recipeCount);
                    for (int recipeIndex = 0;
                         recipeIndex < recipeCount;
                         recipeIndex++) {
                        String recipeId = input.readUTF();
                        int roleCount = bounded(
                                input.readInt(),
                                MAX_ROLES_PER_RECIPE,
                                "recipe role count"
                        );
                        Map<String, List<List<String>>> roles =
                                new LinkedHashMap<>();
                        for (int roleIndex = 0;
                             roleIndex < roleCount;
                             roleIndex++) {
                            String role = strings.computeIfAbsent(
                                    input.readUTF(),
                                    value -> value
                            );
                            int groupCount = bounded(
                                    input.readInt(),
                                    MAX_GROUPS_PER_ROLE,
                                    "ingredient group count"
                            );
                            List<List<String>> groups =
                                    new ArrayList<>(groupCount);
                            for (int groupIndex = 0;
                                 groupIndex < groupCount;
                                 groupIndex++) {
                                int uidCount = bounded(
                                        input.readInt(),
                                        MAX_UIDS_PER_GROUP,
                                        "ingredient UID count"
                                );
                                totalUids += uidCount;
                                if (totalUids > MAX_TOTAL_UIDS) {
                                    throw new IOException(
                                            "JEI recipe index has too many "
                                                    + "total ingredient UIDs"
                                    );
                                }
                                List<String> uids =
                                        new ArrayList<>(uidCount);
                                for (int uidIndex = 0;
                                     uidIndex < uidCount;
                                     uidIndex++) {
                                    uids.add(strings.computeIfAbsent(
                                            input.readUTF(),
                                            value -> value
                                    ));
                                }
                                groups.add(sharedGroups.computeIfAbsent(
                                        List.copyOf(uids),
                                        group -> group
                                ));
                            }
                            roles.put(role, List.copyOf(groups));
                        }
                        recipes.add(new RecipePlan(
                                recipeId,
                                Map.copyOf(roles)
                        ));
                    }
                    batches.put(batchKey, new CategoryPlan(
                            sourceCount,
                            List.copyOf(recipes)
                    ));
                }
                categories.put(
                        categoryUid,
                        new CategoryBatches(Map.copyOf(batches))
                );
            }
            Manifest manifest = new Manifest(
                    cacheKey,
                    fingerprint,
                    Map.copyOf(categories)
            );
            if (!manifestHash(manifest).equals(input.readUTF())) {
                throw new IOException(
                        "JEI recipe index checksum mismatch"
                );
            }
            return manifest;
        } catch (EOFException exception) {
            VHAccelerator.LOGGER.warn(
                    "Persistent JEI recipe index {} is truncated",
                    path.getFileName()
            );
            return null;
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not read persistent JEI recipe index {}",
                    path.getFileName(),
                    exception
            );
            return null;
        }
    }

    private static void write(Manifest manifest) {
        Path temporary = null;
        try {
            Files.createDirectories(DIRECTORY);
            Path target = DIRECTORY.resolve(
                    manifest.cacheKey + ".bin"
            );
            temporary = Files.createTempFile(
                    DIRECTORY,
                    manifest.cacheKey + "-",
                    CacheFiles.TEMP_SUFFIX
            );
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(temporary)
                    )
            )) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(manifest.cacheKey);
                output.writeUTF(manifest.fingerprint);
                List<String> categoryUids =
                        manifest.categories.keySet().stream()
                                .sorted()
                                .toList();
                output.writeInt(categoryUids.size());
                for (String categoryUid : categoryUids) {
                    CategoryBatches category =
                            manifest.categories.get(categoryUid);
                    output.writeUTF(categoryUid);
                    List<String> batchKeys = category.batches.keySet()
                            .stream().sorted().toList();
                    output.writeInt(batchKeys.size());
                    for (String batchKey : batchKeys) {
                        CategoryPlan batch = category.batches.get(batchKey);
                        output.writeUTF(batchKey);
                        output.writeInt(batch.sourceCount);
                        output.writeInt(batch.recipes.size());
                        for (RecipePlan recipe : batch.recipes) {
                            output.writeUTF(recipe.recipeId);
                            List<String> roles =
                                    recipe.roleGroups.keySet().stream()
                                        .sorted()
                                        .toList();
                            output.writeInt(roles.size());
                            for (String role : roles) {
                                output.writeUTF(role);
                                List<List<String>> groups =
                                        recipe.roleGroups.get(role);
                                output.writeInt(groups.size());
                                for (List<String> group : groups) {
                                    output.writeInt(group.size());
                                    for (String uid : group) {
                                        output.writeUTF(uid);
                                    }
                                }
                            }
                        }
                    }
                }
                output.writeUTF(manifestHash(manifest));
            }
            AtomicFiles.moveIntoPlace(temporary, target);
            pruneOldFiles();
            int recipeCount = manifest.categories.values().stream()
                    .flatMap(category -> category.batches.values().stream())
                    .mapToInt(batch -> batch.recipes.size())
                    .sum();
            int batchCount = manifest.categories.values().stream()
                    .mapToInt(category -> category.batches.size())
                    .sum();
            VHAccelerator.LOGGER.info(
                    "Persisted {} JEI recipe index plans across {} "
                            + "categories and {} batches",
                    recipeCount,
                    manifest.categories.size(),
                    batchCount
            );
        } catch (IOException | RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not persist the JEI recipe index cache",
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

    private static String manifestHash(Manifest manifest) {
        MessageDigest digest = Digests.sha256();
        update(digest, manifest.cacheKey);
        update(digest, manifest.fingerprint);
        manifest.categories.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(categoryEntry -> {
                    update(digest, categoryEntry.getKey());
                    CategoryBatches category =
                            categoryEntry.getValue();
                    category.batches.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(batchEntry -> {
                                update(digest, batchEntry.getKey());
                                CategoryPlan batch =
                                        batchEntry.getValue();
                                update(
                                        digest,
                                        Integer.toString(
                                                batch.sourceCount
                                        )
                                );
                                for (RecipePlan recipe : batch.recipes) {
                                    update(
                                            digest,
                                            recipe.recipeId
                                    );
                                    recipe.roleGroups.entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .forEach(roleEntry -> {
                                                update(
                                                        digest,
                                                        roleEntry.getKey()
                                                );
                                                for (List<String> group :
                                                        roleEntry.getValue()) {
                                                    update(
                                                            digest,
                                                            Integer.toString(
                                                                    group.size()
                                                            )
                                                    );
                                                    group.forEach(uid ->
                                                            update(digest, uid));
                                                }
                                            });
                                }
                            });
                });
        return HexFormat.of().formatHex(
                digest.digest()
        );
    }

    private static void update(
            MessageDigest digest,
            String value
    ) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static int bounded(
            int value,
            int maximum,
            String label
    ) throws IOException {
        if (value < 0 || value > maximum) {
            throw new IOException(
                    "Invalid " + label + " " + value
            );
        }
        return value;
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private static void pruneOldFiles() {
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            List<Path> stale = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".bin"))
                    .sorted(Comparator
                            .comparingLong(
                                    PersistentJeiRecipeIndexCache
                                            ::lastModifiedMillis
                            )
                            .reversed())
                    .skip(MAX_FILES)
                    .toList();
            for (Path path : stale) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            VHAccelerator.LOGGER.debug(
                    "Could not prune old JEI recipe index caches",
                    exception
            );
        }
    }

    private static boolean enabled() {
        return JeiRecoveryReload.optimizationsAllowed()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.VALUES
                        .persistentJeiRecipeIndexCache
                        .get();
    }

    public record ActiveRecipe<T>(
            T recipe,
            Map<String, List<List<String>>> roleGroups
    ) {
    }

    public record RestoreResult<T>(
            List<ActiveRecipe<T>> recipes,
            int cachedRecipeCount
    ) {
    }

    public record ReconciledPlans<T>(
            List<ActiveRecipe<T>> plans,
            int cachedCount,
            int rebuiltCount,
            int uncachedRebuiltCount,
            int rejectedCachedCount
    ) {
    }

    private record RecipePlan(
            String recipeId,
            Map<String, List<List<String>>> roleGroups
    ) {
    }

    private record CategoryPlan(
            int sourceCount,
            List<RecipePlan> recipes
    ) {
    }

    private record CategoryBatches(
            Map<String, CategoryPlan> batches
    ) {
    }

    private record Manifest(
            String cacheKey,
            String fingerprint,
            Map<String, CategoryBatches> categories
    ) {
    }
}
