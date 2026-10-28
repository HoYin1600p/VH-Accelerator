package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Raw archive pins and a post-apply method/field proof, with only our return callback omitted. */
public final class WorldZonesLayouts {
    public static final String TARGET = "iskallia/vault/core/world/storage/WorldZones";
    public static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.worldzones";
    public static final String MIXIN = GROUP + ".WorldZonesLoadMixin";
    public static final String HANDLER = "vhaccelerator$compactLoadedMemberships";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_DESC = "(L" + CALLBACK + ";)V";
    private static final Map<String, String> HASHES = Map.of(
            TARGET, "26aa68e629ea64d46dc0c66430e339ffc5d36157b3c311ebc228423776a851ed",
            "iskallia/vault/core/world/storage/WorldZone", "44668b1c53edcdf7e740849c1465ee638e0e4bf826c69ed33eaaffcb2eb57458",
            "iskallia/vault/core/world/storage/BlockCuboid", "86d7ffdb7214b6bf9047e142b9a16819624197c18084305bcf4e623e1f66f78c",
            "iskallia/vault/world/data/WorldZonesData", "76f4775ddbd077bf50bcc512161546b8d6167567c06a0a4e8a43ccf019ee2337");
    private static volatile Map<String, String> reviewedMethods = Map.of();
    private static volatile List<String> reviewedFields = List.of();
    private static volatile String reviewedHeader = "";
    private WorldZonesLayouts() { }

