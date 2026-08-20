package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CanonicalRecipeSemanticsTest {
    @Test
    void ignoresRecipeAndIngredientCandidateIterationOrder() {
        var first = recipe(
                "example:first",
                List.of(List.of("example:b", "example:a"))
        );
        var reordered = recipe(
                "example:first",
                List.of(List.of("example:a", "example:b"))
        );
        var second = recipe(
                "example:second",
                List.of(List.of("example:c"))
        );
        assertEquals(
                CanonicalRecipeSemantics.digest(List.of(first, second)),
                CanonicalRecipeSemantics.digest(List.of(second, reordered))
        );
    }

    @Test
    void detectsIngredientSlotAndOutputChanges() {
        var original = recipe(
                "example:recipe",
                List.of(List.of("example:a"), List.of("example:b"))
        );
        var slotsSwapped = recipe(
                "example:recipe",
                List.of(List.of("example:b"), List.of("example:a"))
        );
        var outputChanged = new CanonicalRecipeSemantics.Entry(
                original.id(),
                original.serializer(),
                original.recipeClass(),
                original.special(),
                original.group(),
                "example:other|1|0|{}",
                original.ingredients()
        );
        assertNotEquals(
                CanonicalRecipeSemantics.digest(List.of(original)),
                CanonicalRecipeSemantics.digest(List.of(slotsSwapped))
        );
        assertNotEquals(
                CanonicalRecipeSemantics.digest(List.of(original)),
                CanonicalRecipeSemantics.digest(List.of(outputChanged))
        );
    }

    private static CanonicalRecipeSemantics.Entry recipe(
            String id,
            List<List<String>> ingredients
    ) {
        return new CanonicalRecipeSemantics.Entry(
                id,
                "minecraft:crafting_shaped",
                "example.Recipe",
                false,
                "",
                "example:result|1|0|{}",
                ingredients
        );
    }
}
