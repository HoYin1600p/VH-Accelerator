package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class ObjectHolderRedundantCallbackPrunerTest {
    @Test
    void allEligibilityCombinationsRetainUnlessEverySafetyConditionPasses() {
        int removable = 0;
        for (int bits = 0; bits < 512; bits++) {
            if (ObjectHolderRedundantCallbackPruner.safeToRemove(
                    new ObjectHolderRedundantCallbackPruner.Eligibility(
                            (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                            (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0,
                            (bits & 64) != 0, (bits & 128) != 0, (bits & 256) != 0))) {
                removable++;
                assertEquals(1 + 2 + 4 + 8 + 16 + 64 + 256, bits);
            }
        }
        assertEquals(1, removable);
    }

    @Test
    void overrideSnapshotsArePerPassAndPerRegistryIdentity() throws Exception {
        var method = FakeRegistry.class.getMethod("owners");
        var lookup = new ObjectHolderRedundantCallbackPruner.OverrideOwnerLookup(method, true);
        FakeRegistry first = new FakeRegistry();
        FakeRegistry second = new FakeRegistry();
        assertEquals(first, second); // Deliberately equal objects must NOT share snapshots.
        var snapshot = lookup.get(first);
        assertSame(snapshot, lookup.get(first));
        assertNotSame(snapshot, lookup.get(second));
        assertEquals(1, first.calls);
        assertEquals(1, second.calls);
        first.values.put("new-override", "owner");
        var nextPass = new ObjectHolderRedundantCallbackPruner.OverrideOwnerLookup(method, true);
        assertTrue(nextPass.get(first).containsKey("new-override"));
        assertEquals(2, first.calls);
    }

    @Test
    void originalModeStillLooksUpEachHolderAndFailuresDoNotCacheAnEmptyMap() throws Exception {
        var method = FakeRegistry.class.getMethod("owners");
        FakeRegistry registry = new FakeRegistry();
        var original = new ObjectHolderRedundantCallbackPruner.OverrideOwnerLookup(method, false);
        original.get(registry);
        original.get(registry);
        assertEquals(2, registry.calls);
        var optimized = new ObjectHolderRedundantCallbackPruner.OverrideOwnerLookup(method, true);
        registry.fail = true;
        assertThrows(ReflectiveOperationException.class, () -> optimized.get(registry));
        registry.fail = false;
        registry.values.put("override", "owner");
        assertTrue(optimized.get(registry).containsKey("override"));
        var malformed = new ObjectHolderRedundantCallbackPruner.OverrideOwnerLookup(
                FakeRegistry.class.getMethod("invalidOwners"), true);
        assertThrows(ReflectiveOperationException.class, () -> malformed.get(registry));
    }

    @Test
    void invalidAndUnresolvedHoldersExitBeforeLaterReflectionAndAreNotRemoved() throws Exception {
        FakeHolder invalid = new FakeHolder();
        invalid.registry = new Object(); // Would fail ForgeRegistry cast if evaluated.
        FakeHolder unresolved = new FakeHolder();
        unresolved.valid = true;
        Set<Object> holders = new LinkedHashSet<>();
        holders.add(invalid);
        holders.add(unresolved);
        holders.add(new Object());
        holders.add(null);
        var result = ObjectHolderRedundantCallbackPruner.pruneHandlers(
                holders, FakeHolder.class, field("registry"), field("key"),
                null, field("valid"), null, null, true);
        assertEquals(4, holders.size());
        assertEquals(2, result.forgeHoldersVisited());
        assertEquals(2, result.safetyCallbacksRetained());
        assertEquals(0, result.failures());
        assertEquals(0, result.redundantCallbacksRemoved());
    }

    private static Field field(String name) throws Exception {
        Field field = FakeHolder.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static final class FakeHolder {
        boolean valid;
        Object registry;
        Object key;
    }

    public static final class FakeRegistry {
        int calls;
        boolean fail;
        final Map<String, String> values = new HashMap<>();
        public Map<String, String> owners() {
            calls++;
            if (fail) throw new IllegalStateException("Simulated lookup failure");
            return new HashMap<>(values);
        }
        public Object invalidOwners() { return null; }
        @Override public boolean equals(Object other) { return other instanceof FakeRegistry; }
        @Override public int hashCode() { return 0; }
    }

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
