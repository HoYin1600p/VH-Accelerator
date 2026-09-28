package dev.hoyin1600p.vhaccelerator.shape;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LaunchShapeCacheTest {
    @AfterEach
    void reset() {
        LaunchShapeCache.resetForTest();
    }

    @Test
    void equalBoundsShareTheFirstBox() {
        Object first = new Object();
        Object second = new Object();
        assertNull(LaunchShapeCache.box(0, 0, 0, 1, 0.5, 1));
        assertSame(first, LaunchShapeCache.storeBox(0, 0, 0, 1, 0.5, 1, first));
        assertSame(first, LaunchShapeCache.storeBox(0, 0, 0, 1, 0.5, 1, second));
        assertSame(first, LaunchShapeCache.box(0, 0, 0, 1, 0.5, 1));
        assertNull(LaunchShapeCache.box(0, 0, 0, 1, 0.25, 1));
    }

    @Test
    void joinsAreKeyedByInputAndOperationIdentity() {
        Object a = new Object();
        Object b = new Object();
        Object or = new Object();
        Object and = new Object();
        Object result = new Object();
        LaunchShapeCache.storeJoin(a, b, or, result);
        assertSame(result, LaunchShapeCache.join(a, b, or));
        assertNull(LaunchShapeCache.join(b, a, or), "Operand order matters");
        assertNull(LaunchShapeCache.join(a, b, and));
        assertNull(LaunchShapeCache.join(new String("a"), b, or));
    }

    @Test
    void optimizedResultsAreKeyedByIdentity() {
        String shape = new String("shape");
        Object optimized = new Object();
        LaunchShapeCache.storeOptimized(shape, optimized);
        assertSame(optimized, LaunchShapeCache.optimized(shape));
        assertNull(LaunchShapeCache.optimized(new String("shape")));
    }

    @Test
    void closingReleasesEverythingAndStopsCaching() {
        Object box = new Object();
        LaunchShapeCache.storeBox(0, 0, 0, 1, 1, 1, box);
        LaunchShapeCache.box(0, 0, 0, 1, 1, 1);
        List<String> messages = new ArrayList<>();
        LaunchShapeCache.close(messages::add);
        assertFalse(LaunchShapeCache.isOpen());
        assertNull(LaunchShapeCache.box(0, 0, 0, 1, 1, 1));
        Object later = new Object();
        assertSame(later, LaunchShapeCache.storeBox(0, 0, 0, 1, 1, 1, later),
                "After closing, callers get their own shape back");
        assertNull(LaunchShapeCache.box(0, 0, 0, 1, 1, 1));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("reused 1 boxes"), messages.get(0));
    }
}
