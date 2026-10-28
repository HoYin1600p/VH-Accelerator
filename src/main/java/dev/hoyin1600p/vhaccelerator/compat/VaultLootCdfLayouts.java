package dev.hoyin1600p.vhaccelerator.compat;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;

/** The eager builder is opt-in only for the reviewed, unmodified Vault implementation. */
public final class VaultLootCdfLayouts {
    public static final String SUPPORTED_VERSION = "1.18.2-3.21.62";
    /**
     * 3.21.6.6884 (Wolds 0.34.x) ships byte-identical TieredLootTableGenerator, CDF and CDFKey classes, so
     * the same SHA-256 pins that prove 3.21.62 prove it; only the version string differs.
     */
    public static final String SUPPORTED_VERSION_6884 = "1.18.2-3.21.6.6884";
    private static final java.util.Set<String> SUPPORTED_VERSIONS =
            java.util.Set.of(SUPPORTED_VERSION, SUPPORTED_VERSION_6884);
    public static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.cdf";
    public static final String MIXIN = GROUP + ".TieredLootTableGeneratorEagerCdfMixin";
    /** The dedicated-server lazy, compact distribution lives in its own group: it composes with VHAPI. */
    public static final String LAZY_GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.cdflazy";
    public static final String LAZY_MIXIN = LAZY_GROUP + ".TieredLootTableGeneratorLazyCdfMixin";
    /** The one reviewed external owner of compute and getProbability (VHAPI 5.0.0 through 5.8.1). */
    public static final String VHAPI_MIXIN = "xyz.iwolfking.vhapi.mixin.fixes.MixinTieredLootTableGenCDF";
    public static final String OUTER = "iskallia/vault/core/world/loot/generator/TieredLootTableGenerator";
    public static final String CDF = OUTER + "$CDF";
    public static final String KEY = OUTER + "$CDFKey";
    public static final String MAP = "Lit/unimi/dsi/fastutil/longs/Long2DoubleMap;";
    private static final Map<String, String> HASHES = Map.of(
            OUTER, "ea057f760aead50841db6f675b7b8a108f2ff38dcc07d0b98abaed1132aaa433",
            CDF, "d907314fe4d67f9c536b9450ec09b73d2626cbd44edddf407a6903bf518fd96c",
            KEY, "66b88fdad6a869c93cec46f6ca414345477e61a238b4669dba90adb7dd70653d");

    private static final Map<String, String> METHOD_HASHES = Map.of(
            OUTER, "fd7056f5cce0f11d5662015844138bd2dec629114a1248721d7a233a4cca7dae",
            CDF, "b0619656149f078182f1920bc2e744960880e80c40af6fc18fdc8d9afbcc5c3c",
            KEY, "954cfdc879ae4e8734ac5fe6626125fd8dfb7ac33ce9f6b8042ea58fcdcc60fc");

    public static final String LEGACY_CLIENT_MIXIN =
            "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.TieredLootTableGeneratorCdfMixin";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String CONSTRUCTOR_HASH =
            "7cd1694cc475dde062d6b70e6d359f6e29b4558968dfe9291352ef293939ee14";
    private static final String INITIALIZER_HASH =
            "b44fe4668f838c7374eb9d8bb6aff7c8fd6001abe94f77ee121ba320c521bcd5";
    // Scalar fingerprints only, derived after every raw archive dependency passed its pin.
    private static volatile Map<String, String> reviewedCdfMethods = Map.of();

    private VaultLootCdfLayouts() { }

