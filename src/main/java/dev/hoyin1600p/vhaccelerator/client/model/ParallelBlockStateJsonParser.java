package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

/**
 * Prepares plain blockstate variant definitions without invoking custom model
 * loaders or publishing partially parsed state.
 */
public final class ParallelBlockStateJsonParser {
    private static final String BUILDSCAPE_NAMESPACE = "buildscape";
    private static final String PREFIX = "blockstates/";
    private static final String SUFFIX = ".json";
    private static final Pattern COMPLEX_FORMAT = Pattern.compile(
            "\"(?:multipart|forge_marker|loader)\"\\s*:"
    );
    private static final ThreadLocal<BlockModelDefinition>
            ACTIVE_DEFINITION = new ThreadLocal<>();

    private ParallelBlockStateJsonParser() {
    }

    @Nullable
    public static Session prepare(ResourceManager resourceManager) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                        .parallelBlockStateLoading
                        .get()
                || LaunchTimer.isFinished()) {
            return null;
        }

        long started = System.nanoTime();
        Collection<ResourceLocation> listed =
                resourceManager.listResources(
                        "blockstates",
                        path -> path.endsWith(SUFFIX)
                );
        List<ResourceLocation> locations =
                new ArrayList<>(listed);
        Map<ResourceLocation, List<Resource>> cached =
                new ConcurrentHashMap<>();
        AtomicInteger parsed = new AtomicInteger();
        AtomicInteger complex = new AtomicInteger();
        AtomicInteger buildscape = new AtomicInteger();
        AtomicInteger unknownBlocks = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        runBatched(locations, location -> {
            if (BUILDSCAPE_NAMESPACE.equals(
                    location.getNamespace()
            )) {
                buildscape.incrementAndGet();
                return;
            }

            ResourceLocation blockLocation =
                    blockLocation(location);
            if (blockLocation == null
                    || !Registry.BLOCK.containsKey(blockLocation)) {
                unknownBlocks.incrementAndGet();
                return;
            }
            Block block = Registry.BLOCK.get(blockLocation);
            BlockModelDefinition.Context context =
                    new BlockModelDefinition.Context();
            context.setDefinition(block.getStateDefinition());

            List<Resource> resources;
            try {
                resources = resourceManager.getResources(location);
            } catch (IOException | RuntimeException failure) {
                failures.incrementAndGet();
                VHAccelerator.LOGGER.debug(
                        "Parallel blockstate preparation deferred {}",
                        location,
                        failure
                );
                return;
            }

            List<Resource> prepared =
                    new ArrayList<>(resources.size());
            try {
                for (Resource resource : resources) {
                    String sourceName = resource.getSourceName();
                    byte[] bytes;
                    try (InputStream input =
                                 resource.getInputStream()) {
                        bytes = input.readAllBytes();
                    }
                    String json = new String(
                            bytes,
                            StandardCharsets.UTF_8
                    );
                    BlockModelDefinition definition = null;
                    if (COMPLEX_FORMAT.matcher(json).find()) {
                        complex.incrementAndGet();
                    } else {
                        try {
                            definition =
                                    BlockModelDefinition.fromStream(
                                            context,
                                            new InputStreamReader(
                                                    new ByteArrayInputStream(
                                                            bytes
                                                    ),
                                                    StandardCharsets.UTF_8
                                            )
                                    );
                            parsed.incrementAndGet();
                        } catch (RuntimeException
                                 | LinkageError failure) {
                            failures.incrementAndGet();
                            VHAccelerator.LOGGER.debug(
                                    "Parallel blockstate parsing deferred "
                                            + "{} from {}",
                                    location,
                                    sourceName,
                                    failure
                            );
                        }
                    }
                    prepared.add(new PreparedResource(
                            location,
                            sourceName,
                            bytes,
                            definition
                    ));
                }
                cached.put(location, List.copyOf(prepared));
            } catch (IOException | RuntimeException failure) {
                failures.incrementAndGet();
                VHAccelerator.LOGGER.debug(
                        "Parallel blockstate reading deferred {}",
                        location,
                        failure
                );
            }
        });

        VHAccelerator.LOGGER.info(
                "Prepared {} blockstate resource stacks in parallel in {} ms "
                        + "[{} plain resources parsed, {} complex resources, "
                        + "{} BuildScape, {} unknown blocks, {} failures "
                        + "deferred]",
                cached.size(),
                (System.nanoTime() - started) / 1_000_000L,
                parsed.get(),
                complex.get(),
                buildscape.get(),
                unknownBlocks.get(),
                failures.get()
        );
        return new Session(Map.copyOf(cached));
    }

    @Nullable
    public static BlockModelDefinition claimPreparedDefinition() {
        BlockModelDefinition definition =
                ACTIVE_DEFINITION.get();
        ACTIVE_DEFINITION.remove();
        return definition;
    }

    public static void clearPreparedDefinition() {
        ACTIVE_DEFINITION.remove();
    }

    @Nullable
    private static ResourceLocation blockLocation(
            ResourceLocation resource
    ) {
        String path = resource.getPath();
        if (!path.startsWith(PREFIX)
                || !path.endsWith(SUFFIX)) {
            return null;
        }
        String blockPath = path.substring(
                PREFIX.length(),
                path.length() - SUFFIX.length()
        );
        return ResourceLocation.fromNamespaceAndPath(
                resource.getNamespace(),
                blockPath
        );
    }

    private static <T> void runBatched(
            List<T> values,
            java.util.function.Consumer<T> action
    ) {
        if (values.isEmpty()) {
            return;
        }
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

    public record Session(
            Map<ResourceLocation, List<Resource>> resources
    ) {
        @Nullable
        public List<Resource> get(ResourceLocation location) {
            return resources.get(location);
        }
    }

    private static final class PreparedResource
            implements Resource {
        private final ResourceLocation location;
        private final String sourceName;
        private final byte[] bytes;
        @Nullable
        private final BlockModelDefinition definition;

        private PreparedResource(
                ResourceLocation location,
                String sourceName,
                byte[] bytes,
                @Nullable BlockModelDefinition definition
        ) {
            this.location = location;
            this.sourceName = sourceName;
            this.bytes = bytes;
            this.definition = definition;
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            if (definition == null) {
                ACTIVE_DEFINITION.remove();
            } else {
                ACTIVE_DEFINITION.set(definition);
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        @Nullable
        public <T> T getMetadata(
                MetadataSectionSerializer<T> serializer
        ) {
            return null;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }

        @Override
        public void close() {
        }
    }
}
