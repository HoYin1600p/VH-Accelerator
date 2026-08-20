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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "semantic-recipe-payload-v1");
            update(digest, Integer.toString(source.size()));
            List<Entry> recipes = new ArrayList<>(source);
            recipes.sort(Comparator.comparing(Entry::id)
                    .thenComparing(Entry::serializer));
            for (Entry recipe : recipes) {
                update(digest, recipe.id());
                update(digest, recipe.serializer());
                update(digest, recipe.recipeClass());
                update(digest, Boolean.toString(recipe.special()));
                update(digest, recipe.group());
                update(digest, recipe.result());
                update(digest, Integer.toString(recipe.ingredients().size()));
                for (List<String> candidates : recipe.ingredients()) {
                    List<String> sorted = candidates.stream()
                            .sorted()
                            .distinct()
                            .toList();
                    update(digest, Integer.toString(sorted.size()));
                    sorted.forEach(candidate -> update(digest, candidate));
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
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
