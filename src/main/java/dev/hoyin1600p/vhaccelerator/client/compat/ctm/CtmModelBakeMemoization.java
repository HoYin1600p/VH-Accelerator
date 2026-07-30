package dev.hoyin1600p.vhaccelerator.client.compat.ctm;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.IdentityHashMap;
import net.minecraft.client.resources.model.UnbakedModel;

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

    private static boolean active;
    private static UnbakedModel currentRoot;
    private static boolean skipCachedFalseTraversal;
    private static int repeatedTrue;
    private static int repeatedFalse;

    private CtmModelBakeMemoization() {
    }

    public static void beginEvent() {
        RESULTS.clear();
        currentRoot = null;
        skipCachedFalseTraversal = false;
        repeatedTrue = 0;
        repeatedFalse = 0;
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

    public static boolean reuseResult(boolean original) {
        if (!active
                || currentRoot == null
                || !RESULTS.containsKey(currentRoot)) {
            skipCachedFalseTraversal = false;
            return original;
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

    public static void recordResult(boolean shouldWrap) {
        if (active && currentRoot != null) {
            RESULTS.putIfAbsent(currentRoot, shouldWrap);
        }
        currentRoot = null;
        skipCachedFalseTraversal = false;
    }

    public static void finishEvent() {
        if (active) {
            VHAccelerator.LOGGER.info(
                    "Memoized CTM model-bake traversal across {} unique "
                            + "unbaked model object(s); avoided {} repeated "
                            + "graph scan(s) [{} CTM, {} plain]",
                    RESULTS.size(),
                    repeatedTrue + repeatedFalse,
                    repeatedTrue,
                    repeatedFalse
            );
        }
        active = false;
        currentRoot = null;
        skipCachedFalseTraversal = false;
        RESULTS.clear();
    }
}
