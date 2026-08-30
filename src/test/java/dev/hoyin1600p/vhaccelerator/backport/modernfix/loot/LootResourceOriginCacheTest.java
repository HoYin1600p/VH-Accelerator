package dev.hoyin1600p.vhaccelerator.backport.modernfix.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import org.junit.jupiter.api.Test;

final class LootResourceOriginCacheTest {
    @Test
    void replaysExactSourceNameAndClearsBetweenReloads() {
        LootResourceOriginCache cache = new LootResourceOriginCache();
        ResourceLocation location = new ResourceLocation(
                "example",
                "loot_tables/test.json"
        );

        cache.record(new TestResource(location, "Default"));

        Resource replay = cache.replay(location);
        assertEquals(location, replay.getLocation());
        assertEquals("Default", replay.getSourceName());
        assertEquals(1, cache.sizeForTesting());

        cache.clear();
        assertEquals(0, cache.sizeForTesting());
        assertNull(cache.replay(location));
    }

    private record TestResource(
            ResourceLocation location,
            String sourceName
    ) implements Resource {
        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
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
        public void close() {
        }
    }
}
