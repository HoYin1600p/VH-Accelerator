package dev.hoyin1600p.vhaccelerator.backport.modernfix.model;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PropertyNameCacheTest {
    @AfterEach
    void clearCache() {
        PropertyNameCache.clearForTest();
    }

    @Test
    void returnsCanonicalFirstInstance() {
        String first = new String("powered");
        String duplicate = new String("powered");

        assertSame(first, PropertyNameCache.deduplicate(first));
        assertSame(first, PropertyNameCache.deduplicate(duplicate));
    }
}
