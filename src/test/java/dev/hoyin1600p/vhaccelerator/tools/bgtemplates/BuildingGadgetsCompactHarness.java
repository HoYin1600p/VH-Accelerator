package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import com.google.common.collect.ImmutableMap;
import com.google.gson.GsonBuilder;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.CompactTemplateLookup;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.PackedTemplatePositions;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Copied-file raw-tag differential and retained map-structure measurement, without a world or registries. */
public final class BuildingGadgetsCompactHarness {
    private static final String JDK_MAP = "com.google.common.collect.JdkBackedImmutableMap";
    private BuildingGadgetsCompactHarness() { }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 == arguments.length || options.put(arguments[index], arguments[index + 1]) != null) {
                throw new IllegalArgumentException("Each explicit option is required exactly once");
            }
        }
        if (!options.keySet().equals(Set.of("--snapshot-file", "--output-directory", "--buildinggadgets-jar"))) {
            throw new IllegalArgumentException("Required: --snapshot-file <copied saved data> --output-directory <new external directory> --buildinggadgets-jar <exact original jar>");
        }
        var result = run(Path.of(options.get("--snapshot-file")), Path.of(options.get("--output-directory")),
                Path.of(options.get("--buildinggadgets-jar")));
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private record TemplateData(CompoundTag body, ImmutableMap<BlockPos, CompoundTag> map,
                                Object entryView, Object keyView, Object valueView, Object[] entries) { }
    private record SaveResult(String uncompressedSha256, String compressedSha256, long bytes) { }

    static Map<String, Object> run(Path input, Path outputDirectory, Path originalJar) throws IOException {
        Path source = checkedExisting(input);
        if (!Files.isRegularFile(source) || !source.getFileName().toString().equals(BuildingGadgetsInventory.SAVED_FILE)) {
            throw new IOException("Expected an explicit copied BuildingGadgets saved-data file");
        }
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path parent = checkedExisting(output.getParent());
        Path protectedRoot = source.getParent();
        if (protectedRoot.getFileName().toString().equals("data")) protectedRoot = protectedRoot.getParent();
        if (!parent.equals(output.getParent()) || output.startsWith(protectedRoot)
                || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output must be a new external directory outside the copied input/world");
        }
        Path jar = checkedExisting(originalJar);
        OriginalRawTemplateCodec codecs = new OriginalRawTemplateCodec(jar);
        String sourceHash = OriginalBuildingGadgetsCodecs.sha256(source);
        long sourceBytes = Files.size(source);
        TypedNbtWriter.NamedRoot root;
        try (var stream = new DataInputStream(new GZIPInputStream(Files.newInputStream(source)))) {
            root = TypedNbtWriter.readRoot(stream);
            if (stream.read() != -1) throw new IOException("Trailing bytes after copied NBT root");
        }
        ListTag records = list(compound(root.data(), "data"), "data_map", Tag.TAG_COMPOUND);
        ArrayList<TemplateData> templates = new ArrayList<>(records.size());
        Set<UUID> ids = new HashSet<>();
        Set<Object> sharedRawValues = Collections.newSetFromMap(new IdentityHashMap<>());
        long blocks = 0;
        long empty = 0;
        long jdkMaps = 0;
        long fullPackedSlots = 0;
        long paletteEntries = 0;
        long nonemptyMaps = 0;
        int maximumPaletteSize = 0;
        for (Tag rawRecord : records) {
            CompoundTag record = (CompoundTag) rawRecord;
            if (!(record.get("key") instanceof IntArrayTag key) || key.getAsIntArray().length != 4
                    || !ids.add(record.getUUID("key"))) throw new IOException("Invalid or duplicate template UUID");
            CompoundTag body = compound(compound(record, "val"), "data");
            ListTag palette = list(body, "data", Tag.TAG_COMPOUND);
            paletteEntries += palette.size();
            maximumPaletteSize = Math.max(maximumPaletteSize, palette.size());
            ListTag positions = list(body, "pos", Tag.TAG_LONG);
            var values = codecs.raw.palette(palette);
            // Match the exact original Template.deserialize builder, including its retained array slack.
            var builder = ImmutableMap.<BlockPos, CompoundTag>builder();
            for (Tag rawPosition : positions) {
                long packed = ((LongTag) rawPosition).getAsLong();
                Tag value;
                try { value = values.apply(codecs.raw.stateId(packed)); }
                catch (RuntimeException failure) { throw new IOException("Invalid raw block palette reference", failure); }
                if (!(value instanceof CompoundTag block)) throw new IOException("Block palette entry is not a compound");
                builder.put(codecs.raw.position(packed), block);
                sharedRawValues.add(block);
            }
            ImmutableMap<BlockPos, CompoundTag> map;
            try { map = builder.build(); }
            catch (IllegalArgumentException failure) { throw new IOException("Duplicate original decoded coordinate", failure); }
            if (map.isEmpty()) empty++;
            else {
                nonemptyMaps++;
                long capacity = 1;
                while (capacity * 0.75 < map.size()) capacity <<= 1;
                fullPackedSlots += capacity;
            }
            if (map.getClass().getName().equals(JDK_MAP)) jdkMaps++;
            blocks += map.size();
            templates.add(new TemplateData(body, map, map.entrySet(), map.keySet(), map.values(), map.entrySet().toArray()));
        }
        Files.createDirectory(output);
        Path baseline = savePath(output, "baseline-copy");
        Path compact = savePath(output, "compact-copy");
        SaveResult baselineSave = serialize(root, templates, codecs, baseline);
        List<ImmutableMap<BlockPos, CompoundTag>> roots = templates.stream().map(TemplateData::map).toList();
        System.gc();
        RetainedGraphWalker.Result before = RetainedGraphWalker.measure(roots, sharedRawValues);
        int installed = 0;
        // Cost split: one get per entry through the original HashMap (the verification's expensive half).
        long originalLoopNanos = 0;
        for (TemplateData template : templates) originalLoopNanos += getLoop(template.map);
        long installNanos = 0;
        for (TemplateData template : templates) {
            long started = System.nanoTime();
            if (CompactTemplateLookup.install(template.map)) installed++;
            installNanos += System.nanoTime() - started;
        }
        long candidateLoopNanos = 0;
        for (TemplateData template : templates) candidateLoopNanos += getLoop(template.map);
        for (TemplateData template : templates) verifyIdentityAndLookups(template);
        SaveResult compactSave = serialize(root, templates, codecs, compact);
        if (!baselineSave.equals(compactSave) || Files.mismatch(baseline, compact) != -1) {
            throw new IOException("Original raw-tag save model differs after production delegate installation");
        }
        System.gc();
        RetainedGraphWalker.Result after = RetainedGraphWalker.measure(roots, sharedRawValues);
        Map<String, Object> packed = packedPhase(root, templates, codecs, output, baselineSave, sharedRawValues);
        if (Files.size(source) != sourceBytes || !OriginalBuildingGadgetsCodecs.sha256(source).equals(sourceHash)
                || !OriginalBuildingGadgetsCodecs.sha256(jar).equals(OriginalBuildingGadgetsCodecs.JAR_SHA256)) {
            throw new IOException("Input copy or original archive changed during harness");
        }
        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("status", "complete");
        report.put("sourceSha256", sourceHash);
        report.put("sourceBytes", sourceBytes);
        report.put("originalJarSha256", OriginalBuildingGadgetsCodecs.JAR_SHA256);
        report.put("originalJarVersion", OriginalBuildingGadgetsCodecs.JAR_VERSION);
        report.put("originalMathClassSha256", codecs.originalMathSha256);
        report.put("linkedMathClassSha256", codecs.linkedMathSha256);
        report.put("originalCompressorClassSha256", codecs.originalCompressorSha256);
        report.put("originalDecompressorClassSha256", codecs.originalDecompressorSha256);
        report.put("namespaceOnlyChanges", Map.of("method", "posToLong", "instructionNameChanges", 3,
                "links", Map.of("m_123341_", "getX", "m_123342_", "getY", "m_123343_", "getZ"),
                "algorithmControlFlowAndDescriptorsUnchanged", true));
        report.put("oracle", "Exact original MathUtils/DataDecompressor/DataCompressor; raw CompoundTag values substitute for registry-backed BlockData");
        report.put("fullOriginalTemplateSaveVerified", false);
        report.put("policyAndMixinActivationVerifiedByThisHarness", false);
        report.put("templates", templates.size());
        report.put("emptyTemplates", empty);
        report.put("blocks", blocks);
        report.put("maximumRawPaletteSize", maximumPaletteSize);
        report.put("originalJdkBackedMaps", jdkMaps);
        report.put("installedDelegates", installed);
        report.put("timingMillis", Map.of("installTotalBuildVerifyPublish", installNanos / 1_000_000,
                "oneGetPerEntryOriginalHashMap", originalLoopNanos / 1_000_000,
                "oneGetPerEntryCompactIndex", candidateLoopNanos / 1_000_000,
                "approxIndexBuildAndChecks", (installNanos - originalLoopNanos - candidateLoopNanos) / 1_000_000));
        report.put("mapViewsAndOriginalEntryKeyValueIdentityPreserved", true);
        report.put("byteIdenticalRawTagSaveModel", true);
        report.put("baselineSave", baselineSave);
        report.put("compactSave", compactSave);
        report.put("baselineFile", baseline.toString());
        report.put("compactFile", compact.toString());
        report.put("measurement", "Instrumentation actual shallow sizes summed once over strong map-root graph; not whole-JVM dominator retained heap");
        report.put("excludedCommonRawTagValueRoots", sharedRawValues.size());
        report.put("excludedCommonRawTagValueGraphs", true);
        report.put("globalIdentityDedupAcrossAllMapRoots", true);
        report.put("baselineMapStructure", before);
        report.put("compactMapStructure", after);
        report.put("mapStructureBytesSaved", before.bytes() - after.bytes());
        report.put("mapStructureSavingsFraction", before.bytes() == 0 ? 0.0 : (double) (before.bytes() - after.bytes()) / before.bytes());
        long arrayHeaders = nonemptyMaps * (RetainedGraphWalker.instrumentation().getObjectSize(new long[0])
                + RetainedGraphWalker.instrumentation().getObjectSize(new byte[0])
                + RetainedGraphWalker.instrumentation().getObjectSize(new int[0])
                + RetainedGraphWalker.instrumentation().getObjectSize(new Object[0]));
        report.put("packedPositions", packed);
        report.put("fullPackedHypothesisOnly", Map.of("coordinateAndBytePalettePayloadBytes", blocks * 9,
                "lookupSlotsAtLoadFactor075", fullPackedSlots, "lookupPayloadBytes", fullPackedSlots * 4,
                "paletteEntries", paletteEntries, "paletteReferencePayloadBytesAssuming4ByteReferences", paletteEntries * 4,
                "arrayHeadersAtMeasuredLayout", arrayHeaders,
                "modelBytes", blocks * 9 + fullPackedSlots * 4 + paletteEntries * 4 + arrayHeaders,
                "assumptions", "8-byte coordinates + 1-byte palette IDs (palettes <=256), int index at0.75 load, 4-byte references; excludes extra object/alignment costs",
                "omits", "original key/entry identity, alias and materialization costs; unimplemented and not a measured or approved full-packed saving"));
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("managerChecks", "Full registry-backed Template save/reopen, actual dedicated-server activation, placement/edit/rollback remain left to manager");
        Files.writeString(output.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return report;
    }

    /**
     * Packs every map with the production class, checks entry-by-entry equality (order, key values, value identity)
     * and a byte-identical raw-tag save through the rebuilt maps, and measures the packed graph on its own.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> packedPhase(TypedNbtWriter.NamedRoot root, List<TemplateData> templates,
                                                   OriginalRawTemplateCodec codecs, Path output, SaveResult baselineSave,
                                                   Set<Object> sharedRawValues) throws IOException {
        List<ImmutableMap<BlockPos, ?>> markers = new ArrayList<>(templates.size());
        long packedMaps = 0;
        long refused = 0;
        long packedEntries = 0;
        long started = System.nanoTime();
        for (TemplateData template : templates) {
            ImmutableMap<BlockPos, ?> marker = PackedTemplatePositions.pack(template.map);
            if (marker == null) { refused++; markers.add(template.map); }
            else { packedMaps++; packedEntries += template.map.size(); markers.add(marker); }
        }
        long packNanos = System.nanoTime() - started;
        List<TemplateData> rebuilt = new ArrayList<>(templates.size());
        started = System.nanoTime();
        for (int index = 0; index < templates.size(); index++) {
            TemplateData original = templates.get(index);
            ImmutableMap<BlockPos, CompoundTag> map = (ImmutableMap<BlockPos, CompoundTag>) PackedTemplatePositions.resolve(markers.get(index));
            if (map.size() != original.map.size() || !map.equals(original.map) || map.hashCode() != original.map.hashCode()
                    || map.getClass() != original.map.getClass()) throw new IOException("Packed map differs from the original");
            var left = original.map.entrySet().iterator();
            for (var entry : map.entrySet()) {
                var expected = left.next();
                if (!expected.getKey().equals(entry.getKey()) || expected.getValue() != entry.getValue()
                        || expected.getKey().getClass() != entry.getKey().getClass()) throw new IOException("Packed entry differs from the original");
            }
            rebuilt.add(new TemplateData(original.body, map, null, null, null, null));
        }
        long rebuildNanos = System.nanoTime() - started;
        Path packedCopy = savePath(output, "packed-copy");
        SaveResult packedSave = serialize(root, rebuilt, codecs, packedCopy);
        if (!baselineSave.equals(packedSave) || Files.mismatch(savePath(output, "baseline-copy"), packedCopy) != -1) {
            throw new IOException("Raw-tag save differs when read through the packed maps");
        }
        rebuilt = null;
        System.gc();
        RetainedGraphWalker.Result graph = RetainedGraphWalker.measure(
                markers.stream().map(map -> (ImmutableMap<BlockPos, ?>) map).toList(), sharedRawValues);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("packedMaps", packedMaps);
        result.put("refusedMaps", refused);
        result.put("packedEntries", packedEntries);
        result.put("packAndVerifyMillis", packNanos / 1_000_000);
        result.put("rebuildAndCompareAllMillis", rebuildNanos / 1_000_000);
        result.put("byteIdenticalRawTagSaveThroughPackedMaps", true);
        result.put("packedSave", packedSave);
        result.put("packedMapStructure", graph);
        return result;
    }

    private static long getLoop(ImmutableMap<BlockPos, CompoundTag> map) {
        long started = System.nanoTime();
        int misses = 0;
        for (var entry : map.entrySet()) if (map.get(entry.getKey()) != entry.getValue()) misses++;
        if (misses != 0) throw new IllegalStateException("lookup mismatch");
        return System.nanoTime() - started;
    }

    private static void verifyIdentityAndLookups(TemplateData template) throws IOException {
        ImmutableMap<BlockPos, CompoundTag> map = template.map;
        if (map.entrySet() != template.entryView || map.keySet() != template.keyView || map.values() != template.valueView) {
            throw new IOException("Original map view identity changed");
        }
        int index = 0;
        for (var entry : map.entrySet()) {
            if (entry != template.entries[index++] || map.get(entry.getKey()) != entry.getValue()
                    || map.get(new BlockPos(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ())) != entry.getValue()) {
                throw new IOException("Original entry/key/value identity, lookup or order changed");
            }
        }
        if (index != template.entries.length) throw new IOException("Map size changed");
    }

    private static Path savePath(Path output, String directory) throws IOException {
        Path data = Files.createDirectories(output.resolve(directory).resolve("data"));
        return data.resolve(BuildingGadgetsInventory.SAVED_FILE);
    }

    private static SaveResult serialize(TypedNbtWriter.NamedRoot root, List<TemplateData> templates,
                                        OriginalRawTemplateCodec codecs, Path destination) throws IOException {
        for (TemplateData template : templates) {
            var compressor = codecs.compressor();
            ListTag positions = new ListTag();
            for (var entry : template.map.entrySet()) {
                positions.add(LongTag.valueOf(codecs.packed(entry.getKey(), compressor.applyAsInt(entry.getValue()))));
            }
            template.body.put("pos", positions);
            template.body.put("data", codecs.palette(compressor));
        }
        MessageDigest digest = sha256();
        try (var file = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW);
             var gzip = new GZIPOutputStream(file);
             var hashing = new DigestOutputStream(gzip, digest);
             var output = new DataOutputStream(hashing)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF(root.name());
            writePayload(output, root.data());
        }
        return new SaveResult(HexFormat.of().formatHex(digest.digest()), OriginalBuildingGadgetsCodecs.sha256(destination), Files.size(destination));
    }

    /** Standard NBT writer preserving raw floating bits and typed empty lists. Compound keys are canonicalized. */
    static void writePayload(DataOutput output, Tag tag) throws IOException {
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE -> output.writeByte(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> output.writeShort(((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> output.writeInt(((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> output.writeLong(((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> output.writeInt(Float.floatToRawIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> output.writeLong(Double.doubleToRawLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_STRING -> output.writeUTF(tag.getAsString());
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                output.writeByte(list.getElementType()); output.writeInt(list.size());
                for (Tag value : list) writePayload(output, value);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                for (String key : new TreeSet<>(compound.getAllKeys())) {
                    Tag value = compound.get(key);
                    output.writeByte(value.getId()); output.writeUTF(key); writePayload(output, value);
                }
                output.writeByte(Tag.TAG_END);
            }
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray(); output.writeInt(values.length); output.write(values);
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray(); output.writeInt(values.length);
                for (int value : values) output.writeInt(value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray(); output.writeInt(values.length);
                for (long value : values) output.writeLong(value);
            }
            default -> throw new IOException("Unsupported raw tag type");
        }
    }

    private static Path checkedExisting(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path real = absolute.toRealPath();
        if (!real.equals(absolute)) throw new IOException("Aliased/symlink paths are unsupported");
        return real;
    }

    private static CompoundTag compound(CompoundTag parent, String name) throws IOException {
        if (!(parent.get(name) instanceof CompoundTag result)) throw new IOException("Missing compound " + name);
        return result;
    }

    private static ListTag list(CompoundTag parent, String name, int expectedType) throws IOException {
        if (!(parent.get(name) instanceof ListTag result)
                || result.getElementType() != expectedType && !(result.isEmpty() && result.getElementType() == Tag.TAG_END)) {
            throw new IOException("Invalid typed list " + name);
        }
        return result;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
