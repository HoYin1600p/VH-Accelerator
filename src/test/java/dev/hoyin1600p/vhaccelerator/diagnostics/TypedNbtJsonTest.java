package dev.hoyin1600p.vhaccelerator.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

final class TypedNbtJsonTest {
    @Test
    void preservesNumericTypesAndLongPrecision() {
        assertNotEquals(TypedNbtJson.encode(ByteTag.valueOf((byte) 1)),
                TypedNbtJson.encode(IntTag.valueOf(1)));
        assertEquals("9223372036854775807", TypedNbtJson.encode(LongTag.valueOf(Long.MAX_VALUE))
                .get("value").getAsString());
        assertNotEquals(TypedNbtJson.encode(FloatTag.valueOf(1.0f)),
                TypedNbtJson.encode(FloatTag.valueOf(1.0001f)));
    }

    @Test
    void canonicalizesOnlyCompoundKeys() {
        CompoundTag first = new CompoundTag();
        first.putInt("b", 2);
        first.putInt("a", 1);
        CompoundTag second = new CompoundTag();
        second.putInt("a", 1);
        second.putInt("b", 2);
        assertEquals(TypedNbtJson.encode(first), TypedNbtJson.encode(second));
        ListTag forward = new ListTag();
        forward.add(IntTag.valueOf(1));
        forward.add(IntTag.valueOf(2));
        ListTag reverse = new ListTag();
        reverse.add(IntTag.valueOf(2));
        reverse.add(IntTag.valueOf(1));
        assertNotEquals(TypedNbtJson.encode(forward), TypedNbtJson.encode(reverse));
        assertNotEquals(TypedNbtJson.encode(new ByteArrayTag(new byte[]{1, 2})),
                TypedNbtJson.encode(new IntArrayTag(new int[]{1, 2})));
    }

    @Test
    void preservesNestedNbtCountsAndArrayOrder() {
        CompoundTag first = new CompoundTag();
        first.putByte("Count", (byte) 2);
        first.put("nested", new LongArrayTag(new long[]{Long.MAX_VALUE, 1}));
        CompoundTag differentCount = first.copy();
        differentCount.putByte("Count", (byte) 1);
        assertNotEquals(TypedNbtJson.encode(first), TypedNbtJson.encode(differentCount));
        CompoundTag differentOrder = first.copy();
        differentOrder.put("nested", new LongArrayTag(new long[]{1, Long.MAX_VALUE}));
        assertNotEquals(TypedNbtJson.encode(first), TypedNbtJson.encode(differentOrder));
    }
}
