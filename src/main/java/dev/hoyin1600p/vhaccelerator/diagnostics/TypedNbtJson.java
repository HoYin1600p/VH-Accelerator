package dev.hoyin1600p.vhaccelerator.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.TreeSet;
import net.minecraft.nbt.*;

/** Lossless typed NBT view. Compound keys have no order; lists and arrays do. */
public final class TypedNbtJson {
    private TypedNbtJson() { }

    public static JsonObject encode(Tag tag) {
        JsonObject result = new JsonObject();
        result.addProperty("type", tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE, Tag.TAG_SHORT, Tag.TAG_INT, Tag.TAG_LONG ->
                    result.addProperty("value", ((NumericTag) tag).getAsNumber().toString());
            case Tag.TAG_FLOAT -> result.addProperty("bits",
                    Integer.toUnsignedString(Float.floatToRawIntBits(((FloatTag) tag).getAsFloat()), 16));
            case Tag.TAG_DOUBLE -> result.addProperty("bits",
                    Long.toUnsignedString(Double.doubleToRawLongBits(((DoubleTag) tag).getAsDouble()), 16));
            case Tag.TAG_STRING -> result.addProperty("value", tag.getAsString());
            case Tag.TAG_COMPOUND -> {
                JsonObject values = new JsonObject();
                CompoundTag compound = (CompoundTag) tag;
                for (String key : new TreeSet<>(compound.getAllKeys())) {
                    values.add(key, encode(compound.get(key)));
                }
                result.add("value", values);
            }
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                result.addProperty("elementType", list.getElementType());
                JsonArray values = new JsonArray();
                for (Tag value : list) values.add(encode(value));
                result.add("value", values);
            }
            case Tag.TAG_BYTE_ARRAY -> {
                JsonArray values = new JsonArray();
                for (byte value : ((ByteArrayTag) tag).getAsByteArray()) values.add(value);
                result.add("value", values);
            }
            case Tag.TAG_INT_ARRAY -> {
                JsonArray values = new JsonArray();
                for (int value : ((IntArrayTag) tag).getAsIntArray()) values.add(value);
                result.add("value", values);
            }
            case Tag.TAG_LONG_ARRAY -> {
                JsonArray values = new JsonArray();
                for (long value : ((LongArrayTag) tag).getAsLongArray()) values.add(Long.toString(value));
                result.add("value", values);
            }
            default -> throw new IllegalArgumentException("Unsupported NBT type " + tag.getId());
        }
        return result;
    }
}
