package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.DynamicModelGuard;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists material dependency results for ordinary JSON model graphs.
 *
 * <p>The cache stores identifiers only. Live model objects, Forge geometry,
 * textures, and baked models are never serialized. A cache hit is accepted
 * only after the active model graph has been checked for custom loaders and
 * its parent links have been rebound.</p>
 */
public final class PersistentModelMaterialCache {
    private static final int MAGIC = 0x56484154;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_MODELS = 500_000;
    private static final int MAX_MATERIALS_PER_MODEL = 4_096;
    private static final int MAX_TOTAL_MATERIALS = 5_000_000;
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("client-assets");
    private static final Path CACHE_FILE =
            DIRECTORY.resolve("model-materials-v1.bin.gz");
    private static final Executor WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "VH Accelerator material cache writer"
                );
                thread.setDaemon(true);
                return thread;
            });
    private static final ThreadLocal<Session> ACTIVE_SESSION =
            new ThreadLocal<>();

    private static CompletableFuture<CachedFile> preload;
    private static boolean preloadStarted;

    private PersistentModelMaterialCache() {
    }

    public static synchronized void prewarm() {
        ClientAssetFingerprint.prewarm();
        if (preloadStarted) {
            return;
        }
        preloadStarted = true;
        preload = CompletableFuture.supplyAsync(
                PersistentModelMaterialCache::read,
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "VH Accelerator material cache reader"
                    );
                    thread.setDaemon(true);
                    thread.start();
                }
        );
    }

    public static void begin(ResourceManager resourceManager) {
        ACTIVE_SESSION.remove();
        if (!enabled() || LaunchTimer.isFinished()) {
            return;
        }

        prewarm();
        String fingerprint =
                ClientAssetFingerprint.current(resourceManager);
        if (fingerprint == null) {
            return;
        }

        CachedFile cached;
        synchronized (PersistentModelMaterialCache.class) {
            cached = preload == null ? null : preload.join();
        }
        boolean restored = cached != null
                && cached.fingerprint.equals(fingerprint);
        Map<ResourceLocation, List<Material>> materials =
                restored ? new HashMap<>(cached.materials) : new HashMap<>();
        Session session = new Session(
                fingerprint,
                materials
        );
        ACTIVE_SESSION.set(session);
        if (restored) {
            VHAccelerator.LOGGER.info(
                    "Restored {} safe model material lists from the "
                            + "persistent client asset cache",
                    materials.size()
            );
        }
    }

    public static Collection<Material> restore(
            BlockModel model,
            java.util.function.Function<ResourceLocation, UnbakedModel>
                    modelGetter
    ) {
        Session session = ACTIVE_SESSION.get();
        if (session == null) {
            return null;
        }
        ResourceLocation location = modelLocation(model);
        if (location == null) {
            return null;
        }
        List<Material> materials = session.materials.get(location);
        if (materials == null) {
            return null;
        }
        if (!session.modelGraph.prepare(model, modelGetter)) {
            session.rejected++;
            return null;
        }
        session.hits++;
        return materials;
    }

    public static void record(
            BlockModel model,
            java.util.function.Function<ResourceLocation, UnbakedModel>
                    modelGetter,
            Collection<Material> materials
    ) {
        Session session = ACTIVE_SESSION.get();
        if (session == null || materials == null || materials.isEmpty()) {
            return;
        }
        ResourceLocation location = modelLocation(model);
        if (location == null
                || session.materials.containsKey(location)
                || materials.size() > MAX_MATERIALS_PER_MODEL
                || containsMissingTexture(materials)
                || session.materials.size() >= MAX_MODELS
                || !session.modelGraph.isSafe(model, modelGetter)) {
            return;
        }
        List<Material> stable = List.copyOf(materials);
        session.materials.put(location, stable);
        session.captured++;
    }

    public static void finish() {
        Session session = ACTIVE_SESSION.get();
        ACTIVE_SESSION.remove();
        if (session == null) {
            return;
        }

        VHAccelerator.LOGGER.info(
                "Persistent model material cache: {} hits, {} captured, "
                        + "{} rejected",
                session.hits,
                session.captured,
                session.rejected
        );
        if (session.captured == 0) {
            return;
        }

        Map<ResourceLocation, List<Material>> stable =
                Map.copyOf(session.materials);
        if (stable.size() > MAX_MODELS
                || countMaterials(stable) > MAX_TOTAL_MATERIALS) {
            VHAccelerator.LOGGER.warn(
                    "Model material results exceeded persistent-cache safety "
                            + "limits; the cache file will not be updated"
            );
            return;
        }
        CachedFile cached = new CachedFile(session.fingerprint, stable);
        CompletableFuture.runAsync(() -> write(cached), WRITER);
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
            if (count < 0 || count > MAX_MODELS) {
                throw new IOException(
                        "Invalid material cache model count " + count
                );
            }

            Map<ResourceLocation, List<Material>> materials =
                    new HashMap<>(Math.max(16, count * 2));
            long totalMaterials = 0L;
            for (int index = 0; index < count; index++) {
                ResourceLocation model =
                        readLocation(input, "model");
                int materialCount = input.readInt();
                if (materialCount < 0
                        || materialCount > MAX_MATERIALS_PER_MODEL) {
                    throw new IOException(
                            "Invalid material-list size " + materialCount
                    );
                }
                totalMaterials += materialCount;
                if (totalMaterials > MAX_TOTAL_MATERIALS) {
                    throw new IOException(
                            "Material cache exceeds its entry limit"
                    );
                }

                List<Material> modelMaterials =
                        new ArrayList<>(materialCount);
                for (int materialIndex = 0;
                        materialIndex < materialCount;
                        materialIndex++) {
                    ResourceLocation atlas =
                            readLocation(input, "atlas");
                    ResourceLocation texture =
                            readLocation(input, "texture");
                    modelMaterials.add(new Material(atlas, texture));
                }
                if (input.readInt() != modelMaterials.hashCode()) {
                    throw new IOException(
                            "Material cache entry checksum mismatch"
                    );
                }
                materials.put(model, List.copyOf(modelMaterials));
            }
            if (input.read() != -1) {
                throw new IOException(
                        "Material cache contains trailing data"
                );
            }
            VHAccelerator.LOGGER.info(
                    "Preloaded {} persistent model material lists in {} ms",
                    materials.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return new CachedFile(fingerprint, Map.copyOf(materials));
        } catch (EOFException failure) {
            VHAccelerator.LOGGER.warn(
                    "The persistent model material cache was truncated; "
                            + "live model discovery will be used",
                    failure
            );
            return null;
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not read the persistent model material cache; "
                            + "live model discovery will be used",
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
            List<Map.Entry<ResourceLocation, List<Material>>> entries =
                    new ArrayList<>(cached.materials.entrySet());
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
                for (Map.Entry<ResourceLocation, List<Material>> entry :
                        entries) {
                    output.writeUTF(entry.getKey().toString());
                    output.writeInt(entry.getValue().size());
                    for (Material material : entry.getValue()) {
                        output.writeUTF(
                                material.atlasLocation().toString()
                        );
                        output.writeUTF(material.texture().toString());
                    }
                    output.writeInt(entry.getValue().hashCode());
                }
            }
            moveAtomically(temporary, CACHE_FILE);
            VHAccelerator.LOGGER.info(
                    "Saved {} safe model material lists to the persistent "
                            + "client asset cache",
                    entries.size()
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the persistent model material cache",
                    failure
            );
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The incomplete temporary file is harmless.
            }
        }
    }

    private static boolean containsMissingTexture(
            Collection<Material> materials
    ) {
        ResourceLocation missing = MissingTextureAtlasSprite.getLocation();
        for (Material material : materials) {
            if (missing.equals(material.texture())) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation readLocation(
            DataInputStream input,
            String role
    ) throws IOException {
        String encoded = input.readUTF();
        ResourceLocation location =
                ResourceLocation.tryParse(encoded);
        if (location == null) {
            throw new IOException(
                    "Invalid " + role + " identifier in material cache"
            );
        }
        return location;
    }

    private static ResourceLocation modelLocation(BlockModel model) {
        if (model.name == null || model.name.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(model.name);
    }

    private static long countMaterials(
            Map<ResourceLocation, List<Material>> materials
    ) {
        long total = 0L;
        for (List<Material> value : materials.values()) {
            total += value.size();
        }
        return total;
    }

    private static boolean enabled() {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .persistentModelMaterialCache
                );
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

    private record CachedFile(
            String fingerprint,
            Map<ResourceLocation, List<Material>> materials
    ) {
    }

    private static final class Session {
        private final String fingerprint;
        private final Map<ResourceLocation, List<Material>> materials;
        private final DynamicModelGuard.PreparedGraph modelGraph =
                DynamicModelGuard.preparedGraph();
        private int hits;
        private int captured;
        private int rejected;

        private Session(
                String fingerprint,
                Map<ResourceLocation, List<Material>> materials
        ) {
            this.fingerprint = fingerprint;
            this.materials = materials;
        }
    }
}
