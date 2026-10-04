package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Lazily fingerprints recipes only after the corresponding tags arrive. Once
 * the tags are applied the fingerprint can start in the background, so JEI's
 * first recipe registration picks up a finished value instead of computing
 * it on the render thread.
 */
final class TagDependentFingerprint {
    private Supplier<String> recipes;
    private boolean tagsReceived;
    private boolean evaluated;
    private String value;
    private CompletableFuture<String> pending;

    synchronized void clear() {
        recipes = null;
        tagsReceived = false;
        evaluated = false;
        value = null;
        pending = null;
    }

    synchronized void receiveRecipes(Supplier<String> recipes) {
        this.recipes = Objects.requireNonNull(recipes);
        tagsReceived = false;
        evaluated = false;
        value = null;
        pending = null;
    }

    synchronized void receiveTags() {
        tagsReceived = true;
        evaluated = false;
        value = null;
        pending = null;
    }

    /** Starts the fingerprint in the background; call only after the tags are applied. */
    synchronized void prefetch() {
        if (recipes == null || !tagsReceived || evaluated || pending != null) {
            return;
        }
        Supplier<String> source = recipes;
        pending = CompletableFuture.supplyAsync(source::get);
    }

    /** Call from the recipe lifecycle after tag application, never packet HEAD. */
    synchronized String current() {
        if (recipes == null || !tagsReceived) {
            return null;
        }
        if (!evaluated) {
            CompletableFuture<String> started = pending;
            pending = null;
            String result = null;
            boolean done = false;
            if (started != null) {
                try {
                    result = started.join();
                    done = true;
                } catch (RuntimeException failure) {
                    // Fall back to computing it here, as before.
                }
            }
            value = done ? result : recipes.get();
            evaluated = true;
        }
        return value;
    }
}
