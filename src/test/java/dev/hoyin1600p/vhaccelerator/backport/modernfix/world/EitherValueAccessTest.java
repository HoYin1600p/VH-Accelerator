package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.datafixers.util.Either;
import org.junit.jupiter.api.Test;

class EitherValueAccessTest {
    @Test
    void returnsLeftValueWithoutChangingRightSemantics() {
        assertTrue(EitherValueAccess.optimizedAccessAvailable());
        assertEquals("value", EitherValueAccess.leftOrNull(
                Either.<String, Integer>left("value")
        ));
        assertNull(EitherValueAccess.leftOrNull(
                Either.<String, Integer>right(7)
        ));
    }
}
