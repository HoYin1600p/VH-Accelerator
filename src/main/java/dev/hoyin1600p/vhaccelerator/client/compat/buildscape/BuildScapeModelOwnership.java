package dev.hoyin1600p.vhaccelerator.client.compat.buildscape;

import dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

public final class BuildScapeModelOwnership {
    public static final String NAMESPACE = "buildscape";
    private static final boolean BUNDLED_BUILDSCAPE = bundledBuildScapePresent();
    private static volatile int loading;
    private static volatile int baking;

    private BuildScapeModelOwnership() {
    }

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

    public static boolean vhaBakes() {
        int state = baking;
        if (state == 0) {
            state = decide(VHAcceleratorClientConfig.VALUES.parallelModelBaking);
            baking = state;
        }
        return state > 0;
    }

    public static boolean buildScapeLoads(ResourceLocation location) {
        return NAMESPACE.equals(location.getNamespace()) && !vhaLoads();
    }

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
