package dev.hoyin1600p.vhaccelerator.client.model;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.Manifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.MaterialId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DeferredBlockStateMaterialRecorderTest {
    private static final int STATES = 64;
    private static final String ATLAS =
            PersistentDeferredBlockStateManifest.BLOCK_ATLAS;
    private static final String FINGERPRINT = "v5|a|b|c|d";
    private static final String FURNACE =
            "minecraft:furnace#facing=north,lit=false";
    private static final String STAIRS =
            "minecraft:oak_stairs#facing=east,half=bottom,"
                    + "shape=straight,waterlogged=false";
    private static final String LEVER =
            "minecraft:lever#face=wall,facing=north,powered=false";

    private static MaterialId texture(String texture) {
        return new MaterialId(ATLAS, texture);
    }

    private static List<MaterialId> plain(String texture) {
        return List.of(texture(texture));
    }

    private static DeferredBlockStateMaterialRecorder.Result record(
            String fingerprint,
            List<String> keys,
            Map<String, Collection<MaterialId>> materials
    ) {
        return recordUngrouped(
                fingerprint,
                () -> keys,
                materials::get
        );
    }

    private static DeferredBlockStateMaterialRecorder.StateGroup ungrouped(
            String key
    ) {
        return new DeferredBlockStateMaterialRecorder.StateGroup(
                PersistentDeferredBlockStateManifest.blockOf(key),
                STATES,
                PersistentDeferredBlockStateManifest.UNGROUPED
        );
    }

    private static DeferredBlockStateMaterialRecorder.Result recordUngrouped(
            String fingerprint,
            Supplier<? extends Iterable<? extends String>> candidates,
            Function<? super String, ? extends Collection<MaterialId>> materials
    ) {
        return DeferredBlockStateMaterialRecorder.record(
                fingerprint,
                candidates,
                key -> PersistentDeferredBlockStateManifest.validModelKey(key)
                        ? ungrouped(key)
                        : null,
                materials
        );
    }

    private static DeferredBlockStateMaterialRecorder.Result recordGroups(
            List<String> keys,
            Map<String, DeferredBlockStateMaterialRecorder.StateGroup> groups
    ) {
        return DeferredBlockStateMaterialRecorder.record(
                FINGERPRINT,
                () -> keys,
                groups::get,
                key -> plain("minecraft:block/stone")
        );
    }

    private static DeferredBlockStateMaterialRecorder.StateGroup group(
            String key,
            int states,
            int group
    ) {
        return new DeferredBlockStateMaterialRecorder.StateGroup(
                PersistentDeferredBlockStateManifest.blockOf(key), states, group);
    }

    @Test
    void groupsAreCanonicalPerBlockWithCoverage() throws IOException {
        String a = "minecraft:lever#face=wall,facing=east,powered=false";
        String b = "minecraft:lever#face=wall,facing=north,powered=false";
        String c = "minecraft:lever#face=wall,facing=south,powered=false";
        String d = "minecraft:lever#face=wall,facing=west,powered=false";
        String e = "minecraft:oak_stairs#facing=east,half=top,"
                + "shape=straight,waterlogged=false";
        Map<String, DeferredBlockStateMaterialRecorder.StateGroup> groups =
                new java.util.HashMap<>();
        groups.put(a, group(a, 4, 907));
        groups.put(b, group(b, 4, 12));
        groups.put(c, group(c, 4, 907));
        groups.put(d, group(d, 4, PersistentDeferredBlockStateManifest.NON_MODEL_GROUP));
        groups.put(STAIRS, group(STAIRS, 80, 44));
        groups.put(e, group(e, 80, PersistentDeferredBlockStateManifest.UNGROUPED));
        DeferredBlockStateMaterialRecorder.Result result =
                recordGroups(List.of(STAIRS, d, c, e, b, a), groups);
        Manifest manifest = result.manifest();
        assertNotNull(manifest);
        assertEquals(6, result.recorded());
        assertEquals(Integer.valueOf(1), manifest.group(a));
        assertEquals(Integer.valueOf(2), manifest.group(b));
        assertEquals(Integer.valueOf(1), manifest.group(c));
        assertEquals(Integer.valueOf(0), manifest.group(d));
        assertEquals(Integer.valueOf(1), manifest.group(STAIRS));
        assertEquals(Integer.valueOf(-1), manifest.group(e));
        assertNull(manifest.group(FURNACE));
        assertTrue(manifest.coverage("minecraft:lever").complete());
        assertEquals(new PersistentDeferredBlockStateManifest.BlockCoverage(80, 2),
                manifest.coverage("minecraft:oak_stairs"));
        assertFalse(manifest.coverage("minecraft:oak_stairs").complete());
        assertEquals(1, manifest.completeBlocks());
        Manifest read = PersistentDeferredBlockStateManifest.read(
                new ByteArrayInputStream(encode(manifest)));
        assertNotNull(read);
        assertEquals(Integer.valueOf(2), read.group(b));
        assertEquals(1, read.completeBlocks());
        assertArrayEquals(encode(manifest), encode(read));
    }

    @Test
    void uncertainGroupsAreSkipped() {
        Map<String, DeferredBlockStateMaterialRecorder.StateGroup> groups =
                new java.util.HashMap<>();
        groups.put(FURNACE, null);
        groups.put(LEVER, new DeferredBlockStateMaterialRecorder.StateGroup(
                "minecraft:furnace", 24, -1));
        groups.put(STAIRS, group(STAIRS, 80, -2));
        String wheat = "minecraft:wheat#age=7";
        groups.put(wheat, group(wheat, 0, -1));
        String cake = "minecraft:cake#bites=6";
        groups.put(cake, group(cake, 7, 3));
        DeferredBlockStateMaterialRecorder.Result result = recordGroups(
                List.of(FURNACE, LEVER, STAIRS, wheat, cake), groups);
        assertEquals(4, result.skipped());
        assertEquals(1, result.recorded());
        assertTrue(result.manifest().contains(cake));
    }

    @Test
    void inconsistentBlocksAreSkippedWhole() {
        String north = "minecraft:furnace#facing=north,lit=true";
        String south = "minecraft:furnace#facing=south,lit=false";
        String wheat = "minecraft:wheat#age=7";
        String cake = "minecraft:cake#bites=6";
        String cake5 = "minecraft:cake#bites=5";
        String lever = LEVER;
        String leverUp = "minecraft:lever#face=floor,facing=north,powered=false";
        Map<String, DeferredBlockStateMaterialRecorder.StateGroup> groups =
                new java.util.HashMap<>();
        // Furnace disagrees on its state count.
        groups.put(FURNACE, group(FURNACE, 8, -1));
        groups.put(north, group(north, 9, -1));
        // Wheat and cake share raw ID 5.
        groups.put(wheat, group(wheat, 8, 5));
        groups.put(cake, group(cake, 7, 5));
        groups.put(cake5, group(cake5, 7, 6));
        // Lever records more keys than its states.
        groups.put(lever, group(lever, 1, -1));
        groups.put(leverUp, group(leverUp, 1, -1));
        groups.put(south, group(south, 8, -1));
        groups.put(STAIRS, group(STAIRS, 80, -1));
        DeferredBlockStateMaterialRecorder.Result result = recordGroups(
                List.of(FURNACE, north, south, wheat, cake, cake5, lever,
                        leverUp, STAIRS),
                groups);
        assertEquals(8, result.skipped());
        assertEquals(1, result.recorded());
        Manifest manifest = result.manifest();
        assertNotNull(manifest);
        assertEquals(List.of(STAIRS), new ArrayList<>(manifest.keys()));
    }

    private static byte[] encode(Manifest manifest) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PersistentDeferredBlockStateManifest.write(manifest, bytes);
        return bytes.toByteArray();
    }

    private static BooleanSupplier constant(boolean value, AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return value;
        };
    }

    @Test
    void gateRequiresEveryCondition() {
        for (int mask = 0; mask < 32; mask++) {
            boolean finished = (mask & 1) != 0;
            boolean flag = (mask & 2) != 0;
            boolean optimizations = (mask & 4) != 0;
            boolean compare = (mask & 8) != 0;
            boolean compatible = (mask & 16) != 0;
            boolean expected = !finished && flag && optimizations
                    && !compare && compatible;
            assertEquals(expected, DeferredBlockStateMaterialRecorder.shouldRecord(
                    finished,
                    () -> flag,
                    () -> optimizations,
                    () -> compare,
                    () -> compatible
            ), "mask " + mask);
        }
    }

    @Test
    void disabledFlagEvaluatesNothingAfterIt() {
        AtomicInteger later = new AtomicInteger();
        AtomicInteger flagReads = new AtomicInteger();
        assertFalse(DeferredBlockStateMaterialRecorder.shouldRecord(
                false,
                constant(false, flagReads),
                constant(true, later),
                constant(false, later),
                constant(true, later)
        ));
        assertEquals(1, flagReads.get());
        assertEquals(0, later.get());
    }

    @Test
    void laterReloadsDoNotEvenReadTheFlag() {
        AtomicInteger reads = new AtomicInteger();
        assertFalse(DeferredBlockStateMaterialRecorder.shouldRecord(
                true,
                constant(true, reads),
                constant(true, reads),
                constant(false, reads),
                constant(true, reads)
        ));
        assertEquals(0, reads.get());
    }

    @Test
    void nullFingerprintReadsNoCandidatesAndWritesNothing() {
        AtomicInteger reads = new AtomicInteger();
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        null,
                        () -> {
                            reads.incrementAndGet();
                            return List.of(FURNACE);
                        },
                        key -> {
                            reads.incrementAndGet();
                            return plain("minecraft:block/furnace_top");
                        }
                );
        assertFalse(result.fingerprinted());
        assertNull(result.manifest());
        assertEquals(0, reads.get());
    }

    @Test
    void ineligibleKeysAreSkippedWithoutMaterialCollection() {
        AtomicInteger collected = new AtomicInteger();
        List<String> keys = List.of(
                "minecraft:stone#",
                "minecraft:stone#inventory",
                "create:shaft#axis=x",
                "minecraft:stone",
                FURNACE
        );
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        FINGERPRINT,
                        () -> keys,
                        key -> {
                            collected.incrementAndGet();
                            return plain("minecraft:block/furnace_top");
                        }
                );
        assertEquals(5, result.candidates());
        assertEquals(1, result.recorded());
        assertEquals(4, result.skipped());
        assertEquals(1, collected.get());
        assertNotNull(result.manifest());
        assertEquals(List.of(FURNACE), new ArrayList<>(result.manifest().keys()));
    }

    @Test
    void invalidMaterialListsDoNotPoisonTheManifest() {
        List<MaterialId> tooMany = new ArrayList<>();
        for (int index = 0; index < 513; index++) {
            tooMany.add(texture("minecraft:block/t" + index));
        }
        String[] bad = {
                "minecraft:a#b=c",
                "minecraft:d#e=f",
                "minecraft:g#h=i",
                "minecraft:j#k=l",
                "minecraft:m#n=o",
                "minecraft:p#q=r"
        };
        Map<String, Collection<MaterialId>> materials = new java.util.HashMap<>();
        materials.put(bad[0], List.of());
        materials.put(bad[1], null);
        materials.put(bad[2], List.of(
                texture("minecraft:block/stone"),
                texture(PersistentDeferredBlockStateManifest.MISSING_TEXTURE)));
        materials.put(bad[3], List.of(new MaterialId(
                "minecraft:textures/atlas/signs.png", "minecraft:entity/signs/oak")));
        materials.put(bad[4], tooMany);
        materials.put(bad[5], Arrays.asList(texture("minecraft:block/stone"), null));
        materials.put(FURNACE, plain("minecraft:block/furnace_top"));
        materials.put(STAIRS, plain("minecraft:block/oak_planks"));

        List<String> keys = new ArrayList<>(Arrays.asList(bad));
        keys.add(1, FURNACE);
        keys.add(STAIRS);
        DeferredBlockStateMaterialRecorder.Result result =
                record(FINGERPRINT, keys, materials);
        assertEquals(bad.length, result.skipped());
        assertEquals(0, result.failed());
        assertEquals(2, result.recorded());
        Manifest manifest = result.manifest();
        assertNotNull(manifest);
        assertTrue(manifest.contains(FURNACE));
        assertTrue(manifest.contains(STAIRS));
        for (String key : bad) {
            assertFalse(manifest.contains(key), key);
        }
    }

    @Test
    void duplicateKeyKeepsTheFirstEntry() {
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        FINGERPRINT,
                        () -> List.of(FURNACE, FURNACE),
                        key -> plain("minecraft:block/furnace_top")
                );
        assertEquals(1, result.recorded());
        assertEquals(1, result.skipped());
        assertNotNull(result.manifest());
    }

    @Test
    void throwingGraphIsSkippedAndOthersRecorded() {
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        FINGERPRINT,
                        () -> List.of(FURNACE, LEVER, STAIRS),
                        key -> {
                            if (LEVER.equals(key)) {
                                throw new IllegalStateException("broken graph");
                            }
                            return plain("minecraft:block/stone");
                        }
                );
        assertEquals(1, result.failed());
        assertEquals(2, result.recorded());
        Manifest manifest = result.manifest();
        assertNotNull(manifest);
        assertFalse(manifest.contains(LEVER));
        assertTrue(result.details().get(0).contains(LEVER));
        assertTrue(result.details().get(0).contains("IllegalStateException"));
    }

    @Test
    void candidateSourceFailurePropagatesSoNothingIsWritten() {
        assertThrows(IllegalStateException.class, () ->
                recordUngrouped(
                        FINGERPRINT,
                        () -> {
                            throw new IllegalStateException("selector failed");
                        },
                        key -> plain("minecraft:block/stone")
                ));
    }

    @Test
    void nothingRecordedMeansNoManifest() {
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        FINGERPRINT,
                        () -> List.of(FURNACE),
                        key -> List.of()
                );
        assertTrue(result.fingerprinted());
        assertNull(result.manifest());
    }

    @Test
    void outputIsDeterministicAndRoundTrips() throws IOException {
        Map<String, Collection<MaterialId>> materials = Map.of(
                FURNACE, List.of(
                        texture("minecraft:block/furnace_top"),
                        texture("minecraft:block/furnace_front"),
                        texture("minecraft:block/furnace_side")),
                STAIRS, plain("minecraft:block/oak_planks"),
                LEVER, List.of(
                        texture("minecraft:block/lever"),
                        texture("minecraft:block/cobblestone"))
        );
        Map<String, Collection<MaterialId>> reversed = Map.of(
                FURNACE, List.of(
                        texture("minecraft:block/furnace_side"),
                        texture("minecraft:block/furnace_front"),
                        texture("minecraft:block/furnace_top")),
                STAIRS, plain("minecraft:block/oak_planks"),
                LEVER, List.of(
                        texture("minecraft:block/cobblestone"),
                        texture("minecraft:block/lever"))
        );
        Manifest first = record(
                FINGERPRINT, List.of(FURNACE, STAIRS, LEVER), materials).manifest();
        Manifest second = record(
                FINGERPRINT, List.of(LEVER, STAIRS, FURNACE), reversed).manifest();
        assertNotNull(first);
        assertNotNull(second);
        byte[] firstBytes = encode(first);
        assertArrayEquals(firstBytes, encode(second));

        Manifest read = PersistentDeferredBlockStateManifest.read(
                new ByteArrayInputStream(firstBytes));
        assertNotNull(read);
        assertTrue(read.matches(FINGERPRINT));
        assertEquals(first.keys(), read.keys());
        assertArrayEquals(firstBytes, encode(read));
    }

    @Test
    void detailsAreBounded() {
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            keys.add("minecraft:bad" + index + "#inventory");
        }
        DeferredBlockStateMaterialRecorder.Result result =
                recordUngrouped(
                        FINGERPRINT,
                        () -> keys,
                        key -> plain("minecraft:block/stone")
                );
        assertEquals(20, result.skipped());
        assertEquals(DeferredBlockStateMaterialRecorder.MAX_DETAILS,
                result.details().size());
    }
}
