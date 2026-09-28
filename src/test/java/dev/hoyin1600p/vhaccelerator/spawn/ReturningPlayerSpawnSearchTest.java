package dev.hoyin1600p.vhaccelerator.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ReturningPlayerSpawnSearchTest {
    @AfterEach
    void clear() {
        ReturningPlayerSpawnSearch.clear();
    }

    @Test
    void defaultsToTheVanillaSearch() {
        assertFalse(ReturningPlayerSpawnSearch.consumeSkip());
    }

    @Test
    void eachDecisionIsConsumedByExactlyOneConstruction() {
        ReturningPlayerSpawnSearch.skipNextConstruction(true);
        assertTrue(ReturningPlayerSpawnSearch.consumeSkip());
        assertFalse(ReturningPlayerSpawnSearch.consumeSkip());
    }

    @Test
    void disarmingOverridesAnEarlierDecision() {
        ReturningPlayerSpawnSearch.skipNextConstruction(true);
        ReturningPlayerSpawnSearch.skipNextConstruction(false);
        assertFalse(ReturningPlayerSpawnSearch.consumeSkip());
    }

    @Test
    void clearRemovesAnUnconsumedDecision() {
        ReturningPlayerSpawnSearch.skipNextConstruction(true);
        ReturningPlayerSpawnSearch.clear();
        assertFalse(ReturningPlayerSpawnSearch.armedForTest());
    }

    @Test
    void decisionsStayOnTheirThread() throws InterruptedException {
        ReturningPlayerSpawnSearch.skipNextConstruction(true);
        boolean[] otherThreadSawSkip = new boolean[1];
        Thread other = new Thread(() -> otherThreadSawSkip[0] = ReturningPlayerSpawnSearch.consumeSkip());
        other.start();
        other.join();
        assertFalse(otherThreadSawSkip[0]);
        assertTrue(ReturningPlayerSpawnSearch.consumeSkip());
    }
}
