package dev.hoyin1600p.vhaccelerator.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.ClientAssetFingerprint;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.fml.ModList;

/**
 * Client-thread lifecycle for deferred inventory-model baking.
 *
 * <p>Unbaked graphs and their materials are loaded eagerly before atlas
 * stitching, and a deferred graph must be fully present in the bakery's
 * unbaked cache. The one exception is a {@link TopLevelSession} on a
 * fingerprint-matched launch: certified inventory graphs are not loaded, but
 * their certified materials are stitched and the graph loads through the
 * bakery on first use. A deferred model bakes only when a consumer
 * reads it, which may be during in-world rendering; there is no title-screen
 * warmup and no drain on level join or dimension change. Bakes run only on
 * the render thread; other threads read the missing model for an unbaked
 * item. The registry is retired at the start of the next
 * {@code ModelManager#apply}, before the atlases it bakes against close.
 */
public final class DeferredItemModelBaking {
    private static final Set<String> EAGER_NAMESPACES = Set.of(
            "the_vault",
            "everycomp",
            "buildscape",
            "ctm"
    );

    private static volatile DeferredModelRegistry<ResourceLocation, BakedModel> current;
    /** Debug diagnostics for {@link #current}; null whenever debug is off. */
    private static volatile DeferredItemModelDiagnostics diagnostics;
    /** Warm stage of {@link #current}; null when every graph loaded. */
    private static volatile TopLevelSession liveSession;
    /** False while Forge's item cache holds unresolved deferred items. */
    private static volatile boolean itemCacheComplete = true;
    /** Installed in place of Forge's item model map; outlives registries. */
    private static volatile DeferredModelRegistry.ItemCache<
            Object, ResourceLocation, BakedModel> itemCache;
    private static Field locationsField;
    private static Field modelsField;
    private static boolean itemCacheReflectionFailed;

    private DeferredItemModelBaking() {
    }

