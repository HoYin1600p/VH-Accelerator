package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.resource.PackIndexPrewarmer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Offline timing of the first query of every pack, as the main thread sees it
 * during a resource reload: each pack's index is built by whoever queries it
 * first ("serial"), or by the shared workers after the first query kicked them
 * ("prewarm"). Reads the jars of a mods folder; writes nothing. Not a test.
 *
 * <p>Usage: {@code PackIndexBench <modsDir> <serial|prewarm> <rounds> [platformwarm]}; one JVM
 * per scenario so round 1 is a cold JVM like a real boot.</p>
 */
public final class PackIndexBench {
    private PackIndexBench() {
    }

    private record Pack(ImmutablePathPackIndex index) implements PackIndexPrewarmer.Source {
        @Override
        public ImmutablePathPackIndex vhaccelerator$prewarmIndex() {
            return this.index;
        }
    }

    private static ImmutablePathPackIndex index(Path root) {
        return ImmutablePathPackIndex.create(root, paths -> {
            Path resolved = root;
            for (String part : paths) {
                resolved = resolved.resolve(part);
            }
            return resolved;
        });
    }

    public static void main(String[] arguments) throws Exception {
        Path mods = Path.of(arguments[0]);
        boolean prewarm = arguments[1].equals("prewarm");
        int rounds = Integer.parseInt(arguments[2]);
        List<Path> roots = new ArrayList<>();
        List<FileSystem> systems = new ArrayList<>();
        try (Stream<Path> jars = Files.list(mods)) {
            for (Path jar : jars.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                try {
                    FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of());
                    systems.add(fileSystem);
                    roots.add(fileSystem.getPath("/"));
                } catch (Exception unreadable) {
                    // Skip unreadable archives.
                }
            }
        }
        if (arguments.length > 3 && arguments[3].equals("platformwarm")) {
            // What Forge's own class scan has already run over every jar before the first resource query.
            long files = 0;
            for (Path root : roots) {
                try (Stream<Path> walk = Files.walk(root)) {
                    files += walk.count();
                }
            }
            System.out.println("platform warm-up walked " + files + " entries");
        }
        java.util.Map<String, Integer> samples = new java.util.concurrent.ConcurrentHashMap<>();
        if (arguments.length > 4 && arguments[4].equals("sample")) {
            Thread sampler = new Thread(() -> {
                try {
                    while (true) {
                        for (var entry : Thread.getAllStackTraces().entrySet()) {
                            String name = entry.getKey().getName();
                            if (!name.startsWith("VH Accelerator compute") && !name.equals("main")) {
                                continue;
                            }
                            StackTraceElement[] stack = entry.getValue();
                            StringBuilder key = new StringBuilder(entry.getKey().getState() + " ");
                            if (stack.length > 0) {
                                key.append(stack[0]);
                            }
                            samples.merge(key.toString(), 1, Integer::sum);
                        }
                        Thread.sleep(5);
                    }
                } catch (InterruptedException done) {
                    // Finished.
                }
            }, "sampler");
            sampler.setDaemon(true);
            sampler.start();
        }
        for (int round = 1; round <= rounds; round++) {
            List<Pack> packs = new ArrayList<>();
            for (Path root : roots) {
                ImmutablePathPackIndex index = index(root);
                if (index != null) {
                    packs.add(new Pack(index));
                }
            }
            long begin = System.nanoTime();
            long kickNanos = 0;
            if (prewarm) {
                for (Pack pack : packs) {
                    PackIndexPrewarmer.register(pack);
                }
                long kickBegin = System.nanoTime();
                PackIndexPrewarmer.kick(packs.get(0));
                kickNanos = System.nanoTime() - kickBegin;
            }
            int answered = 0;
            for (Pack pack : packs) {
                if (pack.index().hasResource("assets/vha/bench/probe") != null) {
                    answered++;
                }
            }
            long total = System.nanoTime() - begin;
            System.out.printf("%s round %d: %d packs, main %.1f ms (kick %.2f ms), %d answered%n",
                    arguments[1], round, packs.size(), total / 1e6, kickNanos / 1e6, answered);
            Thread.sleep(300);
            if (round == 1 && !samples.isEmpty()) {
                samples.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14)
                        .forEach(e -> System.out.println("  sample " + e.getValue() + " " + e.getKey()));
                samples.clear();
            }
        }
        for (FileSystem fileSystem : systems) {
            fileSystem.close();
        }
    }
}
