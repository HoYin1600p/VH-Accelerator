package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
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
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_FILES = 64;
    private static final int MAX_CATEGORIES = 128;
    private static final int MAX_RECIPES_PER_CATEGORY = 250_000;
    private static final int MAX_TOTAL_RECIPES = 500_000;
    private static final int MAX_TOTAL_UIDS = 10_000_000;
    private static final int MAX_ROLES_PER_RECIPE = 16;
    private static final int MAX_GROUPS_PER_ROLE = 64;
    private static final int MAX_UIDS_PER_GROUP = 16_384;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("jei-recipe-index");
    private static final Executor WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator JEI recipe index writer"
                );
                thread.setDaemon(true);
                thread.setPriority(Math.max(
                        Thread.MIN_PRIORITY,
                        Thread.NORM_PRIORITY - 1
                ));
                return thread;
            });

    private static volatile CompletableFuture<Map<String, Manifest>>
            preload;
    private static String reportedMissKey;

    private PersistentJeiRecipeIndexCache() {
    }

    public static void prewarm() {
        if (preload != null) {
            return;
        }
        synchronized (PersistentJeiRecipeIndexCache.class) {
            if (preload == null) {
                preload = CompletableFuture.supplyAsync(
                        PersistentJeiRecipeIndexCache::loadAll,
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "VH Accelerator JEI recipe index reader"
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
    }

    public static <T> RestoreResult<T> restore(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration,
            String categoryUid,
            java.util.Collection<T> sourceRecipes
    ) {
        if (!enabled() || fingerprint == null) {
            return null;
        }
        Manifest manifest = find(fingerprint, jeiGeneration);
        if (manifest == null) {
            return null;
        }
        CategoryPlan category = manifest.categories.get(categoryUid);
        if (category == null
                || category.sourceCount != sourceRecipes.size()) {
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

        List<ActiveRecipe<T>> restored =
                new ArrayList<>(category.recipes.size());
        Set<String> acceptedIds =
                new HashSet<>(category.recipes.size() * 2);
        for (RecipePlan plan : category.recipes) {
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
        return new RestoreResult<>(List.copyOf(restored));
    }

    public static void record(
            LoginStateFingerprint.Snapshot fingerprint,
            String jeiGeneration,
            String categoryUid,
            int sourceCount,
            List<? extends ActiveRecipe<?>> acceptedRecipes
    ) {
        if (!enabled()
                || fingerprint == null
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

        prewarm();
        String cacheKey = cacheKey(
                fingerprint.serverKey(),
                jeiGeneration
        );
        Manifest next;
        synchronized (PersistentJeiRecipeIndexCache.class) {
            Map<String, Manifest> loaded = preload.join();
            Manifest current = loaded.get(cacheKey);
            Map<String, CategoryPlan> categories =
                    new LinkedHashMap<>();
            if (current != null
                    && current.fingerprint.equals(
                            fingerprint.value()
                    )) {
                categories.putAll(current.categories);
            }
            categories.put(
                    categoryUid,
                    new CategoryPlan(
                            sourceCount,
                            List.copyOf(recipes)
                    )
            );
            if (categories.size() > MAX_CATEGORIES) {
                return;
            }
            next = new Manifest(
                    cacheKey,
                    fingerprint.value(),
                    Map.copyOf(categories)
            );
            loaded.put(cacheKey, next);
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
        String cacheKey = cacheKey(
                fingerprint.serverKey(),
                jeiGeneration
        );
        Manifest manifest = preload.join().get(cacheKey);
        if (manifest == null) {
            reportMiss(
                    cacheKey,
                    "no compatible recipe index exists"
            );
            return null;
        }
        if (!manifest.fingerprint.equals(fingerprint.value())) {
            reportMiss(
                    cacheKey,
                    "recipes, tags, configs, mods, or cache schema changed"
            );
            return null;
        }
        return manifest;
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

    private static Map<String, Manifest> loadAll() {
        Map<String, Manifest> loaded =
                new ConcurrentHashMap<>();
        if (!Files.isDirectory(DIRECTORY)) {
            return loaded;
        }
        try (Stream<Path> paths = Files.list(DIRECTORY)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".bin"))
                    .sorted(Comparator.comparing(path ->
                            path.getFileName().toString()))
                    .limit(MAX_FILES)
                    .forEach(path -> {
                        Manifest manifest = read(path);
                        if (manifest != null) {
                            loaded.put(
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
        return loaded;
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
            int categoryCount = bounded(
                    input.readInt(),
                    MAX_CATEGORIES,
                    "category count"
            );
            Map<String, CategoryPlan> categories =
                    new LinkedHashMap<>();
            int totalRecipes = 0;
            int totalUids = 0;
            for (int categoryIndex = 0;
                 categoryIndex < categoryCount;
                 categoryIndex++) {
                String categoryUid = input.readUTF();
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
                        String role = input.readUTF();
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
                                uids.add(input.readUTF());
                            }
                            groups.add(List.copyOf(uids));
                        }
                        roles.put(role, List.copyOf(groups));
                    }
                    recipes.add(new RecipePlan(
                            recipeId,
                            Map.copyOf(roles)
                    ));
                }
                categories.put(
                        categoryUid,
                        new CategoryPlan(
                                sourceCount,
                                List.copyOf(recipes)
                        )
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
                    ".tmp"
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
                    CategoryPlan category =
                            manifest.categories.get(categoryUid);
                    output.writeUTF(categoryUid);
                    output.writeInt(category.sourceCount);
                    output.writeInt(category.recipes.size());
                    for (RecipePlan recipe : category.recipes) {
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
                output.writeUTF(manifestHash(manifest));
            }
            moveAtomically(temporary, target);
            int recipeCount = manifest.categories.values().stream()
                    .mapToInt(category -> category.recipes.size())
                    .sum();
            VHAccelerator.LOGGER.info(
                    "Persisted {} JEI recipe index plans across {} categories",
                    recipeCount,
                    manifest.categories.size()
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
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            update(digest, manifest.cacheKey);
            update(digest, manifest.fingerprint);
            manifest.categories.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(categoryEntry -> {
                        update(digest, categoryEntry.getKey());
                        CategoryPlan category =
                                categoryEntry.getValue();
                        update(
                                digest,
                                Integer.toString(category.sourceCount)
                        );
                        for (RecipePlan recipe : category.recipes) {
                            update(digest, recipe.recipeId);
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

    private static String cacheKey(
            String serverKey,
            String jeiGeneration
    ) {
        return serverKey + "-" + jeiGeneration;
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
            List<ActiveRecipe<T>> recipes
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

    private record Manifest(
            String cacheKey,
            String fingerprint,
            Map<String, CategoryPlan> categories
    ) {
    }
}