    /** Evaluated on the client thread inside {@code uploadTextures}. */
    public static boolean activeForThisBake() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.deferItemModelBaking
                )) {
            return false;
        }
        ModList mods = ModList.get();
        if (mods == null || mods.isLoaded("ctm")) {
            // CTM reads every registry value during the bake event.
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level != null) {
            return false; // In-world reloads keep eager baking.
        }
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        return itemRenderer != null
                && itemRenderer.getItemModelShaper()
                        instanceof ItemModelMesherForge
                && itemCacheFieldsAvailable()
                && itemCacheReplaceable(itemRenderer.getItemModelShaper());
    }

    /** Only Forge's own map, or our cache already in its place, is replaced. */
    private static boolean itemCacheReplaceable(ItemModelShaper shaper) {
        try {
            Object models = modelsField.get(shaper);
            return models instanceof DeferredModelRegistry.ItemCache<?, ?, ?>
                    || (models != null && models.getClass() == HashMap.class);
        } catch (IllegalAccessException | IllegalArgumentException failure) {
            return false;
        }
    }

    public static Set<ResourceLocation> select(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Map<ResourceLocation, UnbakedModel> unbakedCache
    ) {
        UnbakedModel missing =
                unbakedCache.get(ModelBakery.MISSING_MODEL_LOCATION);
        return DeferredItemModelSelector.select(
                topLevelModels,
                DeferredItemModelBaking::eligibleKey,
                new DeferredItemModelSelector.Graph<ResourceLocation, UnbakedModel>() {
                    @Override
                    public UnbakedModel cached(ResourceLocation location) {
                        // Builtin generated/entity markers stay eager.
                        return location.getPath().startsWith("builtin/")
                                ? null
                                : unbakedCache.get(location);
                    }

                    @Override
                    public Collection<ResourceLocation> dependencies(
                            UnbakedModel node
                    ) {
                        return node.getDependencies();
                    }

                    @Override
                    public boolean isPlain(UnbakedModel node) {
                        return node != missing
                                && node.getClass() == BlockModel.class
                                && !((BlockModel) node).customData
                                        .hasCustomGeometry();
                    }
                }
        );
    }

    static boolean eligibleKey(ResourceLocation location) {
        if (!(location instanceof ModelResourceLocation model)
                || !"inventory".equals(model.getVariant())) {
            return false;
        }
        String namespace = location.getNamespace();
        return !EAGER_NAMESPACES.contains(namespace)
                && !namespace.startsWith("sophisticated");
    }

    public static Set<ResourceLocation> without(
            Set<ResourceLocation> source,
            Set<ResourceLocation> deferred
    ) {
        if (deferred.isEmpty()) {
            return source;
        }
        Set<ResourceLocation> remaining = new LinkedHashSet<>(source);
        remaining.removeAll(deferred);
        return remaining;
    }

    /**
     * Starts the warm top-level stage for one bakery, or returns null to
     * keep every graph eager. Only the initial launch reload qualifies, and
     * only when the bakery's material collection can stitch the manifest.
     */
    public static TopLevelSession beginTopLevelSession(
            ResourceManager resourceManager,
            boolean materialSink
    ) {
        if (!materialSink
                || resourceManager == null
                || LaunchTimer.isFinished()
                || !activeForThisBake()) {
            return null;
        }
        return new TopLevelSession(
                resourceManager,
                PersistentDeferredTopLevelManifest.readAsync()
        );
    }

    public static Map<ResourceLocation, BakedModel> install(
            Map<ResourceLocation, BakedModel> eager,
            Set<ResourceLocation> deferred,
            Function<ResourceLocation, BakedModel> baker
    ) {
        return install(eager, deferred, baker, null);
    }

    public static Map<ResourceLocation, BakedModel> install(
            Map<ResourceLocation, BakedModel> eager,
            Set<ResourceLocation> deferred,
            Function<ResourceLocation, BakedModel> baker,
            TopLevelSession session
    ) {
        DeferredItemModelDiagnostics debug =
                VHAcceleratorConfig.debugDiagnosticsEnabled()
                        ? new DeferredItemModelDiagnostics()
                        : null;
        DeferredModelRegistry<ResourceLocation, BakedModel> registry =
                new DeferredModelRegistry<>(
                        eager,
                        deferred,
                        debug == null ? baker : timed(baker, debug),
                        eager.get(ModelBakery.MISSING_MODEL_LOCATION),
                        (location, failure) -> VHAccelerator.LOGGER.warn(
                                "Unable to bake deferred model {}; using "
                                        + "the missing model",
                                location,
                                failure
                        ),
                        RenderSystem::isOnRenderThread
                );
        current = registry;
        diagnostics = debug;
        liveSession = session;
        itemCacheComplete = registry.initialDeferred() == 0;
        VHAccelerator.LOGGER.info(
                "Deferred baking of {} ordinary inventory item models "
                        + "until first use ({} graphs not loaded yet)",
                registry.initialDeferred(),
                session == null ? 0 : session.skipped().size()
        );
        if (debug != null) {
            logSnapshot(debug.snapshot("install", registry));
        }
        return registry;
    }

    private static Function<ResourceLocation, BakedModel> timed(
            Function<ResourceLocation, BakedModel> baker,
            DeferredItemModelDiagnostics debug
    ) {
        return location -> {
            long started = System.nanoTime();
            BakedModel model = null;
            try {
                model = baker.apply(location);
                return model;
            } finally {
                long elapsed = System.nanoTime() - started;
                if (debug.recordBake(location, elapsed)) {
                    VHAccelerator.LOGGER.info(
                            "Deferred item model {} baked on first use "
                                    + "in {} us ({}; {})",
                            location,
                            elapsed / 1_000L,
                            debug.phase(),
                            model == null ? "failed" : "ok"
                    );
                }
            }
        };
    }

    /** Called at the start of every ModelManager apply, before atlases close. */
    public static void retireCurrent() {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null) {
            return;
        }
        DeferredItemModelDiagnostics debug = diagnostics;
        TopLevelSession session = liveSession;
        current = null;
        diagnostics = null;
        liveSession = null;
        if (session != null) {
            session.closeLoading();
        }
        registry.retire();
        DeferredModelRegistry.ItemCache<Object, ResourceLocation, BakedModel>
                cache = itemCache;
        if (cache != null) {
            // Pending items read as missing until the reload's rebuild.
            cache.release();
        }
        // The reload's own item-cache rebuild runs after this apply.
        itemCacheComplete = true;
        VHAccelerator.LOGGER.info(
                "Retired deferred item models before reload: {} selected, "
                        + "{} baked on demand, {} failed, {} never baked, "
                        + "{} off-thread lookups",
                registry.initialDeferred(),
                registry.bakedOnDemand(),
                registry.failedBakes(),
                registry.unresolvedDeferred(),
                registry.offThreadLookups()
        );
        if (debug != null) {
            logSnapshot(debug.snapshot("reload retirement", registry));
        }
    }

    /**
     * Debug-only snapshots at the first menu, each world entry, and each
     * world exit. Returns immediately when debug diagnostics are off.
     */
    public static void observeFrame(Minecraft minecraft) {
        DeferredItemModelDiagnostics debug = diagnostics;
        if (debug == null) {
            return;
        }
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null) {
            return;
        }
        boolean inWorld = minecraft.level != null;
        boolean menu = !inWorld
                && minecraft.getOverlay() == null
                && minecraft.screen != null;
        String phase = debug.observe(menu, inWorld);
        if (phase != null) {
            logSnapshot(debug.snapshot(phase, registry));
        }
    }

    private static void logSnapshot(DeferredItemModelDiagnostics.Snapshot snapshot) {
        VHAccelerator.LOGGER.info("[debug] {}", snapshot.describe());
    }

    /**
     * Forge's rebuild would bake every item model immediately. Its item map
     * is replaced instead by an {@link DeferredModelRegistry.ItemCache} in
     * which unresolved deferred items stay pending, so no stale model
     * survives, and bake on their first {@code get}. Forge's
     * {@code getItemModel(Item)} reads that map directly, so the stack and
     * direct item lookups both resolve through it. Returns false to request
     * Forge's original rebuild.
     */
    @SuppressWarnings("unchecked")
    public static boolean rebuildItemCache(ItemModelShaper shaper) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null
                || registry.isRetired()
                || itemCacheComplete
                || !(shaper instanceof ItemModelMesherForge)
                || !itemCacheFieldsAvailable()
                || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        Map<Object, ModelResourceLocation> locations;
        DeferredModelRegistry.ItemCache<Object, ResourceLocation, BakedModel>
                cache;
        try {
            locations = (Map<Object, ModelResourceLocation>)
                    locationsField.get(shaper);
            cache = installItemCache(shaper);
        } catch (IllegalAccessException | IllegalArgumentException
                 | ClassCastException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not install the deferred item model cache; "
                            + "baking item models eagerly",
                    failure
            );
            return false;
        }
        if (cache == null) {
            return false;
        }
        ModelManager manager = shaper.getModelManager();
        cache.bind(registry);
        for (Map.Entry<Object, ModelResourceLocation> entry
                : locations.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (registry.isUnresolvedDeferred(entry.getValue())) {
                cache.defer(entry.getKey(), entry.getValue());
            } else {
                cache.put(entry.getKey(), manager.getModel(entry.getValue()));
            }
        }
        itemCache = cache;
        return true;
    }

    /**
     * Returns the installed item cache, replacing Forge's own map on first
     * use. Anything other than Forge's plain {@code HashMap} belongs to
     * another mod and is left alone (null).
     */
    @SuppressWarnings("unchecked")
    private static DeferredModelRegistry.ItemCache<
            Object, ResourceLocation, BakedModel> installItemCache(
            ItemModelShaper shaper
    ) throws IllegalAccessException {
        Object models = modelsField.get(shaper);
        if (models instanceof DeferredModelRegistry.ItemCache<?, ?, ?> existing) {
            return (DeferredModelRegistry.ItemCache<
                    Object, ResourceLocation, BakedModel>) existing;
        }
        if (models == null || models.getClass() != HashMap.class) {
            VHAccelerator.LOGGER.warn(
                    "Forge's item model map was replaced by {}; item model "
                            + "baking stays eager",
                    models == null ? null : models.getClass().getName()
            );
            return null;
        }
        ModelManager manager = shaper.getModelManager();
        DeferredModelRegistry.ItemCache<Object, ResourceLocation, BakedModel>
                cache = new DeferredModelRegistry.ItemCache<>(
                        (Map<Object, BakedModel>) models,
                        manager::getMissingModel,
                        RenderSystem::isOnRenderThread
                );
        // The field is final; setAccessible permits this instance write.
        modelsField.set(shaper, cache);
        return cache;
    }

    /**
     * Safety net for the stack lookup if another mod displaced the item
     * cache after installation: resolves a deferred item without writing to
     * the foreign map. With the cache in place the lookup already resolved.
     */
    @SuppressWarnings("unchecked")
    public static BakedModel resolveItemModel(
            ItemModelShaper shaper,
            ItemStack stack,
            BakedModel result
    ) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (itemCacheComplete
                || registry == null
                || registry.isRetired()
                || stack == null
                || locationsField == null
                || modelsField == null
                || !(shaper instanceof ItemModelMesherForge)
                || result != shaper.getModelManager().getMissingModel()) {
            return result;
        }
        try {
            Object key = stack.getItem().delegate;
            Object models = modelsField.get(shaper);
            if (key == null
                    || models instanceof DeferredModelRegistry.ItemCache<?, ?, ?>
                    || ((Map<Object, BakedModel>) models).get(key) != null) {
                return result;
            }
            ModelResourceLocation location =
                    ((Map<Object, ModelResourceLocation>) locationsField
                            .get(shaper)).get(key);
            if (location == null || !registry.isDeferred(location)) {
                return result;
            }
            BakedModel model = shaper.getModelManager().getModel(location);
            return model == null ? result : model;
        } catch (IllegalAccessException | IllegalArgumentException
                 | ClassCastException failure) {
            return result;
        }
    }

    /** Implemented by the bakery mixin that owns a warm top-level stage. */
    public interface TopLevelOwner {
        TopLevelSession vhaccelerator$topLevelSession();
    }

    /**
     * Warm top-level stage of one bakery. On a fingerprint-matched launch,
     * {@code loadTopLevel} is skipped for certified inventory keys and their
     * manifest materials join the atlas stitch set; the first real lookup
     * loads the graph through the bakery's normal {@code getModel}. On any
     * other launch nothing is skipped and the eager graphs are certified for
     * the next launch.
     *
     * <p>Loading-thread state is published to the render thread by the
     * reload's own completion; skipped keys never change after stitching.
     */
    public static final class TopLevelSession {
        private static final int MAX_MISMATCH_WARNINGS = 16;

        private final ResourceManager resourceManager;
        private final CompletableFuture<PersistentDeferredTopLevelManifest.Manifest>
                pending;
        private final Set<ResourceLocation> skipped = new LinkedHashSet<>();
        private final Map<String, List<Material>> certified = new HashMap<>();
        private final AtomicInteger mismatchWarnings = new AtomicInteger();
        private boolean resolved;
        private boolean warm;
        private String fingerprint;
        /** Set once material collection ran; no key is skipped after it. */
        private volatile boolean skipsClosed;
        private volatile boolean materialsAdded;
        private volatile boolean loadingClosed;

        private TopLevelSession(
                ResourceManager resourceManager,
                CompletableFuture<PersistentDeferredTopLevelManifest.Manifest>
                        pending
        ) {
            this.resourceManager = resourceManager;
            this.pending = pending;
        }

        /** Validates the manifest once; any doubt keeps the launch eager. */
        private boolean resolve() {
            if (resolved) {
                return warm;
            }
            resolved = true;
            PersistentDeferredTopLevelManifest.Manifest manifest;
            try {
                manifest = pending.join();
            } catch (RuntimeException failure) {
                manifest = null;
            }
            fingerprint = ClientAssetFingerprint.current(resourceManager);
            if (fingerprint == null) {
                return false;
            }
            if (manifest == null) {
                VHAccelerator.LOGGER.info(
                        "No usable deferred item top-level manifest; "
                                + "loading every model graph eagerly"
                );
                return false;
            }
            if (!manifest.matches(fingerprint)) {
                ClientAssetFingerprint.reportMismatch(
                        "Deferred item top-level",
                        manifest.fingerprint(),
                        fingerprint
                );
                return false;
            }
            if (!PersistentDeferredTopLevelManifest.BLOCK_ATLAS.equals(
                    TextureAtlas.LOCATION_BLOCKS.toString())) {
                return false;
            }
            Map<String, List<Material>> parsed =
                    new HashMap<>(Math.max(16, manifest.size() * 2));
            try {
                Map<String, ResourceLocation> textures = new HashMap<>();
                for (String key : manifest.keys()) {
                    List<PersistentDeferredTopLevelManifest.MaterialId> ids =
                            manifest.materials(key);
                    List<Material> materials = new ArrayList<>(ids.size());
                    for (PersistentDeferredTopLevelManifest.MaterialId id
                            : ids) {
                        materials.add(new Material(
                                TextureAtlas.LOCATION_BLOCKS,
                                textures.computeIfAbsent(
                                        id.texture(),
                                        ResourceLocation::new
                                )
                        ));
                    }
                    parsed.put(key, List.copyOf(materials));
                }
            } catch (RuntimeException failure) {
                VHAccelerator.LOGGER.warn(
                        "The deferred item top-level manifest has invalid "
                                + "identifiers; loading every model eagerly",
                        failure
                );
                return false;
            }
            certified.putAll(parsed);
            warm = true;
            VHAccelerator.LOGGER.info(
                    "Validated deferred item top-level manifest: {} "
                            + "certified inventory models, {} materials",
                    manifest.size(),
                    manifest.totalMaterials()
            );
            return true;
        }

        /**
         * True when {@code loadTopLevel} may be skipped for {@code location}.
         * Never true once material collection has run.
         */
        public boolean skip(
                ModelResourceLocation location,
                Map<ResourceLocation, UnbakedModel> topLevelModels,
                Map<ResourceLocation, UnbakedModel> unbakedCache
        ) {
            if (location == null
                    || loadingClosed
                    || skipsClosed
                    || !eligibleKey(location)
                    || !resolve()
                    || !certified.containsKey(location.toString())
                    || topLevelModels.containsKey(location)
                    || unbakedCache.containsKey(location)
                    || !registeredItem(location)) {
                return false;
            }
            skipped.add(location);
            return true;
        }

        /**
         * Called once by material collection, before atlas stitching. Returns
         * a node that contributes every skipped model's certified materials,
         * or null when nothing was skipped.
         */
        public UnbakedModel materialNode() {
            skipsClosed = true;
            materialsAdded = true;
            if (skipped.isEmpty()) {
                return null;
            }
            Set<Material> union = new LinkedHashSet<>();
            int references = 0;
            for (ResourceLocation location : skipped) {
                List<Material> materials = certified.get(location.toString());
                references += materials.size();
                union.addAll(materials);
            }
            VHAccelerator.LOGGER.info(
                    "Skipped {} certified inventory item top-level model "
                            + "loads; added their {} manifest material "
                            + "references ({} distinct) before atlas "
                            + "stitching",
                    skipped.size(),
                    references,
                    union.size()
            );
            return new ManifestMaterials(List.copyOf(union));
        }

        public boolean materialsAdded() {
            return materialsAdded;
        }

        public Set<ResourceLocation> skipped() {
            return Collections.unmodifiableSet(skipped);
        }

        public boolean isSkipped(ResourceLocation location) {
            return skipped.contains(location);
        }

        void closeLoading() {
            loadingClosed = true;
        }

        /**
         * Fallback when deferral is unavailable at bake time: loads every
         * skipped graph now, exactly as {@code loadTopLevel} would have, so
         * no key is absent. Returns the number restored.
         */
        public int restoreEagerly(
                Map<ResourceLocation, UnbakedModel> topLevelModels,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            int restored = skipped.size();
            if (restored == 0) {
                return 0;
            }
            VHAccelerator.LOGGER.warn(
                    "Deferred item baking is unavailable at bake time; "
                            + "loading {} skipped inventory model graphs "
                            + "eagerly{}",
                    restored,
                    materialsAdded
                            ? ""
                            : " (their textures were not stitched)"
            );
            for (ResourceLocation location : skipped) {
                UnbakedModel model = getter.apply(location);
                topLevelModels.put(location, model);
                // Resolves parent links, as the eager material pass does.
                model.getMaterials(getter, new HashSet<>());
            }
            skipped.clear();
            return restored;
        }

        /**
         * First real lookup of a skipped key: loads its graph through the
         * bakery's normal {@code getModel}, resolves parents through
         * {@code getMaterials}, then bakes. Null once the new ModelManager
         * applies and retires this generation.
         */
        public BakedModel loadAndBake(
                ResourceLocation location,
                Function<ResourceLocation, UnbakedModel> getter,
                Map<ResourceLocation, UnbakedModel> unbakedCache,
                Function<ResourceLocation, BakedModel> baker
        ) {
            if (loadingClosed) {
                return null; // The reload's packs replace this generation.
            }
            UnbakedModel model = getter.apply(location);
            Collection<Material> loaded =
                    model.getMaterials(getter, new HashSet<>());
            verifyLoaded(location, model, loaded, unbakedCache);
            return baker.apply(location);
        }

        private void verifyLoaded(
                ResourceLocation location,
                UnbakedModel model,
                Collection<Material> loaded,
                Map<ResourceLocation, UnbakedModel> unbakedCache
        ) {
            List<Material> stitched = certified.get(location.toString());
            boolean plain = select(Map.of(location, model), unbakedCache)
                    .contains(location);
            boolean covered = stitched != null
                    && new HashSet<>(stitched).containsAll(loaded);
            if ((!plain || !covered)
                    && mismatchWarnings.getAndIncrement()
                            < MAX_MISMATCH_WARNINGS) {
                VHAccelerator.LOGGER.warn(
                        "Deferred inventory model {} differs from its "
                                + "certified graph (plain={}, textures "
                                + "stitched={}); it bakes normally",
                        location,
                        plain,
                        covered
                );
            }
        }

        /**
         * On a launch that skipped nothing, certifies every eligible eager
         * graph and writes the manifest asynchronously for the next launch.
         */
        public void recordIfCold(
                Map<ResourceLocation, UnbakedModel> topLevelModels,
                Map<ResourceLocation, UnbakedModel> unbakedCache,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            skipsClosed = true;
            resolve();
            if (warm || fingerprint == null || loadingClosed) {
                return;
            }
            long started = System.nanoTime();
            PersistentDeferredTopLevelManifest.Builder builder =
                    new PersistentDeferredTopLevelManifest.Builder();
            for (ResourceLocation location
                    : select(topLevelModels, unbakedCache)) {
                if (!(location instanceof ModelResourceLocation model)
                        || !registeredItem(model)) {
                    continue;
                }
                List<PersistentDeferredTopLevelManifest.MaterialId> ids =
                        new ArrayList<>();
                try {
                    for (Material material : topLevelModels.get(location)
                            .getMaterials(getter, new HashSet<>())) {
                        ids.add(new PersistentDeferredTopLevelManifest
                                .MaterialId(
                                        material.atlasLocation().toString(),
                                        material.texture().toString()
                                ));
                    }
                } catch (RuntimeException | LinkageError failure) {
                    ids = null; // Rejected below as incomplete.
                }
                builder.add(location.toString(), ids);
            }
            PersistentDeferredTopLevelManifest.Manifest manifest =
                    builder.build(fingerprint);
            VHAccelerator.LOGGER.info(
                    "Certified {} inventory top-level model graphs for the "
                            + "next matching launch ({} ineligible) in {} ms",
                    builder.size(),
                    builder.rejected(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            if (manifest != null) {
                PersistentDeferredTopLevelManifest.writeAsync(manifest);
            }
        }

        private static boolean registeredItem(ModelResourceLocation location) {
            return "inventory".equals(location.getVariant())
                    && Registry.ITEM.containsKey(new ResourceLocation(
                            location.getNamespace(),
                            location.getPath()
                    ));
        }
    }

    /**
     * Contributes certified materials to the stitch set. Never a top-level
     * model, so it is never baked.
     */
    private static final class ManifestMaterials implements UnbakedModel {
        private final List<Material> materials;

        private ManifestMaterials(List<Material> materials) {
            this.materials = materials;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return List.of();
        }

        @Override
        public Collection<Material> getMaterials(
                Function<ResourceLocation, UnbakedModel> modelGetter,
                Set<Pair<String, String>> missingTextures
        ) {
            return materials;
        }

        @Override
        public BakedModel bake(
                ModelBakery bakery,
                Function<Material, TextureAtlasSprite> spriteGetter,
                ModelState state,
                ResourceLocation location
        ) {
            return null;
        }
    }

    private static synchronized boolean itemCacheFieldsAvailable() {
        if (locationsField != null && modelsField != null) {
            return true;
        }
        if (itemCacheReflectionFailed) {
            return false;
        }
        try {
            Field locations =
                    ItemModelMesherForge.class.getDeclaredField("locations");
            Field models =
                    ItemModelMesherForge.class.getDeclaredField("models");
            if (!Map.class.isAssignableFrom(locations.getType())
                    || !Map.class.isAssignableFrom(models.getType())) {
                throw new NoSuchFieldException("unexpected field types");
            }
            locations.setAccessible(true);
            models.setAccessible(true);
            locationsField = locations;
            modelsField = models;
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            itemCacheReflectionFailed = true;
            VHAccelerator.LOGGER.warn(
                    "Forge's item model cache layout is unrecognized; "
                            + "item model baking stays eager",
                    failure
            );
            return false;
        }
    }
}
