package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockGraphSkipSessionTest {
    @Test
    void restoresVanillaGroupEquivalenceClasses() {
        List<String> states = List.of("s0", "s1", "s2", "s3", "s4", "s5");
        Map<String, Integer> set = new HashMap<>();
        List<List<String>> registered = new ArrayList<>();
        // Labels: two states share group 1, s2 is ungrouped, s3 is non-MODEL,
        // s4 and s5 share group 2.
        BlockGraphSkipSession.restoreGroups(
                new int[] {1, 1, -1, 0, 2, 2}, states, set::put, registered::add);
        assertEquals(Map.of("s3", 0), set);
        assertEquals(List.of(List.of("s0", "s1"), List.of("s4", "s5")), registered);
    }

    @Test
    void singletonLabelsAreLeftUngroupedAsVanillaDoes() {
        List<List<String>> registered = new ArrayList<>();
        BlockGraphSkipSession.restoreGroups(
                new int[] {1, 2}, List.of("a", "b"), (state, group) -> fail(), registered::add);
        assertTrue(registered.isEmpty());
    }

    @Test
    void aReloadOnlyEverClosesTheOlderGeneration() {
        // Launch reload 1 installs its registry.
        BlockGraphSkipSession first = BlockGraphSkipSession.forTest();
        first.activate();
        // Launch reload 2 starts: the old packs close, so reload 1 stops loading.
        BlockGraphSkipSession.closeLoadingForReload();
        assertTrue(first.loadingClosed());
        // Reload 2 prepares its own session, which is not live yet.
        BlockGraphSkipSession second = BlockGraphSkipSession.forTest();
        // Reload 2 applies: retirement must close reload 1, not reload 2.
        BlockGraphSkipSession.closeCurrent();
        assertFalse(second.loadingClosed(), "The session being applied stays open");
        second.activate();
        assertFalse(second.loadingClosed());
        BlockGraphSkipSession.closeCurrent();
        assertTrue(second.loadingClosed());
    }
}
