package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that building the packs' indexes on workers gives exactly the index
 * the querying thread would have built, and that the hand-off never blocks.
 */
final class PackIndexConcurrentBuildTest {
    @TempDir
    Path temporaryDirectory;

    private final List<FileSystem> open = new ArrayList<>();

    @AfterEach
    void closeFileSystems() throws Exception {
        for (FileSystem fileSystem : this.open) {
            fileSystem.close();
        }
    }

    /** Jars with uneven sizes, wide directories, shared directory names and deep chains. */
    private List<Path> syntheticPacks(int count) throws Exception {
        Random random = new Random(20261024L);
        List<Path> roots = new ArrayList<>();
        for (int pack = 0; pack < count; pack++) {
            Path archive = this.temporaryDirectory.resolve("pack" + pack + ".jar");
            FileSystem fileSystem = FileSystems.newFileSystem(
                    URI.create("jar:" + archive.toUri()), Map.of("create", "true"));
            this.open.add(fileSystem);
            Path root = fileSystem.getPath("/");
            int files = 5 + random.nextInt(pack % 5 == 0 ? 900 : 120);
            for (int file = 0; file < files; file++) {
                String namespace = file % 11 == 0 ? "shared" : "mod" + pack;
                String kind = switch (random.nextInt(5)) {
                    case 0 -> "textures/block";
                    case 1 -> "models/item";
                    case 2 -> "lang";
                    case 3 -> "textures/gui/deep/er";
                    default -> "models/block";
                };
                String type = random.nextInt(4) == 0 ? "data" : "assets";
                if (type.equals("data")) {
                    kind = random.nextBoolean() ? "recipes" : "loot_tables/blocks";
                }
                String suffix = random.nextInt(6) == 0 ? ".json.mcmeta" : ".json";
                write(root, type + "/" + namespace + "/" + kind + "/f" + random.nextInt(2000) + suffix);
            }
            roots.add(root);
        }
        return roots;
    }

    private static void write(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.write(file, new byte[] {1});
        }
    }

    private static ImmutablePathPackIndex index(Path root) {
        ImmutablePathPackIndex index = ImmutablePathPackIndex.create(
                root, paths -> {
                    Path resolved = root;
                    for (String part : paths) {
                        resolved = resolved.resolve(part);
                    }
                    return resolved;
                });
        assertNotNull(index);
        return index;
    }

    @Test
    void concurrentPerPackBuildsEqualSerialBuilds() throws Exception {
        List<Path> roots = syntheticPacks(60);

        List<String> serial = new ArrayList<>();
        for (Path root : roots) {
            serial.add(index(root).describeForTesting());
        }

        // Fresh indexes over the same packs, all built at once by many threads,
        // each pack requested by two threads so the per-pack lock is exercised.
        List<ImmutablePathPackIndex> fresh = new ArrayList<>();
        for (Path root : roots) {
            fresh.add(index(root));
        }
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int round = 0; round < 2; round++) {
                for (ImmutablePathPackIndex index : fresh) {
                    futures.add(pool.submit(index::prewarm));
                }
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (int pack = 0; pack < roots.size(); pack++) {
            assertEquals(serial.get(pack), fresh.get(pack).describeForTesting(),
                    "pack " + pack + " differs between serial and concurrent builds");
        }
        assertTrue(serial.stream().anyMatch(text -> text.length() > 2000),
                "the fixture must contain non-trivial packs");
    }

    @Test
    void queriesRacingWithPrewarmSeeTheSameAnswersAsSerialQueries() throws Exception {
        List<Path> roots = syntheticPacks(24);
        List<ImmutablePathPackIndex> reference = new ArrayList<>();
        for (Path root : roots) {
            reference.add(index(root));
        }
        List<ImmutablePathPackIndex> racing = new ArrayList<>();
        for (Path root : roots) {
            racing.add(index(root));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (ImmutablePathPackIndex index : racing) {
                pool.submit(index::prewarm);
            }
            for (int pack = 0; pack < roots.size(); pack++) {
                String namespace = "mod" + pack;
                var expected = reference.get(pack).resources(PackType.CLIENT_RESOURCES,
                        namespace, "textures", Integer.MAX_VALUE, name -> true);
                var actual = racing.get(pack).resources(PackType.CLIENT_RESOURCES,
                        namespace, "textures", Integer.MAX_VALUE, name -> true);
                assertEquals(expected, actual, "listing order must match for " + namespace);
                assertEquals(reference.get(pack).hasResource("assets/shared/lang/f1.json"),
                        racing.get(pack).hasResource("assets/shared/lang/f1.json"));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void absentResourceRootsBuildAnEmptyIndex() throws Exception {
        List<Path> roots = syntheticPacks(1);
        Path root = roots.get(0);
        ImmutablePathPackIndex broken = ImmutablePathPackIndex.create(root, paths -> {
            // Resolves to a path that is not a directory in the archive.
            return root.resolve("missing").resolve(paths[0]);
        });
        assertNotNull(broken);
        broken.prewarm();
        broken.prewarm();
        // Missing roots are "not present", not a failure; the index still answers.
        assertEquals(Boolean.FALSE, broken.hasResource("assets/mod0/lang/f1.json"));
    }
}
