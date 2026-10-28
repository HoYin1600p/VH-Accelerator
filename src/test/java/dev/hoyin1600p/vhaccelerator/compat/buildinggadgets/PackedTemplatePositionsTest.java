package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
class PackedTemplatePositionsTest {
    /** Stands in for BG's Template: a private final map field that the mixin redirects reads of. */
    static final class FakeTemplate {
        private final ImmutableMap<BlockPos, Object> map;
        FakeTemplate(ImmutableMap<BlockPos, Object> map) { this.map = map; }
        /** What the redirected readers (serialize, rotate, ...) see. */
        ImmutableMap<BlockPos, ?> read() { return PackedTemplatePositions.resolve(map); }
        /** withName and friends pass the raw field value to the new template. */
        FakeTemplate withName() { return new FakeTemplate(map); }
    }

    private static Field mapField() throws Exception {
        Field field = FakeTemplate.class.getDeclaredField("map");
        assertTrue(field.trySetAccessible());
        return field;
    }

    @BeforeEach @AfterEach void resetQueue() { CompactTemplateLookup.DeferredCompaction.reset(); }

    /** Template-like contents: normalized coordinates, a few shared block objects, some negative coordinates. */
    private static ImmutableMap<BlockPos, Object> template(long seed, int size, int paletteSize, boolean negative) {
        Random random = new Random(seed);
        Object[] palette = new Object[paletteSize];
        for (int i = 0; i < palette.length; i++) palette[i] = new String("block-" + i);
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
        java.util.HashSet<Long> used = new java.util.HashSet<>();
        while (used.size() < size) {
            int span = negative ? 200 : 100;
            int x = random.nextInt(span) - (negative ? 100 : 0);
            int y = random.nextInt(span) - (negative ? 100 : 0);
            int z = random.nextInt(span) - (negative ? 100 : 0);
            if (used.add(BlockPos.asLong(x, y, z))) builder.put(new BlockPos(x, y, z), palette[random.nextInt(paletteSize)]);
        }
        return builder.build();
    }

    private static void assertSameEntries(ImmutableMap<BlockPos, ?> expected, ImmutableMap<BlockPos, ?> actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        var left = expected.entrySet().iterator();
        var right = actual.entrySet().iterator();
        while (left.hasNext()) {
            assertTrue(right.hasNext());
            var a = left.next();
            var b = right.next();
            assertEquals(a.getKey().getClass(), b.getKey().getClass());
            assertEquals(a.getKey(), b.getKey());
            assertEquals(a.getKey().hashCode(), b.getKey().hashCode());
            assertSame(a.getValue(), b.getValue(), "block data object identity");
            assertSame(b.getValue(), actual.get(b.getKey()));
        }
        assertFalse(right.hasNext());
    }

    @Test void packedMapResolvesToTheSameEntriesInTheSameOrderForEverySizeAndPalette() {
        int[][] shapes = {{8, 1}, {9, 3}, {100, 7}, {2048, 255}, {2049, 256}, {5000, 257}, {20000, 4000}, {70000, 70000}};
        for (boolean negative : new boolean[] {false, true}) {
            for (int[] shape : shapes) {
                ImmutableMap<BlockPos, Object> source = template(shape[0] * 31L + shape[1], shape[0], shape[1], negative);
                ImmutableMap<BlockPos, ?> marker = PackedTemplatePositions.pack(source);
                assertNotNull(marker, shape[0] + "x" + shape[1]);
                assertTrue(PackedTemplatePositions.isMarker(marker));
                assertFalse(PackedTemplatePositions.isMarker(source));
                assertSameEntries(source, PackedTemplatePositions.resolve(marker));
            }
        }
    }

    @Test void bothGuavaLayoutsRoundTrip() {
        ImmutableMap.Builder<BlockPos, Object> collisions = ImmutableMap.builder();
        Object block = new Object();
        // Equal-hash keys overflow a Guava bucket, which selects the JDK-backed layout.
        for (int i = 0; i < 40; i++) collisions.put(new BlockPos(961 * i, 0, 63 - i), i % 2 == 0 ? block : new Object());
        ImmutableMap<BlockPos, Object> jdkBacked = collisions.build();
        assertEquals("com.google.common.collect.JdkBackedImmutableMap", jdkBacked.getClass().getName());
        assertSameEntries(jdkBacked, PackedTemplatePositions.resolve(PackedTemplatePositions.pack(jdkBacked)));
        ImmutableMap<BlockPos, Object> regular = template(5, 30, 4, false);
        assertNotEquals("com.google.common.collect.JdkBackedImmutableMap", regular.getClass().getName());
        assertSameEntries(regular, PackedTemplatePositions.resolve(PackedTemplatePositions.pack(regular)));
    }

