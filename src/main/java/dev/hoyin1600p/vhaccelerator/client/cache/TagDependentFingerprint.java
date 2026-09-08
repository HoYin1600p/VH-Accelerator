package dev.hoyin1600p.vhaccelerator.client.cache;

import java.util.Objects;
import java.util.function.Supplier;

/** Lazily fingerprints recipes only after the corresponding tags arrive. */
final class TagDependentFingerprint {
    private Supplier<String> recipes;
    private boolean tagsReceived;
    private boolean evaluated;
    private String value;

    synchronized void clear() {
        recipes = null;
        tagsReceived = false;
        evaluated = false;
        value = null;
    }

    synchronized void receiveRecipes(Supplier<String> recipes) {
        this.recipes = Objects.requireNonNull(recipes);
        tagsReceived = false;
        evaluated = false;
        value = null;
    }

    synchronized void receiveTags() {
        tagsReceived = true;
        evaluated = false;
        value = null;
    }

    /** Call from the recipe lifecycle after tag application, never packet HEAD. */
    synchronized String current() {
        if (recipes == null || !tagsReceived) {
            return null;
        }
        if (!evaluated) {
            value = recipes.get();
            evaluated = true;
        }
        return value;
    }
}
