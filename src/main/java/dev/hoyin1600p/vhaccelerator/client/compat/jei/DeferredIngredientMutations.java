package dev.hoyin1600p.vhaccelerator.client.compat.jei;

/**
 * Bridges JEI's version-specific ingredient filter to runtime mutation
 * callers. Mutations that require searching the live index are replayed after
 * the complete private index has been published.
 */
public interface DeferredIngredientMutations {
    boolean vhaccelerator$deferIngredientMutation(Runnable mutation);
}
