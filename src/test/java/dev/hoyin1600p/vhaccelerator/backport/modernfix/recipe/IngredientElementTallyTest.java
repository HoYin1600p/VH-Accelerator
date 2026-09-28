package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class IngredientElementTallyTest {
    @Test
    void emptyIngredientHasNoStacks() {
        assertEquals(0, new IngredientElementTally().total());
    }

    @Test
    void identicalItemValueStacksCollapseLikeDistinct() {
        Object stack = new Object();
        IngredientElementTally tally = new IngredientElementTally();
        tally.addItemValueStack(stack);
        tally.addItemValueStack(stack);
        assertEquals(1, tally.total());
        assertSame(stack, tally.singleItemValueStack());
        assertFalse(tally.singleStackIsEmptyTagBarrier());
    }

    @Test
    void differentItemValueStacksCountSeparately() {
        IngredientElementTally tally = new IngredientElementTally();
        tally.addItemValueStack(new Object());
        tally.addItemValueStack(new Object());
        assertEquals(2, tally.total());
        assertNull(tally.singleItemValueStack());
    }

    @Test
    void emptyTagBecomesOneBarrierUnlessTreatedAsAir() {
        IngredientElementTally barrier = new IngredientElementTally();
        barrier.addTag(0, false);
        assertEquals(1, barrier.total());
        assertTrue(barrier.singleStackIsEmptyTagBarrier());
        assertNull(barrier.singleItemValueStack());

        IngredientElementTally air = new IngredientElementTally();
        air.addTag(0, true);
        assertEquals(0, air.total());
    }

    @Test
    void boundTagContributesFreshStacksThatNeverCollapse() {
        Object stack = new Object();
        IngredientElementTally tally = new IngredientElementTally();
        tally.addItemValueStack(stack);
        tally.addTag(3, false);
        assertEquals(4, tally.total());
        assertNull(tally.singleItemValueStack());

        IngredientElementTally single = new IngredientElementTally();
        single.addTag(1, false);
        assertEquals(1, single.total());
        assertFalse(single.singleStackIsEmptyTagBarrier());
        assertNull(single.singleItemValueStack());
    }

    @Test
    void twoEmptyTagsAreTwoBarriers() {
        IngredientElementTally tally = new IngredientElementTally();
        tally.addTag(0, false);
        tally.addTag(0, false);
        assertEquals(2, tally.total());
        assertFalse(tally.singleStackIsEmptyTagBarrier());
    }
}
