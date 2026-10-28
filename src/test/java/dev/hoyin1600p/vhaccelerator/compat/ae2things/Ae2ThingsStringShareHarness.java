package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Offline measurement on an explicit copy of AE2Things' disk_manager.dat: loads it with vanilla NbtIo, wraps the
 * key lists in stand-ins with AE2Things' field layout (as StorageManager.readNbt would), runs the production walker
 * and reports retained heap, instance counts, walk time and whether the uncompressed NBT stream is byte-identical
 * before and after. Optionally writes the saved file before and after for the Round 4 comparer.
 */
public final class Ae2ThingsStringShareHarness {
    private Ae2ThingsStringShareHarness() { }

    public static void main(String[] args) throws Exception {
        Path input = null;
        Path outputDirectory = null;
        boolean writeFiles = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> input = Path.of(args[++i]);
                case "--output-directory" -> outputDirectory = Path.of(args[++i]);
                case "--write-saved-files" -> writeFiles = true;
                default -> throw new IllegalArgumentException("Unknown argument " + args[i]);
            }
        }
        if (input == null || outputDirectory == null) throw new IllegalArgumentException("--input and --output-directory are required");
        Files.createDirectories(outputDirectory);

        long t0 = System.nanoTime();
        CompoundTag root = NbtIo.readCompressed(input.toFile());
        long readMillis = (System.nanoTime() - t0) / 1_000_000L;

        t0 = System.nanoTime();
        DiskFixtures.FakeStorageManager manager = new DiskFixtures.FakeStorageManager();
        ListTag disklist = root.getCompound("data").getList("disklist", 10);
        for (int i = 0; i < disklist.size(); i++) {
            CompoundTag disk = disklist.getCompound(i);
            DiskFixtures.FakeDataStorage storage = new DiskFixtures.FakeDataStorage();
            CompoundTag data = disk.getCompound("diskdata");
            storage.stackKeys = data.getList("keys", 10);
            storage.stackAmounts = data.getLongArray("amts");
            manager.disks().put(disk.getUUID("diskuuid"), storage);
        }
        long buildMillis = (System.nanoTime() - t0) / 1_000_000L;

        String hashBefore = streamHash(root);
        if (writeFiles) NbtIo.writeCompressed(root, outputDirectory.resolve("before.dat").toFile());
        List<ListTag> lists = DiskFixtures.keyLists(manager);
        Counts before = counts(lists);
        long usedBefore = settledHeap();

        t0 = System.nanoTime();
        Ae2ThingsDiskStrings.Result result = Ae2ThingsDiskStrings.share(manager);
        long shareMillis = (System.nanoTime() - t0) / 1_000_000L;

        long usedAfter = settledHeap();
        Counts after = counts(lists);
        String hashAfter = streamHash(root);
        if (writeFiles) NbtIo.writeCompressed(root, outputDirectory.resolve("after.dat").toFile());
        boolean identical = hashBefore.equals(hashAfter);

        String json = String.format(Locale.ROOT, """
                {
                  "input": "%s",
                  "disks": %d,
                  "readCompressedMillis": %d,
                  "buildManagerMillis": %d,
                  "shareMillis": %d,
                  "stringSlots": %d,
                  "replacedSlots": %d,
                  "distinctStrings": %d,
                  "stringTagInstancesBefore": %d,
                  "stringTagInstancesAfter": %d,
                  "stringObjectsBefore": %d,
                  "stringObjectsAfter": %d,
                  "usedHeapBeforeBytes": %d,
                  "usedHeapAfterBytes": %d,
                  "savedBytes": %d,
                  "savedMiB": %.1f,
                  "streamSha256Before": "%s",
                  "streamSha256After": "%s",
                  "streamIdentical": %s
                }
                """, input.getFileName(), result.disks(), readMillis, buildMillis, shareMillis, result.stringSlots(),
                result.replaced(), result.distinct(), before.tags, after.tags, before.strings, after.strings,
                usedBefore, usedAfter, usedBefore - usedAfter, (usedBefore - usedAfter) / 1048576.0,
                hashBefore, hashAfter, identical);
        Files.writeString(outputDirectory.resolve("harness-result.json"), json);
        System.out.println(json);
        if (!identical) throw new IllegalStateException("uncompressed NBT stream changed");
    }

    private record Counts(long tags, long strings) { }

    private static Counts counts(List<ListTag> lists) {
        Set<StringTag> tags = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> strings = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ListTag list : lists) walk(list, tags, strings);
        return new Counts(tags.size(), strings.size());
    }

    private static void walk(Tag tag, Set<StringTag> tags, Set<String> strings) {
        if (tag instanceof StringTag s) {
            tags.add(s);
            strings.add(s.getAsString());
        } else if (tag instanceof CompoundTag c) {
            for (String key : c.getAllKeys()) walk(c.get(key), tags, strings);
        } else if (tag instanceof ListTag l) {
            for (Tag element : l) walk(element, tags, strings);
        }
    }

    private static String streamHash(CompoundTag root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            NbtIo.write(root, out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Used heap after repeated full collections; stable to a few MiB for a graph this size. */
    private static long settledHeap() throws InterruptedException {
        long previous = Long.MAX_VALUE;
        long used = 0;
        for (int i = 0; i < 6; i++) {
            System.gc();
            Thread.sleep(300);
            used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            if (Math.abs(previous - used) < 2L * 1024 * 1024) break;
            previous = used;
        }
        return used;
    }
}
