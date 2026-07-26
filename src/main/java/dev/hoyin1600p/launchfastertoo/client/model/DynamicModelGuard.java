package dev.hoyin1600p.launchfastertoo.client.model;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifies model graphs that must remain on Forge's normal single-threaded
 * baking path.
 */
public final class DynamicModelGuard {
    private static final String VANILLA_MODEL_PACKAGE = "net.minecraft.";

    private DynamicModelGuard() {
    }

    public static boolean requiresSequentialBaking(
            UnbakedModel model,
            Function<ResourceLocation, UnbakedModel> modelGetter
    ) {
        return scanner(modelGetter).requiresSequentialBaking(model);
    }

    public static Scanner scanner(
            Function<ResourceLocation, UnbakedModel> modelGetter
    ) {
        return new Scanner(modelGetter);
    }

    public static final class Scanner {
        private final Function<ResourceLocation, UnbakedModel> modelGetter;
        private final Map<UnbakedModel, Boolean> cache = new IdentityHashMap<>();
        private final Set<UnbakedModel> visiting =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private Scanner(Function<ResourceLocation, UnbakedModel> modelGetter) {
            this.modelGetter = modelGetter;
        }

        public boolean requiresSequentialBaking(UnbakedModel model) {
            if (model == null) {
                return true;
            }
            Boolean cached = cache.get(model);
            if (cached != null) {
                return cached;
            }
            if (!visiting.add(model)) {
                return true;
            }

            boolean sequential = isCustomOrDynamic(model);
            if (!sequential) {
                try {
                    for (ResourceLocation dependency : model.getDependencies()) {
                        UnbakedModel dependencyModel =
                                modelGetter.apply(dependency);
                        if (requiresSequentialBaking(dependencyModel)) {
                            sequential = true;
                            break;
                        }
                    }
                } catch (RuntimeException exception) {
                    /*
                     * Unknown or malformed model graphs are safer on
                     * vanilla's path. Vanilla retains its normal diagnostics
                     * when it processes the model.
                     */
                    sequential = true;
                }
            }

            visiting.remove(model);
            cache.put(model, sequential);
            return sequential;
        }

        private static boolean isCustomOrDynamic(UnbakedModel model) {
            /*
             * Forge JSON geometry loaders are attached to
             * BlockModel.customData. Direct UnbakedModel implementations
             * from mods are also kept sequential because their thread-safety
             * contract is unknown.
             */
            if (!model.getClass().getName().startsWith(VANILLA_MODEL_PACKAGE)) {
                return true;
            }
            return model instanceof BlockModel blockModel
                    && blockModel.customData.hasCustomGeometry();
        }
    }
}
