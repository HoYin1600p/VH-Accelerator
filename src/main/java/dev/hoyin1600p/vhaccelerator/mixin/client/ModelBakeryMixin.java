package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentModelJsonCache;
import dev.hoyin1600p.vhaccelerator.client.model.DynamicModelGuard;
import dev.hoyin1600p.vhaccelerator.client.model.ParallelModelJsonParser;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow
    @Final
    protected ResourceManager resourceManager;

    @Shadow
    private Map<ResourceLocation, Pair<TextureAtlas, TextureAtlas.Preparations>> atlasPreparations;

    @Shadow
    @Final
    @Mutable
    private Map<?, BakedModel> bakedCache;

    @Shadow
    @Final
    private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow
    @Final
    private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow
    public abstract UnbakedModel getModel(ResourceLocation location);

    @Shadow
    @Nullable
    public abstract BakedModel bake(ResourceLocation location, ModelState state);

    @Unique
    private Map<ResourceLocation, String> vhaccelerator$modelJsonCache;

    @Unique
    private Map<ResourceLocation, BlockModel>
            vhaccelerator$parsedModelCache;

    @Unique
    private PersistentModelJsonCache.Session
            vhaccelerator$persistentModelCacheSession;

    @Unique
    private int vhaccelerator$currentMipLevel;

    @Unique
    private Set<ResourceLocation> vhaccelerator$sequentialModels =
            Collections.emptySet();

    @Unique
    private boolean vhaccelerator$modelSafetyEvaluated;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void vhaccelerator$preloadModelJson(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        vhaccelerator$currentMipLevel = mipLevel;
        vhaccelerator$persistentModelCacheSession =
                PersistentModelJsonCache.prepare(resourceManager);
        if (vhaccelerator$persistentModelCacheSession != null) {
            vhaccelerator$modelJsonCache =
                    vhaccelerator$persistentModelCacheSession.models();
            vhaccelerator$prepareParsedModels();
            return;
        }
        if (!vhaccelerator$clientOption(
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelModelLoading
                ))) {
            return;
        }

        long startedAt = System.nanoTime();
        Collection<ResourceLocation> modelFiles =
                resourceManager.listResources("models", path -> path.endsWith(".json"));
        ConcurrentHashMap<ResourceLocation, String> cache = new ConcurrentHashMap<>();
        List<ResourceLocation> locations = new ArrayList<>(modelFiles);
        vhaccelerator$runBatched(locations, location -> {
            try (Resource resource = resourceManager.getResource(location)) {
                String json = new String(
                        resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
                cache.put(location, json);
            } catch (Exception exception) {
                VHAccelerator.LOGGER.debug(
                        "Could not preload model resource {}",
                        location,
                        exception
                );
            }
        });

        vhaccelerator$modelJsonCache = cache;
        vhaccelerator$prepareParsedModels();
        VHAccelerator.LOGGER.info(
                "Preloaded {} model JSON files in {} ms",
                cache.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$loadPreloadedModel(
            ResourceLocation location,
            CallbackInfoReturnable<BlockModel> callback
    ) {
        if ("buildscape".equals(location.getNamespace())) {
            return;
        }
        Map<ResourceLocation, BlockModel> parsed =
                vhaccelerator$parsedModelCache;
        if (parsed != null) {
            BlockModel model = parsed.get(location);
            if (model != null) {
                callback.setReturnValue(model);
                return;
            }
        }

        Map<ResourceLocation, String> cache = vhaccelerator$modelJsonCache;
        if (cache == null || location.getPath().startsWith("builtin/")) {
            return;
        }

        ResourceLocation resourcePath = ResourceLocation.fromNamespaceAndPath(
                location.getNamespace(),
                "models/" + location.getPath() + ".json"
        );
        String json = cache.get(resourcePath);
        if (json == null) {
            return;
        }

        try {
            BlockModel model =
                    BlockModel.fromStream(new StringReader(json));
            model.name = location.toString();
            callback.setReturnValue(model);
        } catch (RuntimeException | LinkageError failure) {
            PersistentModelJsonCache.Session session =
                    vhaccelerator$persistentModelCacheSession;
            if (session != null) {
                session.invalidate();
            }
            VHAccelerator.LOGGER.warn(
                    "Cached model JSON {} could not be parsed; retrying "
                            + "through the active resource manager",
                    location,
                    failure
            );
        }
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void vhaccelerator$releaseModelJsonCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        PersistentModelJsonCache.finish(
                vhaccelerator$persistentModelCacheSession
        );
        vhaccelerator$persistentModelCacheSession = null;
        vhaccelerator$modelJsonCache = null;
        vhaccelerator$parsedModelCache = null;
    }

    @Unique
    private void vhaccelerator$prepareParsedModels() {
        if (!vhaccelerator$clientOption(
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelModelLoading
                )
        )) {
            return;
        }
        vhaccelerator$parsedModelCache =
                ParallelModelJsonParser.parse(
                        vhaccelerator$modelJsonCache
                );
    }

    @Redirect(
            method = "processLoading",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;entrySet()Ljava/util/Set;",
                    ordinal = 0,
                    remap = false
            )
    )
    private Set<?> vhaccelerator$prepareAtlasesInParallel(Map<?, ?> groupedMaterials) {
        if (!vhaccelerator$clientOption(
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelAtlasStitching
                ))) {
            return groupedMaterials.entrySet();
        }
        vhaccelerator$findSequentialModels();
        if (vhaccelerator$dynamicModelProtectionEnabled()
                && !vhaccelerator$sequentialModels.isEmpty()) {
            VHAccelerator.LOGGER.info(
                    "Using vanilla atlas preparation because {} top-level "
                            + "models depend on custom or dynamic model loaders",
                    vhaccelerator$sequentialModels.size()
            );
            return groupedMaterials.entrySet();
        }
        if (groupedMaterials.isEmpty()) {
            return Collections.emptySet();
        }

        long startedAt = System.nanoTime();
        Map<ResourceLocation, Pair<TextureAtlas, TextureAtlas.Preparations>> results =
                new ConcurrentHashMap<>();
        List<Map.Entry<?, ?>> entries = new ArrayList<>(groupedMaterials.entrySet());

        vhaccelerator$runBatched(entries, rawEntry -> {
            ResourceLocation atlasLocation = (ResourceLocation) rawEntry.getKey();
            @SuppressWarnings("unchecked")
            List<Material> materials = (List<Material>) rawEntry.getValue();
            TextureAtlas atlas = new TextureAtlas(atlasLocation);
            TextureAtlas.Preparations preparations = atlas.prepareToStitch(
                    resourceManager,
                    materials.stream().map(Material::texture),
                    InactiveProfiler.INSTANCE,
                    vhaccelerator$currentMipLevel
            );
            results.put(atlasLocation, Pair.of(atlas, preparations));
        });

        atlasPreparations.putAll(results);
        VHAccelerator.LOGGER.info(
                "Prepared {} texture atlases in parallel in {} ms",
                results.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return Collections.emptySet();
    }

    @Redirect(
            method = "uploadTextures",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;keySet()Ljava/util/Set;",
                    ordinal = 0
            )
    )
    private Set<?> vhaccelerator$bakeTopLevelModelsInParallel(
            Map<ResourceLocation, ?> models
    ) {
        if (!vhaccelerator$clientOption(
                VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelModelBaking
                ))) {
            return models.keySet();
        }

        vhaccelerator$findSequentialModels();
        long startedAt = System.nanoTime();
        bakedCache = new ConcurrentHashMap<>(bakedCache);
        List<ResourceLocation> locations = new ArrayList<>(models.keySet());
        locations.removeAll(vhaccelerator$sequentialModels);
        Map<ResourceLocation, BakedModel> results = new ConcurrentHashMap<>();
        Set<ResourceLocation> failures = ConcurrentHashMap.newKeySet();

        vhaccelerator$runBatched(locations, location -> {
            try {
                BakedModel model = bake(location, BlockModelRotation.X0_Y0);
                if (model != null) {
                    results.put(location, model);
                }
            } catch (Exception exception) {
                VHAccelerator.LOGGER.warn(
                        "Unable to bake model {} in parallel; the complete "
                                + "model set will be retried sequentially",
                        location,
                        exception
                );
                failures.add(location);
            }
        });

        if (!failures.isEmpty()) {
            /*
             * A worker may have populated dependency entries before failing.
             * Discard the whole parallel attempt so vanilla retries from a
             * consistent cache and no model can be omitted.
             */
            bakedCache.clear();
            VHAccelerator.LOGGER.warn(
                    "Parallel baking failed for {} models; retrying all {} "
                            + "top-level models on the client thread",
                    failures.size(),
                    models.size()
            );
            return models.keySet();
        }

        bakedTopLevelModels.putAll(results);
        VHAccelerator.LOGGER.info(
                "Baked {} top-level models in parallel and reserved {} "
                        + "custom or dynamic models for the client thread in {} ms",
                results.size(),
                vhaccelerator$sequentialModels.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return vhaccelerator$sequentialModels;
    }

    @Unique
    private void vhaccelerator$findSequentialModels() {
        if (!vhaccelerator$dynamicModelProtectionEnabled()) {
            vhaccelerator$sequentialModels = Collections.emptySet();
            return;
        }
        if (vhaccelerator$modelSafetyEvaluated) {
            return;
        }

        Set<ResourceLocation> sequential = new LinkedHashSet<>();
        DynamicModelGuard.Scanner scanner =
                DynamicModelGuard.scanner(this::getModel);
        topLevelModels.forEach((location, model) -> {
            if (scanner.requiresSequentialBaking(model)) {
                sequential.add(location);
            }
        });
        vhaccelerator$sequentialModels = Collections.unmodifiableSet(sequential);
        vhaccelerator$modelSafetyEvaluated = true;
    }

    @Unique
    private static boolean vhaccelerator$dynamicModelProtectionEnabled() {
        return VHAcceleratorClientConfig.launchValue(
                VHAcceleratorClientConfig.VALUES.protectDynamicModels
        );
    }

    @Unique
    private static boolean vhaccelerator$clientOption(boolean option) {
        return VHAcceleratorClientConfig.optimizationsEnabled()
                && option;
    }

    @Unique
    private static <T> void vhaccelerator$runBatched(
            List<T> values,
            java.util.function.Consumer<T> action
    ) {
        if (values.isEmpty()) {
            return;
        }

        int parallelism = Math.max(1, Math.min(
                Runtime.getRuntime().availableProcessors(),
                values.size()
        ));
        int batchSize = Math.max(1, (values.size() + parallelism - 1) / parallelism);
        List<CompletableFuture<Void>> tasks = new ArrayList<>(parallelism);

        for (int start = 0; start < values.size(); start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, values.size());
            tasks.add(CompletableFuture.runAsync(() -> {
                for (int index = from; index < to; index++) {
                    action.accept(values.get(index));
                }
            }, Util.backgroundExecutor()));
        }

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }
}
