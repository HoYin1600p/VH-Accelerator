package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelCacheSizingTest {
    @Test
    void replacesOnlyPlainEmptyMapsAndDoesNotLimitGrowth() {
        Map<Integer, Integer> original = new HashMap<>();
        Map<Integer, Integer> sized = ModelCacheSizing.reservePlainEmptyMap(original, 1);
        assertNotSame(original, sized);
        for (int i = 0; i < 1000; i++) sized.put(i, i);
        assertEquals(1000, sized.size());
        assertSame(sized, ModelCacheSizing.reservePlainEmptyMap(sized, 10));
        Map<Integer, Integer> specialized = new LinkedHashMap<>();
        assertSame(specialized, ModelCacheSizing.reservePlainEmptyMap(specialized, 10));
    }

    @Test
    void preservesTrackingWrapperAndItsStructuralVersion() {
        MutationTrackingMap<String, String> tracked =
                MutationTrackingMap.ownFreshMap(new HashMap<>());
        assertSame(tracked, ModelCacheSizing.reservePlainEmptyMap(tracked, 10));
        assertTrue(tracked.reserveIfEmpty(ModelCacheSizing.hashMapCapacity(10)));
        assertEquals(0, tracked.structuralVersion());
        tracked.put("dynamic", "model");
        assertFalse(tracked.reserveIfEmpty(100));
        assertEquals("model", tracked.get("dynamic"));
        assertEquals(1, tracked.structuralVersion());
    }

    @Test
    void capacityHandlesEmptySmallAndOverflowCounts() {
        assertEquals(1, ModelCacheSizing.hashMapCapacity(0));
        assertEquals(3, ModelCacheSizing.hashMapCapacity(2));
        assertTrue(ModelCacheSizing.hashMapCapacity(1000) >= 1334);
        assertEquals(1 << 30, ModelCacheSizing.hashMapCapacity(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> ModelCacheSizing.hashMapCapacity(-1));
    }
}
