package dev.hoyin1600p.vhaccelerator.mixin;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Sponge Mixin rejects some injectors at apply time, before any VHA guard can fall back, so a wrong handler
 * modifier crashes startup. For every handler of every mixin this resolves the target method(s) in the real
 * target class bytes and applies Sponge's rule: {@code Redirect}, {@code ModifyVariable}, {@code ModifyArg(s)} and
 * {@code ModifyConstant} need the handler's static modifier to equal the target method's ("'static' modifier of
 * handler method does not match target"); {@code Inject} and the MixinExtras wrappers only reject a non-static
 * handler on a static target. Targets whose class or method cannot be found are listed, not failed.
 */
class MixinHandlerModifierScanTest {
    private static final String PREFIX = "Lorg/spongepowered/asm/mixin/injection/";
    /** 2: static modifier must equal the target's (Redirect); 1: same, reported as a note only; 0: only instance-on-static fails. */
    private static final Map<String, Integer> HANDLERS = Map.of(
            PREFIX + "Inject;", 0, PREFIX + "Redirect;", 2, PREFIX + "ModifyVariable;", 1,
            PREFIX + "ModifyArg;", 1, PREFIX + "ModifyArgs;", 1, PREFIX + "ModifyConstant;", 1,
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", 0,
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;", 0,
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", 0);

    record Finding(String mixin, String handler, String annotation, String target, String reason) {
        @Override public String toString() { return mixin + "#" + handler + " " + annotation + " -> " + target + ": " + reason; }
    }

    record Scan(List<Finding> violations, List<Finding> skipped, List<Finding> notes, int checked) { }

    @Test
    void everyVhaMixinHandlerMatchesTheStaticModifierOfItsRealTarget() throws Exception {
        JsonObject config;
        try (InputStream input = getClass().getResourceAsStream("/vhaccelerator.mixins.json")) {
            config = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject refmap = null;
        try (InputStream input = getClass().getResourceAsStream("/vhaccelerator.refmap.json")) {
            if (input != null) refmap = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<ZipFile> jars = new ArrayList<>();
        try (var files = Files.list(Path.of("libs"))) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) jars.add(new ZipFile(jar.toFile()));
        }
        int extraJars = openExtraJars(jars);
        System.out.println("MIXIN_MODIFIER_EXTRA_JARS " + extraJars);
        try {
            Function<String, ClassNode> targets = name -> find(name, jars);
            List<Finding> violations = new ArrayList<>();
            List<Finding> skipped = new ArrayList<>();
            List<Finding> notes = new ArrayList<>();
            int checked = 0;
            int mixins = 0;
            for (String section : List.of("mixins", "client", "server")) {
                if (!config.has(section)) continue;
                for (var entry : config.getAsJsonArray(section)) {
                    String internal = (config.get("package").getAsString() + "." + entry.getAsString()).replace('.', '/');
                    ClassNode mixin = read(getClass().getClassLoader().getResourceAsStream(internal + ".class"));
                    assertNotNull(mixin, internal);
                    Scan scan = scan(mixin, targets, name -> findAll(name, jars), refmap);
                    violations.addAll(scan.violations());
                    skipped.addAll(scan.skipped());
                    notes.addAll(scan.notes());
                    checked += scan.checked();
                    mixins++;
                }
            }
            System.out.println("MIXIN_MODIFIER_SCAN mixins=" + mixins + " checkedTargets=" + checked
                    + " skippedTargets=" + skipped.size() + " notes=" + notes.size() + " violations=" + violations.size());
            notes.forEach(finding -> System.out.println("MIXIN_MODIFIER_NOTE " + finding));
            skipped.forEach(finding -> System.out.println("MIXIN_MODIFIER_SKIPPED " + finding));
            violations.forEach(finding -> System.out.println("MIXIN_MODIFIER_VIOLATION " + finding));
            assertTrue(mixins > 200 && checked > 100, "the scan must really resolve targets");
            assertEquals(List.of(), violations.stream().map(Finding::toString).toList());
        } finally {
            for (ZipFile jar : jars) jar.close();
        }
    }

    /**
     * Opens the optional mod jars named by the {@code mixin.scan.jars} system property (Gradle: {@code
     * -Pmixin_scan_jars=<file or folder>[;<file or folder>...]}, the path separator of the platform; a folder
     * contributes its top-level jars) and by the existing fixture properties. Nothing is committed: the jars stay
     * wherever the caller keeps them. Returns how many were opened.
     */
    private static int openExtraJars(List<ZipFile> jars) throws java.io.IOException {
        List<String> entries = new ArrayList<>();
        String list = System.getProperty("mixin.scan.jars");
        if (list != null) entries.addAll(List.of(list.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))));
        for (String key : List.of("vault.cdf.jar", "ae2things.jar", "lootjs.jar")) {
            String value = System.getProperty(key);
            if (value != null) entries.add(value);
        }
        int opened = 0;
        for (String entry : entries) {
            if (entry.isBlank()) continue;
            Path path = Path.of(entry.trim());
            List<Path> candidates = new ArrayList<>();
            if (Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    files.filter(file -> file.toString().endsWith(".jar")).sorted().forEach(candidates::add);
                }
            } else if (Files.isRegularFile(path)) {
                candidates.add(path);
            }
            for (Path candidate : candidates) {
                try {
                    jars.add(new ZipFile(candidate.toFile()));
                    opened++;
                } catch (java.io.IOException unreadable) {
                    System.out.println("MIXIN_MODIFIER_JAR_UNREADABLE " + candidate.getFileName());
                }
            }
        }
        return opened;
    }

    /** LootJS 2.7.7: ofItemFilter is a private static method, so the static redirect handler is the right shape. */
    @Test
    void theLootJsRegexFilterHandlerMatchesOfItemFilterWhenLootJsIsAvailable() throws Exception {
        List<ZipFile> jars = new ArrayList<>();
        try {
            openExtraJars(jars);
            ClassNode lootJs = find("com/almostreliable/lootjs/kube/LootJSPlugin", jars);
            org.junit.jupiter.api.Assumptions.assumeTrue(lootJs != null,
                    "LootJS is not on the classpath; pass -Plootjs_jar or -Pmixin_scan_jars");
            MethodNode ofItemFilter = lootJs.methods.stream().filter(method -> method.name.equals("ofItemFilter")
                    && method.desc.equals("(Ljava/lang/Object;)Lcom/almostreliable/lootjs/filters/ItemFilter;"))
                    .findFirst().orElseThrow();
            assertTrue((ofItemFilter.access & Opcodes.ACC_STATIC) != 0, "LootJSPlugin.ofItemFilter is static");
            ClassNode mixin = read(getClass().getClassLoader().getResourceAsStream(
                    "dev/hoyin1600p/vhaccelerator/mixin/compat/lootjs/LootJsItemFilterMixin.class"));
            assertNotNull(mixin);
            Scan scan = scan(mixin, name -> find(name, jars), name -> findAll(name, jars), null);
            assertEquals(1, scan.checked(), scan.skipped().toString());
            assertEquals(List.of(), scan.violations());
        } finally {
            for (ZipFile jar : jars) jar.close();
        }
    }

    @Test
    void aStaticRedirectHandlerOnAnInstanceConstructorIsFlaggedAndAnInstanceHandlerPasses() {
        for (boolean handlerStatic : new boolean[]{true, false}) {
            ClassNode target = new ClassNode();
            target.name = "example/Target";
            target.superName = "java/lang/Object";
            target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null));
            target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make", "()V", null, null));
            ClassNode mixin = new ClassNode();
            mixin.name = "example/TheMixin";
            AnnotationNode mixinAnnotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
            mixinAnnotation.values = List.of("value", List.of(Type.getObjectType("example/Target")));
            mixin.invisibleAnnotations = List.of(mixinAnnotation);
            for (String selector : List.of("<init>", "make")) {
                MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE | (handlerStatic ? Opcodes.ACC_STATIC : 0),
                        "handler" + (selector.equals("make") ? "Make" : "Init"), "()V", null, null);
                AnnotationNode redirect = new AnnotationNode(PREFIX + "Redirect;");
                redirect.values = List.of("method", List.of(selector));
                handler.visibleAnnotations = List.of(redirect);
                mixin.methods.add(handler);
            }
            Scan scan = scan(mixin, name -> name.equals("example/Target") ? target : null, null);
            assertEquals(2, scan.checked());
            // static handler: wrong for the instance constructor, right for the static factory; instance: the reverse
            assertEquals(1, scan.violations().size(), scan.violations().toString());
            assertTrue(scan.violations().get(0).target().contains(handlerStatic ? "<init>" : "make"));
        }
        // Inject only rejects a non-static handler on a static target.
        ClassNode target = new ClassNode();
        target.name = "example/Target";
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null));
        ClassNode mixin = new ClassNode();
        mixin.name = "example/TheMixin";
        AnnotationNode mixinAnnotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        mixinAnnotation.values = List.of("value", List.of(Type.getObjectType("example/Target")));
        mixin.invisibleAnnotations = List.of(mixinAnnotation);
        MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "h", "()V", null, null);
        AnnotationNode inject = new AnnotationNode(PREFIX + "Inject;");
        inject.values = List.of("method", List.of("run"));
        handler.visibleAnnotations = List.of(inject);
        mixin.methods.add(handler);
        assertEquals(List.of(), scan(mixin, name -> target, null).violations());
        assertNotNull(CompatMixinGroups.class);
    }

    static Scan scan(ClassNode mixin, Function<String, ClassNode> classes, JsonObject refmap) {
        return scan(mixin, classes, name -> List.of(), refmap);
    }

    /**
     * {@code alternates} lists every jar copy of a class. The development classpath carries deobfuscated names,
     * while a mixin written for production (SRG) names, or for a Vault build other than the one compiled
     * against, only resolves in a jar copy; a selector that matches nothing on the classpath copy is checked
     * against those copies instead.
     */
    static Scan scan(ClassNode mixin, Function<String, ClassNode> classes,
                     Function<String, List<ClassNode>> alternates, JsonObject refmap) {
        List<Finding> violations = new ArrayList<>();
        List<Finding> skipped = new ArrayList<>();
        List<Finding> notes = new ArrayList<>();
        int checked = 0;
        List<String> targetNames = new ArrayList<>();
        AnnotationNode mixinAnnotation = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixinAnnotation == null) mixinAnnotation = annotation(mixin.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (mixinAnnotation != null && mixinAnnotation.values != null) {
            for (int i = 0; i + 1 < mixinAnnotation.values.size(); i += 2) {
                Object value = mixinAnnotation.values.get(i + 1);
                if (value instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Type type) targetNames.add(type.getInternalName());
                        else if (item instanceof String name) targetNames.add(name.replace('.', '/'));
                    }
                }
            }
        }
        for (MethodNode handler : mixin.methods) {
            List<AnnotationNode> all = new ArrayList<>();
            if (handler.visibleAnnotations != null) all.addAll(handler.visibleAnnotations);
            if (handler.invisibleAnnotations != null) all.addAll(handler.invisibleAnnotations);
            for (AnnotationNode annotation : all) {
                Integer level = HANDLERS.get(annotation.desc);
                if (level == null) continue;
                List<String> selectors = new ArrayList<>();
                if (annotation.values != null) {
                    for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                        if ("method".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof List<?> list) {
                            for (Object item : list) selectors.add(String.valueOf(item));
                        }
                    }
                }
                String kind = annotation.desc.substring(annotation.desc.lastIndexOf('/') + 1, annotation.desc.length() - 1);
                boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
                for (String selector : selectors) {
                    String resolved = selector;
                    if (refmap != null && refmap.has("mappings") && refmap.getAsJsonObject("mappings").has(mixin.name)) {
                        var mapped = refmap.getAsJsonObject("mappings").getAsJsonObject(mixin.name).get(selector);
                        if (mapped != null) resolved = mapped.getAsString();
                    }
                    int semicolon = resolved.indexOf(';');
                    int open = resolved.indexOf('(');
                    if (resolved.startsWith("L") && semicolon > 0 && (open < 0 || semicolon < open)) {
                        resolved = resolved.substring(semicolon + 1);
                    }
                    if (resolved.contains("*") || resolved.startsWith("/")) {
                        skipped.add(new Finding(mixin.name, handler.name, kind, selector, "wildcard or regex selector"));
                        continue;
                    }
                    int paren = resolved.indexOf('(');
                    String name = paren < 0 ? resolved : resolved.substring(0, paren);
                    String descriptor = paren < 0 ? null : resolved.substring(paren);
                    for (String targetName : targetNames) {
                        ClassNode target = classes.apply(targetName);
                        if (target == null) {
                            skipped.add(new Finding(mixin.name, handler.name, kind, targetName + "." + selector, "target class not on the classpath or libs"));
                            continue;
                        }
                        List<MethodNode> matches = target.methods.stream().filter(method -> method.name.equals(name)
                                && (descriptor == null || method.desc.equals(descriptor))).toList();
                        List<MethodNode> alternateMatches = new ArrayList<>();
                        if (matches.isEmpty()) {
                            for (ClassNode copy : alternates.apply(targetName)) {
                                copy.methods.stream().filter(method -> method.name.equals(name)
                                        && (descriptor == null || method.desc.equals(descriptor))).forEach(alternateMatches::add);
                            }
                            if (alternateMatches.isEmpty()) {
                                skipped.add(new Finding(mixin.name, handler.name, kind, targetName + "." + selector, "method not found (obfuscated name without refmap, or inherited)"));
                                continue;
                            }
                            matches = alternateMatches;
                        }
                        List<Finding> bad = new ArrayList<>();
                        for (MethodNode method : matches) {
                            checked++;
                            boolean targetStatic = (method.access & Opcodes.ACC_STATIC) != 0;
                            String what = "handler " + (handlerStatic ? "static" : "instance") + " but target "
                                    + (targetStatic ? "static" : "instance");
                            Finding finding = new Finding(mixin.name, handler.name, kind, targetName + "." + method.name + method.desc, what);
                            boolean fails = level == 2 ? handlerStatic != targetStatic : (!handlerStatic && targetStatic);
                            if (fails) bad.add(finding);
                            else if (level == 1 && handlerStatic != targetStatic) {
                                notes.add(new Finding(mixin.name, handler.name, kind, finding.target(),
                                        what + " (not a Sponge error for this injector kind, listed for review)"));
                            }
                        }
                        if (bad.size() == matches.size()) {
                            violations.addAll(bad);
                        } else {
                            // A name-only selector also names overloads this handler is not written for.
                            bad.forEach(finding -> notes.add(new Finding(finding.mixin(), finding.handler(), finding.annotation(),
                                    finding.target(), finding.reason() + " (name-only selector with overloads; other overload fits)")));
                        }
                    }
                }
            }
        }
        return new Scan(violations, skipped, notes, checked);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) return null;
        return annotations.stream().filter(annotation -> annotation.desc.equals(desc)).findFirst().orElse(null);
    }

    private static ClassNode find(String internal, List<ZipFile> jars) {
        try (InputStream input = MixinHandlerModifierScanTest.class.getClassLoader().getResourceAsStream(internal + ".class")) {
            if (input != null) return read(input);
        } catch (java.io.IOException ignored) { /* try the jars */ }
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(internal + ".class");
            if (entry == null) continue;
            try (InputStream input = jar.getInputStream(entry)) {
                return read(input);
            } catch (java.io.IOException ignored) { /* next */ }
        }
        return null;
    }

    /** Every copy of the class in the opened jars (several Vault builds sit side by side). */
    private static List<ClassNode> findAll(String internal, List<ZipFile> jars) {
        List<ClassNode> copies = new ArrayList<>();
        for (ZipFile jar : jars) {
            ZipEntry entry = jar.getEntry(internal + ".class");
            if (entry == null) continue;
            try (InputStream input = jar.getInputStream(entry)) {
                ClassNode node = read(input);
                if (node != null) copies.add(node);
            } catch (java.io.IOException ignored) { /* next */ }
        }
        return copies;
    }

    private static ClassNode read(InputStream input) {
        if (input == null) return null;
        try (input) {
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, ClassReader.SKIP_CODE);
            return node;
        } catch (java.io.IOException failure) {
            return null;
        }
    }
}
