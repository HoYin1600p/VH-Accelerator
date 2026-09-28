package dev.hoyin1600p.vhaccelerator.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks, before a third-party compatibility mixin is applied, that the
 * members it names still exist in the installed target mod's bytecode.
 *
 * <p>Only unremapped references ({@code remap = false}) are checked: they are
 * literal names in the target mod, so a missing one means that mod changed.
 * Remapped references, wildcard or regex selectors, and injection points
 * without a member target are accepted as-is. A missing target class or
 * member is reported so the caller can skip the whole compatibility group
 * instead of letting a {@code require = 1} injector abort startup.
 */
public final class MixinTargetPreflight {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECTION = "Lorg/spongepowered/asm/mixin/injection/";
    private static final List<String> INJECTORS = List.of(
            "Inject",
            "Redirect",
            "ModifyArg",
            "ModifyArgs",
            "ModifyVariable",
            "ModifyConstant"
    );
    private static final int MAX_SUPERCLASS_DEPTH = 16;

    /** Resolves a class by internal name; returns {@code null} when absent. */
    @FunctionalInterface
    public interface ClassLookup {
        ClassNode find(String internalName) throws Exception;
    }

    private MixinTargetPreflight() {
    }

    /** Internal names of the classes a mixin targets. */
    public static List<String> targets(ClassNode mixin) {
        AnnotationNode annotation = annotation(mixin.invisibleAnnotations, MIXIN);
        if (annotation == null) {
            annotation = annotation(mixin.visibleAnnotations, MIXIN);
        }
        if (annotation == null) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (Object value : listValue(annotation, "value")) {
            if (value instanceof Type type) {
                targets.add(type.getInternalName());
            }
        }
        for (Object value : listValue(annotation, "targets")) {
            if (value instanceof String name) {
                targets.add(name.replace('.', '/'));
            }
        }
        return targets;
    }

    /**
     * Returns every unresolved reference of {@code mixin} against the target
     * {@code targetName}; an empty list means the mixin can be applied.
     */
    public static List<String> problems(
            ClassNode mixin,
            String targetName,
            ClassLookup lookup
    ) {
        List<String> problems = new ArrayList<>();
        ClassNode target;
        try {
            target = lookup.find(targetName);
        } catch (Exception exception) {
            target = null;
        }
        if (target == null) {
            problems.add("target class " + targetName + " was not found");
            return problems;
        }

        boolean classRemap = booleanValue(mixinAnnotation(mixin), "remap", true);
        if (mixin.methods != null) {
            for (MethodNode method : mixin.methods) {
                checkMethod(mixin, method, target, classRemap, lookup, problems);
            }
        }
        if (mixin.fields != null) {
            for (FieldNode field : mixin.fields) {
                AnnotationNode shadow = annotation(field.visibleAnnotations, SHADOW);
                if (shadow == null) {
                    shadow = annotation(field.invisibleAnnotations, SHADOW);
                }
                if (shadow != null
                        && !(classRemap && booleanValue(shadow, "remap", true))
                        && findField(target, shadowNames(field.name, shadow), field.desc, lookup) == null) {
                    problems.add("shadow field " + field.name + " is missing from " + targetName);
                }
            }
        }
        return problems;
    }

