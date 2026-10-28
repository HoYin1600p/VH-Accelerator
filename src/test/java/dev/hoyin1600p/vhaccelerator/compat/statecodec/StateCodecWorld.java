package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * One synthetic block definition built through the real vanilla {@code StateDefinition} constructor, together
 * with the lazy codec built for the same properties. {@link #vanilla} is the eager chain vanilla gave the
 * states; {@link #lazy} is what the mixin installs in its place.
 */
final class StateCodecWorld {
    static final class Owner {
        final String name;
        volatile StateDefinition<Owner, State> definition;

        Owner(String name) {
            this.name = name;
        }

        State defaultState() {
            return definition.any();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final class State extends StateHolder<Owner, State> {
        State(Owner owner, ImmutableMap<Property<?>, Comparable<?>> values, MapCodec<State> codec) {
            super(owner, values, codec);
        }

        static Codec<State> codec(Codec<Owner> ownerCodec) {
            return StateHolder.codec(ownerCodec, Owner::defaultState);
        }
    }

    final Owner owner;
    final List<Property<?>> properties;
    final MapCodec<State> vanilla;
    final MapCodec<State> lazy;
    final StateDefinition<Owner, State> definition;
    /** Full state codec ({@code Name} plus {@code Properties}) whose states carry the chosen property codec. */
    final Codec<State> stateCodec;

    private StateCodecWorld(Owner owner, List<Property<?>> properties, MapCodec<State> vanilla, MapCodec<State> lazy,
            Codec<State> stateCodec) {
        this.owner = owner;
        this.properties = properties;
        this.vanilla = vanilla;
        this.lazy = lazy;
        this.definition = owner.definition;
        this.stateCodec = stateCodec;
    }

    /** {@code useLazy} selects which property codec the states keep; both are always built for comparison. */
    static StateCodecWorld build(String name, List<Property<?>> properties, boolean useLazy) {
        Owner owner = new Owner(name);
        Supplier<State> supplier = owner::defaultState;
        List<Property<?>> sorted = new ArrayList<>(properties);
        sorted.sort(Comparator.comparing(Property::getName));
        MapCodec<State> lazy = LazyStateCodec.base(supplier);
        for (Property<?> property : sorted) {
            lazy = appendCaptured(lazy, supplier, property);
        }
        MapCodec<State> chosenLazy = lazy;
        MapCodec<State>[] vanilla = new MapCodec[1];
        StateDefinition.Builder<Owner, State> builder = new StateDefinition.Builder<>(owner);
        builder.add(properties.toArray(new Property<?>[0]));
        owner.definition = builder.create(Owner::defaultState, (o, values, codec) -> {
            vanilla[0] = codec;
            return new State(o, values, useLazy ? chosenLazy : codec);
        });
        Codec<Owner> ownerCodec = Codec.STRING.xmap(key -> owner, o -> o.name);
        MapCodec<State> vanillaChain = vanilla[0];
        if (vanillaChain == null) {
            throw new IllegalStateException("no states were created for " + name);
        }
        return new StateCodecWorld(owner, sorted, vanillaChain, lazy, State.codec(ownerCodec));
    }

    private static <T extends Comparable<T>> MapCodec<State> appendCaptured(MapCodec<State> previous,
            Supplier<State> supplier, Property<T> property) {
        return LazyStateCodec.append(previous, supplier, property.getName(), property, true);
    }
}
