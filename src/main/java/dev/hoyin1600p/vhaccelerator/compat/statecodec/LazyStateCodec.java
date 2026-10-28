package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.KeyCompressor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The per-definition property codec of a block or fluid state, built on first use instead of in the
 * {@code StateDefinition} constructor.
 *
 * <p>Vanilla builds one codec chain per {@code StateDefinition}: an empty base, then for each property a
 * field, a default, a pair and an {@code xmap}, about 15 small objects and four lambdas per property. Every
 * state of the definition keeps it in {@code propertiesCodec}, though only {@code BlockState.CODEC} and
 * {@code FluidState.CODEC} ever read it. A large Vault Hunters pack has 22,925 definitions and 71,013
 * properties, which is about 52 MB of heap on a server that reads a small share of them.</p>
 *
 * <p>This object records the supplier and the (name, property) pairs in vanilla's order and builds the real
 * chain with {@link #vanillaStep}, which is vanilla's {@code appendPropertyCodec} written out unchanged. Every
 * {@code MapCodec} operation then delegates to that real chain, so encoding, decoding, key lists, the
 * compressed forms and error messages are the ones the eager chain produces.</p>
 *
 * <p>Thread safety: the chain is built from immutable inputs and only calls code that does not touch the
 * definition's states. Racing threads may each build a chain; exactly one is published (under this object's
 * monitor, with no foreign code run inside it) through a volatile field, and every caller returns the published
 * one. The chains are interchangeable, so the redundant build is the only cost of a race.</p>
 */
public final class LazyStateCodec<S extends StateHolder<?, S>> extends MapCodec<S> {
    /** The mixin whose activation gates this class. */
    public static final String MIXIN = StateCodecLayouts.MIXIN;
    private static final Object[] NONE = new Object[0];

    private final Supplier<S> supplier;
    /** name0, property0, name1, property1, ... in vanilla's (sorted by name) order; emptied once built. */
    private volatile Object[] steps;
    private volatile MapCodec<S> built;

    private LazyStateCodec(Supplier<S> supplier, String name, Property<?> property) {
        this.supplier = supplier;
        this.steps = new Object[]{name, property};
    }

    /**
     * Replacement for the {@code appendPropertyCodec} call in the {@code StateDefinition} constructor. The
     * first property turns the (discarded) empty base into a lazy codec, later ones add to it; anything the
     * lazy path does not accept runs vanilla's step on the real codec, so the result is the eager one.
     */
    public static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> append(
            MapCodec<S> previous, Supplier<S> supplier, String name, Property<T> property) {
        return append(previous, supplier, name, property,
                previous instanceof LazyStateCodec || CompatMixinGroups.activationAllowed(MIXIN));
    }

    /** As {@link #append(MapCodec, Supplier, String, Property)} with the activation decision given. */
    public static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> append(
            MapCodec<S> previous, Supplier<S> supplier, String name, Property<T> property, boolean lazyAllowed) {
        if (previous instanceof LazyStateCodec<S> lazy) {
            if (property.valueCodec() != null) {
                lazy.add(name, property);
                return lazy;
            }
            return vanillaStep(lazy.resolve(), supplier, name, property);
        }
        if (property.valueCodec() == null || !lazyAllowed) {
            return vanillaStep(previous, supplier, name, property);
        }
        return new LazyStateCodec<>(supplier, name, property);
    }

    /**
     * Vanilla 1.18.2 {@code StateDefinition.appendPropertyCodec}, statement for statement: the codec of the
     * previous properties paired with this property's field (defaulting to the property's value in the
     * definition's default state), mapped to and from the state.
     */
    public static <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> vanillaStep(
            MapCodec<S> previous, Supplier<S> supplier, String name, Property<T> property) {
        return Codec.mapPair(previous, property.valueCodec().fieldOf(name).orElseGet(error -> { },
                        () -> property.value(supplier.get())))
                .xmap(pair -> pair.getFirst().setValue(property, pair.getSecond().value()),
                        state -> Pair.of(state, property.value(state)));
    }

    /** Vanilla's empty base codec: {@code MapCodec.of(Encoder.empty(), Decoder.unit(supplier))}. */
    public static <S> MapCodec<S> base(Supplier<S> supplier) {
        return MapCodec.of(Encoder.empty(), Decoder.unit(supplier));
    }

    /** Whether the real chain exists yet (tests and diagnostics). */
    public boolean isBuilt() {
        return built != null;
    }

    /** The number of properties recorded in vanilla's order. */
    public int propertyCount() {
        Object[] pending = steps;
        return pending.length / 2;
    }

    private void add(String name, Property<?> property) {
        Object[] current = steps;
        Object[] grown = java.util.Arrays.copyOf(current, current.length + 2);
        grown[current.length] = name;
        grown[current.length + 1] = property;
        steps = grown;
    }

    /** The real chain, building and publishing it on first use. */
    public MapCodec<S> resolve() {
        MapCodec<S> codec = built;
        if (codec != null) {
            return codec;
        }
        MapCodec<S> candidate = build();
        synchronized (this) {
            codec = built;
            if (codec == null) {
                built = candidate;
                steps = NONE;
                codec = candidate;
            }
        }
        return codec;
    }

    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> MapCodec<S> build() {
        Object[] pending = steps;
        MapCodec<S> codec = base(supplier);
        for (int index = 0; index + 1 < pending.length; index += 2) {
            codec = vanillaStep(codec, supplier, (String) pending[index], (Property<T>) pending[index + 1]);
        }
        return codec;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return resolve().keys(ops);
    }

    @Override
    public <T> DataResult<S> decode(DynamicOps<T> ops, MapLike<T> input) {
        return resolve().decode(ops, input);
    }

    @Override
    public <T> RecordBuilder<T> encode(S input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return resolve().encode(input, ops, prefix);
    }

    @Override
    public <T> KeyCompressor<T> compressor(DynamicOps<T> ops) {
        return resolve().compressor(ops);
    }

    @Override
    public <T> RecordBuilder<T> compressedBuilder(DynamicOps<T> ops) {
        return resolve().compressedBuilder(ops);
    }

    @Override
    public <T> DataResult<S> compressedDecode(DynamicOps<T> ops, T input) {
        return resolve().compressedDecode(ops, input);
    }

    @Override
    public String toString() {
        return resolve().toString();
    }
}
