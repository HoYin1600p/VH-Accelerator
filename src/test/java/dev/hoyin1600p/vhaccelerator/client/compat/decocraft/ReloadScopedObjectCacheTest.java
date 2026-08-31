package dev.hoyin1600p.vhaccelerator.client.compat.decocraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ReloadScopedObjectCacheTest {
    @Test
    void reusesOneParsedModelAcrossMaterialVariants() {
        ReloadScopedObjectCache cache = new ReloadScopedObjectCache();
        Object parsed = new Object();

        cache.begin("decocraft:models/bbmodel/armchair.bbmodel");
        assertNull(cache.find());
        cache.store(parsed);
        cache.finish();

        cache.begin("decocraft:models/bbmodel/armchair.bbmodel");
        assertSame(parsed, cache.find());
        cache.finish();

        ReloadScopedObjectCache.Snapshot snapshot = cache.snapshot();
        assertEquals(2L, snapshot.lookups());
        assertEquals(1L, snapshot.hits());
        assertEquals(1L, snapshot.parses());
        assertEquals(1, snapshot.uniqueModels());
    }

    @Test
    void clearPreventsReuseAcrossResourceReloads() {
        ReloadScopedObjectCache cache = new ReloadScopedObjectCache();
        cache.begin("decocraft:models/bbmodel/armchair.bbmodel");
        cache.store(new Object());
        cache.finish();

        cache.clear();
        cache.begin("decocraft:models/bbmodel/armchair.bbmodel");

        assertNull(cache.find());
        assertEquals(0, cache.snapshot().uniqueModels());
    }
}
