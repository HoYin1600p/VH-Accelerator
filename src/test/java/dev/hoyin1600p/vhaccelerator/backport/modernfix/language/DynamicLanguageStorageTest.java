package dev.hoyin1600p.vhaccelerator.backport.modernfix.language;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

final class DynamicLanguageStorageTest {
    @Test
    void reopensSingleUseResourcesAndPreservesLiteralInjections() {
        ResourceLocation location = new ResourceLocation(
                "example",
                "lang/en_us.json"
        );
        TestResourceManager manager = new TestResourceManager(location);
        manager.put(
                "base",
                "{\"shared\":\"base\",\"base_only\":\"base only\"}"
        );
        manager.put(
                "high",
                "{\"shared\":\"high\",\"high_only\":\"high only\"}"
        );

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("shared", "high");
        raw.put("base_only", "base only");
        raw.put("high_only", "high only");
        raw.put("injected", "injected value");

        DynamicLanguageStorage.BuildResult result =
                DynamicLanguageStorage.createStorage(
                        manager,
                        raw,
                        List.of(location)
                );

        assertEquals(3, result.dynamicEntries());
        assertEquals(1, result.literalEntries());
        assertEquals(2, result.sourceCount());
        assertEquals(22L, result.dynamicCharacters());
        assertEquals(2, manager.opened.get());
        assertEquals(2, manager.closed.get());

        manager.put(
                "high",
                "{\"shared\":\"updated\",\"high_only\":\"high only\"}"
        );
        assertEquals("updated", result.storage().get("shared"));
        assertEquals("base only", result.storage().get("base_only"));
        assertEquals("injected value", result.storage().get("injected"));
        assertEquals(manager.opened.get(), manager.closed.get());
    }

    private static final class TestResourceManager implements ResourceManager {
        private final ResourceLocation location;
        private final Map<String, String> sources = new LinkedHashMap<>();
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        private TestResourceManager(ResourceLocation location) {
            this.location = location;
        }

        private void put(String sourceName, String json) {
            sources.put(sourceName, json);
        }

        @Override
        public Resource getResource(ResourceLocation requested)
                throws IOException {
            List<Resource> resources = getResources(requested);
            if (resources.isEmpty()) {
                throw new IOException("missing test resource");
            }
            return resources.get(resources.size() - 1);
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of(location.getNamespace());
        }

        @Override
        public boolean hasResource(ResourceLocation requested) {
            return location.equals(requested);
        }

        @Override
        public List<Resource> getResources(ResourceLocation requested) {
            if (!location.equals(requested)) {
                return List.of();
            }
            return sources.entrySet().stream()
                    .map(entry -> new TestResource(
                            location,
                            entry.getKey(),
                            entry.getValue(),
                            opened,
                            closed
                    ))
                    .map(Resource.class::cast)
                    .toList();
        }

        @Override
        public Collection<ResourceLocation> listResources(
                String path,
                Predicate<String> filter
        ) {
            return filter.test(location.getPath())
                    ? List.of(location)
                    : List.of();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }

    private static final class TestResource implements Resource {
        private final ResourceLocation location;
        private final String sourceName;
        private final InputStream inputStream;
        private final AtomicInteger closed;
        private boolean isClosed;

        private TestResource(
                ResourceLocation location,
                String sourceName,
                String json,
                AtomicInteger opened,
                AtomicInteger closed
        ) {
            this.location = location;
            this.sourceName = sourceName;
            this.closed = closed;
            inputStream = new ByteArrayInputStream(
                    json.getBytes(StandardCharsets.UTF_8)
            );
            opened.incrementAndGet();
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T> T getMetadata(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }

        @Override
        public void close() throws IOException {
            if (!isClosed) {
                isClosed = true;
                inputStream.close();
                closed.incrementAndGet();
            }
        }
    }
}
