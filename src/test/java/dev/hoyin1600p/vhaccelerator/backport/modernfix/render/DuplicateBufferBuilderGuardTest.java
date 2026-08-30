package dev.hoyin1600p.vhaccelerator.backport.modernfix.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DuplicateBufferBuilderGuardTest {
    @Test
    void permitsOnlyTheFirstBuilderForAKey() {
        Map<String, Object> builders = new LinkedHashMap<>();
        Object original = new Object();

        assertTrue(DuplicateBufferBuilderGuard.shouldCreate(
                builders,
                "solid"
        ));
        builders.put("solid", original);
        assertFalse(DuplicateBufferBuilderGuard.shouldCreate(
                builders,
                "solid"
        ));
        assertTrue(DuplicateBufferBuilderGuard.shouldCreate(
                builders,
                "translucent"
        ));
    }
}
