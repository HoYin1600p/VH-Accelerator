package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ItemModelMesherForge;
import net.minecraftforge.fml.ModList;

/**
 * Client-thread lifecycle for deferred inventory-model baking.
 *
 * <p>Only baking is deferred. Unbaked graphs and their materials are loaded
 * eagerly before atlas stitching, and a deferred graph must be fully present
 * in the bakery's unbaked cache. The registry is retired at the start of the
 * next {@code ModelManager#apply}, before the atlases it bakes against close.
 * Remaining models bake in small steps while no world is loaded and all of
 * them bake before a level is set, so none bakes during in-world rendering.
 */
public final class DeferredItemModelBaking {
    private static final long WARMUP_BUDGET_NANOS = 3_000_000L;
    private static final Set<String> EAGER_NAMESPACES = Set.of(
            "the_vault",
            "everycomp",
            "buildscape",
            "ctm"
    );

    private static volatile DeferredModelRegistry<ResourceLocation, BakedModel> current;
    private static volatile boolean itemCacheComplete = true;
    private static Field locationsField;
    private static Field modelsField;
    private static boolean itemCacheReflectionFailed;
    private static long warmupNanos;

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
                && itemCacheFieldsAvailable();
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

    public static Map<ResourceLocation, BakedModel> install(
            Map<ResourceLocation, BakedModel> eager,
            Set<ResourceLocation> deferred,
            Function<ResourceLocation, BakedModel> baker
    ) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry =
                new DeferredModelRegistry<>(
                        eager,
                        deferred,
                        baker,
                        eager.get(ModelBakery.MISSING_MODEL_LOCATION),
                        (location, failure) -> VHAccelerator.LOGGER.warn(
                                "Unable to bake deferred model {}; using "
                                        + "the missing model",
                                location,
                                failure
                        )
                );
        current = registry;
        itemCacheComplete = registry.initialDeferred() == 0;
        warmupNanos = 0L;
        VHAccelerator.LOGGER.info(
                "Deferred baking of {} ordinary inventory item models",
                registry.initialDeferred()
        );
        return registry;
    }

    /** Called at the start of every ModelManager apply, before atlases close. */
    public static void retireCurrent() {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null) {
            return;
        }
        current = null;
        registry.retire();
        boolean incomplete = !itemCacheComplete;
        // The reload's own item-cache rebuild runs after this apply.
        itemCacheComplete = true;
        if (incomplete) {
            VHAccelerator.LOGGER.info(
                    "Retired deferred item models before reload: {} baked, "
                            + "{} failed, {} left unbaked",
                    registry.bakedOnDemand(),
                    registry.failedBakes(),
                    registry.initialDeferred()
                            - registry.bakedOnDemand()
                            - registry.failedBakes()
            );
        }
    }

    /**
     * Forge's rebuild would bake every item model immediately. Unresolved
     * deferred locations are removed instead, so no stale model survives, and
     * resolve through {@link #resolveItemModel}. Returns false to request
     * Forge's original rebuild.
     */
    @SuppressWarnings("unchecked")
    public static boolean rebuildItemCache(ItemModelShaper shaper) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null
                || registry.isRetired()
                || itemCacheComplete
                || !(shaper instanceof ItemModelMesherForge)
                || !itemCacheFieldsAvailable()) {
            return false;
        }
        Map<Object, ModelResourceLocation> locations;
        Map<Object, BakedModel> models;
        try {
            locations = (Map<Object, ModelResourceLocation>)
                    locationsField.get(shaper);
            models = (Map<Object, BakedModel>) modelsField.get(shaper);
        } catch (IllegalAccessException | ClassCastException failure) {
            return false;
        }
        ModelManager manager = shaper.getModelManager();
        for (Map.Entry<Object, ModelResourceLocation> entry
                : locations.entrySet()) {
            if (registry.isUnresolvedDeferred(entry.getValue())) {
                models.remove(entry.getKey());
            } else {
                models.put(entry.getKey(), manager.getModel(entry.getValue()));
            }
        }
        return true;
    }

    /** Resolves an item whose cache entry was left for a deferred bake. */
    @SuppressWarnings("unchecked")
    public static BakedModel resolveItemModel(
            ItemModelShaper shaper,
            ItemStack stack,
            BakedModel result
    ) {
        if (itemCacheComplete
                || current == null
                || locationsField == null
                || result != shaper.getModelManager().getMissingModel()) {
            return result;
        }
        try {
            ModelResourceLocation location =
                    ((Map<Object, ModelResourceLocation>) locationsField
                            .get(shaper)).get(stack.getItem().delegate);
            return location == null
                    ? result
                    : shaper.getModelManager().getModel(location);
        } catch (IllegalAccessException | IllegalArgumentException
                 | ClassCastException failure) {
            return result;
        }
    }

    /** Title-screen warmup, one budgeted step per client tick. */
    public static void tick(Minecraft minecraft) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null
                || itemCacheComplete
                || minecraft.level != null
                || minecraft.getOverlay() != null) {
            return;
        }
        long started = System.nanoTime();
        int remaining = registry.warm(WARMUP_BUDGET_NANOS, System::nanoTime);
        warmupNanos += System.nanoTime() - started;
        if (remaining == 0) {
            completeItemCache(minecraft, registry, "title-screen warmup");
        }
    }

    /** Bakes everything left before a level is set. */
    public static void drainBeforeLevel(Minecraft minecraft) {
        DeferredModelRegistry<ResourceLocation, BakedModel> registry = current;
        if (registry == null || itemCacheComplete) {
            return;
        }
        long started = System.nanoTime();
        registry.warm(Long.MAX_VALUE, System::nanoTime);
        warmupNanos += System.nanoTime() - started;
        completeItemCache(minecraft, registry, "level join");
    }

    private static void completeItemCache(
            Minecraft minecraft,
            DeferredModelRegistry<ResourceLocation, BakedModel> registry,
            String trigger
    ) {
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        if (itemRenderer != null) {
            // Every deferred value is resolved, so Forge's rebuild bakes nothing.
            itemRenderer.getItemModelShaper().rebuildCache();
        }
        itemCacheComplete = true;
        VHAccelerator.LOGGER.info(
                "Completed {} deferred item models at {}: {} baked, "
                        + "{} failed, {} ms of client-thread baking",
                registry.initialDeferred(),
                trigger,
                registry.bakedOnDemand(),
                registry.failedBakes(),
                warmupNanos / 1_000_000L
        );
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
