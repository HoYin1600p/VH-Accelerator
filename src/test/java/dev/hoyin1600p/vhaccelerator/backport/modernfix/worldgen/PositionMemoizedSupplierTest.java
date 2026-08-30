package dev.hoyin1600p.vhaccelerator.backport.modernfix.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class PositionMemoizedSupplierTest {
    @Test
    void resolvesOnlyOnceForEachUpdatedPosition() {
        AtomicInteger calls = new AtomicInteger();
        PositionMemoizedSupplier<String> supplier = new PositionMemoizedSupplier<>(
                position -> {
                    calls.incrementAndGet();
                    return position.getX() + ":" + position.getY() + ":" + position.getZ();
                },
                new BlockPos.MutableBlockPos()
        );

        supplier.update(3, 4, 5);
        assertEquals("3:4:5", supplier.get());
        assertEquals("3:4:5", supplier.get());
        assertEquals(1, calls.get());

        supplier.update(-7, 8, 9);
        assertEquals("-7:8:9", supplier.get());
        assertEquals(2, calls.get());
    }

    @Test
    void memoizesNullLikeVanillasOriginalSupplier() {
        AtomicInteger calls = new AtomicInteger();
        PositionMemoizedSupplier<Object> supplier = new PositionMemoizedSupplier<>(
                position -> {
                    calls.incrementAndGet();
                    return null;
                },
                new BlockPos.MutableBlockPos()
        );

        supplier.update(1, 2, 3);
        assertNull(supplier.get());
        assertNull(supplier.get());
        assertEquals(1, calls.get());
    }
}
