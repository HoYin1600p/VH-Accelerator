package dev.hoyin1600p.vhaccelerator.tools.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The createIndexMerger site, Canary against VHA: vanilla {@code IndirectMerger}, VHA's
 * {@code FastCoordinateMerger} and Canary 0.2.7's {@code CanaryDoublePairList} built from the same
 * random coordinate lists (Canary's class is the original from the pinned jar, loaded by
 * {@link WeavingLoader}), compared on size, every merged coordinate bit and every merged index
 * triple, and timed in one JVM. {@code main} prints the comparison and the timings.
 */
public final class MergerSiteComparison {
    private MergerSiteComparison() { }

    /** One merger family, built reflectively in the loader that also holds Canary's classes. */
    private record Family(String name, Constructor<?> constructor) {
        Object make(DoubleList first, DoubleList second, boolean a, boolean b) throws Exception {
            return constructor.newInstance(first, second, a, b);
        }
    }

    private static Family family(ClassLoader loader, String name, String className) throws Exception {
        Class<?> type = Class.forName(className, true, loader);
        Constructor<?> constructor = type.getDeclaredConstructor(DoubleList.class, DoubleList.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        return new Family(name, constructor);
    }

    /** Runs {@code method} on a copy of this class loaded by a {@link WeavingLoader}, so all types are one set. */
    private static Object inLoader(String canaryJar, String mappings, String method, Class<?>[] types, Object... arguments)
            throws Exception {
        WeavingLoader loader = new WeavingLoader(MergerSiteComparison.class.getClassLoader(), List.of(),
                Path.of(canaryJar), WeavingLoader.readSrgNames(Path.of(mappings)));
        Class<?> copy = Class.forName(MergerSiteComparison.class.getName(), true, loader);
        var target = copy.getDeclaredMethod(method, types);
        target.setAccessible(true);
        return target.invoke(null, arguments);
    }

    private static List<Family> families() throws Exception {
        ClassLoader loader = MergerSiteComparison.class.getClassLoader();
        return List.of(
                family(loader, "vanilla", "net.minecraft.world.phys.shapes.IndirectMerger"),
                family(loader, "vha", "dev.hoyin1600p.vhaccelerator.shape.FastCoordinateMerger"),
                family(loader, "canary", "com.abdelaziz.canary.common.shapes.pairs.CanaryDoublePairList"));
    }

    static DoubleList coordinates(Random random) {
        int size = 1 + random.nextInt(12);
        double[] values = new double[size];
        double value = random.nextDouble() * 2.0D - 1.0D;
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                value += random.nextDouble() < 0.2D ? random.nextDouble() * 1.5E-7D : random.nextDouble() * 0.75D;
            }
            values[index] = value;
        }
        return DoubleArrayList.wrap(values);
    }

    /** A description of everything an {@code IndexMerger} exposes, for exact comparison. */
    @SuppressWarnings("unchecked")
    static String describe(Object merger) throws Exception {
        Class<?> type = merger.getClass().getClassLoader().loadClass("net.minecraft.world.phys.shapes.IndexMerger");
        DoubleList list = (DoubleList) type.getMethod("getList").invoke(merger);
        StringBuilder text = new StringBuilder().append(type.getMethod("size").invoke(merger)).append(':');
        for (int i = 0; i < list.size(); i++) text.append(Long.toHexString(Double.doubleToRawLongBits(list.getDouble(i)))).append(' ');
        Class<?> consumer = merger.getClass().getClassLoader().loadClass("net.minecraft.world.phys.shapes.IndexMerger$IndexConsumer");
        List<String> triples = new ArrayList<>();
        Object proxy = java.lang.reflect.Proxy.newProxyInstance(merger.getClass().getClassLoader(), new Class<?>[] {consumer},
                (p, method, arguments) -> {
                    if (method.getName().equals("merge")) {
                        triples.add(arguments[0] + "," + arguments[1] + "," + arguments[2]);
                        return true;
                    }
                    return null;
                });
        type.getMethod("forMergedIndexes", consumer).invoke(merger, proxy);
        return text.append('|').append(triples).toString();
    }

    /** Returns {@code [iterations, vanillaVsVhaMismatches, vanillaVsCanaryMismatches, vhaVsCanaryMismatches, firstDifference]}. */
    public static Object[] compare(String canaryJar, String mappings, int iterations) throws Exception {
        return (Object[]) inLoader(canaryJar, mappings, "compareHere", new Class<?>[] {int.class}, iterations);
    }

    static Object[] compareHere(int iterations) throws Exception {
        List<Family> all = families();
        Random random = new Random(0x564841L);
        int vv = 0, vc = 0, hc = 0, reachable = 0, reachableDifferent = 0, differentWithOnlyFlag = 0;
        String first = "", firstReachable = "";
        for (int i = 0; i < iterations; i++) {
            DoubleList a = coordinates(random), b = coordinates(random);
            for (boolean x : new boolean[] {false, true}) for (boolean y : new boolean[] {false, true}) {
                String vanilla = describe(all.get(0).make(a, b, x, y));
                String vha = describe(all.get(1).make(a, b, x, y));
                String canary = describe(all.get(2).make(a, b, x, y));
                if (!vanilla.equals(vha)) vv++;
                if (!vanilla.equals(canary)) { vc++; if (first.isEmpty()) first = vanilla + " != " + canary; }
                if (!vha.equals(canary)) hc++;
                if (!vanilla.equals(canary) && (x || y)) differentWithOnlyFlag++;
                // Shapes.createIndexMerger builds an IndirectMerger only for two non-empty shapes (lists of 2+
                // entries) whose ranges overlap; disjoint ranges get a NonOverlappingMerger instead.
                if (a.size() >= 2 && b.size() >= 2
                        && a.getDouble(a.size() - 1) >= b.getDouble(0) - 1.0E-7
                        && b.getDouble(b.size() - 1) >= a.getDouble(0) - 1.0E-7) {
                    reachable++;
                    if (!vanilla.equals(canary)) {
                        reachableDifferent++;
                        if (firstReachable.isEmpty()) firstReachable = a + " " + b + " " + x + y + ": " + vanilla + " != " + canary;
                    }
                }
            }
        }
        return new Object[] {iterations * 4, vv, vc, hc, first, reachable, reachableDifferent, firstReachable, differentWithOnlyFlag};
    }

    /** ns per merger built (list and all indexes materialised by construction), cold first 20k then warm. */
    static double[] time(Family family, List<DoubleList[]> pairs, int operations) throws Exception {
        long begin = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < operations; i++) {
            DoubleList[] pair = pairs.get(i & (pairs.size() - 1));
            Object merger = family.make(pair[0], pair[1], (i & 1) == 0, (i & 2) == 0);
            sink += merger.hashCode() & 1;
        }
        long elapsed = System.nanoTime() - begin;
        if (sink == Integer.MIN_VALUE) System.out.print("");
        return new double[] {(double) elapsed / operations};
    }

    static String timeHere(String only) throws Exception {
        Random random = new Random(7);
        List<DoubleList[]> pairs = new ArrayList<>();
        for (int i = 0; i < 4096; i++) pairs.add(new DoubleList[] {coordinates(random), coordinates(random)});
        Family family = families().stream().filter(f -> f.name().equals(only)).findFirst().orElseThrow();
        StringBuilder line = new StringBuilder("TIME " + only + " nsPerMerger:");
        // The cold figure includes interpreter and JIT warm-up of this JVM, so each family runs in its own JVM.
        line.append(" cold20k=").append(String.format("%.0f", time(family, pairs, 20_000)[0]));
        for (int pass = 0; pass < 6; pass++) line.append(" warm=").append(String.format("%.0f", time(family, pairs, 2_000_000)[0]));
        return line.toString();
    }

    /** {@code canary.jar mappings [family]}: prints the comparison, and the timing of one family when named. */
    public static void main(String[] args) throws Exception {
        String canary = args[0], mappings = args[1];
        if (args.length > 2) {
            System.out.println(inLoader(canary, mappings, "timeHere", new Class<?>[] {String.class}, args[2]));
            return;
        }
        Object[] result = compare(canary, mappings, 25_000);
        System.out.println("COMPARE comparisons=" + result[0] + " vanillaVsVha=" + result[1] + " vanillaVsCanary=" + result[2]
                + " vhaVsCanary=" + result[3] + " first=" + result[4] + "\nREACHABLE comparisons=" + result[5]
                + " vanillaVsCanaryDifferent=" + result[6] + " first=" + result[7]
                + "\nDIFFERENT-WITH-INCLUDE-ONLY-FLAG count=" + result[8]);
    }
}
