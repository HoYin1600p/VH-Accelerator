package dev.hoyin1600p.vhaccelerator.diagnostics.blockshape;

import com.google.gson.stream.JsonWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

/** Explicit streaming registry metadata export; retains no registry or state references. */
public final class BlockShapeDump {
    private BlockShapeDump() { }

    public record Value(int index, String name, String text, String valueClass) { }
    public record PropertyShape(int index, String name, String propertyClass, String valueClass, List<Value> values) {
        public PropertyShape { values = List.copyOf(values); }
    }
    public record DefaultValue(String property, int valueIndex, String name, String text, String valueClass) { }
    public record BlockShape(int index, String id, String blockClass, int stateCount,
                             List<PropertyShape> properties, List<DefaultValue> defaultValues) {
        public BlockShape { properties = List.copyOf(properties); defaultValues = List.copyOf(defaultValues); }
    }
    public record Totals(long blockTypes, long states, long properties, long domainValues) { }

    /** Match using the native domain's equality; never guess a missing or ambiguous index. */
    public static <T> int defaultIndex(List<T> domain, T defaultValue) {
        int found = -1;
        for (int i = 0; i < domain.size(); i++) if (Objects.equals(domain.get(i), defaultValue)) {
            if (found != -1) throw new IllegalArgumentException("Ambiguous default domain value");
            found = i;
        }
        if (found < 0) throw new IllegalArgumentException("Default value absent from domain");
        return found;
    }

    /** CREATE_NEW is intentional: an existing output is never opened or deleted. */
    public static <B> Totals write(Path output, Instant generatedAt, Iterable<B> registry,
                                   Function<B, BlockShape> capture) throws IOException {
        boolean created = false;
        try {
            var raw = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created = true;
            try (raw;
                 var gzip = new GZIPOutputStream(raw);
                 var text = new OutputStreamWriter(gzip, StandardCharsets.UTF_8);
                 var ascii = new ExactJsonWriter(text);
                 var json = new JsonWriter(ascii)) {
                json.beginObject();
                json.name("schemaVersion").value(1);
                json.name("source").value("minecraft-block-registry");
                json.name("generatedAtUtc").value(generatedAt.toString());
                json.name("minecraftVersion").value("1.18.2");
                json.name("blocks").beginArray();
                long blocks = 0, states = 0, properties = 0, values = 0;
                for (B nativeBlock : registry) {
                    BlockShape block = Objects.requireNonNull(capture.apply(nativeBlock));
                    validate(block, blocks);
                    writeBlock(json, block);
                    blocks = Math.addExact(blocks, 1);
                    states = Math.addExact(states, block.stateCount());
                    properties = Math.addExact(properties, block.properties().size());
                    for (PropertyShape property : block.properties()) values = Math.addExact(values, property.values().size());
                }
                json.endArray();
                Totals totals = new Totals(blocks, states, properties, values);
                json.name("totals").beginObject();
                json.name("blockTypes").value(blocks); json.name("states").value(states);
                json.name("properties").value(properties); json.name("domainValues").value(values);
                json.endObject(); json.endObject();
                return totals;
            }
        } catch (IOException | RuntimeException | Error failure) {
            if (created) try { Files.deleteIfExists(output); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void validate(BlockShape block, long expectedIndex) {
        if (block.index() != expectedIndex || block.stateCount() < 1 || block.id() == null || block.blockClass() == null
                || block.defaultValues().size() != block.properties().size()) throw new IllegalArgumentException("Invalid block shape");
        for (int p = 0; p < block.properties().size(); p++) {
            PropertyShape property = block.properties().get(p); DefaultValue selected = block.defaultValues().get(p);
            if (property.index() != p || property.name() == null || property.propertyClass() == null || property.valueClass() == null
                    || property.values().isEmpty() || !property.name().equals(selected.property())
                    || selected.valueIndex() < 0 || selected.valueIndex() >= property.values().size()) throw new IllegalArgumentException("Invalid property shape");
            for (int v = 0; v < property.values().size(); v++) {
                Value value = property.values().get(v);
                if (value.index() != v || value.name() == null || value.text() == null || value.valueClass() == null) throw new IllegalArgumentException("Invalid domain value");
            }
            Value value = property.values().get(selected.valueIndex());
            if (!value.name().equals(selected.name()) || !value.text().equals(selected.text()) || !value.valueClass().equals(selected.valueClass())) throw new IllegalArgumentException("Invalid default value");
        }
    }

    private static void writeBlock(JsonWriter json, BlockShape block) throws IOException {
        json.beginObject(); json.name("index").value(block.index()); json.name("id").value(block.id());
        json.name("blockClass").value(block.blockClass()); json.name("stateCount").value(block.stateCount());
        json.name("properties").beginArray();
        for (PropertyShape property : block.properties()) {
            json.beginObject(); json.name("index").value(property.index()); json.name("name").value(property.name());
            json.name("propertyClass").value(property.propertyClass()); json.name("valueClass").value(property.valueClass());
            json.name("values").beginArray();
            for (Value value : property.values()) {
                json.beginObject(); json.name("index").value(value.index()); writeValue(json, value.name(), value.text(), value.valueClass()); json.endObject();
            }
            json.endArray(); json.endObject();
        }
        json.endArray(); json.name("defaultValues").beginArray();
        for (DefaultValue value : block.defaultValues()) {
            json.beginObject(); json.name("property").value(value.property()); json.name("valueIndex").value(value.valueIndex());
            writeValue(json, value.name(), value.text(), value.valueClass()); json.endObject();
        }
        json.endArray(); json.endObject();
    }

    private static void writeValue(JsonWriter json, String name, String text, String valueClass) throws IOException {
        json.name("name").value(name); json.name("text").value(text); json.name("valueClass").value(valueClass);
    }

    /** Keep even custom strings' unpaired UTF-16 code units exact through UTF-8 JSON. */
    private static final class ExactJsonWriter extends FilterWriter {
        private static final char[] HEX = "0123456789abcdef".toCharArray();
        ExactJsonWriter(Writer writer) { super(writer); }
        @Override public void write(int value) throws IOException {
            if (value < 128) out.write(value);
            else out.write(new char[] {'\\', 'u', HEX[(value >>> 12) & 15], HEX[(value >>> 8) & 15], HEX[(value >>> 4) & 15], HEX[value & 15]});
        }
        @Override public void write(char[] chars, int offset, int length) throws IOException { for (int i = offset; i < offset + length; i++) write(chars[i]); }
        @Override public void write(String text, int offset, int length) throws IOException { for (int i = offset; i < offset + length; i++) write(text.charAt(i)); }
    }
}
