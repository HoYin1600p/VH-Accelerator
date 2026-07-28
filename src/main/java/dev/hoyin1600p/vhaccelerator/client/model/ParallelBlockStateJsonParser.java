package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentBlockStateJsonCache;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Prepares ordered raw blockstate resource stacks without invoking model
 * parsers or publishing partially constructed definitions.
 */
public final class ParallelBlockStateJsonParser {
    private static final String BUILDSCAPE_NAMESPACE = "buildscape";
    private static final String PREFIX = "blockstates/";
    private static final String SUFFIX = ".json";

    private ParallelBlockStateJsonParser() {
    }

    @Nullable
    public static Session prepare(ResourceManager resourceManager) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.parallelBlockStateLoading
                )
                || LaunchTimer.isFinished()) {
            return null;
        }

        long started = System.nanoTime();
        PersistentBlockStateJsonCache.Session persistent =
                PersistentBlockStateJsonCache.begin(
                        resourceManager
                );
        Collection<ResourceLocation> listed =
                resourceManager.listResources(
                        "blockstates",
                        path -> path.endsWith(SUFFIX)
                );
        List<ResourceLocation> locations =
                new ArrayList<>(listed);
        Map<ResourceLocation, List<Resource>> cached =
                new ConcurrentHashMap<>();
        AtomicInteger preparedResources = new AtomicInteger();
        AtomicInteger buildscape = new AtomicInteger();
        AtomicInteger unknownBlocks = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger restoredStacks = new AtomicInteger();

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

            List<PersistentBlockStateJsonCache.RawResource>
                    rawResources = persistent == null
                    ? null
                    : persistent.restored(location);
            if (rawResources != null) {
                restoredStacks.incrementAndGet();
            } else {
                rawResources = readRawResources(
                        resourceManager,
                        location,
                        persistent,
                        failures
                );
                if (rawResources == null) {
                    return;
                }
            }
            List<Resource> prepared =
                    new ArrayList<>(rawResources.size());
            try {
                for (PersistentBlockStateJsonCache.RawResource
                        resource : rawResources) {
                    prepared.add(new PreparedResource(
                            location,
                            resource.sourceName(),
                            resource.bytes()
                    ));
                    preparedResources.incrementAndGet();
                }
                cached.put(location, List.copyOf(prepared));
            } catch (RuntimeException failure) {
                failures.incrementAndGet();
                VHAccelerator.LOGGER.debug(
                        "Parallel blockstate reading deferred {}",
                        location,
                        failure
                );
                if (persistent != null) {
                    persistent.markIncomplete();
                }
            }
        });
        PersistentBlockStateJsonCache.finish(persistent);

        VHAccelerator.LOGGER.info(
                "Prepared {} blockstate resource stacks in parallel in {} ms "
                        + "[{} raw resources, {} persistent stacks, "
                        + "{} BuildScape, {} unknown blocks, "
                        + "{} failures deferred]",
                cached.size(),
                (System.nanoTime() - started) / 1_000_000L,
                preparedResources.get(),
                restoredStacks.get(),
                buildscape.get(),
                unknownBlocks.get(),
                failures.get()
        );
        return new Session(Map.copyOf(cached));
    }

    @Nullable
    private static List<
            PersistentBlockStateJsonCache.RawResource>
            readRawResources(
                    ResourceManager resourceManager,
                    ResourceLocation location,
                    @Nullable
                    PersistentBlockStateJsonCache.Session persistent,
                    AtomicInteger failures
            ) {
        List<Resource> resources;
        try {
            resources = resourceManager.getResources(location);
        } catch (IOException | RuntimeException failure) {
            failures.incrementAndGet();
            if (persistent != null) {
                persistent.markIncomplete();
            }
            VHAccelerator.LOGGER.debug(
                    "Parallel blockstate preparation deferred {}",
                    location,
                    failure
            );
            return null;
        }

        List<PersistentBlockStateJsonCache.RawResource> raw =
                new ArrayList<>(resources.size());
        try {
            for (Resource resource : resources) {
                String sourceName = resource.getSourceName();
                byte[] bytes;
                try (InputStream input =
                             resource.getInputStream()) {
                    bytes = input.readAllBytes();
                }
                raw.add(
                        new PersistentBlockStateJsonCache.RawResource(
                                sourceName,
                                bytes
                        )
                );
            }
            List<PersistentBlockStateJsonCache.RawResource>
                    stable = List.copyOf(raw);
            if (persistent != null) {
                persistent.record(location, stable);
            }
            return stable;
        } catch (IOException | RuntimeException failure) {
            failures.incrementAndGet();
            if (persistent != null) {
                persistent.markIncomplete();
            }
            VHAccelerator.LOGGER.debug(
                    "Parallel blockstate reading deferred {}",
                    location,
                    failure
            );
            return null;
        }
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

        private PreparedResource(
                ResourceLocation location,
                String sourceName,
                byte[] bytes
        ) {
            this.location = location;
            this.sourceName = sourceName;
            this.bytes = bytes;
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
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
