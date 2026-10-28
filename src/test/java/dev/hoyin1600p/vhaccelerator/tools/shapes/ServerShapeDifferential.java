package dev.hoyin1600p.vhaccelerator.tools.shapes;

import com.google.gson.GsonBuilder;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds every vanilla block state (and optionally the original MrCrayfish's Furniture blocks) twice,
 * once per arm, and compares the complete internal representation of each state's shapes. An arm
 * is a set of woven mixins: {@code vanilla}, {@code vha} (box cache, join and optimize cache, flat
 * merger), the Canary 0.2.7 shape mixins ({@code canary}), and Canary with VHA's join and optimize
 * cache ({@code canary+join}); see {@link WeavingLoader#arm}. See {@link MiniWeaver} for what is,
 * and is not, the production code in a woven run.
 */
public final class ServerShapeDifferential {
    private ServerShapeDifferential() { }

    /** One bootstrap in a fresh class loader; returns the run's report. */
    @SuppressWarnings("unchecked")
    static Map<String, String> pass(String arm, String canaryJar, String cfmJar, String mappings, Path dump, int repeats)
            throws Exception {
        List<String> components = WeavingLoader.arm(arm);
        Map<String, String> names = mappings == null || mappings.isEmpty()
                ? Map.of() : WeavingLoader.readSrgNames(Path.of(mappings));
        WeavingLoader loader = new WeavingLoader(ServerShapeDifferential.class.getClassLoader(), components,
                canaryJar == null || canaryJar.isEmpty() ? null : Path.of(canaryJar), names);
        try {
            Class<?> run = Class.forName("dev.hoyin1600p.vhaccelerator.tools.shapes.ShapeDumpRun", true, loader);
            return (Map<String, String>) run.getMethod("run", String.class, String.class, String.class, int.class)
                    .invoke(null, cfmJar == null ? "" : cfmJar, mappings == null ? "" : mappings, dump.toString(), repeats);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    /** The original comparison: vanilla against the full VHA set. */
    public static Map<String, Object> run(String cfmJar, String mappings, Path directory, int repeats) throws Exception {
        return runArms("vanilla", "vha", null, cfmJar, mappings, directory, repeats);
    }

    /**
     * Passes in the order a, b, b, a, each in its own class loader, and compares a with b twice plus
     * each arm with itself. Keys: {@code offVersusOn}, {@code offVersusOnSecondPair},
     * {@code offDeterminism}, {@code onDeterminism} (a is "off", b is "on").
     */
    public static Map<String, Object> runArms(String a, String b, String canaryJar, String cfmJar, String mappings,
                                              Path directory, int repeats) throws Exception {
        Files.createDirectories(directory);
        Map<String, Object> result = new LinkedHashMap<>();
        String[] order = {a, b, b, a};
        List<Path> dumps = new ArrayList<>();
        Map<String, List<Map<String, String>>> reports = new LinkedHashMap<>();
        for (int i = 0; i < order.length; i++) {
            Path dump = directory.resolve("dump-" + order[i] + "-" + (i < 2 ? 1 : 2) + ".tsv");
            dumps.add(dump);
            reports.computeIfAbsent(order[i], key -> new ArrayList<>())
                    .add(pass(order[i], canaryJar, cfmJar, mappings, dump, repeats));
        }
        result.put("armA", a);
        result.put("armB", b);
        result.put("runOrder", List.of(order));
        result.put("runReports", reports);
        result.put("offVersusOn", compare(dumps.get(0), dumps.get(1)));
        result.put("offVersusOnSecondPair", compare(dumps.get(3), dumps.get(2)));
        result.put("offDeterminism", compare(dumps.get(0), dumps.get(3)));
        result.put("onDeterminism", compare(dumps.get(1), dumps.get(2)));
        return result;
    }

    static Map<String, Object> compare(Path left, Path right) throws Exception {
        Map<String, String> a = read(left), b = read(right);
        TreeSet<String> keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        int equal = 0;
        List<String> mismatches = new ArrayList<>();
        for (String key : keys) {
            if (java.util.Objects.equals(a.get(key), b.get(key))) equal++;
            else if (mismatches.size() < 20) mismatches.add(key + " | " + a.get(key) + " | " + b.get(key));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", keys.size());
        result.put("equalStates", equal);
        result.put("mismatchedStates", keys.size() - equal);
        result.put("firstMismatches", mismatches);
        return result;
    }

    private static Map<String, String> read(Path path) throws Exception {
        Map<String, String> lines = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            int tab = line.indexOf('\t');
            lines.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return lines;
    }

    /**
     * {@code diff dir report.json} or {@code bench arm}, then optional {@code key=value} words:
     * a, b (arms), cfm, mappings, canary, repeats.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("diff <dir> <report.json> | bench <arm>  [a= b= cfm= mappings= canary= repeats=]");
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = args[0].equals("diff") ? 3 : 2; i < args.length; i++) {
            int equals = args[i].indexOf('=');
            options.put(args[i].substring(0, equals), args[i].substring(equals + 1));
        }
        int repeats = Integer.parseInt(options.getOrDefault("repeats", "1"));
        if (args[0].equals("bench")) {
            System.setProperty("shape.bench", "true");
            if (options.containsKey("histogram")) System.setProperty("shape.histogram", "true");
            Path scratch = Files.createTempFile("shape-bench", ".tsv");
            Map<String, String> report = pass(args[1], options.get("canary"), options.get("cfm"), options.get("mappings"),
                    scratch, repeats);
            Files.deleteIfExists(scratch);
            System.out.println("BENCH " + new GsonBuilder().create().toJson(report));
            return;
        }
        Path directory = Path.of(args[1]);
        Path report = Path.of(args[2]);
        if (Files.exists(report)) throw new IllegalArgumentException("Report must be new");
        Map<String, Object> result = runArms(options.getOrDefault("a", "vanilla"), options.getOrDefault("b", "vha"),
                options.get("canary"), options.get("cfm"), options.get("mappings"), directory, repeats);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(result);
        Files.writeString(report, json);
        System.out.println(json);
    }
}
