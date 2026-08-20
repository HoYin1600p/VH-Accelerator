package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class JeiRecipeIndexIdentityTest {
    @Test
    void separatesSynchronizedRecipeStates() {
        assertNotEquals(
                JeiRecipeIndexIdentity.cacheKey(
                        "server",
                        "jei10",
                        "full-recipe-state"
                ),
                JeiRecipeIndexIdentity.cacheKey(
                        "server",
                        "jei10",
                        "filtered-recipe-state"
                )
        );
    }

    @Test
    void identifiesRecipeBatchesIndependentlyOfIterationOrder() {
        String expected = JeiRecipeIndexIdentity.batchKey(
                "minecraft:crafting",
                List.of("example:first", "example:second")
        );
        assertEquals(
                expected,
                JeiRecipeIndexIdentity.batchKey(
                        "minecraft:crafting",
                        List.of("example:second", "example:first")
                )
        );
        assertNotEquals(
                expected,
                JeiRecipeIndexIdentity.batchKey(
                        "minecraft:crafting",
                        List.of("example:first")
                )
        );
    }

    @Test
    void separatesCategoriesWithIdenticalRecipes() {
        assertNotEquals(
                JeiRecipeIndexIdentity.batchKey(
                        "minecraft:crafting",
                        List.of("example:recipe")
                ),
                JeiRecipeIndexIdentity.batchKey(
                        "minecraft:smelting",
                        List.of("example:recipe")
                )
        );
    }
}