    @Test void valuesThatAreEqualButNotIdenticalStayDistinctObjects() {
        ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
        for (int i = 0; i < 20; i++) builder.put(new BlockPos(i, 0, 0), new String("same"));
        ImmutableMap<BlockPos, Object> source = builder.build();
        assertSameEntries(source, PackedTemplatePositions.resolve(PackedTemplatePositions.pack(source)));
    }

    @Test void ineligibleMapsAreRefused() {
        assertNull(PackedTemplatePositions.pack(null));
        assertNull(PackedTemplatePositions.pack(ImmutableMap.of()));
        assertNull(PackedTemplatePositions.pack(template(1, PackedTemplatePositions.MINIMUM_SIZE - 1, 2, false)));
        ImmutableMap<BlockPos, Object> tooFar = ImmutableMap.<BlockPos, Object>builder()
                .putAll(template(2, 20, 2, false)).put(new BlockPos(1 << 29, 0, 0), new Object()).build();
        assertNull(PackedTemplatePositions.pack(tooFar), "coordinates outside the lossless packing range");
        ImmutableMap<BlockPos, Object> mutableKey = ImmutableMap.<BlockPos, Object>builder()
                .putAll(template(3, 20, 2, false)).put(new BlockPos.MutableBlockPos(500, 500, 500), new Object()).build();
        assertNull(PackedTemplatePositions.pack(mutableKey), "only plain BlockPos keys");
        ImmutableMap<BlockPos, ?> marker = PackedTemplatePositions.pack(template(4, 20, 2, false));
        assertNull(PackedTemplatePositions.pack(marker), "an already packed map is not packed again");
    }

    @Test void ordinaryMapsAreNeverMistakenForMarkers() {
        Object value = new Object();
        ImmutableMap<BlockPos, Object> single = ImmutableMap.of(new BlockPos(0, 0, 0), value);
        assertFalse(PackedTemplatePositions.isMarker(single));
        assertSame(single, PackedTemplatePositions.resolve(single));
        assertSame(ImmutableMap.of(), PackedTemplatePositions.resolve(ImmutableMap.of()));
        assertNull(PackedTemplatePositions.resolve(null));
        // A marker-shaped map with a foreign value is not a marker either.
        assertSame(single, PackedTemplatePositions.resolve(single));
    }

    @Test void rebuiltMapIsSharedWhileHeldAndReleasedAfter() throws Exception {
        ImmutableMap<BlockPos, ?> marker = PackedTemplatePositions.pack(template(6, 500, 5, false));
        ImmutableMap<BlockPos, ?> first = PackedTemplatePositions.resolve(marker);
        assertSame(first, PackedTemplatePositions.resolve(marker));
        WeakReference<Object> reference = new WeakReference<>(first);
        first = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (reference.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(20); }
        assertNull(reference.get(), "a rebuilt map must not be retained once its callers are done");
    }

    @Test void installReplacesTheFieldAndTemplateCopiesShareThePackedArrays() throws Exception {
        ImmutableMap<BlockPos, Object> source = template(7, 600, 6, false);
        FakeTemplate template = new FakeTemplate(source);
        Field field = mapField();
        int[] used = {0};
        assertTrue(PackedTemplatePositions.installDeferred(template, field, source, () -> used[0]++));
        assertEquals(1, used[0]);
        assertTrue(PackedTemplatePositions.isMarker((ImmutableMap<?, ?>) field.get(template)));
        assertSameEntries(source, template.read());
        FakeTemplate renamed = template.withName();
        assertSameEntries(source, renamed.read());
        assertSame(field.get(template), field.get(renamed), "copies share the packed marker");
        assertFalse(PackedTemplatePositions.installDeferred(template, field, source, null), "second install is a no-op");
        assertFalse(PackedTemplatePositions.installNow(renamed, field));
    }

