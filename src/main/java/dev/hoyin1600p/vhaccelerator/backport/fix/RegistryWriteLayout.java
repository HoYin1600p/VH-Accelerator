package dev.hoyin1600p.vhaccelerator.backport.fix;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks the untransformed {@code MappedRegistry} and {@code Registry} against the shape the registry
 * write-lock mixins were written for (Minecraft 1.18.2 with Forge 40.3.x). Members are found by
 * descriptor, so the check holds for both Mojang and SRG names. Any difference means the mixins would
 * overwrite code they were not written against, so they are skipped instead.
 */
public final class RegistryWriteLayout {
    public static final String MAPPED_REGISTRY = "net/minecraft/core/MappedRegistry";
    public static final String REGISTRY = "net/minecraft/core/Registry";

    private static final String HOLDER = "Lnet/minecraft/core/Holder;";
    private static final String KEY = "Lnet/minecraft/resources/ResourceKey;";
    private static final String LIFECYCLE = "Lcom/mojang/serialization/Lifecycle;";
    private static final String OBJECT = "Ljava/lang/Object;";
    static final String REGISTER_DESC = "(" + KEY + OBJECT + LIFECYCLE + ")" + HOLDER;
    static final String MAPPING_DESC = "(I" + KEY + OBJECT + LIFECYCLE + ")" + HOLDER;
    static final String MAPPING_FULL_DESC = "(I" + KEY + OBJECT + LIFECYCLE + "Z)" + HOLDER;
    static final String OVERRIDE_DESC = "(Ljava/util/OptionalInt;" + KEY + OBJECT + LIFECYCLE + ")" + HOLDER;
    static final String HOLDER_DESC = "(" + KEY + ")" + HOLDER;
    static final String INTERNAL_REGISTER_DESC = "(" + KEY + "Lnet/minecraft/core/WritableRegistry;"
            + "Lnet/minecraft/core/Registry$RegistryBootstrap;" + LIFECYCLE
            + ")Lnet/minecraft/core/WritableRegistry;";

    private RegistryWriteLayout() {
    }

    /** Empty when both classes have the reviewed shape. */
    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>();
        mappedRegistryProblems(find(lookup, MAPPED_REGISTRY), problems);
        registryProblems(find(lookup, REGISTRY), problems);
        return problems;
    }

    public static void mappedRegistryProblems(ClassNode node, List<String> problems) {
        if (node == null) {
            problems.add(MAPPED_REGISTRY + " bytecode is unavailable");
            return;
        }
        MethodNode register = single(node, REGISTER_DESC, Opcodes.ACC_PUBLIC, problems, "register");
        MethodNode mapping = single(node, MAPPING_DESC, Opcodes.ACC_PUBLIC, problems, "registerMapping(int)");
        MethodNode full = single(node, MAPPING_FULL_DESC, Opcodes.ACC_PRIVATE, problems, "registerMapping(boolean)");
        MethodNode override = single(node, OVERRIDE_DESC, Opcodes.ACC_PUBLIC, problems, "registerOrOverride");
        MethodNode holder = single(node, HOLDER_DESC, Opcodes.ACC_PUBLIC, problems, "getOrCreateHolder");
        if (register != null && !callsOn(register, mapping, Opcodes.INVOKEVIRTUAL)) {
            problems.add("register no longer delegates to the four-argument registerMapping");
        }
        if (mapping != null && full != null && !callsOn(mapping, full, Opcodes.INVOKESPECIAL)
                && !callsOn(mapping, full, Opcodes.INVOKEVIRTUAL)) {
            problems.add("registerMapping no longer delegates to the five-argument registerMapping");
        }
        if (full != null && countCalls(full, "computeIfAbsent") != 1) {
            problems.add("the five-argument registerMapping no longer has one computeIfAbsent");
        }
        if (holder != null && countCalls(holder, "computeIfAbsent") != 1) {
            problems.add("getOrCreateHolder no longer has one computeIfAbsent");
        }
        if (override != null && !callsOn(override, full, Opcodes.INVOKESPECIAL)
                && !callsOn(override, full, Opcodes.INVOKEVIRTUAL)) {
            problems.add("registerOrOverride no longer delegates to the five-argument registerMapping");
        }
        if (node.fields.stream().filter(RegistryWriteLayout::plainIntField).count() != 1) {
            problems.add("MappedRegistry no longer has exactly one private int field (nextId)");
        }
    }

    public static void registryProblems(ClassNode node, List<String> problems) {
        if (node == null) {
            problems.add(REGISTRY + " bytecode is unavailable");
            return;
        }
        List<MethodNode> candidates = node.methods.stream()
                .filter(method -> method.desc.equals(INTERNAL_REGISTER_DESC)
                        && (method.access & Opcodes.ACC_STATIC) != 0
                        && (method.access & Opcodes.ACC_SYNTHETIC) == 0)
                .toList();
        if (candidates.size() != 1) {
            problems.add("Registry has " + candidates.size() + " internalRegister candidates, expected 1");
            return;
        }
        long puts = 0;
        for (AbstractInsnNode instruction : candidates.get(0).instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEINTERFACE
                    && call.owner.equals("java/util/Map")
                    && call.name.equals("put")
                    && call.desc.equals("(" + OBJECT + OBJECT + ")" + OBJECT)) {
                puts++;
            }
        }
        if (puts != 1) {
            problems.add("internalRegister has " + puts + " Map.put calls, expected 1");
        }
    }

    private static boolean plainIntField(FieldNode field) {
        return field.desc.equals("I")
                && (field.access & Opcodes.ACC_PRIVATE) != 0
                && (field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == 0;
    }

    private static MethodNode single(ClassNode node, String desc, int access, List<String> problems, String label) {
        List<MethodNode> matches = node.methods.stream()
                .filter(method -> method.desc.equals(desc)
                        && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) == 0
                        && !method.name.startsWith("lambda$"))
                .toList();
        if (matches.size() != 1) {
            problems.add("MappedRegistry has " + matches.size() + " methods shaped like " + label + ", expected 1");
            return null;
        }
        MethodNode method = matches.get(0);
        if ((method.access & Opcodes.ACC_PUBLIC) != (access & Opcodes.ACC_PUBLIC)
                || (method.access & Opcodes.ACC_PRIVATE) != (access & Opcodes.ACC_PRIVATE)) {
            problems.add(label + " has unexpected access flags");
        }
        if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            problems.add(label + " is already synchronized");
        }
        return method;
    }

    private static boolean callsOn(MethodNode caller, MethodNode callee, int opcode) {
        if (caller == null || callee == null) {
            return true;
        }
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == opcode
                    && call.name.equals(callee.name)
                    && call.desc.equals(callee.desc)) {
                return true;
            }
        }
        return false;
    }

    private static long countCalls(MethodNode method, String name) {
        long calls = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) {
                calls++;
            }
        }
        return calls;
    }

    private static ClassNode find(MixinTargetPreflight.ClassLookup lookup, String name) {
        try {
            return lookup.find(name);
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }
}
