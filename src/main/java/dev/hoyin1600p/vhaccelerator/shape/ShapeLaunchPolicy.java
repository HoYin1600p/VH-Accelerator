package dev.hoyin1600p.vhaccelerator.shape;

import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Decides whether the launch shape cache and the flat coordinate merger run.
 *
 * <p>Both features read one launch-time value from the common configuration
 * ({@code optimizations.cacheLaunchVoxelShapes} and
 * {@code optimizations.optimizeVoxelShapeMerging}), migrated from the former
 * client keys. A dedicated server also requires Compare Mode off and the
 * common master switch on. A physical client keeps its former rule: its own
 * client master switch and launch snapshot decide, and nothing happens until
 * that snapshot is captured. The client side registers its rule instead of
 * being referenced here, so no client class is linked on a dedicated
 * server.</p>
 */
public final class ShapeLaunchPolicy {
    /** What the client side says before and after its launch snapshot exists. */
    public static final int UNDECIDED = 0;
    public static final int OFF = 1;
    public static final int ON = 2;

    public enum Feature {
        CACHE("cacheLaunchVoxelShapes"),
        MERGER("optimizeVoxelShapeMerging");

        private final String key;

        Feature(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private static volatile ToIntFunction<Feature> clientRule = feature -> UNDECIDED;

    private ShapeLaunchPolicy() { }

    public static void registerClientRule(ToIntFunction<Feature> rule) {
        clientRule = Objects.requireNonNull(rule);
    }

    /** The launch value of the setting in the (migrated) common configuration. */
    public static boolean configuredAtLaunch(Feature feature) {
        return BootstrapCommonConfig.bool("optimizations", feature.key(), true);
    }

    /** True when a dedicated server should weave this feature's mixins. */
    public static boolean serverSelected(Feature feature) {
        return !BootstrapCompareMode.enabled()
                && BootstrapCommonConfig.bool("optimizations", "enableCommonOptimizations", true)
                && configuredAtLaunch(feature);
    }

    /**
     * Returns {@link #UNDECIDED} while the client launch snapshot is missing;
     * the caller must not cache that answer. Any other answer is final.
     */
    public static int decide(Feature feature) {
        return decide(FMLEnvironment.dist, feature, clientRule);
    }

    static int decide(Dist side, Feature feature, ToIntFunction<Feature> client) {
        if (side == Dist.DEDICATED_SERVER) {
            // Mixin selection already applied the settings; the mixins exist only when enabled.
            return ON;
        }
        if (side != Dist.CLIENT) {
            return OFF;
        }
        try {
            return client.applyAsInt(feature);
        } catch (RuntimeException | LinkageError unavailable) {
            return OFF;
        }
    }
}
