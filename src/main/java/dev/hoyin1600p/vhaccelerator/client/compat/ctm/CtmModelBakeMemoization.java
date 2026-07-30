package dev.hoyin1600p.vhaccelerator.client.compat.ctm;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Event-scoped identity memoization for ConnectedTexturesMod's model scan.
 *
 * <p>Many distinct block-state keys point to the same live unbaked model.
 * CTM's decision is a function of that model graph and the active resource
 * view, so duplicate keys can safely reuse the decision during the same
 * ModelBakeEvent. Nothing survives a resource reload or process restart.
 */
public final class CtmModelBakeMemoization {
    private static final IdentityHashMap<UnbakedModel, Boolean> RESULTS =
            new IdentityHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation>
            ABSOLUTE_TEXTURES = new HashMap<>();

    private static boolean active;
    private static UnbakedModel currentRoot;
    private static boolean skipCachedFalseTraversal;
    private static int repeatedTrue;
    private static int repeatedFalse;
    private static int skippedPlainWrites;
    private static int absoluteTextureHits;

    private CtmModelBakeMemoization() {
    }

    public static void beginEvent() {
        RESULTS.clear();
        ABSOLUTE_TEXTURES.clear();
        currentRoot = null;
        skipCachedFalseTraversal = false;
        repeatedTrue = 0;
        repeatedFalse = 0;
        skippedPlainWrites = 0;
        absoluteTextureHits = 0;
        active = VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES
                                .memoizeCtmModelBakeTraversal
                );
    }

    public static void captureRoot(UnbakedModel model) {
        if (!active) {
            return;
        }
        currentRoot = model;
        skipCachedFalseTraversal = false;
    }

    public static Boolean cachedResult() {
        if (!active
                || currentRoot == null
                || !RESULTS.containsKey(currentRoot)) {
            skipCachedFalseTraversal = false;
            return null;
        }

        boolean cached = Boolean.TRUE.equals(RESULTS.get(currentRoot));
        if (cached) {
            repeatedTrue++;
        } else {
            repeatedFalse++;
            skipCachedFalseTraversal = true;
        }
        return cached;
    }

    public static boolean shouldSkipTraversal() {
        return active && skipCachedFalseTraversal;
    }

    public static boolean recordResult(boolean shouldWrap) {
        if (active && currentRoot != null) {
            RESULTS.putIfAbsent(currentRoot, shouldWrap);
        }
        boolean keepPerKeyResult = !active || shouldWrap;
        if (active && !shouldWrap) {
            skippedPlainWrites++;
        }
        currentRoot = null;
        skipCachedFalseTraversal = false;
        return keepPerKeyResult;
    }

    public static ResourceLocation cachedAbsoluteTexture(
            ResourceLocation texture
    ) {
        if (!active) {
            return null;
        }
        ResourceLocation cached = ABSOLUTE_TEXTURES.get(texture);
        if (cached != null) {
            absoluteTextureHits++;
        }
        return cached;
    }

    public static void recordAbsoluteTexture(
            ResourceLocation texture,
            ResourceLocation absolute
    ) {
        if (active && absolute != null) {
            ABSOLUTE_TEXTURES.putIfAbsent(texture, absolute);
        }
    }

    public static void finishEvent() {
        if (active) {
            VHAccelerator.LOGGER.info(
                    "Memoized CTM model-bake traversal across {} unique "
                            + "unbaked model object(s); avoided {} repeated "
                            + "graph scan(s) [{} CTM, {} plain] and {} "
                            + "redundant plain-result map write(s); reused "
                            + "{} absolute texture path(s) across {} unique "
                            + "sprite ID(s)",
                    RESULTS.size(),
                    repeatedTrue + repeatedFalse,
                    repeatedTrue,
                    repeatedFalse,
                    skippedPlainWrites,
                    absoluteTextureHits,
                    ABSOLUTE_TEXTURES.size()
            );
        }
        active = false;
        currentRoot = null;
        skipCachedFalseTraversal = false;
        skippedPlainWrites = 0;
        absoluteTextureHits = 0;
        RESULTS.clear();
        ABSOLUTE_TEXTURES.clear();
    }
}
