package dev.hoyin1600p.vhaccelerator.startup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Offline classifier for an external JFR export. Never samples the running game. */
public final class StartupSampleClassifier {
    private static final Map<String, String> OWNERS = owners();
    private static final List<String> EXCLUDED = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.",
            "net.minecraft.", "net.minecraftforge.", "cpw.mods.", "org.spongepowered.asm.",
            "org.objectweb.asm.", "com.mojang.", "org.slf4j.", "org.apache.logging.");
    private StartupSampleClassifier() { }

    private static Map<String, String> owners() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("com.blamejared.crafttweaker.", "CraftTweaker");
        result.put("iskallia.vault.", "Vault core");
        result.put("com.razz.decocraft.", "Decocraft");
        result.put("com.mrcrayfish.furniture.", "MrCrayfish Furniture");
        result.put("dev.hoyin1600p.vhaccelerator.", "VH Accelerator");
        result.put("ca.spottedleaf.starlight.", "Starlight");
        result.put("net.mehvahdjukaar.selene.", "Selene");
        result.put("dev.latvian.mods.", "dev.latvian.mods");
        return java.util.Collections.unmodifiableMap(result);
    }

    public static Map<String, Object> contract() {
        return Map.of("schemaVersion", 1, "startupFilter", "startInclusiveNanos <= timestampNanos < doneExclusiveNanos; same reconciled clock required",
                "stackOrientation", "leaf_to_root; class names in dotted binary form; method separate",
                "nearestOwnerDepth", 32, "inclusiveDepth", 48, "packageOwners", OWNERS,
                "excludedPlatformFrameworkPrefixes", EXCLUDED,
                "fallbacks", List.of("other_mod_or_library", "platform_or_framework", "missing_stack"),
                "missingAndTruncated", "missing stacks stay in denominator; truncated stacks stay classified with explicit counts; no inferred frames",
                "referenceReconciliation", "1514 startup samples and published phase/owner totals require the original recording, export and clock mapping; not reproduced by synthetic fixtures");
    }

    public static Map<String, Object> classify(Input input) {
        if (input.schemaVersion != 1 || input.doneExclusiveNanos < input.startInclusiveNanos) {
            throw new IllegalArgumentException("Unsupported sample schema or reversed startup interval");
        }
        Map<String, Long> nearest = new LinkedHashMap<>();
        Map<String, Long> inclusive = new LinkedHashMap<>();
        Map<String, Long> phases = new LinkedHashMap<>();
        long selected = 0, rejected = 0, truncated = 0, missing = 0, nearestCut = 0, inclusiveCut = 0;
        for (Sample sample : input.samples) {
            if (sample.timestampNanos < input.startInclusiveNanos || sample.timestampNanos >= input.doneExclusiveNanos) {
                rejected++;
                continue;
            }
            selected++;
            if (sample.truncated) truncated++;
            List<String> frames = sample.classNamesLeafToRoot == null ? List.of() : sample.classNamesLeafToRoot;
            if (frames.isEmpty()) missing++;
            if (frames.size() > 32) nearestCut++;
            if (frames.size() > 48) inclusiveCut++;
            String owner = frames.isEmpty() ? "missing_stack" : "platform_or_framework";
            for (String frame : frames.subList(0, Math.min(32, frames.size()))) {
                if (!excluded(frame)) { owner = owner(frame); break; }
            }
            nearest.merge(owner, 1L, Long::sum);
            var seen = new LinkedHashSet<String>();
            for (String frame : frames.subList(0, Math.min(48, frames.size()))) {
                if (!excluded(frame)) seen.add(owner(frame));
            }
            if (seen.isEmpty()) seen.add(frames.isEmpty() ? "missing_stack" : "platform_or_framework");
            seen.forEach(value -> inclusive.merge(value, 1L, Long::sum));
            phases.merge(sample.phase == null ? "unassigned" : sample.phase, 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("contract", contract());
        result.put("startInclusiveNanos", input.startInclusiveNanos);
        result.put("doneExclusiveNanos", input.doneExclusiveNanos);
        result.put("selectedSamples", selected);
        result.put("outsideStartupSamples", rejected);
        result.put("missingStackSamples", missing);
        result.put("truncatedStackSamples", truncated);
        result.put("nearestDepthLimitedSamples", nearestCut);
        result.put("inclusiveDepthLimitedSamples", inclusiveCut);
        result.put("nearestOwnerCounts", nearest);
        result.put("inclusiveOwnerCounts", inclusive);
        result.put("externallyAssignedPhaseCounts", phases);
        result.put("reference1514State", "unreconciled: original recording/export and timestamp mapping are not inputs to this implementation");
        result.put("interpretation", "nearest counts partition selected samples; inclusive owners overlap; neither is exclusive CPU or critical-path seconds");
        return result;
    }

    /** Gson bundled with 1.18.2 cannot deserialize Java records; parse the exported schema explicitly. */
    public static Input readInput(String json) {
        var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        var samples = new java.util.ArrayList<Sample>();
        for (var element : root.getAsJsonArray("samples")) {
            var sample = element.getAsJsonObject();
            var frames = new java.util.ArrayList<String>();
            var stack = sample.get("classNamesLeafToRoot");
            if (stack != null && !stack.isJsonNull()) {
                for (var frame : stack.getAsJsonArray()) frames.add(frame.getAsString());
            }
            var phase = sample.get("phase");
            samples.add(new Sample(sample.get("timestampNanos").getAsLong(), List.copyOf(frames),
                    sample.get("truncated").getAsBoolean(), phase == null || phase.isJsonNull() ? null : phase.getAsString()));
        }
        return new Input(root.get("schemaVersion").getAsInt(), root.get("startInclusiveNanos").getAsLong(),
                root.get("doneExclusiveNanos").getAsLong(), List.copyOf(samples));
    }

    private static boolean excluded(String name) {
        if (name == null || name.isBlank() || name.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Expected dotted binary class name");
        }
        return EXCLUDED.stream().anyMatch(name::startsWith);
    }

    private static String owner(String name) {
        return OWNERS.entrySet().stream().filter(entry -> name.startsWith(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("other_mod_or_library");
    }

    /** Usage: java ...StartupSampleClassifier exported-samples.json; writes JSON to stdout only. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("One exported-samples.json input is required");
        Gson gson = new GsonBuilder().serializeNulls().create();
        Input input = readInput(Files.readString(Path.of(arguments[0]), StandardCharsets.UTF_8));
        System.out.println(gson.toJson(classify(input)));
    }

    public record Input(int schemaVersion, long startInclusiveNanos, long doneExclusiveNanos, List<Sample> samples) { }
    public record Sample(long timestampNanos, List<String> classNamesLeafToRoot, boolean truncated, String phase) { }
}
