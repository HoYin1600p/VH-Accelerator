package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CanonicalRecipePayloadFingerprintTest {
    @Test
    void ignoresPacketRecipeIterationOrder() {
        Entry first = new Entry("example:first", "example:shaped", "one");
        Entry second = new Entry("example:second", "example:shaped", "two");
        assertEquals(
                digest(List.of(first, second)),
                digest(List.of(second, first))
        );
    }

    @Test
    void detectsRecipePayloadChanges() {
        Entry original = new Entry(
                "example:recipe",
                "example:shaped",
                "original"
        );
        Entry changed = new Entry(
                "example:recipe",
                "example:shaped",
                "changed"
        );
        assertNotEquals(
                digest(List.of(original)),
                digest(List.of(changed))
        );
    }

    @Test
    void detectsSerializerChanges() {
        Entry shaped = new Entry(
                "example:recipe",
                "example:shaped",
                "same"
        );
        Entry custom = new Entry(
                "example:recipe",
                "example:custom",
                "same"
        );
        assertNotEquals(
                digest(List.of(shaped)),
                digest(List.of(custom))
        );
    }

    private static String digest(List<Entry> entries) {
        return CanonicalRecipePayloadFingerprint.digest(
                entries,
                Entry::id,
                Entry::serializer,
                entry -> entry.payload().getBytes(StandardCharsets.UTF_8)
        );
    }

    private record Entry(
            String id,
            String serializer,
            String payload
    ) {
    }
}
