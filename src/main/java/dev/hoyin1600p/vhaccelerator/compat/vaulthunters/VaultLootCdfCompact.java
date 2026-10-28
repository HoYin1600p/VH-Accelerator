package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import it.unimi.dsi.fastutil.longs.AbstractLong2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMaps;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Map;

/**
 * Stores Vault's cumulative distribution as one {@code double[]} indexed by composition rank.
 *
 * <p>A distribution over {@code parts} loot pools and {@code samples} rolls holds one value per
 * composition of {@code samples} into {@code parts} non-negative parts: C(samples + parts - 1,
 * parts - 1) values (27,720 for four pools and 53 rolls). Vault keeps them in a hash map keyed by
 * the packed frequencies, about 4.9 times the size of this array. The packed key is treated as an
 * opaque long: a key is present exactly when its fields are a composition of {@code samples}, which
 * {@link #tryCompact} proves for the computed map before it is dropped.
 *
 * <p>The rank is the combinatorial number system of the strictly increasing bar positions
 * {@code a0 + ... + ai + i}, so no table other than binomials is needed.
 */
public final class VaultLootCdfCompact implements VaultLootCdfDistribution {
    public static final int MIN_PARTS = 2;
    public static final int MAX_PARTS = 8;
    public static final int MAX_SAMPLES = 255;
    /** At most 32 MiB per distribution; larger shapes keep Vault's map. */
    public static final long MAX_ENTRIES = 1L << 22;
    private static final long[][] CHOOSE = pascal(MAX_SAMPLES + MAX_PARTS, MAX_PARTS);

    private final int samples;
    private final int parts;
    private final int packBits;
    private final long mask;
    private final double[] values;
    private final double defaultValue;
    private final View view;

    private VaultLootCdfCompact(int samples, int parts, int packBits, double[] values, double defaultValue) {
        this.samples = samples;
        this.parts = parts;
        this.packBits = packBits;
        this.mask = (1L << packBits) - 1L;
        this.values = values;
        this.defaultValue = defaultValue;
        this.view = new View();
        this.view.defaultReturnValue(defaultValue);
    }

    private static long[][] pascal(int rows, int columns) {
        long[][] table = new long[rows + 1][columns + 1];
        for (int n = 0; n <= rows; n++) {
            table[n][0] = 1;
            for (int r = 1; r <= Math.min(n, columns); r++) {
                table[n][r] = table[n - 1][r - 1] + (r <= n - 1 ? table[n - 1][r] : 0);
            }
        }
        return table;
    }

    /** Whether a distribution of this shape has a defined compact layout. */
    public static boolean supports(int samples, int parts, int packBits) {
        if (parts < MIN_PARTS || parts > MAX_PARTS || packBits != 64 / parts) return false;
        if (samples < 0 || samples > MAX_SAMPLES || samples > (1L << packBits) - 1L) return false;
        return count(samples, parts) <= MAX_ENTRIES;
    }

    /** The number of compositions of {@code samples} into {@code parts} parts. */
    public static long count(int samples, int parts) {
        return CHOOSE[samples + parts - 1][parts - 1];
    }

    /**
     * Copies {@code computed} into a compact array and proves the copy equal, or returns null.
     *
     * <p>Every key must decode to a composition of {@code samples} that packs back to the same key,
     * the map must hold exactly {@link #count} distinct keys, and each stored value must read back
     * bit-for-bit through the same {@link #get} path later callers use.
     */
    public static VaultLootCdfCompact tryCompact(Long2DoubleMap computed, int samples, int parts, int packBits) {
        if (computed == null || !supports(samples, parts, packBits)) return null;
        long expected = count(samples, parts);
        if (computed.size() != expected) return null;
        double[] values = new double[(int) expected];
        long[] seen = new long[(int) ((expected + 63) >>> 6)];
        VaultLootCdfCompact candidate = new VaultLootCdfCompact(samples, parts, packBits, values,
                computed.defaultReturnValue());
        for (var entry : Long2DoubleMaps.fastIterable(computed)) {
            long rank = candidate.rank(entry.getLongKey());
            if (rank < 0 || (seen[(int) (rank >>> 6)] & (1L << rank)) != 0) return null;
            seen[(int) (rank >>> 6)] |= 1L << rank;
            values[(int) rank] = entry.getDoubleValue();
        }
        for (var entry : Long2DoubleMaps.fastIterable(computed)) {
            long key = entry.getLongKey();
            if (Double.doubleToRawLongBits(candidate.get(key))
                    != Double.doubleToRawLongBits(computed.get(key))) return null;
        }
        // Absent keys must answer with the map's default, exactly as the hash map does.
        for (long probe : new long[]{Long.MIN_VALUE, -1L, 0x7fffffffffffffffL, ~0L << 1}) {
            if (!computed.containsKey(probe) && Double.doubleToRawLongBits(candidate.get(probe))
                    != Double.doubleToRawLongBits(computed.get(probe))) return null;
        }
        return candidate;
    }

