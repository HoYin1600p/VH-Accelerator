package dev.hoyin1600p.vhaccelerator.backport.modernfix.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SectionBlockPosIteratorTest {
    @Test
    void visitsOneSectionInVanillaOrder() {
        BlockPos first = new BlockPos(-16, 32, 48);
        Iterator<BlockPos> positions = SectionBlockPosIterator
                .betweenClosed(first, first.offset(15, 15, 15))
                .iterator();

        int count = 0;
        while (positions.hasNext()) {
            BlockPos position = positions.next();
            int expectedX = first.getX() + (count & 15);
            int expectedY = first.getY() + ((count >> 4) & 15);
            int expectedZ = first.getZ() + ((count >> 8) & 15);
            assertEquals(expectedX, position.getX());
            assertEquals(expectedY, position.getY());
            assertEquals(expectedZ, position.getZ());
            count++;
        }

        assertEquals(4096, count);
        assertThrows(NoSuchElementException.class, positions::next);
    }

    @Test
    void reusesOneMutablePositionLikeVanilla() {
        BlockPos first = BlockPos.ZERO;
        Iterator<BlockPos> positions = SectionBlockPosIterator
                .betweenClosed(first, first.offset(15, 15, 15))
                .iterator();

        BlockPos firstResult = positions.next();
        BlockPos secondResult = positions.next();

        assertSame(firstResult, secondResult);
        assertEquals(new BlockPos(1, 0, 0), secondResult);
    }

    @Test
    void rejectsNonSectionBoundsAndRetainsVanillaTraversal() {
        BlockPos first = new BlockPos(3, 4, 5);
        BlockPos second = first.offset(2, 1, 1);
        assertFalse(SectionBlockPosIterator.isOneSection(first, second));

        Iterator<BlockPos> expected = BlockPos
                .betweenClosed(first, second)
                .iterator();
        Iterator<BlockPos> actual = SectionBlockPosIterator
                .betweenClosed(first, second)
                .iterator();
        while (expected.hasNext()) {
            assertTrue(actual.hasNext());
            assertEquals(expected.next(), actual.next());
        }
        assertFalse(actual.hasNext());
    }
}
