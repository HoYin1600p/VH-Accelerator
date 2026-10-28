package dev.hoyin1600p.vhaccelerator.compat;

import java.util.List;
import java.util.Map;

/** Exact diagnostic adapters, separate from optimization eligibility and client replay helpers. */
public final class StartupAdapterLayouts {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.startup.";
    private static final Map<String, String> VERSIONS = Map.of(
            "crafttweaker", "9.1.213", "kubejs", "1802.5.5-build.569", "selene", "1.18.2-1.17.17");
    private static final Map<String, List<String>> MIXINS = Map.of(
            "crafttweaker", List.of("CraftTweakerExecutionObserverMixin", "CraftTweakerActionObserverMixin"),
            "kubejs", List.of("KubeScriptObserverMixin", "KubeRecipeObserverMixin"),
            "selene", List.of("SeleneResourceObserverMixin"));
    private StartupAdapterLayouts() { }

    public static boolean isMixin(String name) {
        if (!name.startsWith(BASE)) return false;
        int split = name.lastIndexOf('.');
        if (split < BASE.length()) return false;
        return MIXINS.getOrDefault(name.substring(BASE.length(), split), List.of()).contains(name.substring(split + 1));
    }

    public static boolean isGroup(String group) {
        return group.startsWith(BASE) && VERSIONS.containsKey(group.substring(BASE.length()));
    }

    public static boolean supports(String mixin, java.util.function.Function<String, String> version) {
        if (!isMixin(mixin)) return false;
        String id = mixin.substring(BASE.length(), mixin.lastIndexOf('.'));
        return VERSIONS.get(id).equals(version.apply(id));
    }

    public static Map<String, Object> contract(String group) {
        String id = group.substring(BASE.length());
        return Map.of("modId", id, "supportedVersions", List.of(VERSIONS.get(id)),
                "setting", "debug diagnostics (common TOML)", "sides", List.of("DEDICATED_SERVER"),
                "kind", "observational only; no optimization ownership",
                "limits", "inclusive normal-return method aggregates; exceptions leave unfinished operations; early calls before attachment are unavailable");
    }

    /** require=0 is safe only alongside these strict literal method checks and postApply call-edge checks. */
    public static List<String> problems(String mixin, org.objectweb.asm.tree.ClassNode source,
                                         MixinTargetPreflight.ClassLookup lookup) {
        if (!isMixin(mixin)) return List.of();
        var problems = new java.util.ArrayList<String>();
        for (String target : MixinTargetPreflight.targets(source)) {
            org.objectweb.asm.tree.ClassNode node;
            try { node = lookup.find(target); } catch (Exception | LinkageError failure) { node = null; }
            for (var method : source.methods) {
                var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                for (var annotation : annotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                        if (!annotation.values.get(i).equals("method")) continue;
                        for (Object selector : (List<?>) annotation.values.get(i + 1)) {
                            String literal = (String) selector;
                            if (node == null || node.methods.stream().noneMatch(candidate -> (candidate.name + candidate.desc).equals(literal))) {
                                problems.add("diagnostic method missing: " + target + "." + literal);
                            }
                        }
                    }
                }
            }
        }
        return problems;
    }
}
