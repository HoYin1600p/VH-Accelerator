package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ObjectHolderRedundantCallbackPrunerTest {
    @Test
    void removesOnlyResolvedSingleOwnerCallbacksWithMatchingFields() {
        assertTrue(ObjectHolderRedundantCallbackPruner.safeToRemove(
                eligibility(false, true, false, true)
        ));
    }

    @Test
    void retainsOverrideSensitiveCallbacks() {
        assertFalse(ObjectHolderRedundantCallbackPruner.safeToRemove(
                eligibility(true, true, false, true)
        ));
    }

    @Test
    void retainsDummiedCallbacks() {
        assertFalse(ObjectHolderRedundantCallbackPruner.safeToRemove(
                eligibility(false, true, true, true)
        ));
    }

    @Test
    void retainsCallbacksWhoseFieldDoesNotMatchTheRegistry() {
        assertFalse(ObjectHolderRedundantCallbackPruner.safeToRemove(
                eligibility(false, true, false, false)
        ));
    }

    @Test
    void retainsForeignHandlersAndUnresolvedCallbacks() {
        assertFalse(ObjectHolderRedundantCallbackPruner.safeToRemove(
                new ObjectHolderRedundantCallbackPruner.Eligibility(
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        false,
                        true
                )
        ));
        assertFalse(ObjectHolderRedundantCallbackPruner.safeToRemove(
                eligibility(false, false, false, true)
        ));
    }

    private static ObjectHolderRedundantCallbackPruner.Eligibility eligibility(
            boolean hasOverrides,
            boolean containsKey,
            boolean dummied,
            boolean targetMatches
    ) {
        return new ObjectHolderRedundantCallbackPruner.Eligibility(
                true,
                true,
                true,
                true,
                true,
                hasOverrides,
                containsKey,
                dummied,
                targetMatches
        );
    }
}
