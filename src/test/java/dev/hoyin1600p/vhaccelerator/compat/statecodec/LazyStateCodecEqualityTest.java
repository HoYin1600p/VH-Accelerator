package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecWorld.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

/**
 * The lazy codec against the eager chain vanilla builds for the same definition: same encoded forms, same
 * decoded states, same error messages for bad data, over many synthetic definitions with every property type.
 */
class LazyStateCodecEqualityTest {
    enum Shape implements StringRepresentable {
        FLAT, ROUND, SQUARE_EDGE, TALL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    enum Tint implements StringRepresentable {
        RED, GREEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final List<DynamicOps<?>> OPS = List.of(NbtOps.INSTANCE, JsonOps.INSTANCE, JsonOps.COMPRESSED);

    /** DFU's lambdas print their identity hash; everything else in the text must match. */
    static String text(Object value) {
        return String.valueOf(value).replaceAll("@[0-9a-f]+", "@");
    }

    static String describe(DataResult<?> result) {
        return text(result.get().map(value -> "OK:" + value, partial -> "ERR:" + partial.message()
                + "|partial:" + result.resultOrPartial(message -> { }).orElse(null)));
    }

    private static List<Property<?>> randomProperties(Random random, int count, int salt) {
        List<Property<?>> properties = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String name = "p" + salt + "_" + index + (random.nextBoolean() ? "x" : "");
            switch (random.nextInt(4)) {
                case 0 -> properties.add(BooleanProperty.create(name));
                case 1 -> {
                    int min = random.nextInt(3);
                    properties.add(IntegerProperty.create(name, min, min + 1 + random.nextInt(5)));
                }
                case 2 -> properties.add(EnumProperty.create(name, Shape.class));
                default -> properties.add(EnumProperty.create(name, Tint.class));
            }
        }
        return properties;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertCodecsEqual(StateCodecWorld world) {
        for (DynamicOps ops : OPS) {
            for (State state : world.definition.getPossibleStates()) {
                Object eager = world.vanilla.encode(state, ops, ops.mapBuilder()).build(ops.empty());
                Object lazy = world.lazy.encode(state, ops, ops.mapBuilder()).build(ops.empty());
                assertEquals(describe((DataResult) eager), describe((DataResult) lazy), state + " " + ops);
                Object eagerCompressed = world.vanilla.encode(state, ops, world.vanilla.compressedBuilder(ops)).build(ops.empty());
                Object lazyCompressed = world.lazy.encode(state, ops, world.lazy.compressedBuilder(ops)).build(ops.empty());
                assertEquals(describe((DataResult) eagerCompressed), describe((DataResult) lazyCompressed));
                for (MapCodec<State> codec : List.of(world.vanilla, world.lazy)) {
                    Object encoded = codec.encode(state, ops, ops.mapBuilder()).build(ops.empty());
                    Object value = ((DataResult) encoded).result().orElseThrow();
                    MapLike map = (MapLike) ops.getMap(value).result().orElseThrow();
                    DataResult decoded = codec.decode(ops, map);
                    assertSame(state, decoded.result().orElseThrow(), "round trip of " + state);
                }
            }
            assertEquals(world.vanilla.keys(ops).map(Object::toString).collect(Collectors.toList()),
                    world.lazy.keys(ops).map(Object::toString).collect(Collectors.toList()));
        }
        assertEquals(text(world.vanilla), text(world.lazy));
    }

    /** Decodes an input with both chains and requires the same state or the same message. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertDecodeEqual(StateCodecWorld world, Tag input) {
        DataResult eager = world.vanilla.compressedDecode(NbtOps.INSTANCE, input);
        DataResult lazy = world.lazy.compressedDecode(NbtOps.INSTANCE, input);
        assertEquals(describe(eager), describe(lazy), "input " + input);
        DataResult eagerCodec = world.vanilla.codec().parse(NbtOps.INSTANCE, input);
        DataResult lazyCodec = world.lazy.codec().parse(NbtOps.INSTANCE, input);
        assertEquals(describe(eagerCodec), describe(lazyCodec), "input " + input);
    }

    private static void assertBadDataEqual(StateCodecWorld world, Random random) {
        List<Tag> inputs = new ArrayList<>();
        inputs.add(new CompoundTag());
        inputs.add(StringTag.valueOf("not a map"));
        inputs.add(IntTag.valueOf(3));
        for (State state : world.definition.getPossibleStates()) {
            Tag encoded = world.vanilla.encode(state, NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                    .build(NbtOps.INSTANCE.empty()).result().orElseThrow();
            inputs.add(encoded);
            if (encoded instanceof CompoundTag tag && !tag.getAllKeys().isEmpty()) {
                List<String> keys = new ArrayList<>(tag.getAllKeys());
                String key = keys.get(random.nextInt(keys.size()));
                CompoundTag missing = tag.copy();
                missing.remove(key);
                inputs.add(missing);
                CompoundTag badValue = tag.copy();
                badValue.putString(key, "no_such_value");
                inputs.add(badValue);
                CompoundTag wrongType = tag.copy();
                wrongType.putInt(key, 99);
                inputs.add(wrongType);
                CompoundTag extra = tag.copy();
                extra.putString("unknown_property", "x");
                inputs.add(extra);
                CompoundTag shouting = tag.copy();
                shouting.putString(key, "TRUE");
                inputs.add(shouting);
            }
            if (inputs.size() > 60) break;
        }
        for (Tag input : inputs) {
            assertDecodeEqual(world, input);
        }
    }

    @Test
    void synthetic_definitions_encode_and_decode_identically() {
        Random random = new Random(18_2_40_3_11L);
        for (int index = 0; index < 400; index++) {
            int count = random.nextInt(7);
            List<Property<?>> properties = randomProperties(random, count, index);
            long states = 1;
            for (Property<?> property : properties) states *= property.getPossibleValues().size();
            if (states > 600) {
                properties = properties.subList(0, Math.min(properties.size(), 3));
            }
            StateCodecWorld world = StateCodecWorld.build("test:block_" + index, properties, index % 2 == 0);
            assertCodecsEqual(world);
            assertBadDataEqual(world, random);
        }
    }

    @Test
    void the_full_state_codec_agrees_whichever_chain_the_states_keep() {
        Random random = new Random(7);
        for (int index = 0; index < 60; index++) {
            List<Property<?>> properties = randomProperties(random, 1 + random.nextInt(4), 1000 + index);
            StateCodecWorld eager = StateCodecWorld.build("test:full", properties, false);
            StateCodecWorld lazy = StateCodecWorld.build("test:full", properties, true);
            for (DynamicOps<?> ops : OPS) {
                for (int state = 0; state < eager.definition.getPossibleStates().size(); state++) {
                    State a = eager.definition.getPossibleStates().get(state);
                    State b = lazy.definition.getPossibleStates().get(state);
                    assertEquals(a.toString(), b.toString());
                    assertEquals(describe(encode(eager.stateCodec, a, ops)), describe(encode(lazy.stateCodec, b, ops)));
                    Object encoded = encode(eager.stateCodec, a, ops).result().orElseThrow();
                    assertEquals(describe(parse(eager.stateCodec, encoded, ops)),
                            describe(parse(lazy.stateCodec, encoded, ops)));
                }
            }
            // The NBT form of a block state: a Name string and a Properties compound.
            CompoundTag withBadProperties = new CompoundTag();
            withBadProperties.putString("Name", "test:full");
            CompoundTag bad = new CompoundTag();
            bad.putString(properties.get(0).getName(), "definitely_not_valid");
            withBadProperties.put("Properties", bad);
            assertEquals(describe(parse(eager.stateCodec, withBadProperties, NbtOps.INSTANCE)),
                    describe(parse(lazy.stateCodec, withBadProperties, NbtOps.INSTANCE)));
            CompoundTag noProperties = new CompoundTag();
            noProperties.putString("Name", "test:full");
            assertEquals(describe(parse(eager.stateCodec, noProperties, NbtOps.INSTANCE)),
                    describe(parse(lazy.stateCodec, noProperties, NbtOps.INSTANCE)));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult encode(Codec codec, Object state, DynamicOps ops) {
        return codec.encodeStart(ops, state);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataResult parse(Codec codec, Object input, DynamicOps ops) {
        return codec.parse(ops, input);
    }

    @Test
    void lazy_codec_is_built_once_on_first_use_and_not_before() {
        StateCodecWorld world = StateCodecWorld.build("test:once", List.of(BooleanProperty.create("lit"),
                IntegerProperty.create("level", 0, 3)), true);
        LazyStateCodec<State> lazy = (LazyStateCodec<State>) world.lazy;
        assertFalse(lazy.isBuilt());
        assertEquals(2, lazy.propertyCount());
        MapCodec<State> first = lazy.resolve();
        assertTrue(lazy.isBuilt());
        assertSame(first, lazy.resolve());
        assertEquals(0, lazy.propertyCount(), "the recorded steps are released after the build");
    }

    @Test
    void zero_property_definitions_keep_vanillas_plain_base_codec() {
        StateCodecWorld world = StateCodecWorld.build("test:plain", List.of(), true);
        assertFalse(world.lazy instanceof LazyStateCodec);
        assertCodecsEqual(world);
    }

    @Test
    void a_bad_value_is_handled_the_same_way_as_by_the_eager_chain() {
        StateCodecWorld world = StateCodecWorld.build("test:msg", List.of(EnumProperty.create("shape", Shape.class)), true);
        CompoundTag bad = new CompoundTag();
        bad.putString("shape", "triangle");
        DataResult<State> eager = world.vanilla.compressedDecode(NbtOps.INSTANCE, bad);
        DataResult<State> lazy = world.lazy.compressedDecode(NbtOps.INSTANCE, bad);
        // Vanilla's optional field swallows a bad value and keeps the default; the lazy chain must too.
        assertEquals(describe(eager), describe(lazy));
        assertEquals(eager.result().isPresent(), lazy.result().isPresent());
        // A non-map input is a real error with a message.
        DataResult<State> eagerError = world.vanilla.compressedDecode(NbtOps.INSTANCE, StringTag.valueOf("x"));
        DataResult<State> lazyError = world.lazy.compressedDecode(NbtOps.INSTANCE, StringTag.valueOf("x"));
        assertTrue(eagerError.error().isPresent());
        assertEquals(eagerError.error().orElseThrow().message(), lazyError.error().orElseThrow().message());
    }

    @Test
    void json_round_trip_of_a_real_looking_block() {
        StateCodecWorld world = StateCodecWorld.build("test:wall", List.of(BooleanProperty.create("up"),
                BooleanProperty.create("waterlogged"), EnumProperty.create("north", Shape.class),
                EnumProperty.create("east", Shape.class), EnumProperty.create("south", Shape.class),
                EnumProperty.create("west", Shape.class)), true);
        assertEquals(2 * 2 * 4 * 4 * 4 * 4, world.definition.getPossibleStates().size());
        for (State state : world.definition.getPossibleStates()) {
            JsonElement eager = world.vanilla.encode(state, JsonOps.INSTANCE, JsonOps.INSTANCE.mapBuilder())
                    .build(JsonOps.INSTANCE.empty()).result().orElseThrow();
            JsonElement lazy = world.lazy.encode(state, JsonOps.INSTANCE, JsonOps.INSTANCE.mapBuilder())
                    .build(JsonOps.INSTANCE.empty()).result().orElseThrow();
            assertEquals(eager, lazy);
        }
    }
}
