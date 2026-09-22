package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest.Builder;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest.Manifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredTopLevelManifest.MaterialId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersistentDeferredTopLevelManifestTest {
    private static final String ATLAS = PersistentDeferredTopLevelManifest.BLOCK_ATLAS;
    private static final String FINGERPRINT = "v5|a|b|c|d";

    private static MaterialId texture(String texture) {
        return new MaterialId(ATLAS, texture);
    }

    private static Manifest sample() {
        Builder builder = new Builder();
        assertTrue(builder.add("mod:stone_slab#inventory",
                List.of(texture("mod:block/stone"), texture("mod:block/stone_side"))));
        assertTrue(builder.add("minecraft:oak_stairs#inventory",
                List.of(texture("minecraft:block/oak_planks"))));
        Manifest manifest = builder.build(FINGERPRINT);
        assertNotNull(manifest);
        return manifest;
    }

    private static byte[] encode(Manifest manifest) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PersistentDeferredTopLevelManifest.write(manifest, bytes);
        return bytes.toByteArray();
    }

    private static Manifest decode(byte[] bytes) throws IOException {
        return PersistentDeferredTopLevelManifest.read(new ByteArrayInputStream(bytes));
    }

    @Test void roundTripPreservesKeysMaterialsAndFingerprint() throws IOException {
        Manifest manifest = sample();
        Manifest restored = decode(encode(manifest));
        assertEquals(manifest.keys(), restored.keys());
        assertEquals(2, restored.size());
        assertEquals(3, restored.totalMaterials());
        assertEquals(List.of(texture("mod:block/stone"), texture("mod:block/stone_side")),
                restored.materials("mod:stone_slab#inventory"));
        assertTrue(restored.matches(FINGERPRINT));
        assertArrayEquals(encode(manifest), encode(restored), "encoding is canonical");
    }

    @Test void fingerprintMismatchIsNotUsable() throws IOException {
        Manifest restored = decode(encode(sample()));
        assertFalse(restored.matches("v5|a|b|c|other"));
        assertFalse(restored.matches(null));
    }

    @Test void everyFlippedByteIsRejected() throws IOException {
        byte[] valid = encode(sample());
        for (int index = 8; index < valid.length; index++) {
            byte[] corrupt = valid.clone();
            corrupt[index] ^= 0x01;
            int position = index;
            try {
                Manifest result = decode(corrupt);
                fail("byte " + position + " accepted as " + result);
            } catch (IOException expected) {
                // Fails closed.
            }
        }
    }

    @Test void truncationAndTrailingDataAreRejected() throws IOException {
        byte[] valid = encode(sample());
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> decode(truncated), "length " + length);
        }
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> decode(trailing));
    }

    @Test void otherFormatVersionReadsAsAbsent() throws IOException {
        byte[] valid = encode(sample());
        valid[7] = (byte) (PersistentDeferredTopLevelManifest.FORMAT_VERSION + 1);
        assertNull(decode(valid));
    }

    @Test void wrongMagicIsRejected() throws IOException {
        byte[] valid = encode(sample());
        valid[0] ^= 0x7f;
        assertThrows(IOException.class, () -> decode(valid));
    }

    @Test void validDigestOverInvalidContentIsStillRejected() throws Exception {
        // A self-consistent file with a non-inventory key and an unsorted list.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        var digesting = new java.security.DigestOutputStream(bytes, digest);
        DataOutputStream output = new DataOutputStream(digesting);
        output.writeInt(PersistentDeferredTopLevelManifest.MAGIC);
        output.writeInt(PersistentDeferredTopLevelManifest.FORMAT_VERSION);
        output.writeUTF(FINGERPRINT);
        output.writeInt(1);
        output.writeInt(1);
        output.writeUTF("mod:stone#facing=north");
        output.writeInt(1);
        output.writeUTF(ATLAS);
        output.writeUTF("mod:block/stone");
        output.flush();
        digesting.on(false);
        output.write(digest.digest());
        output.flush();
        assertThrows(IOException.class, () -> decode(bytes.toByteArray()));
    }

    @Test void builderRejectsMalformedOrIncompleteEntries() {
        Builder builder = new Builder();
        assertFalse(builder.add("mod:block#facing=north", List.of(texture("mod:block/a"))),
                "block-state variants are never certified");
        assertFalse(builder.add("mod:item", List.of(texture("mod:item/a"))));
        assertFalse(builder.add("Mod:Upper#inventory", List.of(texture("mod:item/a"))));
        assertFalse(builder.add("mod:empty#inventory", List.of()));
        assertFalse(builder.add("mod:none#inventory", null));
        assertFalse(builder.add("mod:missing#inventory",
                List.of(texture(PersistentDeferredTopLevelManifest.MISSING_TEXTURE))),
                "graphs with unresolved textures stay eager");
        assertFalse(builder.add("mod:other_atlas#inventory",
                List.of(new MaterialId("minecraft:textures/atlas/signs.png", "mod:entity/sign"))),
                "atlas topology outside the block atlas is not certified");
        List<MaterialId> nullMember = new ArrayList<>();
        nullMember.add(null);
        assertFalse(builder.add("mod:null#inventory", nullMember));
        List<MaterialId> tooMany = new ArrayList<>();
        for (int i = 0; i <= PersistentDeferredTopLevelManifest.MAX_MATERIALS_PER_ENTRY; i++) {
            tooMany.add(texture("mod:block/t" + i));
        }
        assertFalse(builder.add("mod:huge#inventory", tooMany));
        assertEquals(9, builder.rejected());
        assertNull(builder.build(FINGERPRINT), "nothing certified, nothing written");
    }

    @Test void builderRejectsDuplicatesAndUnusableFingerprints() {
        Builder builder = new Builder();
        assertTrue(builder.add("mod:a#inventory", List.of(texture("mod:block/a"))));
        assertFalse(builder.add("mod:a#inventory", List.of(texture("mod:block/b"))));
        assertNull(builder.build(null));
        assertNull(builder.build(""));
        assertNotNull(builder.build(FINGERPRINT));
    }

    @Test void entriesAreSortedAndDeduplicated() {
        Builder builder = new Builder();
        builder.add("mod:a#inventory", List.of(texture("mod:block/z"), texture("mod:block/a"),
                texture("mod:block/z")));
        Manifest manifest = builder.build(FINGERPRINT);
        assertEquals(List.of(texture("mod:block/a"), texture("mod:block/z")),
                manifest.materials("mod:a#inventory"));
        assertEquals(2, manifest.totalMaterials());
    }

    @Test void unionCoversEverySkippedModelAndIgnoresUnknownKeys() {
        Manifest manifest = sample();
        Set<MaterialId> union = manifest.union(List.of(
                "mod:stone_slab#inventory", "minecraft:oak_stairs#inventory", "mod:unknown#inventory"));
        assertEquals(Set.of(texture("mod:block/stone"), texture("mod:block/stone_side"),
                texture("minecraft:block/oak_planks")), union);
        assertEquals(Set.of(texture("minecraft:block/oak_planks")),
                manifest.union(List.of("minecraft:oak_stairs#inventory")));
        assertTrue(manifest.union(List.of()).isEmpty());
    }

    @Test void identifierValidationMatchesResourceLocationRules() {
        assertTrue(PersistentDeferredTopLevelManifest.validLocation("minecraft:block/oak_planks"));
        assertTrue(PersistentDeferredTopLevelManifest.validLocation("my-mod.x:a/b_c-d.e"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation("noNamespace"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation(":path"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation("mod:"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation("mod/x:path"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation("mod:a:b"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation("mod:Path"));
        assertFalse(PersistentDeferredTopLevelManifest.validLocation(null));
        assertTrue(PersistentDeferredTopLevelManifest.validModelKey("mod:item#inventory"));
        assertFalse(PersistentDeferredTopLevelManifest.validModelKey("mod:item#"));
        assertFalse(PersistentDeferredTopLevelManifest.validModelKey("#inventory"));
    }
}