    private static void checkMethod(
            ClassNode mixin,
            MethodNode method,
            ClassNode target,
            boolean classRemap,
            ClassLookup lookup,
            List<String> problems
    ) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            annotations.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            annotations.addAll(method.invisibleAnnotations);
        }
        for (AnnotationNode annotation : annotations) {
            boolean remap = classRemap && booleanValue(annotation, "remap", true);
            if (annotation.desc.startsWith(INJECTION)
                    && INJECTORS.contains(simpleName(annotation.desc))) {
                if (!remap) {
                    checkInjector(annotation, target, problems);
                }
            } else if (SHADOW.equals(annotation.desc) || OVERWRITE.equals(annotation.desc)) {
                List<String> names = SHADOW.equals(annotation.desc)
                        ? shadowNames(method.name, annotation)
                        : List.of(method.name);
                if (!remap && !method.name.startsWith("<")
                        && findMethod(target, names, method.desc, lookup) == null) {
                    problems.add("method " + method.name + method.desc
                            + " is missing from " + target.name);
                }
            } else if (ACCESSOR.equals(annotation.desc)) {
                if (!remap && !accessorFieldExists(annotation, method, target, lookup)) {
                    problems.add("accessor " + method.name + " has no field in " + target.name);
                }
            } else if (INVOKER.equals(annotation.desc)) {
                if (!remap && !invokerMethodExists(annotation, method, target, lookup)) {
                    problems.add("invoker " + method.name + " has no method in " + target.name);
                }
            }
        }
    }

    private static void checkInjector(
            AnnotationNode injector,
            ClassNode target,
            List<String> problems
    ) {
        List<AnnotationNode> points = new ArrayList<>();
        Object at = value(injector, "at");
        if (at instanceof AnnotationNode single) {
            points.add(single);
        } else if (at instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof AnnotationNode node) {
                    points.add(node);
                }
            }
        }

        for (Object value : listValue(injector, "method")) {
            if (!(value instanceof String selector)) {
                continue;
            }
            Selector parsed = Selector.parse(selector);
            if (parsed == null) {
                continue;
            }
            List<MethodNode> candidates = new ArrayList<>();
            for (MethodNode candidate : target.methods) {
                if (parsed.matches(candidate.name, candidate.desc)) {
                    candidates.add(candidate);
                }
            }
            if (candidates.isEmpty()) {
                // require = 0 marks an optional injection that Mixin itself skips.
                if (!Integer.valueOf(0).equals(value(injector, "require"))) {
                    problems.add("injection target " + selector + " is missing from " + target.name);
                }
                continue;
            }
            // An unremapped injector disables remapping of its points unless a
            // point explicitly opts back in with remap = true.
            for (AnnotationNode point : points) {
                if (Boolean.TRUE.equals(value(point, "remap"))) {
                    continue;
                }
                String missing = missingPointTarget(point, candidates);
                if (missing != null) {
                    problems.add("injection point " + missing + " is missing from "
                            + target.name + "#" + selector);
                }
            }
        }
    }

    private static String missingPointTarget(
            AnnotationNode point,
            List<MethodNode> candidates
    ) {
        Object kind = value(point, "value");
        Object rawTarget = value(point, "target");
        if (!(kind instanceof String type) || !(rawTarget instanceof String target)
                || target.isEmpty()) {
            return null;
        }
        boolean invoke = type.startsWith("INVOKE");
        boolean field = type.equals("FIELD");
        if (!invoke && !field) {
            return null;
        }
        Selector parsed = Selector.parse(target);
        if (parsed == null || obfuscatedOwner(parsed.owner())) {
            // Minecraft members are obfuscated at runtime, so a literal
            // development name can never be compared with the bytecode.
            return null;
        }
        for (MethodNode candidate : candidates) {
            if (candidate.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction : candidate.instructions) {
                if (invoke && instruction instanceof MethodInsnNode call
                        && parsed.matchesMember(call.owner, call.name, call.desc)) {
                    return null;
                }
                if (field && instruction instanceof FieldInsnNode access
                        && parsed.matchesMember(access.owner, access.name, access.desc)) {
                    return null;
                }
            }
        }
        return target;
    }

    private static boolean obfuscatedOwner(String owner) {
        return owner != null
                && (owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/"));
    }

    private static boolean accessorFieldExists(
            AnnotationNode annotation,
            MethodNode method,
            ClassNode target,
            ClassLookup lookup
    ) {
        Object explicit = value(annotation, "value");
        List<String> names = new ArrayList<>();
        if (explicit instanceof String name && !name.isEmpty()) {
            names.add(name);
        } else {
            String base = stripPrefix(method.name, "get", "set", "is");
            if (base == null) {
                return true;
            }
            names.add(decapitalize(base));
            names.add(base);
            names.add(base.toUpperCase(Locale.ROOT));
        }
        return findField(target, names, null, lookup) != null;
    }

    private static boolean invokerMethodExists(
            AnnotationNode annotation,
            MethodNode method,
            ClassNode target,
            ClassLookup lookup
    ) {
        Object explicit = value(annotation, "value");
        List<String> names = new ArrayList<>();
        if (explicit instanceof String name && !name.isEmpty()) {
            names.add(name);
        } else {
            String base = stripPrefix(method.name, "call", "invoke");
            if (base == null) {
                return true;
            }
            names.add(decapitalize(base));
            names.add(base);
        }
        if (names.contains("<init>") || names.contains("new") || names.contains("create")) {
            return true;
        }
        return findMethod(target, names, method.desc, lookup) != null;
    }

    private static FieldNode findField(
            ClassNode start,
            List<String> names,
            String desc,
            ClassLookup lookup
    ) {
        ClassNode current = start;
        for (int depth = 0; current != null && depth < MAX_SUPERCLASS_DEPTH; depth++) {
            if (current.fields != null) {
                for (FieldNode field : current.fields) {
                    if (names.contains(field.name) && (desc == null || desc.equals(field.desc))) {
                        return field;
                    }
                }
            }
            current = superclass(current, lookup);
        }
        return null;
    }

    private static MethodNode findMethod(
            ClassNode start,
            List<String> names,
            String desc,
            ClassLookup lookup
    ) {
        ClassNode current = start;
        for (int depth = 0; current != null && depth < MAX_SUPERCLASS_DEPTH; depth++) {
            if (current.methods != null) {
                for (MethodNode method : current.methods) {
                    if (names.contains(method.name) && desc.equals(method.desc)) {
                        return method;
                    }
                }
            }
            current = superclass(current, lookup);
        }
        return null;
    }

    private static ClassNode superclass(ClassNode node, ClassLookup lookup) {
        if (node.superName == null || node.superName.equals("java/lang/Object")) {
            return null;
        }
        try {
            return lookup.find(node.superName);
        } catch (Exception exception) {
            return null;
        }
    }

    private static List<String> shadowNames(String name, AnnotationNode shadow) {
        List<String> names = new ArrayList<>();
        Object prefix = value(shadow, "prefix");
        String effectivePrefix = prefix instanceof String text ? text : "shadow$";
        names.add(name.startsWith(effectivePrefix)
                ? name.substring(effectivePrefix.length())
                : name);
        for (Object alias : listValue(shadow, "aliases")) {
            if (alias instanceof String text) {
                names.add(text);
            }
        }
        return names;
    }

    private static String stripPrefix(String name, String... prefixes) {
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return null;
    }

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static String simpleName(String desc) {
        return desc.substring(desc.lastIndexOf('/') + 1, desc.length() - 1);
    }

    private static AnnotationNode mixinAnnotation(ClassNode mixin) {
        AnnotationNode annotation = annotation(mixin.invisibleAnnotations, MIXIN);
        return annotation != null ? annotation : annotation(mixin.visibleAnnotations, MIXIN);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) {
                return annotation;
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) {
            return null;
        }
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static List<?> listValue(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (value instanceof List<?> list) {
            return list;
        }
        return value == null ? Collections.emptyList() : List.of(value);
    }

    private static boolean booleanValue(AnnotationNode annotation, String key, boolean fallback) {
        Object value = value(annotation, key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    /** A literal Mixin member selector: optional owner, name, optional descriptor. */
    private record Selector(String owner, String name, String desc) {
        static Selector parse(String selector) {
            String text = selector.trim();
            if (text.isEmpty() || text.contains("*") || text.startsWith("/")
                    || text.contains("{") || text.contains(" ")) {
                return null;
            }
            String owner = null;
            if (text.startsWith("L")) {
                int end = text.indexOf(';');
                int paren = text.indexOf('(');
                int colon = text.indexOf(':');
                int memberStart = Math.min(
                        paren < 0 ? Integer.MAX_VALUE : paren,
                        colon < 0 ? Integer.MAX_VALUE : colon
                );
                if (end > 0 && end < memberStart) {
                    owner = text.substring(1, end);
                    text = text.substring(end + 1);
                }
            }
            String name = text;
            String desc = null;
            int paren = text.indexOf('(');
            int colon = text.indexOf(':');
            if (paren >= 0) {
                name = text.substring(0, paren);
                desc = text.substring(paren);
            } else if (colon >= 0) {
                name = text.substring(0, colon);
                desc = text.substring(colon + 1);
            }
            return name.isEmpty() ? null : new Selector(owner, name, desc);
        }

        boolean matches(String candidateName, String candidateDesc) {
            return name.equals(candidateName) && (desc == null || desc.equals(candidateDesc));
        }

        boolean matchesMember(String candidateOwner, String candidateName, String candidateDesc) {
            return (owner == null || owner.equals(candidateOwner))
                    && matches(candidateName, candidateDesc);
        }
    }
}
