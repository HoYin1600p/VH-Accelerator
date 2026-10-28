package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecWorld.State;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;

/** Concurrency, fallback and activation behaviour of {@link LazyStateCodec}. */
class LazyStateCodecBehaviorTest {
    private static List<Property<?>> sampleProperties() {
        return List.of(BooleanProperty.create("lit"), IntegerProperty.create("level", 0, 5),
                BooleanProperty.create("waterlogged"));
    }

    @Test
    void racing_threads_get_one_published_chain_and_vanillas_results() throws Exception {
        for (int round = 0; round < 40; round++) {
            StateCodecWorld world = StateCodecWorld.build("test:race_" + round, sampleProperties(), true);
            LazyStateCodec<State> lazy = (LazyStateCodec<State>) world.lazy;
            int threads = 12;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<List<Object>>> futures = new ArrayList<>();
                for (int thread = 0; thread < threads; thread++) {
                    final int id = thread;
                    futures.add(pool.submit(() -> {
                        start.await();
                        List<Object> seen = new ArrayList<>();
                        for (State state : world.definition.getPossibleStates()) {
                            // Alternate entry points so the first caller differs between threads.
                            Object encoded = (id % 2 == 0 ? lazy.codec().encodeStart(NbtOps.INSTANCE, state)
                                    : lazy.encode(state, NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                                            .build(NbtOps.INSTANCE.empty()));
                            seen.add(LazyStateCodecEqualityTest.describe((DataResult<?>) encoded));
                        }
                        seen.add(lazy.resolve());
                        return seen;
                    }));
                }
                start.countDown();
                List<List<Object>> results = new ArrayList<>();
                for (Future<List<Object>> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
                Object published = lazy.resolve();
                List<String> expected = new ArrayList<>();
                for (State state : world.definition.getPossibleStates()) {
                    expected.add(LazyStateCodecEqualityTest.describe(
                            world.vanilla.encode(state, NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                                    .build(NbtOps.INSTANCE.empty())));
                }
                for (List<Object> result : results) {
                    assertSame(published, result.get(result.size() - 1), "every thread sees the one published chain");
                    assertEquals(expected, result.subList(0, result.size() - 1));
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void definitions_built_in_parallel_do_not_share_recorded_steps() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<StateCodecWorld>> futures = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                final int id = index;
                futures.add(pool.submit(() -> StateCodecWorld.build("test:parallel_" + id, sampleProperties(), true)));
            }
            for (Future<StateCodecWorld> future : futures) {
                StateCodecWorld world = future.get(30, TimeUnit.SECONDS);
                assertEquals(3, ((LazyStateCodec<State>) world.lazy).propertyCount());
                assertEquals(LazyStateCodecEqualityTest.text(world.vanilla), LazyStateCodecEqualityTest.text(world.lazy));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void activation_off_returns_vanillas_own_step_result() {
        StateCodecWorld world = StateCodecWorld.build("test:off", List.of(BooleanProperty.create("lit")), false);
        Supplier<State> supplier = world.owner::defaultState;
        MapCodec<State> base = LazyStateCodec.base(supplier);
        MapCodec<State> eager = LazyStateCodec.append(base, supplier, "lit", BooleanProperty.create("lit"), false);
        assertFalse(eager instanceof LazyStateCodec);
        // Without a confirmed context the plain entry point is also eager: nothing is lazy before activation.
        MapCodec<State> unconfirmed = LazyStateCodec.append(base, supplier, "lit", BooleanProperty.create("lit"));
        assertFalse(unconfirmed instanceof LazyStateCodec);
        State state = world.definition.any();
        for (MapCodec<State> codec : List.of(eager, unconfirmed)) {
            assertEquals(LazyStateCodecEqualityTest.describe(world.vanilla.encode(state, NbtOps.INSTANCE,
                            NbtOps.INSTANCE.mapBuilder()).build(NbtOps.INSTANCE.empty())),
                    LazyStateCodecEqualityTest.describe(codec.encode(state, NbtOps.INSTANCE,
                            NbtOps.INSTANCE.mapBuilder()).build(NbtOps.INSTANCE.empty())));
        }
    }

    /** A property whose codec is missing: vanilla fails in the constructor, and so does the lazy path. */
    private static BooleanProperty brokenProperty() {
        return new BooleanProperty("broken") {
            @Override
            public Codec<Property.Value<Boolean>> valueCodec() {
                return null;
            }
        };
    }

    @Test
    void a_property_without_a_codec_fails_at_the_same_point_as_vanilla() {
        Supplier<State> supplier = () -> null;
        MapCodec<State> base = LazyStateCodec.base(supplier);
        Throwable vanilla = assertThrows(Throwable.class,
                () -> LazyStateCodec.vanillaStep(base, supplier, "broken", brokenProperty()));
        Throwable lazy = assertThrows(Throwable.class,
                () -> LazyStateCodec.append(base, supplier, "broken", brokenProperty(), true));
        assertEquals(vanilla.getClass(), lazy.getClass());
        // After a lazy property, a broken one resolves the chain and fails the same way.
        MapCodec<State> first = LazyStateCodec.append(base, supplier, "lit", BooleanProperty.create("lit"), true);
        assertTrue(first instanceof LazyStateCodec);
        Throwable later = assertThrows(Throwable.class,
                () -> LazyStateCodec.append(first, supplier, "z_broken", brokenProperty(), true));
        assertEquals(vanilla.getClass(), later.getClass());
    }

    @Test
    void the_mixin_gate_name_is_the_group_member() {
        assertEquals(StateCodecLayouts.GROUP + ".StateDefinitionLazyCodecMixin", LazyStateCodec.MIXIN);
    }

    @Test
    void retained_heap_of_lazy_definitions_is_a_small_fraction_of_the_eager_chain() {
        int definitions = 4_000;
        long eager = retained(definitions, false);
        long lazy = retained(definitions, true);
        System.out.println("state codec heap, " + definitions + " definitions x 3 properties: eager "
                + eager / 1024 + " KiB, lazy " + lazy / 1024 + " KiB, ratio " + (double) eager / Math.max(1, lazy));
        assertTrue(lazy * 4 < eager, "eager " + eager + " lazy " + lazy);
    }

    private static long retained(int definitions, boolean lazy) {
        List<Object> keep = new ArrayList<>(definitions);
        List<Property<?>> shared = sampleProperties();
        Supplier<State> supplier = () -> null;
        long before = used();
        for (int index = 0; index < definitions; index++) {
            MapCodec<State> codec = LazyStateCodec.base(supplier);
            for (Property<?> property : shared) {
                codec = step(codec, supplier, property, lazy);
            }
            keep.add(codec);
        }
        long after = used();
        assertEquals(definitions, keep.size());
        keep.clear();
        return after - before;
    }

    private static <T extends Comparable<T>> MapCodec<State> step(MapCodec<State> codec, Supplier<State> supplier,
            Property<T> property, boolean lazy) {
        return lazy ? LazyStateCodec.append(codec, supplier, property.getName(), property, true)
                : LazyStateCodec.vanillaStep(codec, supplier, property.getName(), property);
    }

    private static long used() {
        for (int pass = 0; pass < 4; pass++) System.gc();
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
