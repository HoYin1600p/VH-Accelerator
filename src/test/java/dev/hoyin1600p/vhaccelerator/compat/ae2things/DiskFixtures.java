package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Stand-ins with AE2Things' field layout, seeded key trees and a strict structural comparison. */
final class DiskFixtures {
    private static final String[] POOL = {"ae2:i", "the_vault:jewel", "the_vault:card", "Common", "Rare", "uniform",
            "Resource", "", "minecraft:stone", "x", "Épée ü", "a longer description with spaces and 123456"};

    private DiskFixtures() { }

    /** Same layout as io.github.projectet.ae2things.util.StorageManager. */
    static final class FakeStorageManager {
        private final Map<UUID, FakeDataStorage> disks = new HashMap<>();

        Map<UUID, FakeDataStorage> disks() { return disks; }
    }

    /** Same layout as io.github.projectet.ae2things.util.DataStorage. */
    static final class FakeDataStorage {
        public ListTag stackKeys = new ListTag();
        public long[] stackAmounts = new long[0];
        public long itemCount;
    }

    /** A manager with {@code diskCount} disks built from {@code seed}; equal seeds give equal, unshared trees. */
    static FakeStorageManager manager(long seed, int diskCount, int keysPerDisk) {
        Random random = new Random(seed);
        FakeStorageManager manager = new FakeStorageManager();
        for (int d = 0; d < diskCount; d++) {
            FakeDataStorage storage = new FakeDataStorage();
            for (int k = 0; k < keysPerDisk; k++) storage.stackKeys.add(key(random, 0));
            storage.stackAmounts = new long[keysPerDisk];
            manager.disks.put(new UUID(seed, d), storage);
        }
        return manager;
    }

    static CompoundTag key(Random random, int depth) {
        CompoundTag tag = new CompoundTag();
        List<String> names = new ArrayList<>(List.of("#c", "id", "tag", "caps", "n", "zeta", "Alpha", "k1", "k2", "k3",
                "k4", "k5", "k6", "k7", "k8", "k9", "k10", "k11", "k12"));
        Collections.shuffle(names, random);
        int count = 3 + random.nextInt(names.size() - 2);
        for (String name : names.subList(0, count)) tag.put(name, value(random, depth));
        return tag;
    }

    private static Tag value(Random random, int depth) {
        int kind = random.nextInt(depth >= 4 ? 8 : 11);
        return switch (kind) {
            case 0, 1, 2 -> text(random);
            case 3 -> IntTag.valueOf(random.nextInt());
            case 4 -> new ByteArrayTag(random.nextBoolean() ? new byte[0] : new byte[] {1, 2, (byte) random.nextInt()});
            case 5 -> new LongArrayTag(new long[] {random.nextLong(), random.nextLong()});
            case 6 -> new IntArrayTag(new int[] {random.nextInt(), 7});
            case 7 -> random.nextBoolean() ? DoubleTag.valueOf(random.nextDouble()) : ByteTag.valueOf((byte) random.nextInt());
            case 8 -> key(random, depth + 1);
            case 9 -> {
                ListTag strings = new ListTag();
                for (int i = random.nextInt(6); i > 0; i--) strings.add(text(random));
                yield strings;
            }
            default -> {
                ListTag mixed = new ListTag();
                boolean compounds = random.nextBoolean();
                for (int i = random.nextInt(4); i > 0; i--) {
                    if (compounds) mixed.add(key(random, depth + 1));
                    else {
                        ListTag inner = new ListTag();
                        inner.add(text(random));
                        inner.add(text(random));
                        mixed.add(inner);
                    }
                }
                yield mixed;
            }
        };
    }

    /** A fresh String object each time, so equal values are never accidentally the same instance. */
    static StringTag text(Random random) {
        return StringTag.valueOf(new String(POOL[random.nextInt(POOL.length)].toCharArray())
                + (random.nextInt(5) == 0 ? "-" + random.nextInt(40) : ""));
    }

    static List<ListTag> keyLists(FakeStorageManager manager) {
        return manager.disks.values().stream().map(storage -> storage.stackKeys).toList();
    }

    /** Type, key order, list order and every value; arrays by content. Fails with the path of the first difference. */
    static void assertSameShape(Tag expected, Tag actual, String path) {
        assertEquals(expected.getId(), actual.getId(), path + " type");
        if (expected instanceof CompoundTag e) {
            CompoundTag a = (CompoundTag) actual;
            assertEquals(new ArrayList<>(e.getAllKeys()), new ArrayList<>(a.getAllKeys()), path + " key order");
            for (String key : e.getAllKeys()) assertSameShape(e.get(key), a.get(key), path + "." + key);
        } else if (expected instanceof ListTag e) {
            ListTag a = (ListTag) actual;
            assertEquals(e.size(), a.size(), path + " size");
            assertEquals(e.getElementType(), a.getElementType(), path + " element type");
            for (int i = 0; i < e.size(); i++) assertSameShape(e.get(i), a.get(i), path + "[" + i + "]");
        } else if (expected instanceof ByteArrayTag e) {
            assertArrayEquals(e.getAsByteArray(), ((ByteArrayTag) actual).getAsByteArray(), path);
        } else if (expected instanceof IntArrayTag e) {
            assertArrayEquals(e.getAsIntArray(), ((IntArrayTag) actual).getAsIntArray(), path);
        } else if (expected instanceof LongArrayTag e) {
            assertArrayEquals(e.getAsLongArray(), ((LongArrayTag) actual).getAsLongArray(), path);
        } else if (expected instanceof StringTag e) {
            assertEquals(e.getAsString(), actual.getAsString(), path);
        } else {
            assertEquals(expected, actual, path);
        }
    }

    static void assertSameManagers(FakeStorageManager expected, FakeStorageManager actual) {
        assertEquals(expected.disks.keySet(), actual.disks.keySet());
        for (UUID id : expected.disks.keySet()) {
            FakeDataStorage e = expected.disks.get(id);
            FakeDataStorage a = actual.disks.get(id);
            assertSameShape(e.stackKeys, a.stackKeys, id.toString());
            assertArrayEquals(e.stackAmounts, a.stackAmounts);
            assertEquals(e.itemCount, a.itemCount);
        }
    }

    /** Every StringTag object reachable from the key lists, by identity. */
    static Set<StringTag> stringInstances(Iterable<ListTag> lists) {
        Set<StringTag> found = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ListTag list : lists) collect(list, found);
        return found;
    }

    static long stringSlots(Iterable<ListTag> lists) {
        long[] count = new long[1];
        for (ListTag list : lists) slots(list, count);
        return count[0];
    }

    private static void collect(Tag tag, Set<StringTag> found) {
        if (tag instanceof StringTag s) found.add(s);
        else if (tag instanceof CompoundTag c) c.getAllKeys().forEach(key -> collect(c.get(key), found));
        else if (tag instanceof ListTag l) l.forEach(element -> collect(element, found));
    }

    private static void slots(Tag tag, long[] count) {
        if (tag instanceof StringTag) count[0]++;
        else if (tag instanceof CompoundTag c) c.getAllKeys().forEach(key -> slots(c.get(key), count));
        else if (tag instanceof ListTag l) l.forEach(element -> slots(element, count));
    }

    static Set<String> distinctValues(Iterable<ListTag> lists) {
        Set<String> values = new java.util.HashSet<>();
        stringInstances(lists).forEach(tag -> values.add(tag.getAsString()));
        return values;
    }
}
