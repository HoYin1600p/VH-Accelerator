package dev.hoyin1600p.vhaccelerator.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

/** Conservative boundaries for speculative JSON preparation. */
public final class ModelJsonSafety {
    public static final int MAX_RESOURCE_BYTES = 16 * 1024 * 1024;
    private static final Pattern CUSTOM_LOADER = Pattern.compile("\"loader\"\\s*:");

    private ModelJsonSafety() {
    }

    public static boolean mayUseCustomLoader(String json) {
        // JSON permits escaped property names (for example an escaped 'a' in
        // loader). Leave those resources to Forge instead of guessing which
        // custom code its deserializer will invoke on a preparation worker.
        return json.contains("\\u") || CUSTOM_LOADER.matcher(json).find();
    }

    public static byte[] readBounded(InputStream input) throws IOException {
        return readBounded(input, MAX_RESOURCE_BYTES);
    }

    static byte[] readBounded(InputStream input, int maximum) throws IOException {
        if (maximum < 0 || maximum == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid resource size limit");
        }
        byte[] bytes = input.readNBytes(maximum + 1);
        if (bytes.length > maximum) {
            throw new IOException("Resource exceeds speculative JSON size limit");
        }
        return bytes;
    }
}
