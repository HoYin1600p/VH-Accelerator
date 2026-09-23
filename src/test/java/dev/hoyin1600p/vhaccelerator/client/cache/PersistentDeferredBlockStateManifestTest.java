package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.Builder;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.Manifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.MaterialId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PersistentDeferredBlockStateManifestTest {
    private static final String ATLAS =
            PersistentDeferredBlockStateManifest.BLOCK_ATLAS;
    private static final String FINGERPRINT = "v5|a|b|c|d";
    private static final String FURNACE =
            "minecraft:furnace#facing=north,lit=false";
    private static final String STAIRS =
            "minecraft:oak_stairs#facing=east,half=bottom,"
                    + "shape=straight,waterlogged=false";

    @FunctionalInterface
    private interface OutputBody {
        void write(DataOutputStream output) throws IOException;
    }

    private static MaterialId texture(String texture) {
        return new MaterialId(ATLAS, texture);
    }

    private static Manifest sample() {
        Builder builder = new Builder();
        assertTrue(builder.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"))));
        assertTrue(builder.add(FURNACE, List.of(
                texture("minecraft:block/furnace_top"),
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_side"),
                texture("minecraft:block/furnace_front"))));
        Manifest manifest = builder.build(FINGERPRINT);
        assertNotNull(manifest);
        return manifest;
    }

    private static byte[] encode(Manifest manifest) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PersistentDeferredBlockStateManifest.write(manifest, bytes);
        return bytes.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(raw);
        }
        return bytes.toByteArray();
    }

    private static Manifest decode(byte[] bytes) throws IOException {
        return PersistentDeferredBlockStateManifest.read(
                new ByteArrayInputStream(bytes));
    }

    private static byte[] withDigest(OutputBody body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        DigestOutputStream digesting =
                new DigestOutputStream(bytes, digest);
        DataOutputStream output = new DataOutputStream(digesting);
        body.write(output);
        output.flush();
        digesting.on(false);
        output.write(digest.digest());
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeMaterial(
            DataOutputStream output,
            String texture
    ) throws IOException {
        output.writeUTF(ATLAS);
        output.writeUTF(texture);
    }

    @Test void roundTripPreservesKeysMaterialsAndFingerprint()
            throws IOException {
        Manifest manifest = sample();
        Manifest restored = decode(encode(manifest));
        assertEquals(List.copyOf(manifest.keys()),
                List.copyOf(restored.keys()));
        assertEquals(List.of(FURNACE, STAIRS), List.copyOf(restored.keys()));
        assertEquals(2, restored.size());
        assertEquals(4, restored.totalMaterials());
        assertEquals(List.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_side"),
                texture("minecraft:block/furnace_top")),
                restored.materials(FURNACE));
        assertEquals(List.of(texture("minecraft:block/oak_planks")),
                restored.materials(STAIRS));
        assertTrue(restored.matches(FINGERPRINT));
        assertEquals(FINGERPRINT, restored.fingerprint());
        assertArrayEquals(encode(manifest), encode(restored),
                "encoding is canonical");
        assertEquals(Set.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_side"),
                texture("minecraft:block/furnace_top"),
                texture("minecraft:block/oak_planks")),
                restored.union(restored.keys()));
        assertTrue(restored.union(List.of(
                "minecraft:missing#facing=north")).isEmpty());
    }

    @Test void formatIsDistinctFromTheInventoryManifest()
            throws IOException {
        assertNotEquals(PersistentDeferredTopLevelManifest.MAGIC,
                PersistentDeferredBlockStateManifest.MAGIC);
        assertEquals(0x56484253,
                PersistentDeferredBlockStateManifest.MAGIC);
        byte[] encoded = encode(sample());
        assertEquals(0x56, encoded[0] & 0xff);
        assertEquals(0x48, encoded[1] & 0xff);
        assertEquals(0x42, encoded[2] & 0xff);
        assertEquals(0x53, encoded[3] & 0xff);
        assertEquals(
                PersistentDeferredBlockStateManifest.FORMAT_VERSION,
                encoded[7] & 0xff);
    }

    @Test void builderAcceptsStructuralMinecraftBlockStates() {
        Builder builder = new Builder();
        assertTrue(builder.add(
                "minecraft:composter#level=0",
                List.of(texture("minecraft:block/composter_side"))));
        assertTrue(builder.add(
                "minecraft:wheat#age=7",
                List.of(texture("minecraft:block/wheat_stage7"))));
        assertTrue(builder.add(
                "minecraft:cake#bites=6",
                List.of(texture("minecraft:block/cake_side"))));
        assertTrue(builder.add(
                "minecraft:redstone_wire#east=side,north=none,power=0,"
                        + "south=side,west=up",
                List.of(texture("minecraft:block/redstone_dust_dot"))));
        assertTrue(builder.add(
                "minecraft:observer#powered=false,facing=north",
                List.of(texture("minecraft:block/observer_front"))));
        String maxKey = maxLengthKey();
        assertEquals(
                PersistentDeferredBlockStateManifest.MAX_IDENTIFIER_LENGTH,
                maxKey.length());
        assertTrue(builder.add(maxKey,
                List.of(texture("minecraft:block/stone"))));
        Manifest manifest = builder.build(FINGERPRINT);
        assertNotNull(manifest);
        assertEquals(6, manifest.size());
        assertTrue(manifest.contains(
                "minecraft:observer#powered=false,facing=north"));
    }

    @Test void fingerprintMismatchIsNotUsable() throws IOException {
        Manifest restored = decode(encode(sample()));
        assertFalse(restored.matches("v5|a|b|c|other"));
        assertFalse(restored.matches("v4|a|b|c|d"));
        assertFalse(restored.matches(""));
        assertFalse(restored.matches(null));
        assertTrue(restored.matches(FINGERPRINT));
    }

    @Test void malformedKeysAreRejected() {
        Builder builder = new Builder();
        String[] malformed = {
                null,
                "",
                "minecraft:stone",
                "minecraft:stone#",
                "minecraft:dirt#",
                "minecraft:stone#normal",
                "minecraft:stone#missing",
                "minecraft:stone#facing",
                "minecraft:stone#facing=",
                "minecraft:stone#=north",
                "minecraft:stone#Facing=north",
                "minecraft:stone#facing=North",
                "minecraft:stone#facing=north,",
                "minecraft:stone#,facing=north",
                "minecraft:stone#facing=north,,half=top",
                "minecraft:stone#facing=north,facing=south",
                "minecraft:stone#facing=north,half=top,facing=east",
                "minecraft:stone#facing=01",
                "minecraft:stone#level=00",
                "minecraft:stone#facing=0a",
                "minecraft:stone#1=north",
                "minecraft:Stone#facing=north",
                "Minecraft:stone#facing=north",
                "minecraft:stone/Stone#facing=north",
                "stone#facing=north",
                ":stone#facing=north",
                "minecraft:#facing=north",
                "minecraft:stone#facing=north#half=top",
                "minecraft:stone#facing=north,half=top ",
                "minecraft:stone#facing=north, half=top",
                "minecraft:stone#Inventory",
                "minecraft:stone#facing=north,half=",
                "#facing=north",
                "minecraft:stone##facing=north"
        };
        for (String key : malformed) {
            assertFalse(builder.add(key, List.of(
                    texture("minecraft:block/stone"))), key);
        }
        String tooLong = "minecraft:"
                + "a".repeat(PersistentDeferredBlockStateManifest
                        .MAX_IDENTIFIER_LENGTH)
                + "#n=1";
        assertFalse(builder.add(tooLong, List.of(
                texture("minecraft:block/stone"))));
        assertEquals(malformed.length + 1, builder.rejected());
        assertEquals(0, builder.size());
        assertNull(builder.build(FINGERPRINT));
    }

    @Test void inventoryKeysAreRejected() {
        Builder builder = new Builder();
        assertTrue(builder.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"))));
        String[] inventory = {
                "minecraft:stone#inventory",
                "minecraft:oak_planks#inventory",
                "minecraft:oak_stairs#inventory",
                "minecraft:furnace#inventory"
        };
        for (String key : inventory) {
            assertFalse(builder.add(key, List.of(
                    texture("minecraft:block/stone"))), key);
        }
        assertEquals(inventory.length, builder.rejected());
        assertNull(builder.build(FINGERPRINT));

        Builder fresh = new Builder();
        assertTrue(fresh.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"))));
        Manifest manifest = fresh.build(FINGERPRINT);
        assertNotNull(manifest);
        assertEquals(1, manifest.size());
        assertTrue(manifest.contains(FURNACE));
        assertFalse(manifest.contains("minecraft:furnace#inventory"));
    }

    @Test void otherNamespacesStayEager() {
        Builder builder = new Builder();
        String[] foreign = {
                "the_vault:wooden_chest#facing=north",
                "everycomp:oak_stairs#facing=east,half=bottom,"
                        + "shape=straight,waterlogged=false",
                "buildscape:custom_block#facing=north",
                "sophisticatedstorage:chest#facing=north",
                "sophisticatedbackpacks:backpack#facing=north",
                "sophisticatedcore:upgrade#facing=north",
                "sophisticatedanything:block#facing=north",
                "ctm:glass#facing=north",
                "mod:stone#facing=north"
        };
        for (String key : foreign) {
            assertFalse(builder.add(key, List.of(
                    texture("minecraft:block/stone"))), key);
        }
        assertEquals(foreign.length, builder.rejected());
        assertNull(builder.build(FINGERPRINT));
    }

    @Test void nonBlockAtlasMaterialsAreRejected() {
        Builder builder = new Builder();
        assertFalse(builder.add(FURNACE, List.of()));
        assertFalse(builder.add(FURNACE, null));
        List<MaterialId> nullMember = new ArrayList<>();
        nullMember.add(null);
        assertFalse(builder.add(FURNACE, nullMember));
        assertFalse(builder.add(FURNACE, List.of(
                texture(PersistentDeferredBlockStateManifest
                        .MISSING_TEXTURE))));
        assertFalse(builder.add(FURNACE, List.of(new MaterialId(
                "minecraft:textures/atlas/signs.png",
                "minecraft:entity/signs/oak"))));
        assertFalse(builder.add(FURNACE, List.of(new MaterialId(
                "minecraft:textures/atlas/particles.png",
                "minecraft:block/stone"))));
        assertFalse(builder.add(FURNACE, List.of(new MaterialId(
                "minecraft:textures/atlas/chest.png",
                "minecraft:entity/chest/normal"))));
        assertFalse(builder.add(FURNACE, List.of(new MaterialId(
                ATLAS + ".bak",
                "minecraft:block/stone"))));
        assertFalse(builder.add(FURNACE, List.of(
                new MaterialId(null, "minecraft:block/stone"))));
        assertFalse(builder.add(FURNACE, List.of(
                new MaterialId(ATLAS, null))));
        assertFalse(builder.add(FURNACE, List.of(
                texture("Block/Stone"))));
        assertFalse(builder.add(FURNACE, List.of(
                texture("minecraft:"))));
        assertFalse(builder.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"),
                new MaterialId(
                        "minecraft:textures/atlas/beds.png",
                        "minecraft:entity/bed/red"))));
        String longTexture = "minecraft:" + "a".repeat(
                PersistentDeferredBlockStateManifest
                        .MAX_IDENTIFIER_LENGTH);
        assertFalse(builder.add(FURNACE, List.of(texture(longTexture))));
        assertEquals(0, builder.size());
        assertNull(builder.build(FINGERPRINT),
                "a partial material list is not certified");
    }

    @Test void corruptDigestIsRejected() throws Exception {
        byte[] valid = encode(sample());
        byte[] corrupt = valid.clone();
        corrupt[corrupt.length - 1] ^= 0x01;
        IOException checksum = assertThrows(IOException.class,
                () -> decode(corrupt));
        assertEquals("Manifest checksum mismatch", checksum.getMessage());

        byte[] selfConsistent = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF("minecraft:stone#inventory");
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/stone");
        });
        IOException invalid = assertThrows(IOException.class,
                () -> decode(selfConsistent));
        assertEquals(
                "Invalid manifest entry minecraft:stone#inventory",
                invalid.getMessage());
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

    @Test void noncanonicalOrderIsRejected() throws Exception {
        byte[] unsortedKeys = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(2);
            output.writeInt(2);
            output.writeUTF(STAIRS);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/oak_planks");
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertEquals("Manifest keys are not canonical",
                assertThrows(IOException.class,
                        () -> decode(unsortedKeys)).getMessage());

        byte[] duplicateKeys = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(2);
            output.writeInt(2);
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_front");
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_top");
        });
        assertEquals("Manifest keys are not canonical",
                assertThrows(IOException.class,
                        () -> decode(duplicateKeys)).getMessage());

        byte[] unsortedMaterials = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(2);
            output.writeUTF(FURNACE);
            output.writeInt(2);
            writeMaterial(output, "minecraft:block/furnace_top");
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertEquals("Manifest materials are not canonical",
                assertThrows(IOException.class,
                        () -> decode(unsortedMaterials)).getMessage());

        byte[] duplicateMaterials = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(2);
            output.writeUTF(FURNACE);
            output.writeInt(2);
            writeMaterial(output, "minecraft:block/furnace_front");
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertEquals("Manifest materials are not canonical",
                assertThrows(IOException.class,
                        () -> decode(duplicateMaterials)).getMessage());
    }

    @Test void entriesAreSortedAndDuplicatesDoNotOverwrite() {
        Builder builder = new Builder();
        assertTrue(builder.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"))));
        assertTrue(builder.add(FURNACE, List.of(
                texture("minecraft:block/furnace_top"),
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_front"))));
        Manifest manifest = builder.build(FINGERPRINT);
        assertNotNull(manifest);
        assertEquals(List.of(FURNACE, STAIRS), List.copyOf(manifest.keys()));
        assertEquals(List.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_top")),
                manifest.materials(FURNACE));
        assertEquals(3, manifest.totalMaterials());

        Builder duplicate = new Builder();
        assertTrue(duplicate.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_top"))));
        assertEquals(1, duplicate.size());
        assertFalse(duplicate.add(FURNACE, List.of(
                texture("minecraft:block/furnace_side"))));
        assertEquals(1, duplicate.size());
        assertEquals(1, duplicate.rejected());
        assertNull(duplicate.build(FINGERPRINT));
    }

    @Test void overLimitInputsAreRejected() throws Exception {
        Builder materials = new Builder();
        List<MaterialId> full = new ArrayList<>();
        for (int index = 0;
                index < PersistentDeferredBlockStateManifest
                        .MAX_MATERIALS_PER_ENTRY;
                index++) {
            full.add(texture("minecraft:block/m" + index));
        }
        assertTrue(materials.add(FURNACE, full));
        Manifest fullManifest = materials.build(FINGERPRINT);
        assertNotNull(fullManifest);
        assertEquals(
                PersistentDeferredBlockStateManifest
                        .MAX_MATERIALS_PER_ENTRY,
                fullManifest.totalMaterials());
        assertArrayEquals(encode(fullManifest),
                encode(decode(encode(fullManifest))));

        List<MaterialId> tooMany = new ArrayList<>(full);
        tooMany.add(texture("minecraft:block/overflow"));
        Builder overflow = new Builder();
        assertFalse(overflow.add(FURNACE, tooMany));
        assertNull(overflow.build(FINGERPRINT));

        Builder entries = new Builder();
        MaterialId stone = texture("minecraft:block/stone");
        int limit = PersistentDeferredBlockStateManifest.MAX_ENTRIES;
        int accepted = 0;
        for (int index = 0; index < limit; index++) {
            if (entries.add(
                    "minecraft:b" + index + "#n=1",
                    List.of(stone))) {
                accepted++;
            }
        }
        assertEquals(limit, accepted);
        assertFalse(entries.add(
                "minecraft:overflow#n=1",
                List.of(stone)));
        assertEquals(limit, entries.size());
        assertNull(entries.build(FINGERPRINT));

        Builder fingerprint = new Builder();
        assertTrue(fingerprint.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"))));
        assertNull(fingerprint.build(null));
        assertNull(fingerprint.build(""));
        assertNull(fingerprint.build("x".repeat(
                PersistentDeferredBlockStateManifest
                        .MAX_FINGERPRINT_LENGTH + 1)));
        String maxFingerprint = "x".repeat(
                PersistentDeferredBlockStateManifest
                        .MAX_FINGERPRINT_LENGTH);
        Manifest longFingerprint = fingerprint.build(maxFingerprint);
        assertNotNull(longFingerprint);
        assertEquals(maxFingerprint,
                decode(encode(longFingerprint)).fingerprint());

        assertTrue(fingerprint.add(maxLengthKey(), List.of(stone))
                || fingerprint.size() == 1);
        Builder longKey = new Builder();
        assertTrue(longKey.add(maxLengthKey(), List.of(stone)));
        Manifest maxKeyManifest = longKey.build(FINGERPRINT);
        assertNotNull(maxKeyManifest);
        assertEquals(List.of(maxLengthKey()),
                List.copyOf(decode(encode(maxKeyManifest)).keys()));
        assertFalse(longKey.add(maxLengthKey() + "a", List.of(stone)));
        assertNull(longKey.build(FINGERPRINT));

        byte[] emptyManifest = header(0, 0, FINGERPRINT);
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(emptyManifest)).getMessage());

        byte[] tooManyEntries = header(
                PersistentDeferredBlockStateManifest.MAX_ENTRIES + 1,
                1,
                FINGERPRINT);
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(tooManyEntries)).getMessage());

        byte[] tooManyDeclared = header(
                1,
                PersistentDeferredBlockStateManifest.MAX_TOTAL_MATERIALS
                        + 1,
                FINGERPRINT);
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(tooManyDeclared)).getMessage());

        byte[] hugeEntry = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(
                    PersistentDeferredBlockStateManifest
                            .MAX_MATERIALS_PER_ENTRY + 1);
        });
        assertEquals("Invalid manifest material count",
                assertThrows(IOException.class,
                        () -> decode(hugeEntry)).getMessage());

        byte[] longStoredFingerprint = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF("x".repeat(
                    PersistentDeferredBlockStateManifest
                            .MAX_FINGERPRINT_LENGTH + 1));
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertEquals("Invalid manifest fingerprint",
                assertThrows(IOException.class,
                        () -> decode(longStoredFingerprint)).getMessage());
    }

    @Test void otherFormatVersionReadsAsAbsent() throws Exception {
        byte[] valid = encode(sample());
        assertEquals(
                PersistentDeferredBlockStateManifest.FORMAT_VERSION,
                valid[7] & 0xff);
        valid[7] = (byte) (
                PersistentDeferredBlockStateManifest.FORMAT_VERSION + 1);
        assertNull(decode(valid));

        byte[] future = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION
                            + 5);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertNull(decode(future));
    }

    @Test void wrongMagicIsRejected() throws Exception {
        byte[] wrongMagic = withDigest(output -> {
            output.writeInt(
                    PersistentDeferredBlockStateManifest.MAGIC ^ 0x7f);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(1);
            writeMaterial(output, "minecraft:block/furnace_front");
        });
        assertEquals("Not a deferred block-state manifest",
                assertThrows(IOException.class,
                        () -> decode(wrongMagic)).getMessage());
    }

    @Test void truncationAndTrailingDataAreRejected() throws IOException {
        byte[] valid = encode(sample());
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> decode(truncated),
                    "length " + length);
        }
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 0x5a;
        assertEquals("Manifest contains trailing data",
                assertThrows(IOException.class,
                        () -> decode(trailing)).getMessage());
    }

    @Test void rejectedAddClosesTheBuilderUntilAFreshOneIsUsed() {
        Builder duplicate = new Builder();
        assertTrue(duplicate.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_top"))));
        assertFalse(duplicate.add(FURNACE, List.of(
                texture("minecraft:block/furnace_side"))));
        assertNull(duplicate.build(FINGERPRINT));
        assertTrue(duplicate.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"))));
        assertNull(duplicate.build(FINGERPRINT));

        Builder incomplete = new Builder();
        assertTrue(incomplete.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"))));
        assertFalse(incomplete.add(FURNACE, List.of()));
        assertFalse(incomplete.add(FURNACE, null));
        assertNull(incomplete.build(FINGERPRINT));

        Builder invalidMaterial = new Builder();
        assertFalse(invalidMaterial.add(FURNACE, List.of(new MaterialId(
                "minecraft:textures/atlas/chest.png",
                "minecraft:entity/chest/normal"))));
        assertNull(invalidMaterial.build(FINGERPRINT));
        assertTrue(invalidMaterial.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"))));
        assertNull(invalidMaterial.build(FINGERPRINT));

        Builder fresh = new Builder();
        assertTrue(fresh.add(STAIRS, List.of(
                texture("minecraft:block/oak_planks"))));
        assertTrue(fresh.add(FURNACE, List.of(
                texture("minecraft:block/furnace_top"),
                texture("minecraft:block/furnace_front"))));
        Manifest manifest = fresh.build(FINGERPRINT);
        assertNotNull(manifest);
        assertEquals(List.of(FURNACE, STAIRS), List.copyOf(manifest.keys()));
        assertEquals(List.of(
                texture("minecraft:block/furnace_front"),
                texture("minecraft:block/furnace_top")),
                manifest.materials(FURNACE));
        assertEquals(3, manifest.totalMaterials());
    }

    @Test void gzipCacheRejectsOversizeInflationAndBadCounts()
            throws Exception {
        assertEquals(64L * 1024L * 1024L,
                PersistentDeferredBlockStateManifest.MAX_UNCOMPRESSED_BYTES);
        assertEquals(
                PersistentDeferredBlockStateManifest.MAX_FINGERPRINT_LENGTH
                        * 3,
                PersistentDeferredBlockStateManifest
                        .MAX_FINGERPRINT_UTF_BYTES);

        byte[] raw = encode(sample());
        byte[] compressed = gzip(raw);
        assertTrue(raw.length > 32);
        IOException overflow = assertThrows(IOException.class, () ->
                PersistentDeferredBlockStateManifest.readCompressed(
                        new ByteArrayInputStream(compressed),
                        raw.length - 1L));
        assertEquals("Manifest exceeds uncompressed limit",
                overflow.getMessage());
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                compressed, raw.length - 1L));
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                compressed, 0L));
        Manifest restored = PersistentDeferredBlockStateManifest
                .readGzipCache(
                        compressed,
                        PersistentDeferredBlockStateManifest
                                .MAX_UNCOMPRESSED_BYTES);
        assertNotNull(restored);
        assertArrayEquals(raw, encode(restored));

        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                new byte[] {1, 2, 3, 4},
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                null,
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));

        byte[] negativeCount = header(-1, 1, FINGERPRINT);
        byte[] negativeMaterials = header(1, Integer.MIN_VALUE, FINGERPRINT);
        byte[] hugeCount = header(Integer.MAX_VALUE, 1, FINGERPRINT);
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(negativeCount)).getMessage());
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(negativeMaterials)).getMessage());
        assertEquals("Invalid manifest counts",
                assertThrows(IOException.class,
                        () -> decode(hugeCount)).getMessage());
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(negativeCount),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(negativeMaterials),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(hugeCount),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));

        byte[] hugeMaterialCount = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(Integer.MAX_VALUE);
        });
        byte[] negativeMaterialCount = withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(FINGERPRINT);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(FURNACE);
            output.writeInt(-1);
        });
        assertEquals("Invalid manifest material count",
                assertThrows(IOException.class,
                        () -> decode(hugeMaterialCount)).getMessage());
        assertEquals("Invalid manifest material count",
                assertThrows(IOException.class,
                        () -> decode(negativeMaterialCount)).getMessage());
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(hugeMaterialCount),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));

        byte[] overlongKey = identifierLengthPrefix(
                PersistentDeferredBlockStateManifest.MAX_IDENTIFIER_LENGTH
                        + 1,
                false);
        byte[] overlongTexture = identifierLengthPrefix(65_535, true);
        assertEquals("Manifest identifier is too long",
                assertThrows(IOException.class,
                        () -> decode(overlongKey)).getMessage());
        assertEquals("Manifest identifier is too long",
                assertThrows(IOException.class,
                        () -> decode(overlongTexture)).getMessage());
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(overlongKey),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));

        byte[] overlongFingerprint = fingerprintLengthPrefix(
                PersistentDeferredBlockStateManifest
                        .MAX_FINGERPRINT_UTF_BYTES + 1);
        assertEquals("Invalid manifest fingerprint",
                assertThrows(IOException.class,
                        () -> decode(overlongFingerprint)).getMessage());
        assertNull(PersistentDeferredBlockStateManifest.readGzipCache(
                gzip(overlongFingerprint),
                PersistentDeferredBlockStateManifest
                        .MAX_UNCOMPRESSED_BYTES));

        String wide = "\u0800".repeat(
                PersistentDeferredBlockStateManifest
                        .MAX_FINGERPRINT_LENGTH);
        Builder wideBuilder = new Builder();
        assertTrue(wideBuilder.add(FURNACE, List.of(
                texture("minecraft:block/furnace_front"))));
        Manifest wideManifest = wideBuilder.build(wide);
        assertNotNull(wideManifest);
        assertEquals(wide, decode(encode(wideManifest)).fingerprint());
        Manifest wideRestored = PersistentDeferredBlockStateManifest
                .readGzipCache(
                        gzip(encode(wideManifest)),
                        PersistentDeferredBlockStateManifest
                                .MAX_UNCOMPRESSED_BYTES);
        assertNotNull(wideRestored);
        assertEquals(wide, wideRestored.fingerprint());
    }

    private static byte[] header(
            int count,
            int declaredMaterials,
            String fingerprint
    ) throws Exception {
        return withDigest(output -> {
            output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
            output.writeInt(
                    PersistentDeferredBlockStateManifest.FORMAT_VERSION);
            output.writeUTF(fingerprint);
            output.writeInt(count);
            output.writeInt(declaredMaterials);
        });
    }

    private static byte[] identifierLengthPrefix(
            int utfByteLength,
            boolean materialField
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
        output.writeInt(
                PersistentDeferredBlockStateManifest.FORMAT_VERSION);
        output.writeUTF(FINGERPRINT);
        output.writeInt(1);
        output.writeInt(1);
        if (materialField) {
            output.writeUTF(FURNACE);
            output.writeInt(1);
        }
        output.writeShort(utfByteLength);
        output.flush();
        return bytes.toByteArray();
    }

    private static byte[] fingerprintLengthPrefix(int utfByteLength)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(PersistentDeferredBlockStateManifest.MAGIC);
        output.writeInt(
                PersistentDeferredBlockStateManifest.FORMAT_VERSION);
        output.writeShort(utfByteLength);
        output.flush();
        return bytes.toByteArray();
    }

    private static String maxLengthKey() {
        String suffix = "#n=1";
        int pathLength =
                PersistentDeferredBlockStateManifest.MAX_IDENTIFIER_LENGTH
                        - "minecraft:".length()
                        - suffix.length();
        return "minecraft:" + "a".repeat(pathLength) + suffix;
    }
}
