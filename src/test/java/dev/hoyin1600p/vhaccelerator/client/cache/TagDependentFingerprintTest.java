package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class TagDependentFingerprintTest {
    @Test
    void expandsOnlyAfterTagsAndMemoizesAcrossCategories() {
        TagDependentFingerprint state = new TagDependentFingerprint();
        AtomicReference<String> tags = new AtomicReference<>("previous-server");
        AtomicInteger calls = new AtomicInteger();
        state.receiveRecipes(() -> {
            calls.incrementAndGet();
            return tags.get();
        });
        assertNull(state.current());
        assertEquals(0, calls.get());
        state.receiveTags();
        tags.set("current-server");
        assertEquals("current-server", state.current());
        assertEquals("current-server", state.current());
        assertEquals(1, calls.get());
    }

    @Test
    void newRecipesCannotUseOldTagsAndTagOnlyUpdatesRecompute() {
        TagDependentFingerprint state = new TagDependentFingerprint();
        AtomicReference<String> tags = new AtomicReference<>("a");
        state.receiveRecipes(tags::get);
        state.receiveTags();
        assertEquals("a", state.current());
        state.receiveRecipes(tags::get);
        assertNull(state.current());
        state.receiveTags();
        tags.set("b");
        assertEquals("b", state.current());
        state.receiveTags();
        tags.set("c");
        assertEquals("c", state.current());
        state.clear();
        state.receiveTags();
        assertNull(state.current());
    }

    @Test
    void failedFingerprintBypassesCacheWithoutRetryingEveryCategory() {
        TagDependentFingerprint state = new TagDependentFingerprint();
        AtomicInteger calls = new AtomicInteger();
        state.receiveRecipes(() -> { calls.incrementAndGet(); return null; });
        state.receiveTags();
        assertNull(state.current());
        assertNull(state.current());
        assertEquals(1, calls.get());
    }
}
