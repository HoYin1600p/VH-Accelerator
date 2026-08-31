package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextureAtlasWorkerPoolTest {
    @Test
    void retainsOneWorkerOnSmallSystems() {
        assertEquals(1, TextureAtlasWorkerPool.recommendedWorkerCount(1));
        assertEquals(1, TextureAtlasWorkerPool.recommendedWorkerCount(2));
        assertEquals(1, TextureAtlasWorkerPool.recommendedWorkerCount(0));
    }

    @Test
    void scalesWithHalfTheAvailableProcessors() {
        assertEquals(2, TextureAtlasWorkerPool.recommendedWorkerCount(4));
        assertEquals(4, TextureAtlasWorkerPool.recommendedWorkerCount(8));
        assertEquals(8, TextureAtlasWorkerPool.recommendedWorkerCount(16));
    }

    @Test
    void capsLargeSystems() {
        assertEquals(16, TextureAtlasWorkerPool.recommendedWorkerCount(32));
        assertEquals(16, TextureAtlasWorkerPool.recommendedWorkerCount(128));
    }
}
