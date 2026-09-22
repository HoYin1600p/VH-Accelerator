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
import net.minecraftforge.fml.ModList;

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
 * {@code ModelManager#getModel} on first read. Nothing is baked at the menu,
 * on world join, or on a dimension change. The registry is retired at the
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

    private static volatile ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel>
            current;

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
        ModList mods = ModList.get();
        if (mods == null || mods.isLoaded("ctm")) {
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

    public static Map<ResourceLocation, BakedModel> install(
            Map<ResourceLocation, BakedModel> eager,
            Set<ResourceLocation> deferred,
            Function<ResourceLocation, BakedModel> baker
    ) {
        boolean debug = VHAcceleratorConfig.debugDiagnosticsEnabled();
        AtomicInteger logged = new AtomicInteger();
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
                            } else if (debug && logged.getAndIncrement()
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
        current = registry;
        VHAccelerator.LOGGER.info(
                "Deferred baking of {} plain block-state models until first use",
                registry.initialDeferred()
        );
        return registry;
    }

    /** Called at the start of every ModelManager apply, before atlases close. */
    public static void retireCurrent() {
        ConcurrentDeferredModelRegistry<ResourceLocation, BakedModel> registry =
                current;
        if (registry == null) {
            return;
        }
        current = null;
        registry.retire(); // Waits for bakes in progress on any thread.
        VHAccelerator.LOGGER.info(
                "Retired deferred block-state models before reload: {} "
                        + "selected, {} baked on demand, {} failed, {} never "
                        + "baked, {} removed, {} shared waits, {} retired lookups",
                registry.initialDeferred(),
                registry.bakedOnDemand(),
                registry.failedBakes(),
                registry.unresolvedDeferred(),
                registry.removedDeferred(),
                registry.sharedWaits(),
                registry.retiredLookups()
        );
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
        LazyStateModelCache<BlockState, ModelResourceLocation, BakedModel> cache =
                new LazyStateModelCache<>(
                        states.size(),
                        manager::getModel,
                        () -> !registry.isRetired()
                );
        AtomicInteger deferredStates = new AtomicInteger();
        // Every call here is thread-safe; resolved lookups never bake.
        SharedWorkers.forRange(states.size(), index -> {
            BlockState state = states.get(index);
            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(state);
            if (registry.isUnresolvedDeferred(location)) {
                cache.defer(state, location);
                deferredStates.incrementAndGet();
            } else {
                cache.putResolved(state, manager.getModel(location));
            }
        });
        int pending = deferredStates.get();
        VHAccelerator.LOGGER.info(
                "Built {} block model render lookups with {} left for "
                        + "first-use baking in {} ms",
                states.size(),
                pending,
                (System.nanoTime() - started) / 1_000_000L
        );
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