    /** The rank of a packed key, or -1 when the key is not a composition of {@code samples}. */
    long rank(long packed) {
        long repacked = 0;
        long sum = 0;
        long rank = 0;
        int position = -1;
        for (int i = 0; i < parts; i++) {
            long field = (packed >>> (packBits * (parts - 1 - i))) & mask;
            sum += field;
            repacked = (repacked << packBits) | field;
            if (i < parts - 1) {
                position += (int) field + 1;
                if (position < 0 || position >= CHOOSE.length) return -1;
                rank += CHOOSE[position][i + 1];
            }
        }
        return sum == samples && repacked == packed ? rank : -1;
    }

    /** The packed key of {@code rank}, the inverse of {@link #rank}. */
    long keyAt(long rank) {
        int[] bars = new int[parts - 1];
        long remaining = rank;
        int upper = samples + parts - 2;
        for (int j = parts - 1; j >= 1; j--) {
            int c = upper;
            while (CHOOSE[c][j] > remaining) c--;
            bars[j - 1] = c;
            remaining -= CHOOSE[c][j];
            upper = c - 1;
        }
        long packed = 0;
        int previous = -1;
        int used = 0;
        for (int i = 0; i < parts - 1; i++) {
            int field = bars[i] - previous - 1;
            previous = bars[i];
            used += field;
            packed = (packed << packBits) | field;
        }
        return (packed << packBits) | (samples - used);
    }

    @Override
    public double get(long packed) {
        long rank = rank(packed);
        return rank < 0 ? defaultValue : values[(int) rank];
    }

    @Override
    public Long2DoubleMap map() {
        return view;
    }

    @Override
    public boolean compact() {
        return true;
    }

    /** Heap held by the array, for measurement. */
    public long arrayBytes() {
        return 16L + 8L * values.length;
    }

    /**
     * Read-only view with the computed map's keys and values. Lookups are identical; iteration runs in
     * rank order instead of hash order, and every mutator throws {@link UnsupportedOperationException}.
     */
    private final class View extends AbstractLong2DoubleMap {
        private static final long serialVersionUID = 1L;

        @Override
        public double get(long key) {
            return VaultLootCdfCompact.this.get(key);
        }

        @Override
        public boolean containsKey(long key) {
            return rank(key) >= 0;
        }

        @Override
        public int size() {
            return values.length;
        }

        @Override
        public ObjectSet<Long2DoubleMap.Entry> long2DoubleEntrySet() {
            return new AbstractObjectSet<>() {
                @Override
                public ObjectIterator<Long2DoubleMap.Entry> iterator() {
                    return new AbstractObjectIterator<>() {
                        private int next;

                        @Override
                        public boolean hasNext() {
                            return next < values.length;
                        }

                        @Override
                        public Long2DoubleMap.Entry next() {
                            if (next >= values.length) throw new java.util.NoSuchElementException();
                            int rank = next++;
                            return new ReadOnlyEntry(keyAt(rank), values[rank]);
                        }
                    };
                }

                @Override
                public int size() {
                    return values.length;
                }

                @Override
                public boolean contains(Object candidate) {
                    if (!(candidate instanceof Map.Entry<?, ?> entry)
                            || !(entry.getKey() instanceof Long key)
                            || !(entry.getValue() instanceof Double value)) return false;
                    long rank = rank(key);
                    return rank >= 0 && Double.doubleToLongBits(values[(int) rank]) == Double.doubleToLongBits(value);
                }
            };
        }
    }

    private static final class ReadOnlyEntry extends AbstractLong2DoubleMap.BasicEntry {
        ReadOnlyEntry(long key, double value) {
            super(key, value);
        }

        @Override
        public double setValue(double value) {
            throw new UnsupportedOperationException();
        }
    }
}
