package dev.hoyin1600p.vhaccelerator.backport.modernfix.recipe;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class IngredientItemStacksSoftReferenceTest {
    @Test
    void collectedReferenceIsReportedByIdentity() {
        RecordingOwner owner = new RecordingOwner();
        IngredientItemStacksSoftReference reference =
                new IngredientItemStacksSoftReference(
                        owner,
                        new ItemStack[0]
                );

        reference.enqueue();
        IngredientItemStacksSoftReference.clearCollectedReferences();

        assertSame(reference, owner.cleared);
    }

    private static final class RecordingOwner
            implements IngredientExpansionCacheOwner {
        private IngredientItemStacksSoftReference cleared;

        @Override
        public void vha$clearExpansionReference(
                IngredientItemStacksSoftReference expected
        ) {
            this.cleared = expected;
        }
    }
}
