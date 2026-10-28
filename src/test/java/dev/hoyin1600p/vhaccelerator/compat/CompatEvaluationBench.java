package dev.hoyin1600p.vhaccelerator.compat;

import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Offline timing of the compatibility group evaluation done in the mixin plugin's onLoad,
 * against the mod jars of a folder. The gate selects every mixin, so this is an upper bound
 * for the installed-mod set. Counts class lookups and distinct classes read. Not a test.
 */
public final class CompatEvaluationBench {
    /** Groups whose preflight passed in the Wolds B boot log (the installed-mod set that matters). */
    private static final List<String> WOLDS = List.of(".compat.buildinggadgets.", ".compat.ae2things.",
            ".compat.crafttweaker.removals.", ".compat.vaulthunters.", ".compat.ferritecore.", ".compat.targetdummy.");

    private CompatEvaluationBench() {
    }

    public static void main(String[] arguments) throws Exception {
        List<URL> urls = new ArrayList<>();
        File[] jars = new File(arguments[0]).listFiles((dir, name) -> name.endsWith(".jar"));
        for (File jar : jars) {
            urls.add(jar.toURI().toURL());
        }
        ClassLoader modLoader = new URLClassLoader(urls.toArray(URL[]::new), CompatEvaluationBench.class.getClassLoader());
        boolean all = arguments.length > 1 && arguments[1].equals("all");
        List<String> configured = CompatMixinGroups.readConfiguredMixins(
                CompatEvaluationBench.class.getClassLoader(), "vhaccelerator.mixins.json");
        for (int round = 1; round <= 5; round++) {
            Map<String, Integer> reads = new HashMap<>();
            long[] parseNanos = {0};
            MixinTargetPreflight.ClassLookup lookup = name -> {
                reads.merge(name, 1, Integer::sum);
                long begin = System.nanoTime();
                try (InputStream stream = modLoader.getResourceAsStream(name + ".class")) {
                    if (stream == null) {
                        return null;
                    }
                    ClassNode node = new ClassNode();
                    new ClassReader(stream.readAllBytes()).accept(node, 0);
                    return node;
                } finally {
                    parseNanos[0] += System.nanoTime() - begin;
                }
            };
            CompatMixinGroups groups = new CompatMixinGroups(configured, (target, mixin) -> all || WOLDS.stream().anyMatch(mixin::contains), lookup);
            long begin = System.nanoTime();
            groups.publishDiagnostics(new java.util.LinkedHashMap<>(Map.of("physicalSide", "DEDICATED_SERVER")));
            long total = System.nanoTime() - begin;
            int calls = reads.values().stream().mapToInt(Integer::intValue).sum();
            long found = reads.keySet().stream().filter(name -> modLoader.getResource(name + ".class") != null).count();
            if (round == 5) {
                reads.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
                        .forEach(e -> System.out.println("  repeated " + e.getValue() + "x " + e.getKey()));
            }
            System.out.printf("round %d: evaluate %.1f ms (class read+parse %.1f ms), %d lookups, %d distinct, %d present, %d repeated%n",
                    round, total / 1e6, parseNanos[0] / 1e6, calls, reads.size(), found, calls - reads.size());
        }
    }
}
