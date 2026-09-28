package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ServerScopedCacheMemoryTest {
    @Test
    void matchesEveryKeyShapeForOneServerOnly() {
        assertTrue(ServerScopedCacheMemory.belongsTo("abc", "abc"));
        assertTrue(ServerScopedCacheMemory.belongsTo("abc:10", "abc"));
        assertTrue(ServerScopedCacheMemory.belongsTo("abc-jei10-f00", "abc"));
        assertFalse(ServerScopedCacheMemory.belongsTo("abcd", "abc"));
        assertFalse(ServerScopedCacheMemory.belongsTo("abcd:10", "abc"));
        assertFalse(ServerScopedCacheMemory.belongsTo("xyz:10", "abc"));
    }

    @Test
    void retainsOnlyTheCurrentServerAndKeepsTheMapKind() {
        Map<String, String> concurrent = new ConcurrentHashMap<>(Map.of(
                "abc:9", "a", "abc-jei10-1", "b", "other:9", "c"));
        Map<String, String> kept = ServerScopedCacheMemory.retain(concurrent, "abc");
        assertEquals(Map.of("abc:9", "a", "abc-jei10-1", "b"), kept);
        assertInstanceOf(ConcurrentHashMap.class, kept);

        Map<String, String> plain = new HashMap<>(Map.of("abc", "a", "other", "c"));
        Map<String, String> keptPlain = ServerScopedCacheMemory.retain(plain, "abc");
        assertEquals(Map.of("abc", "a"), keptPlain);
        assertInstanceOf(HashMap.class, keptPlain);
        keptPlain.put("abc", "updated");
        assertEquals("a", plain.get("abc"), "The retained map is an independent copy");
    }
}
