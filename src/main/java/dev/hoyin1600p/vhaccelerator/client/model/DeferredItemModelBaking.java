package dev.hoyin1600p.vhaccelerator.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
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
 * in the bakery's unbaked cache. A deferred model bakes only when a consumer
 * reads it, which may be during in-world rendering; there is no title-screen
 * warmup and no drain on level join or dimension change. The registry is
 * retired at the start of the next {@code ModelManager#apply}, before the
 * atlases it bakes against close.
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
    /** False while Forge's item cache omits unresolved deferred items. */
    private static volatile boolean itemCacheComplete = true;
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
                        )
                );
        current = registry;
        diagnostics = debug;
        itemCacheComplete = registry.initialDeferred() == 0;
        VHAccelerator.LOGGER.info(
                "Deferred baking of {} ordinary inventory item models "
                        + "until first use",
                registry.initialDeferred()
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
        current = null;
        diagnostics = null;
        registry.retire();
        // The reload's own item-cache rebuild runs after this apply.
        itemCacheComplete = true;
        VHAccelerator.LOGGER.info(
                "Retired deferred item models before reload: {} selected, "
                        + "{} baked on demand, {} failed, {} never baked",
                registry.initialDeferred(),
                registry.bakedOnDemand(),
                registry.failedBakes(),
                registry.unresolvedDeferred()
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

    /**
     * Resolves an item whose cache entry was left for a deferred bake, and
     * caches the result in Forge's item cache on the render thread so later
     * lookups take Forge's direct path. A present entry, including a
     * legitimately missing model, is never looked up again.
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
            Map<Object, BakedModel> models =
                    (Map<Object, BakedModel>) modelsField.get(shaper);
            if (key == null || models.containsKey(key)) {
                return result;
            }
            ModelResourceLocation location =
                    ((Map<Object, ModelResourceLocation>) locationsField
                            .get(shaper)).get(key);
            if (location == null || !registry.isDeferred(location)) {
                return result;
            }
            BakedModel model = shaper.getModelManager().getModel(location);
            if (model == null) {
                return result;
            }
            if (RenderSystem.isOnRenderThread()) {
                // Forge's cache is a plain HashMap owned by the render thread.
                models.put(key, model);
            }
            return model;
        } catch (IllegalAccessException | IllegalArgumentException
                 | ClassCastException failure) {
            return result;
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
