package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Parses only plain vanilla-format model JSON away from the model-loading
 * thread. Forge geometry loaders are deliberately left on their established
 * sequential path because their deserializers may execute arbitrary mod code.
 */
public final class ParallelModelJsonParser {
    private static final String BUILDSCAPE_NAMESPACE = "buildscape";
    private static final String MODEL_PREFIX = "models/";
    private static final String JSON_SUFFIX = ".json";
    private static final Pattern CUSTOM_LOADER =
            Pattern.compile("\"loader\"\\s*:");

    private ParallelModelJsonParser() {
    }

    public static Map<ResourceLocation, BlockModel> parse(
            Map<ResourceLocation, String> resources
    ) {
        if (resources == null || resources.isEmpty()) {
            return Map.of();
        }

        long started = System.nanoTime();
        List<Map.Entry<ResourceLocation, String>> entries =
                new ArrayList<>(resources.entrySet());
        Map<ResourceLocation, BlockModel> parsed =
                new ConcurrentHashMap<>();
        AtomicInteger customLoaders = new AtomicInteger();
        AtomicInteger buildscapeModels = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        runBatched(entries, entry -> {
            ResourceLocation resourceLocation = entry.getKey();
            if (BUILDSCAPE_NAMESPACE.equals(
                    resourceLocation.getNamespace()
            )) {
                buildscapeModels.incrementAndGet();
                return;
            }

            String path = resourceLocation.getPath();
            if (!path.startsWith(MODEL_PREFIX)
                    || !path.endsWith(JSON_SUFFIX)) {
                return;
            }

            String json = entry.getValue();
            if (CUSTOM_LOADER.matcher(json).find()) {
                customLoaders.incrementAndGet();
                return;
            }

            String modelPath = path.substring(
                    MODEL_PREFIX.length(),
                    path.length() - JSON_SUFFIX.length()
            );
            ResourceLocation modelLocation =
                    ResourceLocation.fromNamespaceAndPath(
                            resourceLocation.getNamespace(),
                            modelPath
                    );
            try {
                BlockModel model = BlockModel.fromStream(
                        new StringReader(json)
                );
                model.name = modelLocation.toString();
                parsed.put(modelLocation, model);
            } catch (RuntimeException | LinkageError failure) {
                failures.incrementAndGet();
                VHAccelerator.LOGGER.debug(
                        "Parallel model parsing deferred {} to the "
                                + "established model loader",
                        modelLocation,
                        failure
                );
            }
        });

        VHAccelerator.LOGGER.info(
                "Parsed {} plain model JSON resources in parallel in {} ms "
                        + "[{} Forge/custom-loader, {} BuildScape, "
                        + "{} failed resources deferred]",
                parsed.size(),
                (System.nanoTime() - started) / 1_000_000L,
                customLoaders.get(),
                buildscapeModels.get(),
                failures.get()
        );
        return parsed;
    }

    private static <T> void runBatched(
            List<T> values,
            java.util.function.Consumer<T> action
    ) {
        int parallelism = Math.max(
                1,
                Math.min(
                        Runtime.getRuntime().availableProcessors(),
                        values.size()
                )
        );
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
