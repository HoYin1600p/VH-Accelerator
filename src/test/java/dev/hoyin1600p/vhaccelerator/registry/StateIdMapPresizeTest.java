package dev.hoyin1600p.vhaccelerator.registry;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.core.IdMapper;
import org.junit.jupiter.api.Test;

/**
 * Equality of Forge's fill of the block-state id map with and without the presize, on synthetic registries
 * shaped like Forge's: blocks in id order, each with its states, added one by one to a vanilla {@code IdMapper}
 * subclass that overrides {@code getId} the way Forge's map does and clears itself the way Forge's does. The
 * mixin only adds the {@link StateIdMapPresize.Slots} accessors, so this class provides them directly.
 */
class StateIdMapPresizeTest {
    private static final String FORGE_LIKE = ForgeLikeMap.class.getName();

    /** Stands in for Forge's {@code ClearableObjectIntIdentityMap}. */
    static class ForgeLikeMap<T> extends IdMapper<T> implements StateIdMapPresize.Slots {
        private boolean attempted;

        @Override
        public int getId(T key) {
            Integer id = this.tToId.get((Object) key);
            return id == null ? -1 : id;
        }

        void clear() {
            tToId.clear();
            idToT.clear();
            nextId = 0;
        }

        @Override public Object2IntMap<?> vhaccelerator$table() { return tToId; }
        @Override public List<?> vhaccelerator$list() { return idToT; }
        @Override public int vhaccelerator$nextId() { return nextId; }
        @Override public boolean vhaccelerator$presizeAttempted() { return attempted; }
        @Override public void vhaccelerator$markPresizeAttempted() { attempted = true; }

        @Override
        public void vhaccelerator$replaceTable(Object2IntMap<?> table) {
            try {
                Field field = null;
                for (Field candidate : IdMapper.class.getDeclaredFields()) {
                    if (candidate.getType() == Object2IntMap.class) {
                        field = candidate;
                    }
                }
                field.setAccessible(true);
                field.set(this, table);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    /** The anonymous subclass Forge creates in {@code BlockCallbacks.onCreate}. */
    static final class ForgeAnonymous<T> extends ForgeLikeMap<T> { }

    /** A state: only its identity matters, as for a real block state. */
    private static final class State {
        final int block;
        final int index;

        State(int block, int index) {
            this.block = block;
            this.index = index;
        }
    }

    private record Registry(List<List<State>> blocks, int total) { }

    private static Registry registry(long seed, int blockCount, int maxStates) {
        Random random = new Random(seed);
        List<List<State>> blocks = new ArrayList<>(blockCount);
        int total = 0;
        for (int block = 0; block < blockCount; block++) {
            int count = 1 + (random.nextInt(8) == 0 ? random.nextInt(maxStates) : random.nextInt(4));
            List<State> states = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                states.add(new State(block, index));
            }
            blocks.add(states);
            total += count;
        }
        return new Registry(blocks, total);
    }

    /** Forge's {@code onBake}: every state of every block, in registry order, added once. */
    private static void bake(IdMapper<State> map, List<List<State>> blocks, int[] order) {
        for (int block : order) {
            for (State state : blocks.get(block)) {
                map.add(state);
            }
        }
    }

    private static int[] identity(int count) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        return order;
    }

