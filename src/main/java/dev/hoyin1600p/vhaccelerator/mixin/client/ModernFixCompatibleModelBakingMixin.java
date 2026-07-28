package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.model.DynamicModelGuard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores only the independent top-level bake pass when ModernFix is loaded.
 *
 * <p>The mixin plugin rejects this mixin if ModernFix's dynamic-resources
 * ModelBakery replacement is enabled. The broader VH Accelerator ModelBakery
 * mixin remains disabled beside ModernFix, so its resource loading and atlas
 * work cannot overlap ModernFix's implementations.</p>
 */
@Mixin(ModelBakery.class)
public abstract class ModernFixCompatibleModelBakingMixin {
    private static final String BUILDSCAPE_NAMESPACE = "buildscape";

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
    public abstract BakedModel bake(
            ResourceLocation location,
            ModelState state
    );

    @Unique
    private Set<ResourceLocation> vhaccelerator$sequentialModels =
            Collections.emptySet();

    @Redirect(
            method = "uploadTextures",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;keySet()Ljava/util/Set;",
                    ordinal = 0
            )
    )
    private Set<?> vhaccelerator$bakeTopLevelModelsBesideModernFix(
            Map<ResourceLocation, ?> models
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                        .parallelModelBaking
                        .get()) {
            return models.keySet();
        }

        vhaccelerator$findSequentialModels();
        long startedAt = System.nanoTime();
        bakedCache = new ConcurrentHashMap<>(bakedCache);
        List<ResourceLocation> locations =
                new ArrayList<>(models.keySet());
        locations.removeAll(vhaccelerator$sequentialModels);
        Map<ResourceLocation, BakedModel> results =
                new ConcurrentHashMap<>();
        Set<ResourceLocation> failures =
                ConcurrentHashMap.newKeySet();

        vhaccelerator$runBatched(locations, location -> {
            try {
                BakedModel model = bake(
                        location,
                        BlockModelRotation.X0_Y0
                );
                if (model != null) {
                    results.put(location, model);
                }
            } catch (RuntimeException | LinkageError failure) {
                VHAccelerator.LOGGER.warn(
                        "Unable to bake model {} in parallel beside "
                                + "ModernFix; all top-level models will be "
                                + "retried on the client thread",
                        location,
                        failure
                );
                failures.add(location);
            }
        });

        if (!failures.isEmpty()) {
            /*
             * Dependency entries may have been published before a worker
             * failed. Discard the complete attempt so vanilla retries from a
             * consistent cache and cannot omit a model.
             */
            bakedCache.clear();
            VHAccelerator.LOGGER.warn(
                    "Parallel model baking beside ModernFix failed for {} "
                            + "models; retrying all {} top-level models "
                            + "sequentially",
                    failures.size(),
                    models.size()
            );
            return models.keySet();
        }

        bakedTopLevelModels.putAll(results);
        VHAccelerator.LOGGER.info(
                "Baked {} top-level models in parallel beside ModernFix; "
                        + "{} custom, dynamic, or BuildScape models stayed "
                        + "on their established client-thread paths ({} ms)",
                results.size(),
                vhaccelerator$sequentialModels.size(),
                (System.nanoTime() - startedAt) / 1_000_000L
        );
        return vhaccelerator$sequentialModels;
    }

    @Unique
    private void vhaccelerator$findSequentialModels() {
        Set<ResourceLocation> sequential = new LinkedHashSet<>();
        DynamicModelGuard.Scanner scanner =
                DynamicModelGuard.scanner(this::getModel);
        boolean protectDynamic = VHAcceleratorClientConfig.VALUES
                .protectDynamicModels
                .get();
        topLevelModels.forEach((location, model) -> {
            if (BUILDSCAPE_NAMESPACE.equals(location.getNamespace())
                    || protectDynamic
                    && scanner.requiresSequentialBaking(model)) {
                sequential.add(location);
            }
        });
        vhaccelerator$sequentialModels =
                Collections.unmodifiableSet(sequential);
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
        int batchSize = Math.max(
                1,
                (values.size() + parallelism - 1) / parallelism
        );
        List<CompletableFuture<Void>> tasks =
                new ArrayList<>(parallelism);

        for (int start = 0; start < values.size(); start += batchSize) {
            int from = start;
            int to = Math.min(start + batchSize, values.size());
            tasks.add(CompletableFuture.runAsync(() -> {
                for (int index = from; index < to; index++) {
                    action.accept(values.get(index));
                }
            }, Util.backgroundExecutor()));
        }

        CompletableFuture.allOf(
                tasks.toArray(CompletableFuture[]::new)
        ).join();
    }
}
