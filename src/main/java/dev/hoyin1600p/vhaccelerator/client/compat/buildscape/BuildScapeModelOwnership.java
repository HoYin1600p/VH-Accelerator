package dev.hoyin1600p.vhaccelerator.client.compat.buildscape;

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
 */
public final class BuildScapeModelOwnership {
    public static final String NAMESPACE = "buildscape";
    /** 0 until the launch config is readable, then 1 (VHA) or -1 (BuildScape). */
    private static volatile int loading;
    private static volatile int baking;

    private BuildScapeModelOwnership() {
    }

    /** VHA parses BuildScape's model JSON; BuildScape skips its parallel parse. */
    public static boolean vhaLoads() {
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
}
