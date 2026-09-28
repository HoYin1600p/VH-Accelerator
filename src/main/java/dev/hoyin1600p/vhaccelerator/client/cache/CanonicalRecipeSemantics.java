package dev.hoyin1600p.vhaccelerator.client.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Order-stable semantic recipe fingerprint inputs used by JEI caches. */
final class CanonicalRecipeSemantics {
    private CanonicalRecipeSemantics() {
    }

    static String digest(Collection<Entry> source) {
        return digest(source, true);
    }

    /**
     * {@code includeResultNbt=false} ignores each result's NBT. Some recipes
     * (Wolds Vaults' random crystals, augments and relics) randomize their
     * displayed result on every data-pack reload.
     */
    static String digest(Collection<Entry> source, boolean includeResultNbt) {
        String[] pair = digestPair(source);
        return includeResultNbt ? pair[0] : pair[1];
    }

    /**
     * Returns {full, withoutResultNbt} in one pass. Each recipe is hashed on
     * its own (in parallel; recipes are independent), then the per-recipe
     * hashes are combined in recipe-ID order, so the result does not depend
     * on packet order or thread scheduling.
     */
    static String[] digestPair(Collection<Entry> source) {
        List<Entry> recipes = new ArrayList<>(source);
        recipes.sort(Comparator.comparing(Entry::id)
                .thenComparing(Entry::serializer));
        byte[][] full = new byte[recipes.size()][];
        byte[][] structural = new byte[recipes.size()][];
        java.util.stream.IntStream.range(0, recipes.size()).parallel().forEach(index -> {
            MessageDigest shared = newDigest();
            Entry recipe = recipes.get(index);
            update(shared, recipe.id());
            update(shared, recipe.serializer());
            update(shared, recipe.recipeClass());
            update(shared, Boolean.toString(recipe.special()));
            update(shared, recipe.group());
            MessageDigest resultFull = clone(shared);
            update(resultFull, recipe.result());
            update(shared, withoutNbt(recipe.result()));
            MessageDigest ingredientsDigest = newDigest();
            update(ingredientsDigest, Integer.toString(recipe.ingredients().size()));
            for (List<String> candidates : recipe.ingredients()) {
                List<String> sorted = candidates.stream()
                        .sorted()
                        .distinct()
                        .toList();
                update(ingredientsDigest, Integer.toString(sorted.size()));
                sorted.forEach(candidate -> update(ingredientsDigest, candidate));
            }
            byte[] ingredients = ingredientsDigest.digest();
            resultFull.update(ingredients);
            shared.update(ingredients);
            full[index] = resultFull.digest();
            structural[index] = shared.digest();
        });
        return new String[] {combine(full), combine(structural)};
    }

    private static String combine(byte[][] perRecipe) {
        MessageDigest digest = newDigest();
        update(digest, "semantic-recipe-payload-v2");
        update(digest, Integer.toString(perRecipe.length));
        for (byte[] hash : perRecipe) {
            digest.update(hash);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static MessageDigest clone(MessageDigest digest) {
        try {
            return (MessageDigest) digest.clone();
        } catch (CloneNotSupportedException exception) {
            throw new IllegalStateException("SHA-256 digests must be cloneable", exception);
        }
    }

    /** Stack semantics are {@code id|count|damage|nbt}; keeps the first three. */
    static String withoutNbt(String stack) {
        int bar = -1;
        for (int i = 0; i < 3; i++) {
            bar = stack.indexOf('|', bar + 1);
            if (bar < 0) {
                return stack;
            }
        }
        return stack.substring(0, bar);
    }

        private static void update(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }

    record Entry(
            String id,
            String serializer,
            String recipeClass,
            boolean special,
            String group,
            String result,
            List<List<String>> ingredients
    ) {
        Entry {
            ingredients = ingredients.stream()
                    .map(List::copyOf)
                    .toList();
        }
    }
}
