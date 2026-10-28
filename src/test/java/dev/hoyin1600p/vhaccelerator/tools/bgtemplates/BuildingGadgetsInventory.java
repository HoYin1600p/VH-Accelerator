package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Offline inspection of a caller-provided disposable saved-data COPY. Never loads or saves a world. */
public final class BuildingGadgetsInventory {
    public static final String SAVED_FILE = "buildinggadgets_template_save.dat";
    private BuildingGadgetsInventory() { }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 == arguments.length || options.put(arguments[index], arguments[index + 1]) != null) {
                throw new IllegalArgumentException("Expected each explicit option exactly once");
            }
        }
        if (!options.keySet().equals(Set.of("--snapshot-file", "--output", "--buildinggadgets-jar"))) {
            throw new IllegalArgumentException("Required: --snapshot-file <copied saved-data file> --output <external JSON> --buildinggadgets-jar <exact original jar>");
        }
        writeInventory(Path.of(options.get("--snapshot-file")), Path.of(options.get("--output")),
                Path.of(options.get("--buildinggadgets-jar")));
    }

    public static void writeInventory(Path snapshot, Path output, Path originalJar) throws IOException {
        Path source = checkedExisting(snapshot);
        if (!Files.isRegularFile(source) || !source.getFileName().toString().equals(SAVED_FILE)) {
            throw new IOException("Input must be a copied " + SAVED_FILE);
        }
        Path destination = output.toAbsolutePath().normalize();
        Path parent = checkedExisting(destination.getParent());
        if (!Files.isDirectory(parent) || !parent.equals(destination.getParent())
                || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output parent must exist without aliases; output must not already exist");
        }
        Path protectedRoot = source.getParent();
        if (protectedRoot.getFileName() != null && protectedRoot.getFileName().toString().equals("data")) {
            protectedRoot = protectedRoot.getParent();
        }
        if (destination.startsWith(protectedRoot)) throw new IOException("Output must be outside the input copy/world directory");
        Path jar = checkedExisting(originalJar);
        OriginalBuildingGadgetsCodecs codecs = new OriginalBuildingGadgetsCodecs(jar);
        String sourceHash = OriginalBuildingGadgetsCodecs.sha256(source);
        long sourceBytes = Files.size(source);
        TypedNbtWriter.NamedRoot namedRoot;
        try (var input = new DataInputStream(new GZIPInputStream(Files.newInputStream(source)))) {
            namedRoot = TypedNbtWriter.readRoot(input);
            if (input.read() != -1) throw new IOException("Trailing data after saved-data NBT root");
        }
        Path temporary = Files.createTempFile(parent, ".bg-inventory-", ".tmp");
        try {
            MessageDigest digest = sha256Digest();
            try (var bytes = new DigestOutputStream(Files.newOutputStream(temporary), digest);
                 var writer = new JsonWriter(TypedNbtWriter.asciiJson(new OutputStreamWriter(bytes, StandardCharsets.UTF_8)))) {
                writer.setSerializeNulls(true);
                bytes.on(false);
                writer.beginObject().name("schemaVersion").value(1).name("status").value("complete");
                writer.name("semantic").beginObject();
                writer.flush();
                digest.update((byte) '{');
                bytes.on(true);
                writeSemanticContents(writer, namedRoot.data(), namedRoot.name(), codecs);
                writer.flush();
                bytes.on(false);
                writer.name("provenance").beginObject()
                        .name("sourceSha256").value(sourceHash).name("sourceBytes").value(sourceBytes)
                        .name("jarSha256").value(OriginalBuildingGadgetsCodecs.JAR_SHA256)
                        .name("jarVersion").value(OriginalBuildingGadgetsCodecs.JAR_VERSION)
                        .name("originalCoordinateCodecVerified").value(true)
                        .name("originalPaletteCodecVerified").value(true)
                        .name("fullOriginalTemplateDecode").value(false)
                        .name("coordinateCodec").value(OriginalBuildingGadgetsCodecs.MATH + ".posFromLong/readStateId")
                        .name("paletteCodec").value(OriginalBuildingGadgetsCodecs.PALETTE)
                        .name("registryResolution").value("raw-state-and-serializer-tags-preserved; no Forge registry resolution")
                        .name("semanticSha256").value(HexFormat.of().formatHex(digest.digest()))
                        .endObject().endObject();
            }
            if (Files.size(source) != sourceBytes || !OriginalBuildingGadgetsCodecs.sha256(source).equals(sourceHash)) {
                throw new IOException("Input snapshot changed during inventory; no output published");
            }
            if (!OriginalBuildingGadgetsCodecs.sha256(jar).equals(OriginalBuildingGadgetsCodecs.JAR_SHA256)) {
                throw new IOException("Original archive changed during inventory; no output published");
            }
            // A hard link publishes a complete file atomically and fails if any output already exists.
            // Both paths share the already validated external output directory/filesystem.
            Files.createLink(destination, temporary);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Path checkedExisting(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path real = absolute.toRealPath();
        if (!real.equals(absolute)) throw new IOException("Symbolic links or aliased paths are not accepted");
        return real;
    }

    private static MessageDigest sha256Digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static void writeSemantic(JsonWriter output, CompoundTag root, OriginalBuildingGadgetsCodecs codecs) throws IOException {
        output.beginObject();
        writeSemanticContents(output, root, "", codecs);
    }

    private static void writeSemanticContents(JsonWriter output, CompoundTag root, String rootName, OriginalBuildingGadgetsCodecs codecs) throws IOException {
        CompoundTag savedData = compound(root, "data", "root");
        ListTag entries = list(savedData, "data_map", Tag.TAG_COMPOUND, "root.data");
        output.name("rawRootName").value(rootName).name("rawSavedData");
        TypedNbtWriter.write(output, root);
        output.name("templates").beginArray();
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = (CompoundTag) entries.get(index);
            String context = "template[" + index + "]";
            if (!(entry.get("key") instanceof IntArrayTag key) || key.getAsIntArray().length != 4) {
                throw new IOException(context + " has an invalid UUID tag");
            }
            UUID id = entry.getUUID("key");
            if (!ids.add(id)) throw new IOException("Duplicate template UUID: " + id);
            CompoundTag value = compound(entry, "val", context);
            CompoundTag data = compound(value, "data", context + ".val");
            CompoundTag header = compound(data, "header", context + ".data");
            CompoundTag bounds = compound(header, "bounds", context + ".header");
            for (String coordinate : new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}) {
                require(bounds, coordinate, Tag.TAG_INT, context + ".bounds");
            }
            String name = optionalString(header, "name", context);
            String author = optionalString(header, "author", context);
            if (value.contains("time")) require(value, "time", Tag.TAG_LONG, context);
            ListTag serializers = list(data, "serializer", Tag.TAG_STRING, context);
            ListTag palette = list(data, "data", Tag.TAG_COMPOUND, context);
            ListTag positions = list(data, "pos", Tag.TAG_LONG, context);
            IntFunction<Tag> serializerLookup = codecs.palette(serializers);
            IntFunction<Tag> blockLookup = codecs.palette(palette);
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                CompoundTag block = (CompoundTag) blockLookup.apply(paletteIndex);
                CompoundTag state = compound(block, "state", context + ".palette");
                require(state, "Name", Tag.TAG_STRING, context + ".state");
                if (state.contains("Properties")) {
                    CompoundTag properties = compound(state, "Properties", context + ".state");
                    for (String property : properties.getAllKeys()) require(properties, property, Tag.TAG_STRING, context + ".properties");
                }
                require(block, "serializer", Tag.TAG_INT, context + ".palette");
                int serializerIndex = block.getInt("serializer");
                if (serializerIndex < 0 || serializerIndex >= serializers.size()) throw new IOException(context + " has an invalid serializer palette reference");
                serializerLookup.apply(serializerIndex);
                compound(block, "data", context + ".palette");
            }
            output.beginObject().name("entryIndex").value(index).name("id").value(id.toString())
                    .name("name").value(name).name("author").value(author).name("header");
            TypedNbtWriter.write(output, header);
            output.name("updatedTime");
            if (value.contains("time")) TypedNbtWriter.write(output, value.get("time")); else output.nullValue();
            output.name("serializerPalette");
            TypedNbtWriter.write(output, serializers);
            output.name("blockPalette");
            TypedNbtWriter.write(output, palette);
            output.name("blocks").beginArray();
            LongOpenHashSet coordinates = new LongOpenHashSet(positions.size());
            for (int positionIndex = 0; positionIndex < positions.size(); positionIndex++) {
                long packed = ((LongTag) positions.get(positionIndex)).getAsLong();
                int paletteIndex = codecs.stateId(packed);
                if (paletteIndex >= palette.size()) throw new IOException(context + " has an invalid block palette reference");
                blockLookup.apply(paletteIndex);
                BlockPos position = codecs.position(packed);
                if (!coordinates.add(position.asLong())) throw new IOException(context + " repeats a coordinate");
                output.beginObject().name("positionIndex").value(positionIndex).name("packed").value(Long.toString(packed))
                        .name("coordinate").beginArray().value(position.getX()).value(position.getY()).value(position.getZ()).endArray()
                        .name("paletteIndex").value(paletteIndex).endObject();
            }
            output.endArray().endObject();
        }
        output.endArray().endObject();
    }

    private static void require(CompoundTag parent, String name, int type, String context) throws IOException {
        Tag value = parent.get(name);
        if (value == null || value.getId() != type) throw new IOException(context + "." + name + " has missing/wrong NBT type");
    }

    private static CompoundTag compound(CompoundTag parent, String name, String context) throws IOException {
        require(parent, name, Tag.TAG_COMPOUND, context);
        return (CompoundTag) parent.get(name);
    }

    private static ListTag list(CompoundTag parent, String name, int elementType, String context) throws IOException {
        require(parent, name, Tag.TAG_LIST, context);
        ListTag value = (ListTag) parent.get(name);
        if ((!value.isEmpty() && value.getElementType() != elementType)
                || (value.isEmpty() && value.getElementType() != Tag.TAG_END && value.getElementType() != elementType)) {
            throw new IOException(context + "." + name + " has the wrong list element type");
        }
        return value;
    }

    private static String optionalString(CompoundTag parent, String name, String context) throws IOException {
        if (!parent.contains(name)) return null;
        require(parent, name, Tag.TAG_STRING, context);
        return parent.getString(name);
    }
}
