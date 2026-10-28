package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildingGadgetsInventoryTest {
    @TempDir Path directory;

    static CompoundTag fixture(boolean empty) {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 2975);
        root.putByte("unknownRootByte", (byte) -1);
        CompoundTag saved = new CompoundTag();
        root.put("data", saved);
        ListTag entries = new ListTag();
        saved.put("data_map", entries);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("key", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        CompoundTag value = new CompoundTag();
        entry.put("val", value);
        value.putLong("time", Long.MAX_VALUE);
        value.putString("unknownMetadata", "preserve me");
        CompoundTag data = new CompoundTag();
        value.put("data", data);
        CompoundTag header = new CompoundTag();
        data.put("header", header);
        header.putString("name", "Fixture");
        header.putString("author", "Fixture author");
        CompoundTag bounds = new CompoundTag();
        header.put("bounds", bounds);
        bounds.putInt("minX", -65536); bounds.putInt("maxX", 65535);
        bounds.putInt("minY", -256); bounds.putInt("maxY", 255);
        bounds.putInt("minZ", -1); bounds.putInt("maxZ", 65535);
        ListTag serializer = new ListTag();
        ListTag palette = new ListTag();
        ListTag positions = new ListTag();
        data.put("serializer", serializer); data.put("data", palette); data.put("pos", positions);
        if (!empty) {
            serializer.add(StringTag.valueOf("unknown_mod:tile_serializer"));
            CompoundTag block = new CompoundTag();
            CompoundTag state = new CompoundTag();
            state.putString("Name", "unknown_mod:unregistered_block");
            CompoundTag properties = new CompoundTag(); properties.putString("facing", "east");
            state.put("Properties", properties);
            block.put("state", state); block.putInt("serializer", 0);
            CompoundTag tile = new CompoundTag();
            tile.put("unknownArray", new LongArrayTag(new long[]{Long.MAX_VALUE, -1}));
            ListTag unknown = new ListTag(); unknown.add(ShortTag.valueOf((short) 7)); unknown.add(ShortTag.valueOf((short) -1));
            tile.put("unknownList", unknown); block.put("data", tile);
            palette.add(block);
            positions.add(LongTag.valueOf(0));
            positions.add(LongTag.valueOf(0xffffffffffL));
        }
        entries.add(entry);
        return root;
    }

    private static CompoundTag templateData(CompoundTag root) {
        return ((CompoundTag) root.getCompound("data").getList("data_map", Tag.TAG_COMPOUND).get(0)).getCompound("val").getCompound("data");
    }

    private Path snapshot(CompoundTag root) throws IOException {
        Path source = directory.resolve("source-copy/data/" + BuildingGadgetsInventory.SAVED_FILE);
        Files.createDirectories(source.getParent());
        try (var output = Files.newOutputStream(source)) { NbtIo.writeCompressed(root, output); }
        return source;
    }

    private JsonObject semantic(CompoundTag root) throws Exception {
        var codecs = new OriginalBuildingGadgetsCodecs(OriginalBuildingGadgetsCodecsTest.originalJar());
        StringWriter text = new StringWriter();
        try (JsonWriter output = new JsonWriter(TypedNbtWriter.asciiJson(text))) {
            output.setSerializeNulls(true);
            BuildingGadgetsInventory.writeSemantic(output, root, codecs);
        }
        return JsonParser.parseString(text.toString()).getAsJsonObject();
    }

    @Test void inventoryKeepsAllUnknownRawFieldsAndOriginalCoordinatePalettePairs() throws Exception {
        CompoundTag root = fixture(false);
        JsonObject inventory = semantic(root);
        assertEquals(dev.hoyin1600p.vhaccelerator.diagnostics.TypedNbtJson.encode(root), inventory.get("rawSavedData"));
        JsonObject template = inventory.getAsJsonArray("templates").get(0).getAsJsonObject();
        assertEquals("Fixture", template.get("name").getAsString());
        assertEquals("00000000-0000-0000-0000-000000000001", template.get("id").getAsString());
        assertEquals(2, template.getAsJsonArray("blocks").size());
        assertEquals("[65535,255,65535]", template.getAsJsonArray("blocks").get(1).getAsJsonObject().get("coordinate").toString());
        assertEquals(0, template.getAsJsonArray("blocks").get(1).getAsJsonObject().get("paletteIndex").getAsInt());
        assertTrue(template.get("blockPalette").toString().contains("unknown_mod:unregistered_block"));
        assertTrue(template.get("blockPalette").toString().contains("unknownArray"));
        assertTrue(template.get("header").toString().contains("-65536"));
        assertEquals("9223372036854775807", template.getAsJsonObject("updatedTime").get("value").getAsString());
    }

    @Test void emptyTemplatesAndEmptySavedDataAreCompleteWithoutFabricatingBlocks() throws Exception {
        JsonObject one = semantic(fixture(true));
        assertEquals(1, one.getAsJsonArray("templates").size());
        assertEquals(0, one.getAsJsonArray("templates").get(0).getAsJsonObject().getAsJsonArray("blocks").size());
        CompoundTag root = fixture(true);
        root.getCompound("data").put("data_map", new ListTag());
        assertEquals(0, semantic(root).getAsJsonArray("templates").size());
    }

    @Test void malformedPaletteReferencesDuplicateIdsAndCoordinatesFailInsteadOfReturningEmpty() throws Exception {
        OriginalBuildingGadgetsCodecsTest.originalJar();
        CompoundTag missing = fixture(false);
        templateData(missing).put("data", new ListTag());
        assertThrows(IOException.class, () -> semantic(missing));
        CompoundTag badSerializer = fixture(false);
        templateData(badSerializer).put("serializer", new ListTag());
        assertThrows(IOException.class, () -> semantic(badSerializer));
        CompoundTag duplicates = fixture(false);
        ListTag positions = templateData(duplicates).getList("pos", Tag.TAG_LONG);
        positions.add(positions.get(0).copy());
        assertThrows(IOException.class, () -> semantic(duplicates));
        CompoundTag duplicateIds = fixture(true);
        ListTag entries = duplicateIds.getCompound("data").getList("data_map", Tag.TAG_COMPOUND);
        entries.add(entries.get(0).copy());
        assertThrows(IOException.class, () -> semantic(duplicateIds));
        CompoundTag missingRoot = new CompoundTag();
        assertThrows(IOException.class, () -> semantic(missingRoot));
    }

    @Test void toolPublishesExternalOutputOnlyAfterUnchangedSourceAndKeepsCanonicalSemanticDigest() throws Exception {
        Path jar = OriginalBuildingGadgetsCodecsTest.originalJar();
        Path source = snapshot(fixture(false));
        byte[] original = Files.readAllBytes(source);
        var modified = Files.getLastModifiedTime(source);
        Path output = directory.resolve("inventory.json");
        BuildingGadgetsInventory.writeInventory(source, output, jar);
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(modified, Files.getLastModifiedTime(source));
        String text = Files.readString(output);
        JsonObject inventory = JsonParser.parseString(text).getAsJsonObject();
        assertEquals("complete", inventory.get("status").getAsString());
        JsonObject provenance = inventory.getAsJsonObject("provenance");
        assertTrue(provenance.get("originalCoordinateCodecVerified").getAsBoolean());
        assertTrue(provenance.get("originalPaletteCodecVerified").getAsBoolean());
        assertFalse(provenance.get("fullOriginalTemplateDecode").getAsBoolean());
        assertEquals(OriginalBuildingGadgetsCodecs.JAR_SHA256, provenance.get("jarSha256").getAsString());
        assertEquals(OriginalBuildingGadgetsCodecs.sha256(source), provenance.get("sourceSha256").getAsString());
        String semantic = inventory.get("semantic").toString();
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(semantic.getBytes(StandardCharsets.UTF_8))),
                provenance.get("semanticSha256").getAsString());
        assertThrows(IOException.class, () -> BuildingGadgetsInventory.writeInventory(source, output, jar));
        try (var files = Files.list(directory)) { assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp"))); }
    }

    @Test void outputWithinInputWorldAndInputAliasesAreRejectedBeforeAnyWrite() throws Exception {
        Path source = snapshot(fixture(true));
        Path forbidden = source.getParent().resolve("inventory.json");
        assertThrows(IOException.class, () -> BuildingGadgetsInventory.writeInventory(source, forbidden, Path.of("unused.jar")));
        assertFalse(Files.exists(forbidden));
        Path inputAlias = source.getParent().resolve("..data");
        assertThrows(IOException.class, () -> BuildingGadgetsInventory.writeInventory(inputAlias, directory.resolve("output.json"), Path.of("unused.jar")));
    }

    @Test void truncatedOrTrailingNbtNeverPublishesSuccessfulInventory() throws Exception {
        Path jar = OriginalBuildingGadgetsCodecsTest.originalJar();
        Path source = snapshot(fixture(true));
        byte[] original = Files.readAllBytes(source);
        Files.write(source, java.util.Arrays.copyOf(original, original.length / 2));
        Path output = directory.resolve("truncated.json");
        assertThrows(IOException.class, () -> BuildingGadgetsInventory.writeInventory(source, output, jar));
        assertFalse(Files.exists(output));
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(source)); var data = new DataOutputStream(gzip)) {
            data.writeByte(Tag.TAG_COMPOUND); data.writeUTF(""); data.writeByte(Tag.TAG_END); data.writeByte(99);
        }
        assertThrows(IOException.class, () -> BuildingGadgetsInventory.writeInventory(source, output, jar));
        assertFalse(Files.exists(output));
    }

    @Test void independentReaderPreservesRootNameFloatingBitsAndDeclaredEmptyListType() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeByte(Tag.TAG_COMPOUND); data.writeUTF("root\ud800\ud83d\ude80");
            data.writeByte(Tag.TAG_FLOAT); data.writeUTF("negativeFloat"); data.writeInt(0x80000000);
            data.writeByte(Tag.TAG_DOUBLE); data.writeUTF("negativeDouble"); data.writeLong(0x8000000000000000L);
            data.writeByte(Tag.TAG_FLOAT); data.writeUTF("nan"); data.writeInt(0x7fc00001);
            data.writeByte(Tag.TAG_DOUBLE); data.writeUTF("infinity"); data.writeLong(0x7ff0000000000000L);
            data.writeByte(Tag.TAG_LIST); data.writeUTF("emptyLongs"); data.writeByte(Tag.TAG_LONG); data.writeInt(0);
            data.writeByte(Tag.TAG_END);
        }
        var root = TypedNbtWriter.readRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals("root\ud800\ud83d\ude80", root.name());
        assertEquals("80000000", TypedNbtWriterTest.encode(root.data().get("negativeFloat")).getAsJsonObject().get("bits").getAsString());
        assertEquals("8000000000000000", TypedNbtWriterTest.encode(root.data().get("negativeDouble")).getAsJsonObject().get("bits").getAsString());
        assertEquals("7fc00001", TypedNbtWriterTest.encode(root.data().get("nan")).getAsJsonObject().get("bits").getAsString());
        assertEquals(Tag.TAG_LONG, ((ListTag) root.data().get("emptyLongs")).getElementType());
    }

    @Test void asciiJsonPreservesLoneSurrogatesAstralNamesAndNonAsciiCompoundKeys() throws Exception {
        CompoundTag compound = new CompoundTag();
        String key = "\ud800 Caf\u00e9 \ud83d\ude80";
        compound.putString(key, "\udfff\ud83d\ude80\u65e5");
        StringWriter buffer = new StringWriter();
        try (JsonWriter output = new JsonWriter(TypedNbtWriter.asciiJson(buffer))) { TypedNbtWriter.write(output, compound); }
        assertTrue(buffer.toString().chars().allMatch(value -> value < 128));
        assertEquals(compound.getString(key), JsonParser.parseString(buffer.toString()).getAsJsonObject()
                .getAsJsonObject("value").getAsJsonObject(key).get("value").getAsString());
    }

    @Test void invalidListTypesDuplicateCompoundNamesAndAbsurdLengthsFail() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeByte(Tag.TAG_COMPOUND); data.writeUTF("");
                if (kind == 0) {
                    data.writeByte(Tag.TAG_LIST); data.writeUTF("bad"); data.writeByte(Tag.TAG_END); data.writeInt(1);
                } else if (kind == 1) {
                    data.writeByte(Tag.TAG_INT); data.writeUTF("dup"); data.writeInt(1);
                    data.writeByte(Tag.TAG_INT); data.writeUTF("dup"); data.writeInt(2);
                } else {
                    data.writeByte(Tag.TAG_LONG_ARRAY); data.writeUTF("huge"); data.writeInt(Integer.MAX_VALUE);
                }
                data.writeByte(Tag.TAG_END);
            }
            byte[] malformed = bytes.toByteArray();
            assertThrows(IOException.class, () -> TypedNbtWriter.readRoot(new DataInputStream(new ByteArrayInputStream(malformed))));
        }
    }
}
