package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class IngredientReloadTrackerTest {
    @AfterEach
    void reset() {
        IngredientReloadTracker.resetForTest();
    }

    @Test
    void keepsOptimizationDisabledUntilNestedReloadsFinish() {
        IngredientReloadTracker.begin();
        IngredientReloadTracker.begin();

        assertTrue(IngredientReloadTracker.active());
        assertEquals(2, IngredientReloadTracker.activeCount());

        IngredientReloadTracker.finish();
        assertTrue(IngredientReloadTracker.active());

        IngredientReloadTracker.finish();
        assertFalse(IngredientReloadTracker.active());
    }

    @Test
    void cannotUnderflowAfterFailedOrRepeatedCompletion() {
        IngredientReloadTracker.finish();

        assertEquals(0, IngredientReloadTracker.activeCount());
        assertFalse(IngredientReloadTracker.active());
    }
}
