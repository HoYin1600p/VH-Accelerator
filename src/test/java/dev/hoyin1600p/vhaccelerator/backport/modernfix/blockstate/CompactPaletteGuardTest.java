package dev.hoyin1600p.vhaccelerator.backport.modernfix.blockstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompactPaletteGuardTest {
    @Test
    void invalidPaletteLookupDegradesWithoutThrowing() {
        assertTrue(CompactPaletteGuard.firstValue(index -> {
            throw new IllegalArgumentException("invalid palette");
        }).isEmpty());
    }

    @Test
    void validPaletteLookupReturnsItsFirstValue() {
        assertEquals(
                "air",
                CompactPaletteGuard.firstValue(index -> "air").orElseThrow()
        );
    }

    @Test
    void emptyStorageRequiresEveryWordToBeZero() {
        assertTrue(CompactPaletteGuard.storageIsEmpty(new long[]{0L, 0L}));
        assertFalse(CompactPaletteGuard.storageIsEmpty(new long[]{0L, 1L}));
    }
}
