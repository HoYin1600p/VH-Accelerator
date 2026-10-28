package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.DataInput;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;
import net.minecraft.nbt.*;

/** Streaming counterpart of the typed NBT schema; only compound key order is canonicalized. */
public final class TypedNbtWriter {
    private TypedNbtWriter() { }
    private static final int MAX_ARRAY_ELEMENTS = 32_000_000;
    private static final MethodHandle FLOAT_CONSTRUCTOR = primitiveConstructor(FloatTag.class, float.class);
    private static final MethodHandle DOUBLE_CONSTRUCTOR = primitiveConstructor(DoubleTag.class, double.class);

    /** JSON syntax is ASCII, so escaping every non-ASCII UTF-16 unit preserves even lone surrogates. */
    public static Writer asciiJson(Writer target) {
        return new Writer() {
            private final char[] hex = "0123456789abcdef".toCharArray();
            @Override public void write(char[] values, int offset, int length) throws IOException {
                int start = offset;
                for (int index = offset; index < offset + length; index++) {
                    char value = values[index];
                    if (value < 128) continue;
                    if (index > start) target.write(values, start, index - start);
                    target.write(new char[]{'\\', 'u', hex[value >>> 12], hex[value >>> 8 & 15],
                            hex[value >>> 4 & 15], hex[value & 15]});
                    start = index + 1;
                }
                if (offset + length > start) target.write(values, start, offset + length - start);
            }
            @Override public void flush() throws IOException { target.flush(); }
            @Override public void close() throws IOException { target.close(); }
        };
    }

    public record NamedRoot(String name, CompoundTag data) { }

    /** Standard NBT framing, independently read so vanilla's numeric zero caches cannot erase -0 bits. */
    public static NamedRoot readRoot(DataInput input) throws IOException {
        if (input.readUnsignedByte() != Tag.TAG_COMPOUND) throw new IOException("Saved-data root must be a compound");
        String name = input.readUTF();
        return new NamedRoot(name, (CompoundTag) readPayload(input, Tag.TAG_COMPOUND, 0));
    }

    private static MethodHandle primitiveConstructor(Class<?> owner, Class<?> primitive) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                    .findConstructor(owner, MethodType.methodType(void.class, primitive));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Lossless NBT primitive constructor unavailable", failure);
        }
    }

    static FloatTag rawFloat(int bits) {
        try { return (FloatTag) FLOAT_CONSTRUCTOR.invokeExact(Float.intBitsToFloat(bits)); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot preserve NBT float bits", failure); }
    }

    static DoubleTag rawDouble(long bits) {
        try { return (DoubleTag) DOUBLE_CONSTRUCTOR.invokeExact(Double.longBitsToDouble(bits)); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot preserve NBT double bits", failure); }
    }

    private static int length(DataInput input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > MAX_ARRAY_ELEMENTS) throw new IOException("NBT collection length exceeds the supported safety bound");
        return size;
    }

    private static Tag readPayload(DataInput input, int kind, int depth) throws IOException {
        if (depth > 512) throw new IOException("NBT nesting is too deep");
        return switch (kind) {
            case Tag.TAG_END -> EndTag.INSTANCE;
            case Tag.TAG_BYTE -> ByteTag.valueOf(input.readByte());
            case Tag.TAG_SHORT -> ShortTag.valueOf(input.readShort());
            case Tag.TAG_INT -> IntTag.valueOf(input.readInt());
            case Tag.TAG_LONG -> LongTag.valueOf(input.readLong());
            case Tag.TAG_FLOAT -> rawFloat(input.readInt());
            case Tag.TAG_DOUBLE -> rawDouble(input.readLong());
            case Tag.TAG_STRING -> StringTag.valueOf(input.readUTF());
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = new byte[length(input)]; input.readFully(values); yield new ByteArrayTag(values);
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = new int[length(input)];
                for (int index = 0; index < values.length; index++) values[index] = input.readInt();
                yield new IntArrayTag(values);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = new long[length(input)];
                for (int index = 0; index < values.length; index++) values[index] = input.readLong();
                yield new LongArrayTag(values);
            }
            case Tag.TAG_LIST -> {
                int elementType = input.readUnsignedByte();
                int size = length(input);
                if (elementType > 12 || elementType == Tag.TAG_END && size != 0) throw new IOException("Invalid NBT list element type");
                ArrayList<Tag> values = new ArrayList<>(Math.min(size, 1024));
                for (int index = 0; index < size; index++) values.add(readPayload(input, elementType, depth + 1));
                // The constructor preserves the declared element type even for an empty list.
                yield listWithType(values, (byte) elementType);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = new CompoundTag();
                HashSet<String> names = new HashSet<>();
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == Tag.TAG_END) break;
                    if (childType > 12) throw new IOException("Unknown NBT tag type");
                    String name = input.readUTF();
                    if (!names.add(name)) throw new IOException("Duplicate NBT compound key");
                    compound.put(name, readPayload(input, childType, depth + 1));
                }
                yield compound;
            }
            default -> throw new IOException("Unknown NBT tag type: " + kind);
        };
    }

    private static ListTag listWithType(java.util.List<Tag> values, byte type) throws IOException {
        try {
            var constructor = ListTag.class.getDeclaredConstructor(java.util.List.class, byte.class);
            constructor.setAccessible(true);
            return constructor.newInstance(values, type);
        } catch (ReflectiveOperationException failure) { throw new IOException("Cannot preserve NBT list element type", failure); }
    }


    public static void write(JsonWriter output, Tag tag) throws IOException {
        output.beginObject().name("type").value(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG ->
                    output.name("value").value(((NumericTag) tag).getAsNumber().toString());
            case Tag.TAG_FLOAT -> output.name("bits").value(Integer.toUnsignedString(
                    Float.floatToRawIntBits(((FloatTag) tag).getAsFloat()), 16));
            case Tag.TAG_DOUBLE -> output.name("bits").value(Long.toUnsignedString(
                    Double.doubleToRawLongBits(((DoubleTag) tag).getAsDouble()), 16));
            case Tag.TAG_STRING -> output.name("value").value(tag.getAsString());
            case Tag.TAG_COMPOUND -> {
                output.name("value").beginObject();
                CompoundTag compound = (CompoundTag) tag;
                for (String key : new TreeSet<>(compound.getAllKeys())) {
                    output.name(key);
                    write(output, compound.get(key));
                }
                output.endObject();
            }
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                output.name("elementType").value(list.getElementType()).name("value").beginArray();
                for (Tag value : list) write(output, value);
                output.endArray();
            }
            case Tag.TAG_BYTE_ARRAY -> {
                output.name("value").beginArray();
                for (byte value : ((ByteArrayTag) tag).getAsByteArray()) output.value(value);
                output.endArray();
            }
            case Tag.TAG_INT_ARRAY -> {
                output.name("value").beginArray();
                for (int value : ((IntArrayTag) tag).getAsIntArray()) output.value(value);
                output.endArray();
            }
            case Tag.TAG_LONG_ARRAY -> {
                output.name("value").beginArray();
                for (long value : ((LongArrayTag) tag).getAsLongArray()) output.value(Long.toString(value));
                output.endArray();
            }
            default -> throw new IllegalArgumentException("Unsupported NBT type: " + tag.getId());
        }
        output.endObject();
    }
}