    /** Raw archive bytes, before transformation: even same-version replacement implementations reject. */
    public static boolean supports(String version, Function<String, byte[]> bytes) {
        if (version == null || !SUPPORTED_VERSIONS.contains(version)) return false;
        try {
            byte[] cdf = null;
            for (var entry : HASHES.entrySet()) {
                byte[] content = bytes.apply(entry.getKey());
                if (content == null || !entry.getValue().equals(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(content)))) return false;
                if (CDF.equals(entry.getKey())) cdf = content;
            }
            ClassNode original = new ClassNode();
            new ClassReader(cdf).accept(original, 0);
            Map<String, String> methods = new java.util.LinkedHashMap<>();
            for (MethodNode method : original.methods) {
                methods.put(method.name + method.desc, singleMethodFingerprint(method));
            }
            reviewedCdfMethods = Map.copyOf(methods);
            return true;
        } catch (Exception | LinkageError | AssertionError unavailable) {
            return false;
        }
    }

    /** Recheck the transformed target's members and eager constructor assignment boundary. */
    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>();
        ClassNode node;
        try { node = lookup.find(CDF); } catch (Exception | LinkageError unavailable) { node = null; }
        if (node == null) return List.of("Vault CDF target bytecode unavailable");
        for (var expected : METHOD_HASHES.entrySet()) {
            try {
                ClassNode target = lookup.find(expected.getKey());
                if (target == null || !expected.getValue().equals(methodFingerprint(target))) {
                    problems.add("Vault CDF reviewed method bodies changed: " + expected.getKey());
                }
            } catch (Exception | LinkageError unavailable) {
                problems.add("Vault CDF method-body fingerprint unavailable: " + expected.getKey());
            }
        }
        fieldProblems(node, problems);
        for (var method : Map.of("compute", "()" + MAP, "pack", "([I)J", "unpack", "(J)[I",
                "getHeuristic", "([I)D", "getProbability", "([I)D").entrySet()) {
            if (node.methods.stream().noneMatch(candidate -> candidate.name.equals(method.getKey())
                    && candidate.desc.equals(method.getValue())
                    && (candidate.access & Opcodes.ACC_PUBLIC) != 0)) {
                problems.add("Vault CDF method changed: " + method.getKey());
            }
        }
        var constructor = node.methods.stream().filter(method -> method.name.equals("<init>")
                && method.desc.equals("(L" + KEY + ";)V")).findFirst();
        long calls = constructor.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.equals(CDF) && call.name.equals("compute")
                        && call.desc.equals("()" + MAP)).count();
        if (calls != 1) problems.add("Vault CDF eager constructor compute boundary changed");
        return problems;
    }

    /**
     * All standard Mixin application passes finish before any postApply callback. Validate their
     * final ownership before this optimization can activate, rather than trusting the earlier tree.
     */
    public static List<String> applicationProblems(ClassNode target, boolean physicalClient) {
        List<String> problems = new ArrayList<>();
        if (target == null || !CDF.equals(target.name)) return List.of("Vault CDF final target unavailable");
        try {
            fieldProblems(target, problems);
            boolean legacyClientOwner = false;
            boolean lazyOwner = false;
            for (MethodNode method : target.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                for (AnnotationNode annotation : annotations) {
                    if (!MERGED.equals(annotation.desc)) continue;
                    String owner = null;
                    if (annotation.values != null) {
                        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                            if ("mixin".equals(annotation.values.get(i))
                                    && annotation.values.get(i + 1) instanceof String name) {
                                owner = name.replace('/', '.');
                            }
                        }
                    }
                    if (method.name.equals("compute") || method.name.equals("getProbability")) {
                        problems.add("Vault CDF arithmetic method has a merged overwrite owner: "
                                + method.name + method.desc + " from " + (owner == null ? "unavailable" : owner));
                    }
                    if (LEGACY_CLIENT_MIXIN.equals(owner) && physicalClient) {
                        legacyClientOwner = true;
                    } else if (LAZY_MIXIN.equals(owner) && !physicalClient) {
                        // The dedicated-server lazy mixin checks its own constructor, get and getMap hooks.
                        lazyOwner = true;
                    } else if (!MIXIN.equals(owner)) {
                        problems.add("Vault CDF has an unreviewed merged method owner: "
                                + method.name + method.desc + " from " + (owner == null ? "unavailable" : owner));
                    }
                }
            }
            // Initializer-only Mixin changes have no merged handler metadata. The common eager
            // mixin changes neither method. Only the existing physical-client lazy redirect may
            // change the constructor; it never authorizes this exception on a dedicated server.
            if (!(physicalClient && legacyClientOwner) && !lazyOwner) {
                finalMethodFingerprint(target, "<init>", "(L" + KEY + ";)V", CONSTRUCTOR_HASH, problems);
            }
            finalMethodFingerprint(target, "<clinit>", "()V", INITIALIZER_HASH, problems);
            reviewedBodyProblems(target, (physicalClient && legacyClientOwner) || lazyOwner, problems);
        } catch (Exception | LinkageError | AssertionError unavailable) {
            problems.add("Vault CDF final application verification unavailable: " + unavailable.getClass().getSimpleName());
        }
        return problems;
    }

    private static void reviewedBodyProblems(ClassNode target, boolean legacyClient, List<String> problems)
            throws Exception {
        Map<String, String> expected = reviewedCdfMethods;
        if (expected.isEmpty()) {
            problems.add("Vault CDF reviewed archive method fingerprints unavailable");
            return;
        }
        for (var entry : expected.entrySet()) {
            List<MethodNode> matches = target.methods.stream()
                    .filter(method -> entry.getKey().equals(method.name + method.desc)).toList();
            if (matches.size() != 1) {
                problems.add("Vault CDF final method missing or duplicated: " + entry.getKey());
                continue;
            }
            MethodNode method = matches.get(0);
            // The pre-existing client lazy hooks alone may change these publication/read boundaries.
            // Their original arithmetic is still checked below, independently of their owner metadata.
            if (legacyClient && (method.name.equals("<init>") || method.name.equals("get")
                    || method.name.equals("getMap"))) continue;
            MethodNode copy = new MethodNode(method.access, method.name, method.desc, null, null);
            method.accept(copy);
            if (method.name.equals("compute") && !stripEagerHead(copy, target)) {
                problems.add("Vault CDF eager HEAD cancellation boundary changed");
                continue;
            }
            if (!entry.getValue().equals(singleMethodFingerprint(copy))) {
                problems.add("Vault CDF final reviewed method body changed: " + entry.getKey());
            }
        }
    }

    /** Normalize only Sponge's exact cancellable HEAD sequence for our one no-argument handler. */
    private static boolean stripEagerHead(MethodNode compute, ClassNode target) {
        String callback = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
        List<AbstractInsnNode> code = java.util.Arrays.stream(compute.instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        boolean callsOwn = code.stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(CDF) && ownEagerHandler(target, call.name, call.desc));
        if (!callsOwn) return true; // Missing injection is separately rejected by the call-edge observer.
        if (code.size() < 16 || !(code.get(5) instanceof VarInsnNode store)) return false;
        int local = store.var;
        if (!(code.get(8) instanceof MethodInsnNode handler) || !ownEagerHandler(target, handler.name, handler.desc)
                || !handler.owner.equals(CDF) || handler.getOpcode() != Opcodes.INVOKESPECIAL || handler.itf
                || !(code.get(11) instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFEQ
                || !type(code.get(0), Opcodes.NEW, callback) || code.get(1).getOpcode() != Opcodes.DUP
                || !(code.get(2) instanceof LdcInsnNode id) || !"compute".equals(id.cst)
                || code.get(3).getOpcode() != Opcodes.ICONST_1
                || !call(code.get(4), Opcodes.INVOKESPECIAL, callback, "<init>", "(Ljava/lang/String;Z)V")
                || store.getOpcode() != Opcodes.ASTORE || local < 1
                || !variable(code.get(6), Opcodes.ALOAD, 0) || !variable(code.get(7), Opcodes.ALOAD, local)
                || !variable(code.get(9), Opcodes.ALOAD, local)
                || !call(code.get(10), Opcodes.INVOKEVIRTUAL, callback, "isCancelled", "()Z")
                || !variable(code.get(12), Opcodes.ALOAD, local)
                || !call(code.get(13), Opcodes.INVOKEVIRTUAL, callback, "getReturnValue", "()Ljava/lang/Object;")
                || !type(code.get(14), Opcodes.CHECKCAST, "it/unimi/dsi/fastutil/longs/Long2DoubleMap")
                || code.get(15).getOpcode() != Opcodes.ARETURN) return false;
        AbstractInsnNode after = code.get(15).getNext();
        while (after != null && after != jump.label && after.getOpcode() < 0) after = after.getNext();
        if (after != jump.label) return false;
        while (compute.instructions.getFirst() != jump.label) compute.instructions.remove(compute.instructions.getFirst());
        return true;
    }

    private static boolean ownEagerHandler(ClassNode target, String name, String descriptor) {
        if (!name.contains("vhaccelerator$computeEagerly") || !descriptor.equals(
                "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V")) return false;
        return target.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull).flatMap(List::stream)
                .anyMatch(annotation -> MERGED.equals(annotation.desc) && annotation.values != null
                        && annotation.values.contains(MIXIN)));
    }

    private static boolean variable(AbstractInsnNode node, int opcode, int local) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == local;
    }

    private static boolean type(AbstractInsnNode node, int opcode, String descriptor) {
        return node instanceof TypeInsnNode type && type.getOpcode() == opcode && type.desc.equals(descriptor);
    }

    private static boolean call(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor) && !call.itf;
    }

    private static String singleMethodFingerprint(MethodNode method) throws Exception {
        ClassNode isolated = new ClassNode();
        isolated.methods.add(method);
        return methodFingerprint(isolated);
    }

    private static void fieldProblems(ClassNode node, List<String> problems) {
        for (var field : Map.of("samples", "I", "weights", "[D", "map", MAP).entrySet()) {
            if (node.fields.stream().noneMatch(candidate -> candidate.name.equals(field.getKey())
                    && candidate.desc.equals(field.getValue())
                    && (candidate.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))
                    == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))) {
                problems.add("Vault CDF final field changed: " + field.getKey());
            }
        }
    }

    private static void finalMethodFingerprint(ClassNode target, String name, String descriptor,
                                               String expected, List<String> problems) {
        ClassNode isolated = new ClassNode();
        target.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .forEach(isolated.methods::add);
        try {
            if (isolated.methods.size() != 1 || !expected.equals(methodFingerprint(isolated))) {
                problems.add("Vault CDF final initialization method changed: " + name + descriptor);
            }
        } catch (Exception | LinkageError unavailable) {
            problems.add("Vault CDF final initialization method unavailable: " + name + descriptor);
        }
    }

    /**
     * Hash executable method structure independently of constant-pool indices, frame/debug metadata
     * and method declaration order. This catches another transform changing the reviewed arithmetic,
     * key equality, cache/reload flow or eager constructor map assignment after the archive check.
     */
    static String methodFingerprint(ClassNode node) throws Exception {
        ClassNode clean = new ClassNode();
        clean.version = Opcodes.V17;
        clean.access = Opcodes.ACC_PUBLIC;
        clean.name = "Reviewed";
        clean.superName = "java/lang/Object";
        for (MethodNode original : node.methods) {
            // VHA's own additional handler methods do not replace the reviewed original methods.
            if (original.name.startsWith("vhaccelerator$")) continue;
            original.instructions.resetLabels();
            MethodNode copy = new MethodNode(original.access, original.name, original.desc, null, null);
            original.accept(copy);
            copy.signature = null;
            copy.exceptions = null;
            copy.parameters = null;
            copy.attrs = null;
            copy.visibleAnnotations = null;
            copy.invisibleAnnotations = null;
            copy.visibleTypeAnnotations = null;
            copy.invisibleTypeAnnotations = null;
            copy.visibleParameterAnnotations = null;
            copy.invisibleParameterAnnotations = null;
            copy.annotationDefault = null;
            copy.localVariables = null;
            copy.visibleLocalVariableAnnotations = null;
            copy.invisibleLocalVariableAnnotations = null;
            copy.maxStack = 0;
            copy.maxLocals = 0;
            for (AbstractInsnNode instruction : copy.instructions.toArray()) {
                if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) {
                    copy.instructions.remove(instruction);
                }
            }
            clean.methods.add(copy);
        }
        clean.methods.sort(java.util.Comparator.comparing(method -> method.name + method.desc));
        ClassWriter writer = new ClassWriter(0);
        clean.accept(writer);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
    }

    // ------------------------------------------------------------------------------------------
    // Dedicated-server lazy, compact distribution (own group; composes with VHAPI).
    // ------------------------------------------------------------------------------------------

    private static final String CALLBACK_RETURNABLE =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String LAZY_CONSTRUCTOR_HANDLER = "vhaccelerator$deferCompute";

    /** Raw-lookup preflight: the reviewed bodies plus the members the lazy hooks read and shadow. */
    public static List<String> lazyProblems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>(problems(lookup));
        ClassNode node;
        try { node = lookup.find(CDF); } catch (Exception | LinkageError unavailable) { node = null; }
        if (node == null) return problems;
        packBitsProblem(node, problems);
        for (var method : Map.of("get", "([I)D", "getMap", "()" + MAP).entrySet()) {
            if (node.methods.stream().noneMatch(candidate -> candidate.name.equals(method.getKey())
                    && candidate.desc.equals(method.getValue())
                    && (candidate.access & Opcodes.ACC_PUBLIC) != 0)) {
                problems.add("Vault CDF method changed: " + method.getKey());
            }
        }
        return problems;
    }

    private static void packBitsProblem(ClassNode node, List<String> problems) {
        if (node.fields.stream().noneMatch(candidate -> candidate.name.equals("packBits")
                && candidate.desc.equals("I")
                && (candidate.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))
                == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL))) {
            problems.add("Vault CDF final field changed: packBits");
        }
    }

    /**
     * Final-class verification for the lazy hooks, after every standard Mixin pass. VHAPI may own
     * compute and getProbability (and the members it merges to support them); nothing else may own
     * them, and nothing but this mixin may touch the constructor, get or getMap. The three hooks are
     * normalized back to the reviewed bodies, so another transformer's change to them is also caught.
     */
    public static List<String> lazyApplicationProblems(ClassNode target) {
        List<String> problems = new ArrayList<>();
        if (target == null || !CDF.equals(target.name)) return List.of("Vault CDF final target unavailable");
        try {
            fieldProblems(target, problems);
            packBitsProblem(target, problems);
            for (MethodNode method : target.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
                if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
                for (AnnotationNode annotation : annotations) {
                    if (!MERGED.equals(annotation.desc)) continue;
                    String owner = mergedOwner(annotation);
                    boolean arithmetic = method.name.equals("compute") || method.name.equals("getProbability");
                    if (LAZY_MIXIN.equals(owner)) {
                        continue;
                    } else if (VHAPI_MIXIN.equals(owner)) {
                        if (!arithmetic && !isVhapiSupportLambda(method.name)) {
                            problems.add("VHAPI merged an unreviewed member into the Vault CDF: " + method.name + method.desc);
                        }
                    } else if (MIXIN.equals(owner)) {
                        if (arithmetic) problems.add("Vault CDF arithmetic method has a merged overwrite owner: "
                                + method.name + method.desc + " from " + owner);
                    } else {
                        problems.add("Vault CDF has an unreviewed merged method owner: " + method.name + method.desc
                                + " from " + (owner == null ? "unavailable" : owner));
                    }
                }
            }
            lazyHookProblems(target, problems);
        } catch (Exception | LinkageError | AssertionError unavailable) {
            problems.add("Vault CDF final lazy verification unavailable: " + unavailable.getClass().getSimpleName());
        }
        return problems;
    }

    /**
     * Mixin renames a merged synthetic lambda with a session prefix and a unique suffix, for example
     * {@code md380e25$lambda$getProbability$3$0}; an unrenamed {@code lambda$compute$0} is accepted too.
     */
    private static final java.util.regex.Pattern VHAPI_SUPPORT_LAMBDA = java.util.regex.Pattern.compile(
            "(?:md[0-9a-f]+\\$)?lambda\\$(?:compute|getProbability)\\$\\d+(?:\\$\\d+)?");

    public static boolean isVhapiSupportLambda(String name) {
        return VHAPI_SUPPORT_LAMBDA.matcher(name).matches();
    }

    private static String mergedOwner(AnnotationNode annotation) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if ("mixin".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof String name) {
                return name.replace('/', '.');
            }
        }
        return null;
    }

    private static void lazyHookProblems(ClassNode target, List<String> problems) throws Exception {
        Map<String, String> expected = reviewedCdfMethods;
        if (expected.isEmpty()) {
            problems.add("Vault CDF reviewed archive method fingerprints unavailable");
            return;
        }
        String constructor = "<init>(L" + KEY + ";)V";
        String get = "get([I)D";
        String getMap = "getMap()" + MAP;
        for (String signature : List.of(constructor, get, getMap)) {
            if (!expected.containsKey(signature)) {
                problems.add("Vault CDF reviewed member missing: " + signature);
                continue;
            }
            List<MethodNode> matches = target.methods.stream()
                    .filter(method -> signature.equals(method.name + method.desc)).toList();
            if (matches.size() != 1) {
                problems.add("Vault CDF final method missing or duplicated: " + signature);
                continue;
            }
            MethodNode method = matches.get(0);
            MethodNode copy = new MethodNode(method.access, method.name, method.desc, null, null);
            method.accept(copy);
            boolean normalized = signature.equals(constructor)
                    ? restoreComputeCall(copy, target)
                    : stripCallbackHead(copy, target, signature.equals(get) ? "vhaccelerator$getLazily"
                            : "vhaccelerator$getMapLazily", signature.equals(get));
            if (!normalized) {
                problems.add("Vault CDF lazy hook boundary changed: " + signature);
            } else if (!expected.get(signature).equals(singleMethodFingerprint(copy))) {
                problems.add("Vault CDF final reviewed method body changed: " + signature);
            }
        }
    }

    /**
     * Undo the constructor redirect. Mixin rewrites an instance call to an instance handler as the receiver
     * saved to a local, {@code this} pushed, the receiver reloaded and the handler invoked (or {@code this} and a
     * swap). Exactly one such call to our merged handler, with exactly that glue, becomes this.compute() again;
     * any other shape fails closed.
     */
    private static boolean restoreComputeCall(MethodNode constructor, ClassNode target) {
        String handlerDescriptor = "(L" + CDF + ";)" + MAP;
        MethodInsnNode found = null;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(CDF)
                    && call.name.contains(LAZY_CONSTRUCTOR_HANDLER) && call.desc.equals(handlerDescriptor)) {
                if (found != null || (call.getOpcode() != Opcodes.INVOKESPECIAL && call.getOpcode() != Opcodes.INVOKEVIRTUAL)
                        || call.itf || !ownLazyHandler(target, call.name, call.desc)) return false;
                found = call;
            }
        }
        if (found == null) return false;
        AbstractInsnNode one = previousReal(found);
        AbstractInsnNode two = one == null ? null : previousReal(one);
        AbstractInsnNode three = two == null ? null : previousReal(two);
        if (one != null && two != null && three != null && one instanceof VarInsnNode reload
                && one.getOpcode() == Opcodes.ALOAD && variable(two, Opcodes.ALOAD, 0)
                && three instanceof VarInsnNode save && save.getOpcode() == Opcodes.ASTORE && save.var == reload.var) {
            constructor.instructions.remove(one);
            constructor.instructions.remove(two);
            constructor.instructions.remove(three);
        } else if (one != null && two != null && one.getOpcode() == Opcodes.SWAP && variable(two, Opcodes.ALOAD, 0)) {
            constructor.instructions.remove(one);
            constructor.instructions.remove(two);
        } else {
            return false;
        }
        constructor.instructions.set(found, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CDF, "compute", "()" + MAP, false));
        return true;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode node) {
        AbstractInsnNode previous = node.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    /** Normalize only Sponge's exact cancellable HEAD sequence for one of our private instance handlers. */
    private static boolean stripCallbackHead(MethodNode method, ClassNode target, String handlerBase, boolean get) {
        List<AbstractInsnNode> code = java.util.Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction.getOpcode() >= 0).toList();
        int arguments = get ? 1 : 0;
        int loadLocal = 7 + arguments;
        int returnIndex = loadLocal + (get ? 7 : 8);
        if (code.size() <= returnIndex || !(code.get(5) instanceof VarInsnNode store)) return false;
        int local = store.var;
        String handlerDescriptor = "(" + (get ? "[I" : "") + "L" + CALLBACK_RETURNABLE + ";)V";
        if (!type(code.get(0), Opcodes.NEW, CALLBACK_RETURNABLE) || code.get(1).getOpcode() != Opcodes.DUP
                || !(code.get(2) instanceof LdcInsnNode id) || !method.name.equals(id.cst)
                || code.get(3).getOpcode() != Opcodes.ICONST_1
                || !call(code.get(4), Opcodes.INVOKESPECIAL, CALLBACK_RETURNABLE, "<init>", "(Ljava/lang/String;Z)V")
                || store.getOpcode() != Opcodes.ASTORE || local < 1 + arguments
                || !variable(code.get(6), Opcodes.ALOAD, 0)
                || (get && !variable(code.get(7), Opcodes.ALOAD, 1))
                || !variable(code.get(loadLocal), Opcodes.ALOAD, local)) return false;
        if (!(code.get(loadLocal + 1) instanceof MethodInsnNode handler) || handler.getOpcode() != Opcodes.INVOKESPECIAL
                || handler.itf || !handler.owner.equals(CDF) || !handler.name.contains(handlerBase)
                || !handler.desc.equals(handlerDescriptor) || !ownLazyHandler(target, handler.name, handler.desc)
                || !variable(code.get(loadLocal + 2), Opcodes.ALOAD, local)
                || !call(code.get(loadLocal + 3), Opcodes.INVOKEVIRTUAL, CALLBACK_RETURNABLE, "isCancelled", "()Z")
                || !(code.get(loadLocal + 4) instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFEQ
                || !variable(code.get(loadLocal + 5), Opcodes.ALOAD, local)) return false;
        // Sponge emits its specialized primitive accessor for double returns; reference returns use a cast.
        if (get) {
            if (!call(code.get(loadLocal + 6), Opcodes.INVOKEVIRTUAL, CALLBACK_RETURNABLE,
                    "getReturnValueD", "()D")) return false;
        } else if (!call(code.get(loadLocal + 6), Opcodes.INVOKEVIRTUAL, CALLBACK_RETURNABLE,
                "getReturnValue", "()Ljava/lang/Object;")
                || !type(code.get(loadLocal + 7), Opcodes.CHECKCAST,
                        "it/unimi/dsi/fastutil/longs/Long2DoubleMap")) return false;
        if (code.get(returnIndex).getOpcode() != (get ? Opcodes.DRETURN : Opcodes.ARETURN)) return false;
        AbstractInsnNode after = code.get(returnIndex).getNext();
        while (after != null && after != jump.label && after.getOpcode() < 0) after = after.getNext();
        if (after != jump.label) return false;
        while (method.instructions.getFirst() != jump.label) method.instructions.remove(method.instructions.getFirst());
        return true;
    }

    private static boolean ownLazyHandler(ClassNode target, String name, String descriptor) {
        return target.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull).flatMap(List::stream)
                .anyMatch(annotation -> MERGED.equals(annotation.desc) && LAZY_MIXIN.equals(mergedOwner(annotation))));
    }

    public static Map<String, Object> lazyContract() {
        return Map.of("modId", "the_vault", "supportedVersions", List.of(SUPPORTED_VERSION, SUPPORTED_VERSION_6884),
                "setting", "compatibility.lazyCompactVaultLootCdf", "settingDefault", true,
                "sides", List.of("DEDICATED_SERVER"),
                "competingOwner", "VHAPI may own compute and getProbability and is left untouched; no other owner of compute, "
                        + "getProbability, the constructor, get or getMap; reviewed archive, preflight and final method bodies",
                "injectionSites", List.of("CDF.<init> redirect of compute() (deferred to first use); CDF.get HEAD; CDF.getMap HEAD"),
                "classSha256", HASHES, "methodStructureSha256", METHOD_HASHES,
                "runtimePolicy", "common master and Compare Mode; read per constructed distribution; computed once under a "
                        + "per-distribution lock and stored as a compact array after a bit-for-bit self-check");
    }

    public static Map<String, Object> contract() {
        return Map.of("modId", "the_vault", "supportedVersions", List.of(SUPPORTED_VERSION, SUPPORTED_VERSION_6884),
                "setting", "compatibility.optimizeVaultLootCdf", "settingDefault", true,
                "sides", List.of("CLIENT", "DEDICATED_SERVER"),
                "competingOwner", "VHAPI presence always yields; reviewed archive/preflight and final method bodies plus standard Mixin ownership; replacements retain original/external behavior",
                "injectionSites", List.of("CDF.compute HEAD; constructor still eagerly assigns complete map on servers"),
                "classSha256", HASHES, "methodStructureSha256", METHOD_HASHES,
                "runtimePolicy", "common master and Compare Mode; physical clients also require client master; next build/reload");
    }
}
