package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.util.Digests;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;

final class JeiRecipeIndexIdentity {
    private JeiRecipeIndexIdentity() {
    }

    static String cacheKey(
            String serverKey,
            String jeiGeneration,
            String fingerprint
    ) {
        return serverKey + "-" + jeiGeneration + "-" + fingerprint;
    }

    static String batchKey(
            String categoryUid,
            Collection<String> recipeIds
    ) {
        MessageDigest digest = Digests.sha256();
        update(digest, categoryUid);
        update(digest, Integer.toString(recipeIds.size()));
        recipeIds.stream().sorted().forEach(id -> update(digest, id));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
