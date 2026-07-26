package dev.hoyin1600p.launchfastertoo.mixin.client;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import dev.hoyin1600p.launchfastertoo.client.LaunchFasterTooClientConfig;
import dev.hoyin1600p.launchfastertoo.client.model.DynamicModelGuard;
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
    private Map<ResourceLocation, String> launchfastertoo$modelJsonCache;

    @Unique
    private int launchfastertoo$currentMipLevel;

    @Unique
    private Set<ResourceLocation> launchfastertoo$sequentialModels =
            Collections.emptySet();

    @Unique
    private boolean launchfastertoo$modelSafetyEvaluated;

    @Inject(method = "processLoading", at = @At("HEAD"), remap = false)
    private void launchfastertoo$preloadModelJson(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        launchfastertoo$currentMipLevel = mipLevel;
        if (!launchfastertoo$clientOption(
                LaunchFasterTooClientConfig.VALUES.parallelModelLoading.get())) {
            return;
        }

        long startedAt = System.nanoTime();
        Collection<ResourceLocation> modelFiles =
                resourceManager.listResources("models", path -> path.endsWith(".json"));
        ConcurrentHashMap<ResourceLocation, String> cache = new ConcurrentHashMap<>();
        List<ResourceLocation> locations = new ArrayList<>(modelFiles);
        launchfastertoo$runBatched(locations, location -> {
            try (Resource resource = resourceManager.getResource(location)) {
                String json = new String(
                        resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8
                );
                cache.put(location, json);
            } catch (Exception exception) {
                LaunchFasterToo.LOGGER.debug(
                        "Could not preload model resource {}",
                        location,
                        exception
                );
            }
        });

        launchfastertoo$modelJsonCache = cache;
        LaunchFasterToo.LOGGER.info(
                "Preloaded {} model JSON files in {} ms",
                cache.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
    }

    @Inject(method = "loadBlockModel", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$loadPreloadedModel(
            ResourceLocation location,
            CallbackInfoReturnable<BlockModel> callback
    ) {
        Map<ResourceLocation, String> cache = launchfastertoo$modelJsonCache;
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

        BlockModel model = BlockModel.fromStream(new StringReader(json));
        model.name = location.toString();
        callback.setReturnValue(model);
    }

    @Inject(method = "processLoading", at = @At("TAIL"), remap = false)
    private void launchfastertoo$releaseModelJsonCache(
            ProfilerFiller profiler,
            int mipLevel,
            CallbackInfo callback
    ) {
        launchfastertoo$modelJsonCache = null;
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
    private Set<?> launchfastertoo$prepareAtlasesInParallel(Map<?, ?> groupedMaterials) {
        if (!launchfastertoo$clientOption(
                LaunchFasterTooClientConfig.VALUES.parallelAtlasStitching.get())) {
            return groupedMaterials.entrySet();
        }
        launchfastertoo$findSequentialModels();
        if (launchfastertoo$dynamicModelProtectionEnabled()
                && !launchfastertoo$sequentialModels.isEmpty()) {
            LaunchFasterToo.LOGGER.info(
                    "Using vanilla atlas preparation because {} top-level "
                            + "models depend on custom or dynamic model loaders",
                    launchfastertoo$sequentialModels.size()
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

        launchfastertoo$runBatched(entries, rawEntry -> {
            ResourceLocation atlasLocation = (ResourceLocation) rawEntry.getKey();
            @SuppressWarnings("unchecked")
            List<Material> materials = (List<Material>) rawEntry.getValue();
            TextureAtlas atlas = new TextureAtlas(atlasLocation);
            TextureAtlas.Preparations preparations = atlas.prepareToStitch(
                    resourceManager,
                    materials.stream().map(Material::texture),
                    InactiveProfiler.INSTANCE,
                    launchfastertoo$currentMipLevel
            );
            results.put(atlasLocation, Pair.of(atlas, preparations));
        });

        atlasPreparations.putAll(results);
        LaunchFasterToo.LOGGER.info(
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
    private Set<?> launchfastertoo$bakeTopLevelModelsInParallel(
            Map<ResourceLocation, ?> models
    ) {
        if (!launchfastertoo$clientOption(
                LaunchFasterTooClientConfig.VALUES.parallelModelBaking.get())) {
            return models.keySet();
        }

        launchfastertoo$findSequentialModels();
        long startedAt = System.nanoTime();
        bakedCache = new ConcurrentHashMap<>(bakedCache);
        List<ResourceLocation> locations = new ArrayList<>(models.keySet());
        locations.removeAll(launchfastertoo$sequentialModels);
        Map<ResourceLocation, BakedModel> results = new ConcurrentHashMap<>();
        Set<ResourceLocation> failures = ConcurrentHashMap.newKeySet();

        launchfastertoo$runBatched(locations, location -> {
            try {
                BakedModel model = bake(location, BlockModelRotation.X0_Y0);
                if (model != null) {
                    results.put(location, model);
                }
            } catch (Exception exception) {
                LaunchFasterToo.LOGGER.warn(
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
            LaunchFasterToo.LOGGER.warn(
                    "Parallel baking failed for {} models; retrying all {} "
                            + "top-level models on the client thread",
                    failures.size(),
                    models.size()
            );
            return models.keySet();
        }

        bakedTopLevelModels.putAll(results);
        LaunchFasterToo.LOGGER.info(
                "Baked {} top-level models in parallel and reserved {} "
                        + "custom or dynamic models for the client thread in {} ms",
                results.size(),
                launchfastertoo$sequentialModels.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return launchfastertoo$sequentialModels;
    }

    @Unique
    private void launchfastertoo$findSequentialModels() {
        if (!launchfastertoo$dynamicModelProtectionEnabled()) {
            launchfastertoo$sequentialModels = Collections.emptySet();
            return;
        }
        if (launchfastertoo$modelSafetyEvaluated) {
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
        launchfastertoo$sequentialModels = Collections.unmodifiableSet(sequential);
        launchfastertoo$modelSafetyEvaluated = true;
    }

    @Unique
    private static boolean launchfastertoo$dynamicModelProtectionEnabled() {
        return LaunchFasterTooClientConfig.VALUES.protectDynamicModels.get();
    }

    @Unique
    private static boolean launchfastertoo$clientOption(boolean option) {
        return LaunchFasterTooClientConfig.VALUES.enableClientOptimizations.get() && option;
    }

    @Unique
    private static <T> void launchfastertoo$runBatched(
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
