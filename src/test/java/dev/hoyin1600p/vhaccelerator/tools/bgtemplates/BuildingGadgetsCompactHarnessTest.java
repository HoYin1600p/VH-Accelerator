package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildingGadgetsCompactHarnessTest {
    @TempDir Path directory;

    @Test void originalPackingAndCompressorUseOnlyReviewedNamespaceLinks() throws Exception {
        var codecs = new OriginalRawTemplateCodec(OriginalBuildingGadgetsCodecsTest.originalJar());
        assertNotEquals(codecs.originalMathSha256, codecs.linkedMathSha256);
        assertEquals(0xffffffffffL, codecs.packed(new BlockPos(-1, -1, -1), 0));
        assertEquals(-1L, codecs.packed(new BlockPos(65535, 255, 65535), 0xffffff));
        CompoundTag first = new CompoundTag(); first.putString("unknown", "value");
        CompoundTag equal = first.copy();
        CompoundTag other = new CompoundTag(); other.putInt("unknown", 1);
        var compressor = codecs.compressor();
        assertEquals(0, compressor.applyAsInt(first));
        assertEquals(0, compressor.applyAsInt(equal));
        assertEquals(1, compressor.applyAsInt(other));
        ListTag palette = codecs.palette(compressor);
        assertEquals(2, palette.size());
        assertSame(first, palette.get(0));
        assertSame(other, palette.get(1));
    }

    private static final class Node {
        final Object common;
        final byte[] bytes;
        Node next;
        Node(Object common) { this.common = common; bytes = new byte[31]; }
    }

    @Test void measuredGraphCountsIdentityOnceAndExcludesSharedValueSubgraphs() throws Exception {
        Object common = new Object();
        Node first = new Node(common); Node second = new Node(common);
        first.next = second; second.next = first;
        Set<Object> excluded = Collections.newSetFromMap(new IdentityHashMap<>());
        excluded.add(common);
        var sizes = RetainedGraphWalker.instrumentation();
        var graph = RetainedGraphWalker.measure(List.of(first, first), excluded);
        assertEquals(4, graph.objects());
        assertEquals(sizes.getObjectSize(first) + sizes.getObjectSize(second)
                + sizes.getObjectSize(first.bytes) + sizes.getObjectSize(second.bytes), graph.bytes());
        assertFalse(graph.classes().containsKey(Object.class.getName()));
        assertEquals(2, graph.classes().get(Node.class.getName()).objects());
    }

    @Test void canonicalNbtWriterPreservesRawFloatingBitsEmptyTypesAndRootCharacters() throws Exception {
        CompoundTag original = new CompoundTag();
        original.put("zero", TypedNbtWriter.rawFloat(0x80000000));
        original.put("nan", TypedNbtWriter.rawDouble(0x7ff8000000000001L));
        ByteArrayOutputStream empty = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(empty)) {
            out.writeByte(Tag.TAG_COMPOUND); out.writeUTF("");
            out.writeByte(Tag.TAG_LIST); out.writeUTF("empty"); out.writeByte(Tag.TAG_LONG); out.writeInt(0); out.writeByte(Tag.TAG_END);
        }
        original.put("list", TypedNbtWriter.readRoot(new DataInputStream(new ByteArrayInputStream(empty.toByteArray()))).data().get("empty"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(Tag.TAG_COMPOUND); out.writeUTF("root\ud800\ud83d\ude80");
            BuildingGadgetsCompactHarness.writePayload(out, original);
        }
        var restored = TypedNbtWriter.readRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals("root\ud800\ud83d\ude80", restored.name());
        assertEquals(0x80000000, Float.floatToRawIntBits(restored.data().getFloat("zero")));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(restored.data().getDouble("nan")));
        assertEquals(Tag.TAG_LONG, ((ListTag) restored.data().get("list")).getElementType());
    }

    @Test void copiedFixtureUsesActualInstallerAndByteIdenticalRawTagSaveWithoutChangingInput() throws Exception {
        Path jar = OriginalBuildingGadgetsCodecsTest.originalJar();
        CompoundTag root = BuildingGadgetsInventoryTest.fixture(false);
        CompoundTag record = (CompoundTag) root.getCompound("data").getList("data_map", Tag.TAG_COMPOUND).get(0);
        CompoundTag body = record.getCompound("val").getCompound("data");
        ListTag positions = new ListTag();
        // All 64 original Vec3i hashes coincide, forcing Guava's real JDK-backed representation.
        for (int index = 0; index < 64; index++) positions.add(LongTag.valueOf(((long) (index * 961) << 24) | (63 - index)));
        body.put("pos", positions);
        Path input = directory.resolve("copy/data/" + BuildingGadgetsInventory.SAVED_FILE);
        Files.createDirectories(input.getParent());
        try (var out = Files.newOutputStream(input)) { NbtIo.writeCompressed(root, out); }
        String before = OriginalBuildingGadgetsCodecs.sha256(input);
        Path output = directory.resolve("measurement");
        var report = BuildingGadgetsCompactHarness.run(input, output, jar);
        assertEquals(1, report.get("templates"));
        assertEquals(64L, report.get("blocks"));
        assertEquals(1L, report.get("originalJdkBackedMaps"));
        assertEquals(1, report.get("installedDelegates"));
        assertEquals(true, report.get("byteIdenticalRawTagSaveModel"));
        assertEquals(false, report.get("fullOriginalTemplateSaveVerified"));
        assertTrue((long) report.get("mapStructureBytesSaved") > 0);
        assertEquals(-1L, Files.mismatch(Path.of((String) report.get("baselineFile")), Path.of((String) report.get("compactFile"))));
        assertEquals(before, OriginalBuildingGadgetsCodecs.sha256(input));
        assertThrows(java.io.IOException.class, () -> BuildingGadgetsCompactHarness.run(input, output, jar));
        assertThrows(java.io.IOException.class, () -> BuildingGadgetsCompactHarness.run(input, input.getParent().resolve("forbidden"), jar));
    }
}
