package dev.hoyin1600p.vhaccelerator.client.compat.ctm;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Exact-version implementation of CTM's model-bake pass which resolves a live
 * unbaked model graph once, then applies that answer to all of its baked keys.
 */
public final class CtmModelBakeOptimizer {
    private static final String CTM_BAKED_MODEL =
            "team.chisel.ctm.client.model.AbstractCTMBakedModel";
    private static final String CTM_RESOURCE_UTIL =
            "team.chisel.ctm.client.util.ResourceUtil";

    private static volatile Compatibility compatibility;

    private CtmModelBakeOptimizer() {
    }

    public static boolean optimize(
            ModelBakeEvent event,
            Object2BooleanMap<ResourceLocation> wrappedModels,
            ModelWrapper wrapper
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .memoizeCtmModelBakeTraversal
                )) {
            return false;
        }

        Compatibility active = compatibility();
        if (active == null) {
            return false;
        }

        long started = System.nanoTime();
        ForgeModelBakery loader = event.getModelLoader();
        Map<ResourceLocation, UnbakedModel> stateModels =
                ObfuscationReflectionHelper.getPrivateValue(
                        ModelBakery.class,
                        loader,
                        "f_119212_"
                );
        if (stateModels == null) {
            return false;
        }

        Map<ResourceLocation, BakedModel> modelRegistry =
                event.getModelRegistry();
        IdentityHashMap<UnbakedModel, Boolean> rootResults =
                new IdentityHashMap<>(Math.min(
                        stateModels.size(),
                        modelRegistry.size()
                ));
        TraversalScratch traversal = new TraversalScratch();
        int candidates = 0;
        int repeatedPlain = 0;
        int repeatedCtm = 0;
        int wrappedKeys = 0;

        for (Map.Entry<ResourceLocation, BakedModel> entry
                : modelRegistry.entrySet()) {
            ResourceLocation location = entry.getKey();
            UnbakedModel rootModel = stateModels.get(location);
            BakedModel bakedModel = entry.getValue();
            if (rootModel == null
                    || active.ctmBakedModel().isInstance(bakedModel)
                    || bakedModel.isCustomRenderer()) {
                continue;
            }
            candidates++;

            boolean shouldWrap;
            if (wrappedModels.getOrDefault(location, false)) {
                shouldWrap = true;
            } else {
                Boolean cached = rootResults.get(rootModel);
                if (cached != null) {
                    shouldWrap = cached;
                    if (shouldWrap) {
                        repeatedCtm++;
                    } else {
                        repeatedPlain++;
                    }
                } else {
                    shouldWrap = graphUsesCtm(
                            location,
                            rootModel,
                            loader,
                            active.metadataLookup(),
                            traversal
                    );
                    rootResults.put(rootModel, shouldWrap);
                }
            }

            if (!shouldWrap) {
                continue;
            }
            wrappedModels.put(location, true);
            try {
                modelRegistry.put(
                        location,
                        wrapper.wrap(
                                location,
                                rootModel,
                                bakedModel,
                                loader
                        )
                );
                wrappedKeys++;
            } catch (IOException exception) {
                VHAccelerator.LOGGER.error(
                        "Could not wrap CTM model {}",
                        location,
                        exception
                );
            }
        }

        VHAccelerator.LOGGER.info(
                "Optimized CTM model-bake pass across {} candidate key(s) "
                        + "and {} unique unbaked model object(s); reused {} "
                        + "plain and {} CTM alias result(s), wrapped {} key(s) "
                        + "in {} ms",
                candidates,
                rootResults.size(),
                repeatedPlain,
                repeatedCtm,
                wrappedKeys,
                (System.nanoTime() - started) / 1_000_000L
        );
        return true;
    }

    private static boolean graphUsesCtm(
            ResourceLocation rootLocation,
            UnbakedModel rootModel,
            ForgeModelBakery loader,
            MethodHandle metadataLookup,
            TraversalScratch traversal
    ) {
        ArrayDeque<ResourceLocation> dependencies =
                traversal.dependencies();
        Set<ResourceLocation> seenModels = traversal.seenModels();
        Set<Pair<String, String>> missingTextureErrors =
                traversal.missingTextureErrors();
        dependencies.clear();
        seenModels.clear();
        missingTextureErrors.clear();
        dependencies.push(rootLocation);
        seenModels.add(rootLocation);

        while (!dependencies.isEmpty()) {
            ResourceLocation dependency = dependencies.pop();
            UnbakedModel model;
            try {
                model = dependency == rootLocation
                        ? rootModel
                        : loader.getModel(dependency);
            } catch (Exception ignored) {
                continue;
            }

            try {
                missingTextureErrors.clear();
                Collection<Material> materials = model.getMaterials(
                        loader::getModel,
                        missingTextureErrors
                );
                for (Material material : materials) {
                    ResourceLocation texture = absoluteTexture(
                            material.texture()
                    );
                    try {
                        Object metadata =
                                (Object) metadataLookup.invokeExact(texture);
                        if (metadata != null) {
                            return true;
                        }
                    } catch (IOException ignored) {
                        // CTM treats unreadable metadata as non-CTM here.
                    } catch (RuntimeException | Error exception) {
                        throw exception;
                    } catch (Throwable throwable) {
                        throw new IllegalStateException(
                                "Could not query CTM texture metadata",
                                throwable
                        );
                    }
                }

                for (ResourceLocation next : model.getDependencies()) {
                    if (seenModels.add(next)) {
                        dependencies.push(next);
                    }
                }
            } catch (Exception exception) {
                VHAccelerator.LOGGER.error(
                        "Error loading model dependency {} for model {}; "
                                + "skipping it",
                        dependency,
                        rootLocation,
                        exception
                );
            }
        }
        return false;
    }

    private record TraversalScratch(
            ArrayDeque<ResourceLocation> dependencies,
            Set<ResourceLocation> seenModels,
            Set<Pair<String, String>> missingTextureErrors
    ) {
        private TraversalScratch() {
            this(new ArrayDeque<>(), new HashSet<>(), new HashSet<>());
        }
    }

    private static ResourceLocation absoluteTexture(ResourceLocation texture) {
        String path = texture.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        return path.equals(texture.getPath())
                ? texture
                : new ResourceLocation(texture.getNamespace(), path);
    }

    private static Compatibility compatibility() {
        Compatibility resolved = compatibility;
        if (resolved != null) {
            return resolved.available() ? resolved : null;
        }
        synchronized (CtmModelBakeOptimizer.class) {
            resolved = compatibility;
            if (resolved == null) {
                compatibility = resolved = resolveCompatibility();
            }
        }
        return resolved.available() ? resolved : null;
    }

    private static Compatibility resolveCompatibility() {
        try {
            ClassLoader loader =
                    CtmModelBakeOptimizer.class.getClassLoader();
            Class<?> bakedModel = Class.forName(
                    CTM_BAKED_MODEL,
                    false,
                    loader
            );
            Class<?> resourceUtil = Class.forName(
                    CTM_RESOURCE_UTIL,
                    false,
                    loader
            );
            Method method = resourceUtil.getMethod(
                    "getMetadata",
                    ResourceLocation.class
            );
            MethodHandle metadataLookup = MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(
                            Object.class,
                            ResourceLocation.class
                    ));
            return new Compatibility(
                    true,
                    bakedModel,
                    metadataLookup
            );
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError exception) {
            VHAccelerator.LOGGER.warn(
                    "CTM model-bake optimizer could not bind its exact "
                            + "compatibility targets; leaving CTM untouched",
                    exception
            );
            return Compatibility.UNAVAILABLE;
        }
    }

    @FunctionalInterface
    public interface ModelWrapper {
        BakedModel wrap(
                ResourceLocation location,
                UnbakedModel model,
                BakedModel bakedModel,
                ForgeModelBakery loader
        ) throws IOException;
    }

    private record Compatibility(
            boolean available,
            Class<?> ctmBakedModel,
            MethodHandle metadataLookup
    ) {
        private static final Compatibility UNAVAILABLE =
                new Compatibility(false, Object.class, null);
    }
}
