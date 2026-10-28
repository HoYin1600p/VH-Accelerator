package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import static dev.hoyin1600p.vhaccelerator.compat.ae2things.DiskFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class Ae2ThingsDiskStringsTest {
    private static final long SEED = 20261024L;

    @Test
    void wholeTreeIsEqualBeforeAndAfter() throws Exception {
        FakeStorageManager shared = manager(SEED, 7, 60);
        FakeStorageManager twin = manager(SEED, 7, 60);
        assertSameManagers(twin, shared);
        Ae2ThingsDiskStrings.Result result = Ae2ThingsDiskStrings.share(shared);
        assertEquals(7, result.disks());
        assertTrue(result.replaced() > 0);
        assertSameManagers(twin, shared);
        // Idempotent: a second walk finds nothing left to replace.
        assertEquals(0, Ae2ThingsDiskStrings.share(shared).replaced());
        assertSameManagers(twin, shared);
    }

    @Test
    void equalStringsEndUpAsOneInstanceEach() throws Exception {
        FakeStorageManager manager = manager(SEED + 1, 5, 80);
        List<ListTag> lists = keyLists(manager);
        long slots = stringSlots(lists);
        int distinctValues = distinctValues(lists).size();
        int before = stringInstances(lists).size();
        assertTrue(before > distinctValues * 4, "fixture must repeat values");
        Ae2ThingsDiskStrings.Result result = Ae2ThingsDiskStrings.share(manager);
        assertEquals(distinctValues, stringInstances(lists).size());
        assertEquals(distinctValues, result.distinct());
        assertEquals(slots, result.stringSlots());
        assertTrue(result.replaced() >= before - distinctValues);
    }

    @Test
    void equalsAndHashCodeAreUnchangedForEveryKey() throws Exception {
        FakeStorageManager shared = manager(SEED + 2, 3, 70);
        FakeStorageManager twin = manager(SEED + 2, 3, 70);
        Ae2ThingsDiskStrings.share(shared);
        List<CompoundTag> after = new ArrayList<>();
        List<CompoundTag> before = new ArrayList<>();
        for (UUID id : twin.disks().keySet()) {
            ListTag a = shared.disks().get(id).stackKeys;
            ListTag b = twin.disks().get(id).stackKeys;
            for (int i = 0; i < a.size(); i++) {
                CompoundTag x = (CompoundTag) a.get(i);
                CompoundTag y = (CompoundTag) b.get(i);
                assertEquals(y, x);
                assertEquals(x, y);
                assertEquals(y.hashCode(), x.hashCode());
                after.add(x);
                before.add(y);
            }
        }
        // Relations between different keys are the same too, including near-misses.
        after.get(3).put("extra", StringTag.valueOf("Common"));
        before.get(3).put("extra", StringTag.valueOf("Common"));
        for (int i = 0; i < after.size(); i += 5) {
            for (int j = 0; j < after.size(); j += 7) {
                assertEquals(before.get(i).equals(before.get(j)), after.get(i).equals(after.get(j)), i + "," + j);
            }
        }
    }

    @Test
    void sharedTreeBehavesLikeTheOriginalUnderMutation() throws Exception {
        FakeStorageManager shared = manager(SEED + 3, 4, 50);
        FakeStorageManager twin = manager(SEED + 3, 4, 50);
        Ae2ThingsDiskStrings.share(shared);
        Random script = new Random(99);
        for (UUID id : twin.disks().keySet()) {
            ListTag a = shared.disks().get(id).stackKeys;
            ListTag b = twin.disks().get(id).stackKeys;
            for (int i = 0; i < a.size(); i++) {
                CompoundTag x = (CompoundTag) a.get(i);
                CompoundTag y = (CompoundTag) b.get(i);
                int step = script.nextInt(4);
                String name = "added" + script.nextInt(30);
                Tag value = script.nextBoolean() ? StringTag.valueOf("Common") : IntTag.valueOf(script.nextInt());
                if (step == 0) {
                    x.put(name, value);
                    y.put(name, value);
                } else if (step == 1) {
                    for (String key : new ArrayList<>(y.getAllKeys())) {
                        if (script.nextInt(3) == 0) {
                            x.remove(key);
                            y.remove(key);
                        }
                    }
                } else if (step == 2) {
                    // Replacing a shared string in one compound must not show in any other holder of that string.
                    x.putString("id", "changed-" + i);
                    y.putString("id", "changed-" + i);
                } else {
                    x.put("#c", StringTag.valueOf("ae2:f"));
                    y.put("#c", StringTag.valueOf("ae2:f"));
                    x.put("fresh", StringTag.valueOf("ae2:i"));
                    y.put("fresh", StringTag.valueOf("ae2:i"));
                }
            }
        }
        assertSameManagers(twin, shared);
    }

    @Test
    void aliasedStorageAndRepeatedSubtreesKeepTheirIdentityRelations() throws Exception {
        FakeStorageManager manager = manager(SEED + 4, 3, 40);
        FakeStorageManager twin = manager(SEED + 4, 3, 40);
        List<UUID> ids = new ArrayList<>(manager.disks().keySet());
        // The creative-clone path: two UUIDs, one DataStorage object.
        FakeDataStorage first = manager.disks().get(ids.get(0));
        manager.disks().put(ids.get(1), first);
        twin.disks().put(ids.get(1), twin.disks().get(ids.get(0)));
        // A subtree reachable from two parents, and one key list holding the same compound twice.
        CompoundTag common = (CompoundTag) first.stackKeys.get(0);
        FakeDataStorage third = manager.disks().get(ids.get(2));
        third.stackKeys.add(common);
        third.stackKeys.add(common);
        FakeDataStorage twinThird = twin.disks().get(ids.get(2));
        CompoundTag twinCommon = (CompoundTag) twin.disks().get(ids.get(0)).stackKeys.get(0);
        twinThird.stackKeys.add(twinCommon);
        twinThird.stackKeys.add(twinCommon);

        ListTag firstKeys = first.stackKeys;
        List<Tag> beforeIdentity = new ArrayList<>(first.stackKeys);
        Ae2ThingsDiskStrings.share(manager);

        assertSame(manager.disks().get(ids.get(0)), manager.disks().get(ids.get(1)));
        assertSame(first, manager.disks().get(ids.get(0)));
        assertSame(firstKeys, first.stackKeys);
        for (int i = 0; i < beforeIdentity.size(); i++) assertSame(beforeIdentity.get(i), first.stackKeys.get(i));
        assertSame(common, first.stackKeys.get(0));
        assertSame(common, third.stackKeys.get(third.stackKeys.size() - 1));
        assertSame(third.stackKeys.get(third.stackKeys.size() - 1), third.stackKeys.get(third.stackKeys.size() - 2));
        assertSameManagers(twin, manager);
        // The clone path still behaves: a change through one UUID shows through the other.
        ((CompoundTag) manager.disks().get(ids.get(1)).stackKeys.get(1)).putString("id", "edited");
        assertEquals("edited", ((CompoundTag) first.stackKeys.get(1)).getString("id"));
    }

    @Test
    void failureHalfwayLeavesTheTreeEqualToTheOriginal() throws Exception {
        for (int failAt : new int[] {1, 2, 3}) {
            for (boolean inList : new boolean[] {true, false}) {
                FakeStorageManager manager = manager(SEED + 5, 3, 45);
                FakeStorageManager twin = manager(SEED + 5, 3, 45);
                AtomicInteger writes = new AtomicInteger();
                // Plant a tag that throws on its failAt-th write into the middle of the first disk's keys.
                FakeDataStorage victim = manager.disks().values().iterator().next();
                FakeDataStorage twinVictim = twin.disks().values().iterator().next();
                CompoundTag key = (CompoundTag) victim.stackKeys.get(0);
                CompoundTag twinKey = (CompoundTag) twinVictim.stackKeys.get(0);
                if (inList) {
                    FailingList list = new FailingList(writes, failAt);
                    FailingList twinList = new FailingList(new AtomicInteger(), Integer.MAX_VALUE);
                    for (String text : new String[] {"Common", "Rare", "Common", "Common", "Rare", "Common"}) {
                        list.add(StringTag.valueOf(new String(text.toCharArray())));
                        twinList.add(StringTag.valueOf(new String(text.toCharArray())));
                    }
                    key.put("planted", list);
                    twinKey.put("planted", twinList);
                } else {
                    FailingCompound planted = new FailingCompound(writes, failAt);
                    FailingCompound twinPlanted = new FailingCompound(new AtomicInteger(), Integer.MAX_VALUE);
                    for (String name : new String[] {"a", "b", "c", "d", "e"}) {
                        planted.put(name, StringTag.valueOf(new String("Common".toCharArray())));
                        twinPlanted.put(name, StringTag.valueOf(new String("Common".toCharArray())));
                    }
                    key.put("planted", planted);
                    twinKey.put("planted", twinPlanted);
                }
                writes.set(0);
                assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded(manager));
                assertTrue(writes.get() >= failAt, "the injected failure must have fired");
                assertSameManagers(twin, manager);
            }
        }
    }

    @Test
    void faultyWalkReportsThroughShareAndStaysQuietThroughShareLoaded() {
        FakeStorageManager manager = manager(SEED + 6, 2, 30);
        FakeDataStorage storage = manager.disks().values().iterator().next();
        AtomicInteger writes = new AtomicInteger();
        FailingList list = new FailingList(writes, 1);
        list.add(StringTag.valueOf(new String("Common".toCharArray())));
        list.add(StringTag.valueOf(new String("Common".toCharArray())));
        ((CompoundTag) storage.stackKeys.get(0)).put("planted", list);
        assertThrows(IllegalStateException.class, () -> Ae2ThingsDiskStrings.share(manager));
        FakeStorageManager second = manager(SEED + 6, 2, 30);
        FakeDataStorage secondStorage = second.disks().values().iterator().next();
        FailingList again = new FailingList(new AtomicInteger(), 1);
        again.add(StringTag.valueOf(new String("Common".toCharArray())));
        again.add(StringTag.valueOf(new String("Common".toCharArray())));
        ((CompoundTag) secondStorage.stackKeys.get(0)).put("planted", again);
        assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded(second));
    }

    @Test
    void tooDeepNestingFailsClosedAndLeavesTheTreeValid() {
        FakeStorageManager manager = manager(SEED + 7, 1, 10);
        FakeStorageManager twin = manager(SEED + 7, 1, 10);
        for (FakeStorageManager target : List.of(manager, twin)) {
            CompoundTag root = new CompoundTag();
            CompoundTag cursor = root;
            for (int i = 0; i < 600; i++) {
                CompoundTag next = new CompoundTag();
                cursor.put("n", next);
                cursor = next;
            }
            cursor.putString("leaf", "Common");
            target.disks().values().iterator().next().stackKeys.add(root);
        }
        assertThrows(IllegalStateException.class, () -> Ae2ThingsDiskStrings.share(manager));
        assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded(manager));
        assertSameManagers(twin, manager);
    }

    @Test
    void absentOrDifferentlyShapedManagersAreLeftAlone() {
        assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded(null));
        assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded(new Object()));
        assertDoesNotThrow(() -> Ae2ThingsDiskStrings.shareLoaded("not a manager"));
        assertThrows(RuntimeException.class, () -> Ae2ThingsDiskStrings.share(new Object()));
        // Right field name, wrong shape: a public (non-final) map is not the reviewed layout.
        class Lookalike { public java.util.Map<UUID, Object> disks = new java.util.HashMap<>(); }
        assertThrows(RuntimeException.class, () -> Ae2ThingsDiskStrings.share(new Lookalike()));
    }

    @Test
    void emptyManagerAndEmptyDisksAreFine() throws Exception {
        FakeStorageManager empty = new FakeStorageManager();
        assertEquals(0, Ae2ThingsDiskStrings.share(empty).disks());
        empty.disks().put(UUID.randomUUID(), new FakeDataStorage());
        Ae2ThingsDiskStrings.Result result = Ae2ThingsDiskStrings.share(empty);
        assertEquals(1, result.disks());
        assertEquals(0, result.stringSlots());
    }

    /** Throws from its failAt-th set(), after the earlier ones went through. */
    private static final class FailingList extends ListTag {
        private final AtomicInteger writes;
        private final int failAt;

        FailingList(AtomicInteger writes, int failAt) {
            this.writes = writes;
            this.failAt = failAt;
        }

        @Override
        public Tag set(int index, Tag tag) {
            if (writes.incrementAndGet() == failAt) throw new IllegalStateException("injected set failure");
            return super.set(index, tag);
        }
    }

    /** Throws from its failAt-th put() that the sharing walk makes after construction. */
    private static final class FailingCompound extends CompoundTag {
        private final AtomicInteger writes;
        private final int failAt;
        private boolean armed;

        FailingCompound(AtomicInteger writes, int failAt) {
            this.writes = writes;
            this.failAt = failAt;
        }

        @Override
        public Tag put(String key, Tag value) {
            if (armed && writes.incrementAndGet() == failAt) throw new IllegalStateException("injected put failure");
            Tag previous = super.put(key, value);
            return previous;
        }

        @Override
        public java.util.Set<String> getAllKeys() {
            armed = true;
            return super.getAllKeys();
        }
    }
}
