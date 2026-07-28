package dev.hoyin1600p.vhaccelerator.client.model;

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

    public static PreparedGraph preparedGraph() {
        return new PreparedGraph();
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

    /**
     * Validates ordinary JSON model graphs and restores the parent bindings
     * that vanilla's material walk normally establishes.
     */
    public static final class PreparedGraph {
        private Scanner scanner;
        private final Set<UnbakedModel> prepared =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<UnbakedModel> preparing =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private PreparedGraph() {
        }

        public boolean isSafe(
                UnbakedModel model,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            return scanner(getter).requiresSequentialBaking(model) == false;
        }

        public boolean prepare(
                UnbakedModel model,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            if (!isSafe(model, getter)) {
                return false;
            }
            return bind(model, getter);
        }

        private Scanner scanner(
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            if (scanner == null) {
                scanner = DynamicModelGuard.scanner(getter);
            }
            return scanner;
        }

        private boolean bind(
                UnbakedModel model,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            if (model == null) {
                return false;
            }
            if (prepared.contains(model)) {
                return true;
            }
            if (!preparing.add(model)) {
                return false;
            }

            boolean success = true;
            if (model instanceof BlockModel blockModel) {
                success = bindParentChain(blockModel, getter);
            }
            if (success) {
                try {
                    for (ResourceLocation dependency :
                            model.getDependencies()) {
                        UnbakedModel dependencyModel =
                                getter.apply(dependency);
                        if (dependencyModel != model
                                && !bind(dependencyModel, getter)) {
                            success = false;
                            break;
                        }
                    }
                } catch (RuntimeException failure) {
                    success = false;
                }
            }

            preparing.remove(model);
            if (success) {
                prepared.add(model);
            }
            return success;
        }

        private static boolean bindParentChain(
                BlockModel model,
                Function<ResourceLocation, UnbakedModel> getter
        ) {
            Set<BlockModel> chain =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            BlockModel current = model;
            while (current.getParentLocation() != null
                    && current.parent == null) {
                if (!chain.add(current)) {
                    return false;
                }
                UnbakedModel parent =
                        getter.apply(current.getParentLocation());
                if (!(parent instanceof BlockModel parentModel)
                        || chain.contains(parentModel)) {
                    return false;
                }
                current.parent = parentModel;
                current = parentModel;
            }
            return true;
        }
    }
}
