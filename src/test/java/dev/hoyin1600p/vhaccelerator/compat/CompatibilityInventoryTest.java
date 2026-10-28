package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Renderer for packaged declarations; reads class bytes without initializing optional/client classes. */
class CompatibilityInventoryTest {
    private static final String OWN = "dev/hoyin1600p/vhaccelerator/";
    private static final Path OUTPUT = Path.of("src/main/resources", CompatibilityInventory.RESOURCE);

    @Test
    void packagedInventoryMatchesEveryConfiguredCompatibilityGroup() throws Exception {
        var gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        Map<String, List<String>> groups = new LinkedHashMap<>();
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/vhaccelerator.mixins.json"))).getAsJsonObject();
        Map<String, List<String>> sides = new LinkedHashMap<>();
        for (String section : List.of("mixins", "client", "server")) {
            if (!config.has(section)) continue;
            for (var entry : config.getAsJsonArray(section)) {
                String mixin = config.get("package").getAsString() + "." + entry.getAsString();
                String group = CompatMixinGroups.groupOf(mixin);
                if (group == null) continue;
                groups.computeIfAbsent(group, unused -> new ArrayList<>()).add(mixin);
                sides.put(mixin, section.equals("mixins") ? List.of("CLIENT", "DEDICATED_SERVER")
                        : List.of(section.equals("client") ? "CLIENT" : "DEDICATED_SERVER"));
            }
        }
        Map<String, Object> inventories = new LinkedHashMap<>();
        for (var group : groups.entrySet()) {
            Walker walker = new Walker();
            var members = new ArrayList<Map<String, Object>>();
            for (String mixin : group.getValue()) {
                ClassNode node = walker.resolve(mixin.replace('.', '/'));
                assertNotNull(node, mixin);
                var sites = new ArrayList<Map<String, Object>>();
                for (var method : node.methods) {
                    if (!method.name.equals("<init>")) walker.pending.add(new MethodRef(node.name, method.name, method.desc));
                    var annotations = new ArrayList<AnnotationNode>();
                    if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                    if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                    var injections = annotations.stream().filter(annotation -> annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/"))
                            .map(CompatibilityInventoryTest::annotation).toList();
                    if (!injections.isEmpty()) sites.add(Map.of("handler", method.name + method.desc, "annotations", injections));
                }
                members.add(Map.of("mixin", mixin, "registeredSides", sides.get(mixin),
                        "targets", MixinTargetPreflight.targets(node), "declaredFields", node.fields.stream()
                                .map(field -> field.name + ":" + field.desc).toList(), "injectionSites", sites));
            }
            walker.walk();
            inventories.put(group.getKey(), Map.of("members", members, "gatePolicy", policy(group.getKey()),
                    "helperClasses", List.copyOf(walker.classes.keySet()), "methodReachability", walker.visited.stream()
                            .map(ref -> ref.owner + "." + ref.name + ref.descriptor).sorted().toList(),
                    "configurationAccessors", walker.config.stream().sorted().toList(),
                    "externalMemberDependencies", walker.external.stream().sorted().toList(),
                    "coverage", "static direct calls, static initializers, field owners and lambda handles in VHA code; arbitrary external callbacks/reflection are unavailable"));
        }
        Map<String, String> policySources = new LinkedHashMap<>();
        for (String source : List.of("MixinGateRules.java", "GateFactsDiscovery.java", "LiveGateInputs.java")) {
            String normalized = Files.readString(Path.of("src/main/java/dev/hoyin1600p/vhaccelerator/mixin/plugin", source))
                    .replace("\r\n", "\n");
            policySources.put(source, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        Map<String, Object> result = Map.of("schemaVersion", 1, "groups", inventories, "gatePolicySourceSha256", policySources,
                "runtimeEvidence", "Per-boot versions, gate facts, settings, preflight and application/use records are authoritative; declarations do not authorize a server exception",
                "sideEligibility", "registeredSides is registration only; MixinGateRules remains authoritative and rejects client-dependent hooks on dedicated servers");
        if ("1".equals(System.getenv("VHA_REGENERATE_COMPAT_INVENTORY"))) {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, gson.toJson(result) + System.lineSeparator());
        }
        assertEquals(JsonParser.parseString(Files.readString(OUTPUT)), gson.toJsonTree(result),
                "Regenerate with VHA_REGENERATE_COMPAT_INVENTORY=1 after intentional declaration/helper changes");
        assertTrue(inventories.size() >= 30);
    }

    private static Object annotation(AnnotationNode value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("annotation", value.desc);
        if (value.values != null) for (int i = 0; i + 1 < value.values.size(); i += 2) {
            result.put((String) value.values.get(i), scalar(value.values.get(i + 1)));
        }
        return result;
    }

    private static Object scalar(Object value) {
        if (value instanceof AnnotationNode node) return annotation(node);
        if (value instanceof Type type) return type.getDescriptor();
        if (value instanceof List<?> list) return list.stream().map(CompatibilityInventoryTest::scalar).toList();
        if (value instanceof String[] strings) return List.of(strings);
        return value;
    }

    /** Existing policies are documented, never tightened by this inventory. Unpinned policies stay explicit. */
    private static Map<String, Object> policy(String group) {
        String suffix = group.substring(group.lastIndexOf(".compat.") + 8);
        if (StartupAdapterLayouts.isGroup(group)) return StartupAdapterLayouts.contract(group);
        if (suffix.equals("crafttweaker.removals") || suffix.equals("buildinggadgets") || suffix.equals("ae2things") || suffix.equals("lootjs") || suffix.equals("thermalfuels") || suffix.equals("statecodec")
                || suffix.equals("vaulthunters.cdf") || suffix.equals("vaulthunters.cdflazy") || suffix.equals("vaulthunters.worldzones")) {
            return CommonCompatibilityLayouts.contract(group);
        }
        String mods = switch (suffix) {
            case "decocraft" -> "decocraft=3.0.4-1.18.2";
            case "ferritecore" -> group.contains(".mixin.client.") ? "minecraft=1.18.2; FerriteCore not required" : "ferritecore=4.2.2";
            case "kubejs" -> "kubejs=1802.5.5-build.569";
            case "copycats" -> "copycats:presence";
            case "leaks" -> "minecraft=1.18.2";
            case "targetdummy" -> "dummmmmmy=1.18-1.5.2";
            case "create" -> "create=0.5.1.i";
            case "geckolib" -> "geckolib3=3.0.57; ars_nouveau=2.9.0 for ArsNouveau member";
            case "selene" -> "selene=1.18.2-1.17.14|1.18.2-1.17.17 AND everycomp=1.18.2-1.5.18|1.18.2-1.6.7";
            case "buildscape" -> "buildscape:presence";
            case "farsight" -> "farsight_view:presence";
            case "jei.v9" -> "jei:presence AND mezz/jei/startup/JeiStarter.class (generation 9)";
            case "jei.v10" -> "jei:presence AND mezz/jei/common/startup/JeiStarter.class (generation 10)";
            case "placebo" -> "debug-only observer; target preflight, no exact version gate";
            case "jeitweaker" -> "jei:presence AND jeitweaker:presence";
            case "jer" -> "jei:presence AND jeresources:presence";
            case "crafttweaker" -> "crafttweaker:presence";
            case "ctm" -> "ctm=1.18.2-1.1.5+5";
            case "everycomp" -> "everycomp=1.18.2-1.6.7 AND selene=1.18.2-1.17.14";
            case "thermal" -> "thermal:presence";
            case "ironfurnaces" -> "jei:presence AND ironfurnaces:presence";
            case "industrialforegoing" -> "jei:presence AND industrialforegoing:presence";
            case "xaero" -> "xaerominimap=25.2.10 or xaeroworldmap=1.39.12 by member";
            case "powah" -> "powah:presence";
            case "sophisticated" -> "the_vault:presence AND sophisticatedcore:presence";
            case "supplementaries" -> "the_vault:presence AND supplementaries:presence";
            case "vaulthunters" -> "the_vault:presence; DecoratorCascadeRun additionally requires old modifier bytecode without getModifierGroup";
            case "modelbake.Ae2ModelBakeMixin" -> "ae2:presence";
            case "modelbake.CableTiersModelBakeMixin" -> "cabletiers=1.18.2-0.56";
            case "modelbake.CloudStorageModelBakeMixin" -> "cloudstorage=1.1.0";
            case "modelbake.ElevatorModelBakeMixin" -> "elevatorid:presence";
            case "modelbake.ExtraStorageModelBakeMixin" -> "extrastorage:presence";
            case "modelbake.IndustrialForegoingModelBakeMixin" -> "industrialforegoing:presence";
            case "modelbake.MegaCellsModelBakeMixin" -> "megacells=1.4.2-1.18.2";
            case "modelbake.MekanismModelBakeMixin" -> "mekanism=10.2.5";
            case "modelbake.RefinedStorageModelBakeMixin" -> "refinedstorage:presence";
            default -> throw new AssertionError("Uncatalogued compatibility policy: " + group);
        };
        String owners = switch (suffix) {
            case "farsight" -> "VRO owns when META-INF/vro-features/farsight-chunk-bound is declared";
            case "ferritecore" -> group.contains(".mixin.client.") ? "VRO owns face compaction when installed" : "FerriteCore owns FastMap; VHA removes the view only";
            case "buildscape" -> "ModernFix dynamic resources blocks VHA interop; unknown dynamic setting blocks it too";
            default -> "no additional competing owner declared by the existing compatibility gate; runtime feature guards may decline work";
        };
        return Map.of("supportedModPolicy", mods, "versionInterpretation", "presence means existing gate does not pin an exact version; installed version and whole-group layout preflight are recorded per boot",
                "competingOwners", owners, "configurationDependencies", "configurationAccessors plus per-boot raw configurationSnapshots and bootstrap featureFacts",
                "sidePolicy", "physical client unless an exact common-safe decision precedes dedicated rejection; registeredSides alone never authorizes application",
                "correctionExceptions", "TargetDummySetupMixin and CrystalBuddingChunkLoadMixin retain independent correction semantics");
    }

    private record MethodRef(String owner, String name, String descriptor) { }

    private static final class Walker {
        final Map<String, ClassNode> classes = new LinkedHashMap<>();
        final ArrayDeque<MethodRef> pending = new ArrayDeque<>();
        final Set<MethodRef> visited = new LinkedHashSet<>();
        final Set<String> config = new LinkedHashSet<>();
        final Set<String> external = new LinkedHashSet<>();

        ClassNode resolve(String owner) throws Exception {
            if (!classes.containsKey(owner)) {
                assertTrue(classes.size() < 512, "Inventory helper limit exceeded: " + owner);
                ClassNode node = MixinTargetPreflightTest.read(owner);
                assertNotNull(node, "Inventory helper unavailable: " + owner);
                classes.put(owner, node);
                pending.add(new MethodRef(owner, "<clinit>", "()V"));
            }
            return classes.get(owner);
        }

        void call(String owner, String name, String descriptor) throws Exception {
            String member = owner + "." + name + descriptor;
            if (owner.startsWith(OWN)) {
                resolve(owner);
                pending.add(new MethodRef(owner, name, descriptor));
                if (owner.contains("Config")) config.add(member);
            } else external.add(member);
        }

        void constant(Object constant) throws Exception {
            if (constant instanceof Handle handle) {
                if (handle.getTag() > 4) call(handle.getOwner(), handle.getName(), handle.getDesc());
                else field(handle.getOwner(), handle.getName(), handle.getDesc());
            }
        }

        void field(String owner, String name, String descriptor) throws Exception {
            if (owner.startsWith(OWN)) {
                resolve(owner);
                if (owner.contains("Config")) config.add(owner + "." + name + ":" + descriptor);
            } else external.add(owner + "." + name + ":" + descriptor);
        }

        void walk() throws Exception {
            while (!pending.isEmpty()) {
                MethodRef ref = pending.removeFirst();
                if (!visited.add(ref)) continue;
                assertTrue(visited.size() < 8192, "Inventory method limit exceeded");
                ClassNode node = resolve(ref.owner);
                MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals(ref.name)
                        && candidate.desc.equals(ref.descriptor)).findFirst().orElse(null);
                if (method == null) {
                    if (!ref.name.startsWith("<") && node.superName != null && node.superName.startsWith(OWN)) call(node.superName, ref.name, ref.descriptor);
                    continue;
                }
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) call(call.owner, call.name, call.desc);
                    else if (instruction instanceof FieldInsnNode field) field(field.owner, field.name, field.desc);
                    else if (instruction instanceof TypeInsnNode type && type.desc.startsWith(OWN)) resolve(type.desc);
                    else if (instruction instanceof LdcInsnNode value) constant(value.cst);
                    else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        constant(dynamic.bsm);
                        for (Object argument : dynamic.bsmArgs) constant(argument);
                    }
                }
            }
        }
    }
}
