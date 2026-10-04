package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.util.Digests;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** The SHA-256 encodings the fingerprint is built from. */
final class FingerprintDigests {
    private FingerprintDigests() {
    }

    static String digestFile(Path path) {
        try {
            MessageDigest digest = Digests.sha256();
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(path)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return HexFormat.of().formatHex(
                    digest.digest()
            );
        } catch (IOException exception) {
            throw new UnstableFingerprintInputException(
                    "Could not read asset-affecting configuration",
                    exception
            );
        }
    }

    static String digestStrings(List<String> values) {
        MessageDigest digest = Digests.sha256();
        for (String value : values) {
            byte[] encoded =
                    value.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (encoded.length >>> 24));
            digest.update((byte) (encoded.length >>> 16));
            digest.update((byte) (encoded.length >>> 8));
            digest.update((byte) encoded.length);
            digest.update(encoded);
        }
        return HexFormat.of().formatHex(
                digest.digest()
        );
    }
}
