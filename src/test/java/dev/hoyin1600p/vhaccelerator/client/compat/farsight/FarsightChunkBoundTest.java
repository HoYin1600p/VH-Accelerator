package dev.hoyin1600p.vhaccelerator.client.compat.farsight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FarsightChunkBoundTest {
    @Test
    void boundKeepsTheLargerRadiusPlusOneChunkOfMargin() {
        assertEquals(13, FarsightChunkBound.bound(10, 12));
        assertEquals(17, FarsightChunkBound.bound(16, 8));
        assertEquals(1, FarsightChunkBound.bound(0, 0));
    }

    @Test
    void chunksInsideTheBoundStayResident() {
        assertFalse(FarsightChunkBound.exceedsBound(13, -13, 0, 0, 13));
        assertFalse(FarsightChunkBound.exceedsBound(100, 200, 100, 200, 0));
    }

    @Test
    void chunksOutsideTheBoundOnEitherAxisAreEvicted() {
        assertTrue(FarsightChunkBound.exceedsBound(14, 0, 0, 0, 13));
        assertTrue(FarsightChunkBound.exceedsBound(0, -14, 0, 0, 13));
        assertTrue(FarsightChunkBound.exceedsBound(-20, 5, 0, 0, 13));
    }
}
