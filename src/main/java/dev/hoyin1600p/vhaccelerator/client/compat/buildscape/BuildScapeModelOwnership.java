package dev.hoyin1600p.vhaccelerator.client.compat.buildscape;

import dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Whether VHA's model pipeline, rather than BuildScape's own launch
 * optimizations, loads and bakes BuildScape's models.
 *
 * <p>BuildScape parses its bundled model JSON and bakes its top-level models
 * in parallel itself, and steps aside when {@code LaunchFasterInterop} reports
 * that another mod does. With VHA installed and owning the model bakery, VHA
 * answers that check (see {@code LaunchFasterInteropMixin}) and then treats
 * BuildScape models like any other: parsed with the rest, and deferred or
 * skipped when plain. Without VHA, BuildScape keeps optimizing its own launch.
 * Every caller must use this one decision so the two mods never both skip, or
 * both handle, the same work.</p>
 *
 * <p>Each stage keeps one decision per launch once the launch config is
 * readable. Bundled BuildScape also keeps its native loader until it registers
 * the model API bridge; that gate is checked on every loading query.</p>
 */
public final class BuildScapeModelOwnership {
    public static final String NAMESPACE = "buildscape";
    private static final boolean BUNDLED_BUILDSCAPE = bundledBuildScapePresent();
    /** 0 until the launch config is readable, then 1 (VHA) or -1 (BuildScape). */
    private static volatile int loading;
    private static volatile int baking;

    private BuildScapeModelOwnership() {
    }

    /** VHA loads BuildScape models; bundled models require a registered API bridge. */
    public static boolean vhaLoads() {
        // Binary bundles are not loose JSON. Keep their native loader until it registers the API bridge.
        if (BUNDLED_BUILDSCAPE && !ModelBakeryEvents.isRegistered(NAMESPACE)) {
            return false;
        }
        int state = loading;
        if (state == 0) {
            state = decide(VHAcceleratorClientConfig.VALUES.parallelModelLoading);
            loading = state;
        }
        return state > 0;
    }

    /** VHA bakes BuildScape's models; BuildScape skips its parallel bake. */
    public static boolean vhaBakes() {
        int state = baking;
        if (state == 0) {
            state = decide(VHAcceleratorClientConfig.VALUES.parallelModelBaking);
            baking = state;
        }
        return state > 0;
    }

    /** True for a BuildScape location whose loading BuildScape still owns. */
    public static boolean buildScapeLoads(ResourceLocation location) {
        return NAMESPACE.equals(location.getNamespace()) && !vhaLoads();
    }

    /** True for a BuildScape location whose baking BuildScape still owns. */
    public static boolean buildScapeBakes(ResourceLocation location) {
        return NAMESPACE.equals(location.getNamespace()) && !vhaBakes();
    }

    private static int decide(net.minecraftforge.common.ForgeConfigSpec.BooleanValue stage) {
        if (!VHAcceleratorClientConfig.launchSnapshotCaptured()) {
            // Too early to know; BuildScape stays in charge until decided.
            return 0;
        }
        ModList mods = ModList.get();
        boolean vha = mods != null
                && mods.isLoaded(NAMESPACE)
                && LaunchFasterInteropState.applied()
                && VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.takeOverBuildScapeModelLoading)
                && VHAcceleratorClientConfig.launchValue(stage);
        return vha ? 1 : -1;
    }

    private static boolean bundledBuildScapePresent() {
        try {
            return BuildScapeModelOwnership.class.getClassLoader().getResource(
                    "com/kingodogo/buildscape/resources/bundle/runtime/BundledResourceRegistry.class") != null;
        } catch (RuntimeException | LinkageError failure) {
            // Unknown bundle support must not suppress another mod's native loader.
            return true;
        }
    }
}
