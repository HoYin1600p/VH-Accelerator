package dev.hoyin1600p.vhaccelerator.backport.modernfix.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ProfileTextureHashCacheTest {
    @Test
    void sharesHashesByExactUrlAndBoundsRetainedEntries() {
        ProfileTextureHashCache cache = new ProfileTextureHashCache(2L, 60L);
        MinecraftProfileTexture first = texture(
                "https://textures.minecraft.net/texture/abcdef"
        );
        MinecraftProfileTexture duplicate = texture(
                "https://textures.minecraft.net/texture/abcdef"
        );

        assertEquals("abcdef", cache.resolve(first));
        assertEquals("abcdef", cache.resolve(duplicate));
        assertEquals(1L, cache.stats().missCount());
        assertEquals(1L, cache.stats().hitCount());

        cache.resolve(texture("https://example.invalid/texture/second"));
        cache.resolve(texture("https://example.invalid/texture/third"));
        assertTrue(cache.estimatedSize() <= 2L);
    }

    private static MinecraftProfileTexture texture(String url) {
        return new MinecraftProfileTexture(url, Map.of());
    }
}