    @Test void maximumPaletteIdsAndHugeTemplatesDoNotOverflowTheIndex() {
        // Exactly 256 and 65536 distinct values use the largest ids of the byte and char index widths.
        for (int distinct : new int[] {256, 65536, 65537}) {
            ImmutableMap.Builder<BlockPos, Object> builder = ImmutableMap.builder();
            for (int i = 0; i < distinct; i++) builder.put(new BlockPos(i % 100, (i / 100) % 100, i / 10000), new Object());
            ImmutableMap<BlockPos, Object> source = builder.build();
            assertSameEntries(source, PackedTemplatePositions.resolve(PackedTemplatePositions.pack(source)));
        }
    }

    @Test void largeTemplatesArePackedOnTheWorkerAndOnlyHeldWeakly() throws Exception {
        ImmutableMap<BlockPos, Object> source = template(8, CompactTemplateLookup.SYNCHRONOUS_LIMIT + 500, 12, false);
        FakeTemplate template = new FakeTemplate(source);
        Field field = mapField();
        CountDownLatch installed = new CountDownLatch(1);
        assertTrue(PackedTemplatePositions.installDeferred(template, field, source, installed::countDown));
        assertTrue(installed.await(30, TimeUnit.SECONDS));
        assertTrue(PackedTemplatePositions.isMarker((ImmutableMap<?, ?>) field.get(template)));
        assertSameEntries(source, template.read());

        // A template queued and then dropped is not kept alive by the queue.
        FakeTemplate dropped = new FakeTemplate(template(9, CompactTemplateLookup.SYNCHRONOUS_LIMIT + 10, 3, false));
        CompactTemplateLookup.DeferredCompaction.shutdown();
        CompactTemplateLookup.DeferredCompaction.reset();
        assertTrue(CompactTemplateLookup.DeferredCompaction.enqueue(dropped, subject -> false, null));
        WeakReference<Object> reference = new WeakReference<>(dropped);
        dropped = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (reference.get() != null && System.nanoTime() < deadline) { System.gc(); Thread.sleep(20); }
        assertNull(reference.get());
    }

    @Test void unpackableMapsKeepTheirOriginalFieldValue() throws Exception {
        ImmutableMap<BlockPos, Object> tooFar = ImmutableMap.<BlockPos, Object>builder()
                .putAll(template(10, 40, 2, false)).put(new BlockPos(1 << 29, 0, 0), new Object()).build();
        FakeTemplate template = new FakeTemplate(tooFar);
        assertFalse(PackedTemplatePositions.installDeferred(template, mapField(), tooFar, null));
        assertSame(tooFar, mapField().get(template));
        assertSame(tooFar, template.read());
    }

    @Test void changedFieldBlocksPublication() throws Exception {
        ImmutableMap<BlockPos, Object> source = template(11, 100, 4, false);
        FakeTemplate template = new FakeTemplate(source);
        Field field = mapField();
        ImmutableMap<BlockPos, Object> other = template(12, 100, 4, false);
        field.set(template, other);
        // The worker saw `source` earlier, but the field now holds a different map: nothing may be replaced.
        assertTrue(PackedTemplatePositions.installNow(template, field), "packs whatever the field holds now");
        assertSameEntries(other, template.read());
    }

