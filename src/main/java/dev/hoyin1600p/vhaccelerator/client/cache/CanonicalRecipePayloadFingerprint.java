package dev.hoyin1600p.vhaccelerator.client.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

final class CanonicalRecipePayloadFingerprint {
    private CanonicalRecipePayloadFingerprint() {
    }

    static <T> String digest(
            Collection<T> values,
            Function<T, String> id,
            Function<T, String> serializer,
            Function<T, byte[]> payload
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "canonical-recipe-payload-v1");
            update(digest, Integer.toString(values.size()));
            List<T> sorted = new ArrayList<>(values);
            sorted.sort(Comparator.comparing(id).thenComparing(serializer));
            for (T value : sorted) {
                update(digest, id.apply(value));
                update(digest, serializer.apply(value));
                update(digest, payload.apply(value));
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
        update(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(MessageDigest digest, byte[] value) {
        digest.update((byte) (value.length >>> 24));
        digest.update((byte) (value.length >>> 16));
        digest.update((byte) (value.length >>> 8));
        digest.update((byte) value.length);
        digest.update(value);
    }
}
