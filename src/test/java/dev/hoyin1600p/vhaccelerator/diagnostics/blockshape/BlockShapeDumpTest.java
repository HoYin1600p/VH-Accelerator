package dev.hoyin1600p.vhaccelerator.diagnostics.blockshape;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class BlockShapeDumpTest {
    @TempDir Path directory;
    private static final Instant TIME = Instant.parse("2026-10-24T12:34:56.123Z");

    @Test void fakeRegistryPreservesExactFieldsEncounterOrderDefaultsAndTotals() throws Exception {
        var first = new BlockShapeDump.PropertyShape(0, "z_axis", "fake.DirectionProperty", "fake.Direction", List.of(
                new BlockShapeDump.Value(0, "south", "South DISPLAY", "fake.Direction"),
                new BlockShapeDump.Value(1, "north", "North DISPLAY", "fake.Direction")));
        var second = new BlockShapeDump.PropertyShape(1, "active", "fake.BooleanProperty", "java.lang.Boolean", List.of(
                new BlockShapeDump.Value(0, "false", "false", "java.lang.Boolean"),
                new BlockShapeDump.Value(1, "true", "true", "java.lang.Boolean")));
        var blocks = List.of(new BlockShapeDump.BlockShape(0, "fake:z_first", "fake.FirstBlock", 4,
                        List.of(first, second), List.of(selected(first, 1), selected(second, 0))),
                emptyBlock(1, "fake:a_second"));
        Path output = directory.resolve("shape.json.gz");
        assertEquals(new BlockShapeDump.Totals(2, 5, 2, 4), BlockShapeDump.write(output, TIME, blocks, Function.identity()));
        JsonObject root = read(output);
        assertEquals(Set.of("schemaVersion", "source", "generatedAtUtc", "minecraftVersion", "blocks", "totals"), root.keySet());
        assertEquals(1, root.get("schemaVersion").getAsInt()); assertEquals("minecraft-block-registry", root.get("source").getAsString());
        assertEquals(TIME.toString(), root.get("generatedAtUtc").getAsString()); assertEquals("1.18.2", root.get("minecraftVersion").getAsString());
        var records = root.getAsJsonArray("blocks"); assertEquals("fake:z_first", records.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("fake:a_second", records.get(1).getAsJsonObject().get("id").getAsString());
        JsonObject block = records.get(0).getAsJsonObject();
        assertEquals(Set.of("index", "id", "blockClass", "stateCount", "properties", "defaultValues"), block.keySet());
        assertEquals(0, block.get("index").getAsInt()); assertEquals(4, block.get("stateCount").getAsInt());
        assertEquals("fake.FirstBlock", block.get("blockClass").getAsString());
        var properties = block.getAsJsonArray("properties");
        assertEquals("z_axis", properties.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("active", properties.get(1).getAsJsonObject().get("name").getAsString());
        JsonObject property = properties.get(0).getAsJsonObject();
        assertEquals(Set.of("index", "name", "propertyClass", "valueClass", "values"), property.keySet());
        assertEquals("fake.DirectionProperty", property.get("propertyClass").getAsString()); assertEquals("fake.Direction", property.get("valueClass").getAsString());
        JsonObject value = property.getAsJsonArray("values").get(1).getAsJsonObject();
        assertEquals(Set.of("index", "name", "text", "valueClass"), value.keySet());
        assertEquals(1, value.get("index").getAsInt()); assertEquals("north", value.get("name").getAsString()); assertEquals("North DISPLAY", value.get("text").getAsString());
        JsonObject defaultValue = block.getAsJsonArray("defaultValues").get(0).getAsJsonObject();
        assertEquals(Set.of("property", "valueIndex", "name", "text", "valueClass"), defaultValue.keySet());
        assertEquals("z_axis", defaultValue.get("property").getAsString()); assertEquals(1, defaultValue.get("valueIndex").getAsInt());
        assertEquals(value.get("name"), defaultValue.get("name")); assertEquals(value.get("text"), defaultValue.get("text")); assertEquals(value.get("valueClass"), defaultValue.get("valueClass"));
        assertEquals(Set.of("blockTypes", "states", "properties", "domainValues"), root.getAsJsonObject("totals").keySet());
        assertEquals(2, root.getAsJsonObject("totals").get("blockTypes").getAsLong()); assertEquals(5, root.getAsJsonObject("totals").get("states").getAsLong());
        assertEquals(2, root.getAsJsonObject("totals").get("properties").getAsLong()); assertEquals(4, root.getAsJsonObject("totals").get("domainValues").getAsLong());
    }

    @Test void emptyRegistryWritesValidEmptyEnvelope() throws Exception {
        Path output = directory.resolve("empty.gz");
        assertEquals(new BlockShapeDump.Totals(0, 0, 0, 0), BlockShapeDump.write(output, TIME, List.<BlockShapeDump.BlockShape>of(), Function.identity()));
        assertEquals(0, read(output).getAsJsonArray("blocks").size());
    }

    @Test void defaultDomainUsesEqualityAndRejectsMissingOrAmbiguousCustomValues() {
        record CustomValue(int code) { @Override public String toString() { return "display-" + code; } }
        assertEquals(1, BlockShapeDump.defaultIndex(List.of(new CustomValue(7), new CustomValue(4)), new CustomValue(4)));
        assertThrows(IllegalArgumentException.class, () -> BlockShapeDump.defaultIndex(List.of(new CustomValue(7)), new CustomValue(4)));
        assertThrows(IllegalArgumentException.class, () -> BlockShapeDump.defaultIndex(List.of(new CustomValue(4), new CustomValue(4)), new CustomValue(4)));
    }

    @Test void customUtf16TextSurvivesGzipJsonWithoutChangingCanonicalName() throws Exception {
        String text = "display \u00e9 \ud83d\ude80 \ud800";
        var property = new BlockShapeDump.PropertyShape(0, "custom", "fake.CustomProperty", "fake.Value", List.of(new BlockShapeDump.Value(0, "canonical", text, "fake.Value")));
        Path output = directory.resolve("custom.gz");
        BlockShapeDump.write(output, TIME, List.of(new BlockShapeDump.BlockShape(0, "fake:custom", "fake.Block", 1, List.of(property), List.of(selected(property, 0)))), Function.identity());
        JsonObject value = read(output).getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonArray("properties").get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsJsonObject();
        assertEquals(text, value.get("text").getAsString()); assertEquals("canonical", value.get("name").getAsString());
    }

    @Test void createOnlyNeverOpensOrDeletesExistingOutput() throws Exception {
        Path output = directory.resolve("existing.gz"); byte[] original = new byte[] {12, 34, 56}; Files.write(output, original);
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> BlockShapeDump.write(output, TIME, List.of(emptyBlock(0, "fake:block")), Function.identity()));
        assertArrayEquals(original, Files.readAllBytes(output));
    }

    @Test void failureAfterPartialCaptureDeletesOnlyItsNewOutput() {
        Path output = directory.resolve("failed.gz"); AtomicInteger captured = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> BlockShapeDump.write(output, TIME, List.of("first", "second"), name -> {
            if (captured.getAndIncrement() == 1) throw new IllegalStateException("custom property failure");
            return emptyBlock(0, "fake:first");
        }));
        assertFalse(Files.exists(output)); assertEquals(2, captured.get());
    }

    @Test void streamingConsumesOneBlockAtATimeAndRejectsInconsistentDefaultMetadata() {
        AtomicInteger captures = new AtomicInteger();
        Iterable<Integer> registry = () -> new Iterator<>() {
            int index;
            public boolean hasNext() { return index < 3; }
            public Integer next() { assertEquals(index, captures.get(), "Previous block must already be emitted"); return index++; }
        };
        Path output = directory.resolve("streamed.gz");
        assertDoesNotThrow(() -> BlockShapeDump.write(output, TIME, registry, index -> { captures.incrementAndGet(); return emptyBlock(index, "fake:" + index); }));
        assertEquals(3, captures.get());
        var property = new BlockShapeDump.PropertyShape(0, "flag", "fake.Property", "fake.Value", List.of(new BlockShapeDump.Value(0, "yes", "YES", "fake.Value")));
        var bad = new BlockShapeDump.BlockShape(0, "fake:bad", "fake.Block", 1, List.of(property), List.of(new BlockShapeDump.DefaultValue("flag", 0, "no", "NO", "fake.Value")));
        Path rejected = directory.resolve("invalid.gz");
        assertThrows(IllegalArgumentException.class, () -> BlockShapeDump.write(rejected, TIME, List.of(bad), Function.identity())); assertFalse(Files.exists(rejected));
    }

    @Test void commandHasExplicitPermissionFourChecksEvenUnderPermissiveParent() throws Exception {
        ClassNode command = bytecode("dev/hoyin1600p/vhaccelerator/diagnostics/blockshape/BlockShapeCommand"); int checks = 0;
        for (var method : command.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/commands/CommandSourceStack") && call.name.equals("hasPermission")) {
                assertEquals(Opcodes.ICONST_4, instruction.getPrevious().getOpcode()); checks++;
            }
        }
        assertEquals(3, checks, "Both command nodes and the executor must guard permission level four");
        ClassNode root = bytecode("dev/hoyin1600p/vhaccelerator/VHAcceleratorCommand");
        assertTrue(root.methods.stream().anyMatch(method -> java.util.Arrays.stream(method.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.endsWith("/BlockShapeCommand") && call.name.equals("node"))));
    }

    private static BlockShapeDump.BlockShape emptyBlock(int index, String id) { return new BlockShapeDump.BlockShape(index, id, "fake.EmptyBlock", 1, List.of(), List.of()); }
    private static BlockShapeDump.DefaultValue selected(BlockShapeDump.PropertyShape property, int index) {
        var value = property.values().get(index); return new BlockShapeDump.DefaultValue(property.name(), index, value.name(), value.text(), value.valueClass());
    }
    private static JsonObject read(Path file) throws Exception {
        try (var gzip = new GZIPInputStream(Files.newInputStream(file)); var text = new InputStreamReader(gzip, StandardCharsets.UTF_8)) { return JsonParser.parseReader(text).getAsJsonObject(); }
    }
    private static ClassNode bytecode(String name) throws Exception {
        try (var input = BlockShapeDumpTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
}
