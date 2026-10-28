package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.Field;
import java.util.NoSuchElementException;

/**
 * Experimental private WorldZones lookup. Only pristine default-capacity singletons are packed.
 * Once a set is materialized it stays original, even after shrinking to one member. In particular,
 * multi-member iteration, collision chains, capacity history and iterator removal stay in fastutil.
 * This is not a general replacement for maps whose values escape: inline views are call-local.
 */
public final class CompactChunkMembershipMap extends AbstractLong2ObjectMap<IntSet> {
    private Long2IntOpenHashMap singletons;
    private Long2ObjectOpenHashMap<IntSet> originals;

    private CompactChunkMembershipMap() { }

    /** Builds without changing the original map or any set. Failure leaves publication to the caller. */
    public static CompactChunkMembershipMap copyOf(Long2ObjectMap<IntSet> source) {
        if (source.getClass() != Long2ObjectOpenHashMap.class
                && source.getClass() != CompactChunkMembershipMap.class) return null;
        if (source.defaultReturnValue() != null || !SingletonLayout.AVAILABLE) return null;
        CompactChunkMembershipMap result = new CompactChunkMembershipMap();
        for (Long2ObjectMap.Entry<IntSet> entry : source.long2ObjectEntrySet()) {
            IntSet set = entry.getValue();
            if (set == null) return null;
            if (set instanceof InlineSet inline && inline.materialized == null && inline.present) {
                result.inline(entry.getLongKey(), inline.member);
            } else if (SingletonLayout.eligible(set)) {
                result.inline(entry.getLongKey(), set.iterator().nextInt());
            } else {
                result.original(entry.getLongKey(), set);
            }
        }
        return result;
    }

    private void inline(long key, int member) {
        if (singletons == null) singletons = new Long2IntOpenHashMap();
        singletons.put(key, member);
    }

    private void original(long key, IntSet value) {
        if (originals == null) originals = new Long2ObjectOpenHashMap<>();
        originals.put(key, value);
    }

    @Override public IntSet get(long key) {
        if (singletons != null && singletons.containsKey(key)) return new InlineSet(key, singletons.get(key));
        return originals == null ? null : originals.get(key);
    }

    @Override public boolean containsKey(long key) {
        return (singletons != null && singletons.containsKey(key))
                || (originals != null && originals.containsKey(key));
    }

    @Override public IntSet put(long key, IntSet value) {
        if (value == null) throw new IllegalArgumentException("WorldZones memberships must not be null");
        IntSet old = remove(key);
        // Fresh computeIfAbsent results must retain their original mutable identity and behavior.
        original(key, value);
        return old;
    }

    @Override public IntSet remove(long key) {
        if (singletons != null && singletons.containsKey(key)) {
            int member = singletons.remove(key);
            return new InlineSet(key, member);
        }
        return originals == null ? null : originals.remove(key);
    }

    @Override public int size() {
        return (singletons == null ? 0 : singletons.size()) + (originals == null ? 0 : originals.size());
    }

    @Override public void clear() {
        singletons = null;
        originals = null;
    }

    public int inlineCount() { return singletons == null ? 0 : singletons.size(); }
    public int originalCount() { return originals == null ? 0 : originals.size(); }

    @Override public ObjectSet<Long2ObjectMap.Entry<IntSet>> long2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override public int size() { return CompactChunkMembershipMap.this.size(); }
            @Override public ObjectIterator<Long2ObjectMap.Entry<IntSet>> iterator() {
                long[] inlineKeys = singletons == null ? new long[0] : singletons.keySet().toLongArray();
                long[] originalKeys = originals == null ? new long[0] : originals.keySet().toLongArray();
                return new ObjectIterator<>() {
                    int index;
                    @Override public boolean hasNext() { return index < inlineKeys.length + originalKeys.length; }
                    @Override public Long2ObjectMap.Entry<IntSet> next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        int current = index++;
                        long key = current < inlineKeys.length ? inlineKeys[current] : originalKeys[current - inlineKeys.length];
                        return new BasicEntry<>(key, get(key));
                    }
                };
            }
        };
    }

    /** Used only by the reviewed WorldZones get/add/remove call sites; never stored or exposed. */
    private final class InlineSet extends AbstractIntSet {
        private final long chunk;
        private final int member;
        private boolean present = true;
        private IntSet materialized;
        InlineSet(long chunk, int member) { this.chunk = chunk; this.member = member; }
        @Override public int size() { return materialized != null ? materialized.size() : present ? 1 : 0; }
        @Override public boolean contains(int value) {
            return materialized != null ? materialized.contains(value) : present && member == value;
        }
        @Override public IntIterator iterator() {
            return materialized != null ? materialized.iterator() : present ? IntIterators.singleton(member) : IntIterators.EMPTY_ITERATOR;
        }
        @Override public boolean add(int value) {
            if (materialized == null) {
                if (present && value == member) return false;
                materialized = new IntOpenHashSet();
                if (present) materialized.add(member);
                if (singletons != null) singletons.remove(chunk);
                original(chunk, materialized);
            }
            return materialized.add(value);
        }
        @Override public boolean remove(int value) {
            if (materialized != null) return materialized.remove(value);
            if (!present || value != member) return false;
            present = false;
            if (singletons != null) singletons.remove(chunk);
            return true;
        }
    }

    /** A singleton can be rebuilt exactly only when all original default-capacity state matches. */
    private static final class SingletonLayout {
        static final String[] NAMES = {"n", "mask", "maxFill", "minN", "f", "key", "containsNull"};
        static final Field[] FIELDS = new Field[NAMES.length];
        static final boolean AVAILABLE;
        static {
            boolean ok = true;
            try {
                for (int i = 0; i < NAMES.length; i++) {
                    FIELDS[i] = IntOpenHashSet.class.getDeclaredField(NAMES[i]);
                    ok &= FIELDS[i].trySetAccessible();
                }
                IntOpenHashSet probe = new IntOpenHashSet();
                ok &= (int) FIELDS[0].get(probe) == 32 && (int) FIELDS[3].get(probe) == 32;
            } catch (ReflectiveOperationException | RuntimeException failure) { ok = false; }
            AVAILABLE = ok;
        }
        static boolean eligible(IntSet set) {
            if (set.getClass() != IntOpenHashSet.class || set.size() != 1) return false;
            try {
                if ((int) FIELDS[0].get(set) != 32 || (int) FIELDS[1].get(set) != 31
                        || (int) FIELDS[2].get(set) != 24 || (int) FIELDS[3].get(set) != 32
                        || (float) FIELDS[4].get(set) != 0.75F) return false;
                int member = set.iterator().nextInt();
                int[] key = (int[]) FIELDS[5].get(set);
                if (key.length != 33 || (boolean) FIELDS[6].get(set) != (member == 0)) return false;
                int slot = member == 0 ? -1 : HashCommon.mix(member) & 31;
                for (int i = 0; i < key.length; i++) if (key[i] != (i == slot ? member : 0)) return false;
                return true;
            } catch (ReflectiveOperationException | RuntimeException failure) { return false; }
        }
    }
}