    private static int[] shuffled(int count, long seed) {
        int[] order = identity(count);
        Random random = new Random(seed);
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    private static void assertSameMapping(ForgeLikeMap<State> expected, ForgeLikeMap<State> actual, Registry registry,
                                          int[] order) {
        assertEquals(expected.size(), actual.size());
        assertEquals(registry.total(), actual.size());
        for (int block : order) {
            for (State state : registry.blocks().get(block)) {
                assertEquals(expected.getId(state), actual.getId(state));
            }
        }
        int ids = expected.size();
        for (int id = -2; id < ids + 3; id++) {
            assertSame(expected.byId(id), actual.byId(id), "byId " + id);
            assertEquals(expected.contains(id), actual.contains(id));
        }
        Iterator<State> a = expected.iterator();
        Iterator<State> b = actual.iterator();
        while (a.hasNext()) {
            assertTrue(b.hasNext());
            assertSame(a.next(), b.next());
        }
        assertFalse(b.hasNext());
        assertEquals(-1, actual.getId(new State(-1, -1)));
        assertEquals(-1, expected.getId(new State(-1, -1)));
        // The next free id is the same: one more state gets the same id from both.
        State extra = new State(-2, 0);
        expected.add(extra);
        actual.add(extra);
        assertEquals(expected.getId(extra), actual.getId(extra));
        assertEquals(ids, actual.getId(extra));
    }

    @Test void largeRegistryGetsTheSameIdsAndOrderAsForgesOwnFill() {
        Registry registry = registry(7, 60_000, 400);
        assertTrue(registry.total() > 600_000, "synthetic registry should be large: " + registry.total());
        int[] order = identity(registry.blocks().size());
        ForgeLikeMap<State> original = new ForgeAnonymous<>();
        bake(original, registry.blocks(), order);

        ForgeLikeMap<State> sized = new ForgeAnonymous<>();
        assertTrue(StateIdMapPresize.presize(sized, registry.total(), FORGE_LIKE));
        bake(sized, registry.blocks(), order);

        assertSameMapping(original, sized, registry, order);
    }

    @Test void aMillionStatesAreIdenticalAndTheTableDoesNotGrow() {
        Registry registry = registry(11, 400_000, 60);
        int[] order = identity(registry.blocks().size());
        ForgeLikeMap<State> original = new ForgeAnonymous<>();
        bake(original, registry.blocks(), order);
        ForgeLikeMap<State> sized = new ForgeAnonymous<>();
        assertTrue(StateIdMapPresize.presize(sized, registry.total(), FORGE_LIKE));
        Object2IntMap<?> table = sized.vhaccelerator$table();
        bake(sized, registry.blocks(), order);
        assertSame(table, sized.vhaccelerator$table(), "the table was sized for the fill and never replaced again");
        assertSameMapping(original, sized, registry, order);
    }

    @Test void refillAfterClearWithADifferentOrderMatchesTheOriginal() {
        // A world with other saved ids: Forge clears the map and fills it again in another block order.
        Registry registry = registry(23, 30_000, 200);
        int[] first = identity(registry.blocks().size());
        int[] second = shuffled(registry.blocks().size(), 5);

        ForgeLikeMap<State> original = new ForgeAnonymous<>();
        bake(original, registry.blocks(), first);
        original.clear();
        bake(original, registry.blocks(), second);

        ForgeLikeMap<State> sized = new ForgeAnonymous<>();
        assertTrue(StateIdMapPresize.presize(sized, registry.total(), FORGE_LIKE));
        bake(sized, registry.blocks(), first);
        sized.clear();
        assertFalse(StateIdMapPresize.presize(sized, registry.total(), FORGE_LIKE), "once per map");
        bake(sized, registry.blocks(), second);

        assertSameMapping(original, sized, registry, second);
    }

    @Test void presizeNeverAddsOrChangesAnything() {
        ForgeLikeMap<State> sized = new ForgeAnonymous<>();
        assertTrue(StateIdMapPresize.presize(sized, 100_000, FORGE_LIKE));
        assertEquals(0, sized.size());
        assertEquals(0, sized.vhaccelerator$nextId());
        assertTrue(sized.vhaccelerator$list().isEmpty());
        assertEquals(-1, sized.getId(new State(0, 0)));
        assertSame(Util.identityStrategy(), ((Object2IntOpenCustomHashMap<?>) sized.vhaccelerator$table()).strategy());
        assertEquals(-1, sized.vhaccelerator$table().defaultReturnValue());
    }

    @Test void aMapOfAnyOtherLayoutIsLeftAlone() {
        // Not a Slots implementor (the mixin did not apply).
        assertFalse(StateIdMapPresize.presize(new IdMapper<State>(), 100_000, FORGE_LIKE));
        assertFalse(StateIdMapPresize.presize(new Object(), 100_000, FORGE_LIKE));
        assertFalse(StateIdMapPresize.presize(null, 100_000, FORGE_LIKE));

        // Not Forge's map class.
        ForgeLikeMap<State> wrongClass = new ForgeAnonymous<>();
        assertFalse(StateIdMapPresize.presize(wrongClass, 100_000, "net.minecraftforge.registries.Other"));
        assertEquals(0, wrongClass.size());

        // Already holds states.
        ForgeLikeMap<State> filled = new ForgeAnonymous<>();
        filled.add(new State(0, 0));
        Object2IntMap<?> before = filled.vhaccelerator$table();
        assertFalse(StateIdMapPresize.presize(filled, 100_000, FORGE_LIKE));
        assertSame(before, filled.vhaccelerator$table());

        // Out-of-range counts.
        assertFalse(StateIdMapPresize.presize(new ForgeAnonymous<State>(), StateIdMapPresize.MIN_STATES - 1, FORGE_LIKE));
        assertFalse(StateIdMapPresize.presize(new ForgeAnonymous<State>(), StateIdMapPresize.MAX_STATES + 1, FORGE_LIKE));
        assertFalse(StateIdMapPresize.presize(new ForgeAnonymous<State>(), -5, FORGE_LIKE));

        // A table of another kind (another mod replaced it), or another default.
        ForgeLikeMap<State> foreign = new ForgeAnonymous<>();
        Object2IntOpenHashMap<State> plain = new Object2IntOpenHashMap<>();
        plain.defaultReturnValue(-1);
        foreign.vhaccelerator$replaceTable(plain);
        assertFalse(StateIdMapPresize.presize(foreign, 100_000, FORGE_LIKE));
        assertSame(plain, foreign.vhaccelerator$table());

        ForgeLikeMap<State> otherDefault = new ForgeAnonymous<>();
        Object2IntOpenCustomHashMap<State> custom = new Object2IntOpenCustomHashMap<>(Util.identityStrategy());
        custom.defaultReturnValue(0);
        otherDefault.vhaccelerator$replaceTable(custom);
        assertFalse(StateIdMapPresize.presize(otherDefault, 100_000, FORGE_LIKE));
        assertSame(custom, otherDefault.vhaccelerator$table());

        ForgeLikeMap<State> otherStrategy = new ForgeAnonymous<>();
        Object2IntOpenCustomHashMap<State> equalsKeys = new Object2IntOpenCustomHashMap<>(
                new it.unimi.dsi.fastutil.Hash.Strategy<State>() {
                    @Override public int hashCode(State o) { return o == null ? 0 : o.block; }
                    @Override public boolean equals(State a, State b) { return a == b; }
                });
        equalsKeys.defaultReturnValue(-1);
        otherStrategy.vhaccelerator$replaceTable(equalsKeys);
        assertFalse(StateIdMapPresize.presize(otherStrategy, 100_000, FORGE_LIKE));
        assertSame(equalsKeys, otherStrategy.vhaccelerator$table());
    }

    @Test void aFailedAttemptIsNotRetried() {
        ForgeLikeMap<State> map = new ForgeAnonymous<>();
        assertFalse(StateIdMapPresize.presize(map, 10, FORGE_LIKE));
        assertTrue(map.vhaccelerator$presizeAttempted());
        assertFalse(StateIdMapPresize.presize(map, 100_000, FORGE_LIKE));
    }

    @Test void theForgeClassNamesMatchTheReviewedForgeBuild() throws ClassNotFoundException {
        Class<?> forgeMap = Class.forName(StateIdMapPresize.FORGE_MAP_SUPERCLASS, false,
                StateIdMapPresizeTest.class.getClassLoader());
        assertSame(IdMapper.class, forgeMap.getSuperclass());
        assertEquals("minecraft:blockstatetoid", StateIdMapPresize.STATE_MAP_KEY.toString());
    }
}