    @Test void readersDuringPublicationAlwaysSeeCompleteEqualMaps() throws Exception {
        for (int round = 0; round < 20; round++) {
            ImmutableMap<BlockPos, Object> source = template(100 + round, 3000, 9, round % 2 == 0);
            FakeTemplate template = new FakeTemplate(source);
            Field field = mapField();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch go = new CountDownLatch(1);
            List<Thread> readers = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Thread reader = new Thread(() -> {
                    try {
                        go.await();
                        for (int n = 0; n < 200 && failure.get() == null; n++) {
                            ImmutableMap<BlockPos, ?> seen = template.read();
                            assertEquals(source.size(), seen.size());
                            assertEquals(source, seen);
                        }
                    } catch (Throwable thrown) { failure.compareAndSet(null, thrown); }
                });
                reader.start();
                readers.add(reader);
            }
            go.countDown();
            assertTrue(PackedTemplatePositions.installNow(template, field));
            for (Thread reader : readers) reader.join(60_000);
            assertNull(failure.get(), () -> String.valueOf(failure.get()));
            assertSameEntries(source, template.read());
        }
    }

    @Test void positionsKeepTheirNormalizedMeaningAfterRepeatedPackingCycles() {
        // pack -> resolve -> pack again must be a fixed point for entries and order.
        ImmutableMap<BlockPos, Object> source = template(13, 1000, 8, true);
        ImmutableMap<BlockPos, ?> once = PackedTemplatePositions.resolve(PackedTemplatePositions.pack(source));
        @SuppressWarnings("unchecked") ImmutableMap<BlockPos, Object> typed = (ImmutableMap<BlockPos, Object>) once;
        ImmutableMap<BlockPos, ?> twice = PackedTemplatePositions.resolve(PackedTemplatePositions.pack(typed));
        assertSameEntries(source, twice);
        Map.Entry<BlockPos, ?> first = twice.entrySet().iterator().next();
        assertEquals(source.entrySet().iterator().next().getKey(), first.getKey());
    }

    @SuppressWarnings("unchecked")
    private static PackedTemplatePositions packed(ImmutableMap<BlockPos, ?> marker) {
        return (PackedTemplatePositions) marker.values().iterator().next();
    }

    @Test void streamedEntriesMatchTheSourceEntryByEntry() {
        int[][] shapes = {{8, 1}, {100, 7}, {2049, 256}, {20000, 4000}, {70000, 70000}};
        for (boolean negative : new boolean[] {false, true}) {
            for (int[] shape : shapes) {
                ImmutableMap<BlockPos, Object> source = template(shape[0] * 17L + shape[1], shape[0], shape[1], negative);
                ImmutableMap<BlockPos, ?> marker = PackedTemplatePositions.pack(source);
                var streamed = PackedTemplatePositions.entries(marker.entrySet());
                var expected = source.entrySet().iterator();
                int count = 0;
                while (expected.hasNext()) {
                    assertTrue(streamed.hasNext());
                    var left = expected.next();
                    var right = streamed.next();
                    assertEquals(BlockPos.class, right.getKey().getClass());
                    assertEquals(left.getKey(), right.getKey());
                    assertEquals(left.getKey().hashCode(), right.getKey().hashCode());
                    assertSame(left.getValue(), right.getValue());
                    assertThrows(UnsupportedOperationException.class, streamed::remove);
                    count++;
                }
                assertFalse(streamed.hasNext());
                assertThrows(java.util.NoSuchElementException.class, streamed::next);
                assertEquals(source.size(), count);
            }
        }
    }

    @Test void entriesOfOrdinarySetsIterateNormally() {
        ImmutableMap<BlockPos, Object> source = template(21, 50, 3, false);
        var iterator = PackedTemplatePositions.entries(source.entrySet());
        assertEquals(source.entrySet().iterator().next(), iterator.next());
        ImmutableMap<BlockPos, Object> single = ImmutableMap.of(new BlockPos(0, 0, 0), new Object());
        assertEquals(single.entrySet().iterator().next(), PackedTemplatePositions.entries(single.entrySet()).next());
        assertFalse(PackedTemplatePositions.entries(ImmutableMap.<BlockPos, Object>of().entrySet()).hasNext());
    }

    @Test void lightViewBehavesLikeTheOriginalMapForReaders() {
        ImmutableMap<BlockPos, Object> source = template(22, 3000, 9, true);
        Map<BlockPos, Object> view = packed(PackedTemplatePositions.pack(source)).view();
        assertEquals(source.size(), view.size());
        assertFalse(view.isEmpty());
        assertSameEntries(source, ImmutableMap.copyOf(view));
        var expected = source.entrySet().iterator();
        for (var entry : view.entrySet()) {
            var original = expected.next();
            assertEquals(original.getKey(), entry.getKey());
            assertSame(original.getValue(), entry.getValue());
        }
        assertEquals(source, view);
        assertEquals(source.hashCode(), view.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> view.put(new BlockPos(0, 0, 0), new Object()));
        assertEquals(source.size(), java.util.stream.StreamSupport.stream(view.entrySet().spliterator(), false).count());
    }
}
