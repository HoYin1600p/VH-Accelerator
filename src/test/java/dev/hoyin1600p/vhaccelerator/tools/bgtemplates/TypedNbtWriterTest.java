package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.StringWriter;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

final class TypedNbtWriterTest {
    static JsonElement encode(Tag tag) throws Exception {
        StringWriter text = new StringWriter();
        try (JsonWriter output = new JsonWriter(text)) { TypedNbtWriter.write(output, tag); }
        return JsonParser.parseString(text.toString());
    }

    @Test void allTagTypesKeepNumericWidthsArrayOrderAndRawFloatingBits() throws Exception {
        CompoundTag tags = new CompoundTag();
        tags.put("byte", ByteTag.valueOf((byte) -128));
        tags.put("short", ShortTag.valueOf((short) -32768));
        tags.putInt("int", Integer.MIN_VALUE);
        tags.putLong("long", Long.MIN_VALUE);
        tags.put("float", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
        tags.put("double", DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L)));
        tags.put("bytes", new ByteArrayTag(new byte[]{-128, 127}));
        tags.putString("string", "unknown\n\u0000tag");
        ListTag list = new ListTag();
        list.add(LongTag.valueOf(Long.MAX_VALUE));
        list.add(LongTag.valueOf(-1));
        tags.put("list", list);
        tags.put("compound", new CompoundTag());
        tags.put("ints", new IntArrayTag(new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}));
        tags.put("longs", new LongArrayTag(new long[]{Long.MAX_VALUE, Long.MIN_VALUE}));
        assertEquals(dev.hoyin1600p.vhaccelerator.diagnostics.TypedNbtJson.encode(tags), encode(tags));
        assertEquals("7fc00001", encode(tags.get("float")).getAsJsonObject().get("bits").getAsString());
        assertEquals("7ff8000000000001", encode(tags.get("double")).getAsJsonObject().get("bits").getAsString());
        assertEquals(0, encode(EndTag.INSTANCE).getAsJsonObject().get("type").getAsInt());
    }

    @Test void negativeZeroInfinitiesAndDistinctNanPayloadsStayDistinct() throws Exception {
        assertNotEquals(encode(FloatTag.valueOf(0.0f)), encode(TypedNbtWriter.rawFloat(0x80000000)));
        assertNotEquals(encode(DoubleTag.valueOf(0.0)), encode(TypedNbtWriter.rawDouble(0x8000000000000000L)));
        assertNotEquals(encode(FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001))),
                encode(FloatTag.valueOf(Float.intBitsToFloat(0x7fc00002))));
        assertNotEquals(encode(DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L))),
                encode(DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000002L))));
        assertEquals("7f800000", encode(FloatTag.valueOf(Float.POSITIVE_INFINITY)).getAsJsonObject().get("bits").getAsString());
        assertEquals("fff0000000000000", encode(DoubleTag.valueOf(Double.NEGATIVE_INFINITY)).getAsJsonObject().get("bits").getAsString());
    }

    @Test void compoundKeysOnlyAreSortedAndEmptyListElementTypeIsKept() throws Exception {
        CompoundTag first = new CompoundTag(); first.putInt("z", 2); first.putInt("a", 1);
        CompoundTag second = new CompoundTag(); second.putInt("a", 1); second.putInt("z", 2);
        assertEquals(encode(first).toString(), encode(second).toString());
        ListTag forward = new ListTag(); forward.add(IntTag.valueOf(1)); forward.add(IntTag.valueOf(2));
        ListTag reverse = new ListTag(); reverse.add(IntTag.valueOf(2)); reverse.add(IntTag.valueOf(1));
        assertNotEquals(encode(forward), encode(reverse));
        var empty = encode(new ListTag()).getAsJsonObject();
        assertEquals(0, empty.get("elementType").getAsInt());
        assertEquals(0, empty.getAsJsonArray("value").size());
    }
}
