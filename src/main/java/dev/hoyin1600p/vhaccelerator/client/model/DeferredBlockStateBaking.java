package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lifecycle of deferred block-state model baking.
 *
 * <p>Only the bake is deferred. Every block-state graph is still loaded,
 * parent-bound and material-collected before atlas stitching, so all of its
 * textures are in the atlas. A key qualifies only when its whole closure is
 * a vanilla {@code MultiVariant} or {@code MultiPart} over ordinary JSON
 * {@code BlockModel}s already in the unbaked cache with their parents bound;
 * baking it then only reads bakery state, exactly as the parallel top-level
 * bake does on worker threads. The bakery's unbaked and baked caches are
 * switched to concurrent maps before the registry is published, so a
 * first-use bake may run on a chunk-compile worker while the render thread
 * uses the bakery.
 *
 * <p>{@code BlockModelShaper}'s lookup is rebuilt as a
 * {@link LazyStateModelCache}: deferred states resolve through
 * {@code ModelManager#getModel} on first read. There is no eager warmup at the
 * menu, join, or dimension change; initial chunks can cause first-use bakes
 * before the first playable frame. The registry is retired at the
 * start of the next {@code ModelManager#apply}, before its atlases close.
 */
public final class DeferredBlockStateBaking {
    private static final Set<String> EAGER_NAMESPACES = Set.of(
            "the_vault",
            "everycomp",
            "buildscape",
            "ctm"
    );
    private static final int MAX_FIRST_USE_LOGS = 32;

    /**
     * Approximate registry bytes per deferred key (linked-hash-set entry and
     * table share) and per resolved key (hash-map entry and table share).
     */
    private static final long REGISTRY_BYTES_PER_KEY = 48L;
    private static final long RESOLVED_BYTES_PER_KEY = 40L;

    private static volatile ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel>
            current;
    /** Non-null only while a registry installed with debug on is live. */
    private static volatile DeferredBlockStateFirstUseDiagnostics
            firstUseDiagnostics;

    // Phase timings of the latest bake, reported once by buildShaperCache.
    private static volatile long selectNanos;
    private static volatile long copyNanos;
    private static volatile long registryNanos;

    private DeferredBlockStateBaking() {
    }

    /** Evaluated on the client thread inside {@code uploadTextures}. */
    public static boolean activeForThisBake() {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || VHAcceleratorConfig.compareModeEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.deferBlockStateModelBaking
                )) {
            return false;
        }
        if (!DeferredModelCompatibility.allowsDeferral()) {
            // CTM reads every value in the bake event. The mixin plugin
            // separately excludes this bakery path when ModernFix's own
            // dynamic-resource provider is active or cannot be verified off.
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level == null;
    }

    static boolean eligibleKey(ResourceLocation location) {
        if (!(location instanceof ModelResourceLocation model)
                || "inventory".equals(model.getVariant())) {
            return false;
        }
        String namespace = location.getNamespace();
        return !EAGER_NAMESPACES.contains(namespace)
                && !namespace.startsWith("sophisticated");
    }

    /** Certified plain block-state keys; cache-only, never loads a model. */
    public static Set<ResourceLocation> select(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Map<ResourceLocation, UnbakedModel> unbakedCache
    ) {
        long started = System.nanoTime();
        try {
            return selectCertified(topLevelModels, unbakedCache);
        } finally {
            selectNanos = System.nanoTime() - started;
        }
    }

    private static Set<ResourceLocation> selectCertified(
            Map<ResourceLocation, UnbakedModel> topLevelModels,
            Map<ResourceLocation, UnbakedModel> unbakedCache
    ) {
        UnbakedModel missing =
                unbakedCache.get(ModelBakery.MISSING_MODEL_LOCATION);
        return DeferredItemModelSelector.select(
                topLevelModels,
                DeferredBlockStateBaking::eligibleKey,
                new DeferredItemModelSelector.Graph<ResourceLocation, UnbakedModel>() {
                    @Override
                    public UnbakedModel cached(ResourceLocation location) {
                        return location == null
                                || location.getPath().startsWith("builtin/")
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
                        return plainNode(node, missing);
                    }
                }
        );
    }

    static boolean plainNode(UnbakedModel node, UnbakedModel missing) {
        if (node == null || node == missing) {
            return false;
        }
        Class<?> type = node.getClass();
        if (type == MultiVariant.class || type == MultiPart.class) {
            return true;
        }
        if (type != BlockModel.class) {
            return false;
        }
        BlockModel model = (BlockModel) node;
        // An unbound parent would be resolved, mutating the graph, during bake.
        return !model.customData.hasCustomGeometry()
                && (model.getParentLocation() == null || model.parent != null);
    }

    /**
     * Copies a bakery cache into a concurrent map that tolerates vanilla's
     * null-key probes; a null value removes the key.
     */
    public static <K, V> Map<K, V> concurrentCopy(Map<K, V> source) {
        if (source instanceof NullTolerantConcurrentMap<K, V> already) {
            return already;
        }
        NullTolerantConcurrentMap<K, V> copy =
                new NullTolerantConcurrentMap<>(Math.max(16, source.size()));
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return copy;
    }

    /** Time spent switching the bakery caches to concurrent copies. */
    public static void recordCacheCopy(long nanos) {
        copyNanos = nanos;
    }

    public static Map<ResourceLocation, BakedModel> install(
            Map<ResourceLocation, BakedModel> eager,
            Set<ResourceLocation> deferred,
            Function<ResourceLocation, BakedModel> baker
    ) {
        long started = System.nanoTime();
        boolean debug = VHAcceleratorConfig.debugDiagnosticsEnabled();
        AtomicInteger logged = new AtomicInteger();
        // Set once the registry exists; bakes cannot start before publication.
        AtomicReference<DeferredBlockStateFirstUseDiagnostics> diagnosticsSlot =
                debug ? new AtomicReference<>() : null;
        ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel> registry =
                new ConcurrentDeferredModelRegistry<>(
                        eager,
                        deferred,
                        baker,
                        eager.get(ModelBakery.MISSING_MODEL_LOCATION),
                        (location, nanos, failed, failure) -> {
                            if (failed) {
                                VHAccelerator.LOGGER.warn(
                                        "Unable to bake deferred block-state "
                                                + "model {}; using the missing model",
                                        location,
                                        failure
                                );
                            }
                            if (diagnosticsSlot == null) {
                                return; // Debug off: no timing aggregation.
                            }
                            DeferredBlockStateFirstUseDiagnostics recorder =
                                    diagnosticsSlot.get();
                            if (recorder != null) {
                                recorder.recordBake(location, nanos);
                            }
                            if (!failed && logged.getAndIncrement()
                                    < MAX_FIRST_USE_LOGS) {
                                VHAccelerator.LOGGER.info(
                                        "[debug] Deferred block-state model {} "
                                                + "baked on first use in {} us "
                                                + "on {}",
                                        location,
                                        nanos / 1_000L,
                                        Thread.currentThread().getName()
                                );
                            }
                        }
                );
        DeferredBlockStateFirstUseDiagnostics diagnostics = null;
        if (diagnosticsSlot != null) {
            diagnostics = new DeferredBlockStateFirstUseDiagnostics(registry);
            diagnosticsSlot.set(diagnostics);
        }
        firstUseDiagnostics = diagnostics;
        current = registry;
        registryNanos = System.nanoTime() - started;
        if (debug) {
            VHAccelerator.LOGGER.info(
                    "[debug] Deferred baking of {} plain block-state models "
                            + "until first use",
                    registry.initialDeferred()
            );
        }
        return registry;
    }

    /** Called at the start of every ModelManager apply, before atlases close. */
    public static void retireCurrent() {
        // A pending first-use snapshot would describe a retired registry.
        firstUseDiagnostics = null;
        ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel> registry =
                current;
        if (registry == null) {
            return;
        }
        current = null;
        registry.retire(); // Waits for bakes in progress on any thread.
        int failed = registry.failedBakes();
        if (failed == 0 && !VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return;
        }
        // One line per reload; failures are always reported.
        VHAccelerator.LOGGER.info(
                "Retired deferred block-state models before reload: {} "
                        + "selected, {} baked on demand, {} failed, {} never "
                        + "baked, {} removed, {} shared waits, {} retired "
                        + "lookups, registry ~{} KiB",
                registry.initialDeferred(),
                registry.bakedOnDemand(),
                failed,
                registry.unresolvedDeferred(),
                registry.removedDeferred(),
                registry.sharedWaits(),
                registry.retiredLookups(),
                registryBytes(registry) / 1024L
        );
    }

    /**
     * Render thread, on each playable world frame. Logs the first-frame and
     * about five-second first-use snapshots of the level's session. Only a
     * volatile read when debug diagnostics were off at install.
     */
    public static void observePlayableFrame(Object level) {
        DeferredBlockStateFirstUseDiagnostics diagnostics = firstUseDiagnostics;
        if (diagnostics == null || level == null) {
            return;
        }
        DeferredBlockStateFirstUseDiagnostics.Snapshot snapshot =
                diagnostics.observeFrame(level, System.nanoTime());
        if (snapshot != null) {
            VHAccelerator.LOGGER.info("[debug] {}", snapshot.describe());
        }
    }

    /** Disconnect: drops any pending first-use snapshot. */
    public static void worldExited() {
        DeferredBlockStateFirstUseDiagnostics diagnostics = firstUseDiagnostics;
        if (diagnostics != null) {
            diagnostics.cancelSession();
        }
    }

    /** Approximate deferred-key overhead of the registry, excluding models. */
    static long registryBytes(
            ConcurrentDeferredModelRegistry<?, ?> registry
    ) {
        int deferred = registry.initialDeferred() - registry.removedDeferred();
        int resolved = deferred - registry.unresolvedDeferred();
        return REGISTRY_BYTES_PER_KEY * deferred
                + RESOLVED_BYTES_PER_KEY * resolved;
    }

    /**
     * Builds the block render lookup without baking deferred models, or
     * returns null to keep the normal rebuild. Client thread only.
     */
    public static Map<BlockState, BakedModel> buildShaperCache(
            ModelManager manager
    ) {
        ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel> registry =
                current;
        if (registry == null || registry.isRetired()
                || registry.unresolvedDeferred() == 0) {
            return null;
        }
        long started = System.nanoTime();
        List<BlockState> states = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            states.addAll(block.getStateDefinition().getPossibleStates());
        }
        long collected = System.nanoTime();
        // Only a model the registry has published may be kept: a retired or
        // reentrant lookup returns the missing model without publishing it.
        LazyStateModelCache<BlockState, ModelResourceLocation, BakedModel> cache =
                new LazyStateModelCache<>(
                        states,
                        BlockModelShaper::stateToModelLocation,
                        manager::getModel,
                        location -> !registry.isRetired()
                                && !registry.isUnresolvedDeferred(location)
                );
        long indexed = System.nanoTime();
        AtomicInteger deferredStates = new AtomicInteger();
        // Every call here is thread-safe; resolved lookups never bake.
        // Deferred states stay pending with no per-state allocation.
        SharedWorkers.forRange(cache.fixedSize(), position -> {
            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(cache.keyAt(position));
            if (registry.isUnresolvedDeferred(location)) {
                deferredStates.incrementAndGet();
            } else {
                cache.resolveAt(position, manager.getModel(location));
            }
        });
        long filled = System.nanoTime();
        int pending = deferredStates.get();
        VHAccelerator.LOGGER.info(
                "Built {} block model render lookups with {} of {} deferred "
                        + "block-state models left for first-use baking in {} ms",
                cache.fixedSize(),
                pending,
                registry.initialDeferred(),
                (filled - started) / 1_000_000L
        );
        if (VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            long cacheBytes = cache.estimatedBytes();
            VHAccelerator.LOGGER.info(
                    "[debug] Deferred block-state phases: select {} ms, "
                            + "bakery cache copy {} ms, registry {} ms, state "
                            + "collect {} ms, lookup index {} ms, lookup fill "
                            + "{} ms; lookup ~{} KiB ({} B/state), registry "
                            + "deferred keys ~{} KiB",
                    selectNanos / 1_000_000L,
                    copyNanos / 1_000_000L,
                    registryNanos / 1_000_000L,
                    (collected - started) / 1_000_000L,
                    (indexed - collected) / 1_000_000L,
                    (filled - indexed) / 1_000_000L,
                    cacheBytes / 1024L,
                    cache.fixedSize() == 0 ? 0 : cacheBytes / cache.fixedSize(),
                    registryBytes(registry) / 1024L
            );
        }
        return cache;
    }

    static final class NullTolerantConcurrentMap<K, V>
            extends ConcurrentHashMap<K, V> {
        NullTolerantConcurrentMap(int capacity) {
            super(capacity);
        }

        @Override
        public V get(Object key) {
            return key == null ? null : super.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return key != null && super.containsKey(key);
        }

        @Override
        public V getOrDefault(Object key, V defaultValue) {
            return key == null ? defaultValue : super.getOrDefault(key, defaultValue);
        }

        @Override
        public V put(K key, V value) {
            if (value == null) {
                return key == null ? null : super.remove(key);
            }
            return super.put(key, value);
        }

        @Override
        public V remove(Object key) {
            return key == null ? null : super.remove(key);
        }
    }
}