    public static boolean supports(String version, Function<String, byte[]> bytes) {
        if (!Set.of("1.18.2-3.21.62", "1.18.2-3.21.6.6884").contains(version == null ? "" : version)) return false;
        try {
            byte[] target = null;
            for (var entry : HASHES.entrySet()) {
                byte[] value = bytes.apply(entry.getKey());
                if (value == null || !digest(value).equals(entry.getValue())) return false;
                if (entry.getKey().equals(TARGET)) target = value;
            }
            ClassNode node = new ClassNode(); new ClassReader(target).accept(node, 0);
            Map<String, String> methods = new java.util.LinkedHashMap<>();
            for (MethodNode method : node.methods) methods.put(method.name + method.desc, fingerprint(method));
            reviewedMethods = Map.copyOf(methods); reviewedFields = fields(node); reviewedHeader = header(node);
            return true;
        } catch (Exception | LinkageError failure) { return false; }
    }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        try { return check(lookup.find(TARGET), false); }
        catch (Exception | LinkageError failure) { return List.of("WorldZones bytecode unavailable"); }
    }
    public static List<String> applicationProblems(ClassNode target) { return check(target, true); }

    private static List<String> check(ClassNode target, boolean applied) {
        List<String> failures = new ArrayList<>();
        if (target == null || !target.name.equals(TARGET) || reviewedMethods.isEmpty()) return List.of("reviewed WorldZones bytes unavailable");
        if (!fields(target).equals(reviewedFields)) failures.add("WorldZones field layout changed");
        if (!header(target).equals(reviewedHeader)) failures.add("WorldZones class layout changed");
        Set<String> remaining = new java.util.HashSet<>(reviewedMethods.keySet());
        int hookMethods = 0;
        int handlers = 0;
        try {
            for (MethodNode method : target.methods) {
                String merged = mergedOwner(method);
                boolean own = MIXIN.equals(merged) && method.name.contains(HANDLER) && method.desc.equals(CALLBACK_DESC);
                if (merged != null && !own) failures.add("foreign WorldZones mixin: " + method.name);
                if (own) {
                    handlers++;
                    if (!handlerBody(method)) failures.add("WorldZones handler changed: " + method.name);
                    continue;
                }
                String key = method.name + method.desc;
                if (!remaining.remove(key)) { failures.add("unexpected WorldZones method: " + key); continue; }
                MethodNode copy = new MethodNode(method.access, method.name, method.desc, null, null); method.accept(copy);
                int callbacks = applied ? removeCallback(copy, target, failures) : 0;
                boolean loaded = key.equals("readNbt(Lnet/minecraft/nbt/CompoundTag;)V")
                        || key.equals("readBits(Liskallia/vault/core/net/BitBuffer;)V");
                if (applied && loaded && callbacks != 1) failures.add("missing load callback: " + key);
                if (!loaded && callbacks != 0) failures.add("callback outside load: " + key);
                if (callbacks == 1) hookMethods++;
                if (!fingerprint(copy).equals(reviewedMethods.get(key))) failures.add("WorldZones method changed: " + key);
            }
            if (!remaining.isEmpty()) failures.add("missing WorldZones methods: " + remaining);
            if (applied && hookMethods != 2) failures.add("both WorldZones load hooks must be confirmed");
            if (applied && handlers != 1) failures.add("exactly one WorldZones load handler must be confirmed");
        } catch (RuntimeException failure) { failures.add("unrecognized WorldZones callback layout"); }
        return failures;
    }

    private static int removeCallback(MethodNode method, ClassNode target, List<String> failures) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(TARGET)
                    || !call.name.contains(HANDLER) || !call.desc.equals(CALLBACK_DESC)) continue;
            boolean owned = target.methods.stream().anyMatch(m -> m.name.equals(call.name) && m.desc.equals(call.desc) && MIXIN.equals(mergedOwner(m)));
            List<AbstractInsnNode> code = realInstructions(method);
            int at = code.indexOf(instruction);
            if (!owned || call.getOpcode() != Opcodes.INVOKESPECIAL || call.itf
                    || at + 1 >= code.size() || code.get(at + 1).getOpcode() != Opcodes.RETURN) {
                failures.add("unrecognized return callback: " + method.name); continue;
            }
            List<AbstractInsnNode> glue = new ArrayList<>();
            if (at >= 6 && variable(code.get(at - 6), Opcodes.ALOAD, 0)
                    && callbackConstruction(code.subList(at - 5, at), method.name)) {
                glue.addAll(code.subList(at - 6, at + 1));
            } else if (at >= 8 && variable(code.get(at - 2), Opcodes.ALOAD, 0)
                    && code.get(at - 1) instanceof VarInsnNode local && local.getOpcode() == Opcodes.ALOAD
                    && local.var >= 2 && callbackConstruction(code.subList(0, 5), method.name)
                    && variable(code.get(5), Opcodes.ASTORE, local.var)) {
                // Sponge 0.8.5 counts both target methods as injections. For a non-cancellable
                // void callback it creates CallbackInfo at HEAD and reloads that private local
                // at RETURN. Remove only that exact construction/store and receiver/load/call.
                glue.addAll(code.subList(0, 6));
                glue.addAll(code.subList(at - 2, at + 1));
                boolean shared = code.stream().filter(i -> !glue.contains(i)).anyMatch(i ->
                        (i instanceof VarInsnNode v && v.var == local.var)
                                || (i instanceof IincInsnNode increment && increment.var == local.var));
                if (shared) glue.clear();
            }
            if (glue.isEmpty()) { failures.add("unrecognized return callback: " + method.name); continue; }
            glue.forEach(method.instructions::remove); count++;
        }
        return count;
    }

    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
    }
    private static boolean variable(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot;
    }
    private static boolean callbackConstruction(List<AbstractInsnNode> code, String method) {
        return code.size() == 5 && code.get(0) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                && type.desc.equals(CALLBACK) && code.get(1).getOpcode() == Opcodes.DUP
                && code.get(2) instanceof LdcInsnNode id && method.equals(id.cst)
                && code.get(3).getOpcode() == Opcodes.ICONST_0
                && code.get(4) instanceof MethodInsnNode init && init.getOpcode() == Opcodes.INVOKESPECIAL
                && !init.itf && init.owner.equals(CALLBACK) && init.name.equals("<init>")
                && init.desc.equals("(Ljava/lang/String;Z)V");
    }
    private static boolean handlerBody(MethodNode method) {
        List<AbstractInsnNode> code = realInstructions(method);
        return (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == Opcodes.ACC_PRIVATE
                && code.size() == 3 && variable(code.get(0), Opcodes.ALOAD, 0)
                && code.get(1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                && call.owner.equals("dev/hoyin1600p/vhaccelerator/compat/worldzones/WorldZonesCompaction")
                && call.name.equals("compact") && call.desc.equals("(Ljava/lang/Object;)V")
                && code.get(2).getOpcode() == Opcodes.RETURN;
    }

    private static List<String> fields(ClassNode target) {
        return target.fields.stream().map(f -> f.access + ":" + f.name + ":" + f.desc + ":" + f.signature).toList();
    }
    private static String header(ClassNode target) {
        return target.name + ":" + target.access + ":" + target.superName + ":" + target.signature + ":" + target.interfaces;
    }
    private static String fingerprint(MethodNode method) {
        MethodNode copy = new MethodNode(method.access, method.name, method.desc, null,
                method.exceptions == null ? null : method.exceptions.toArray(String[]::new));
        method.accept(copy); copy.visibleAnnotations = null; copy.invisibleAnnotations = null; copy.localVariables = null;
        copy.visibleLocalVariableAnnotations = null; copy.invisibleLocalVariableAnnotations = null;
        for (AbstractInsnNode instruction : copy.instructions.toArray()) if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) copy.instructions.remove(instruction);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
        copy.accept(writer); writer.visitEnd(); return digest(writer.toByteArray());
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
    private static String mergedOwner(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        for (AnnotationNode a : annotations) if (a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && a.values != null) {
            for (int i = 0; i + 1 < a.values.size(); i += 2) if (a.values.get(i).equals("mixin")) return String.valueOf(a.values.get(i + 1));
        }
        return null;
    }
    public static Map<String, Object> contract() {
        return Map.of("contractVersion", 1, "modId", "the_vault", "supportedVersions", List.of("1.18.2-3.21.62", "1.18.2-3.21.6.6884"),
                "setting", "compatibility.compactWorldZonesMemberships", "settingDefault", true,
                "sides", List.of("DEDICATED_SERVER"), "selection", "exact archive class hashes, dedicated, common master, setting enabled, Compare Mode off",
                "activation", "both load RETURN calls and unchanged original bodies/fields; private default singleton state only",
                "injectionSites", List.of("WorldZones.readNbt RETURN", "WorldZones.readBits RETURN"),
                "requiredDependencies", List.of("private chunkToZones map", "default IntOpenHashSet n/minN32 f0.75", "original multi-member and diff sets"));
    }
}
