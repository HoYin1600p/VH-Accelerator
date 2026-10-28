package dev.hoyin1600p.vhaccelerator.backport.fix;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Pins Forge 40.3.11's complete createIntrusiveHolder body before redirecting its single map update. */
public final class ForgeIntrusiveHolderLayout {
    public static final String TARGET = "net/minecraftforge/registries/NamespacedHolderHelper";
    static final String CREATE = "(Lnet/minecraftforge/registries/IForgeRegistryEntry;)Lnet/minecraft/core/Holder$Reference;";
    private static final String REVIEWED = "1192f8a1e8106063224cc4c1b29300991ea326b46bf0d0e1114e863ccce4f4f8";

    private ForgeIntrusiveHolderLayout() { }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        try { return problems(lookup.find(TARGET)); }
        catch (Exception | LinkageError | AssertionError unavailable) { return List.of("Forge intrusive-holder bytecode unavailable"); }
    }

    static List<String> problems(ClassNode node) {
        if (node == null || !TARGET.equals(node.name)) return List.of("Forge intrusive-holder target unavailable");
        List<String> problems = new ArrayList<>();
        for (var field : Map.of("self", "Lnet/minecraft/core/Registry;", "holders", "Ljava/util/Map;").entrySet()) {
            int expected = Opcodes.ACC_PRIVATE | (field.getKey().equals("self") ? Opcodes.ACC_FINAL : 0);
            if (node.fields.stream().filter(f -> f.name.equals(field.getKey()) && f.desc.equals(field.getValue())
                    && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE
                    | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == expected).count() != 1) {
                problems.add("Forge intrusive-holder field changed: " + field.getKey());
            }
        }
        List<MethodNode> found = node.methods.stream().filter(m -> m.name.equals("createIntrusiveHolder")
                && m.desc.equals(CREATE)).toList();
        if (found.size() != 1) return List.of("Forge createIntrusiveHolder missing or duplicated");
        MethodNode method = found.get(0);
        if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED
                | Opcodes.ACC_NATIVE | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) {
            problems.add("Forge createIntrusiveHolder modifiers changed");
        }
        if (java.util.stream.Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull).flatMap(List::stream)
                .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;"))) {
            problems.add("Forge createIntrusiveHolder has another overwrite owner");
        }
        try {
            if (!REVIEWED.equals(fingerprint(method))) problems.add("Forge createIntrusiveHolder reviewed body changed");
        } catch (Exception | LinkageError | AssertionError unavailable) {
            problems.add("Forge intrusive-holder fingerprint unavailable");
        }
        return problems;
    }

    /** Constant-pool, frame and source-line independent; retains executable instructions and exception edges. */
    static String fingerprint(MethodNode original) throws Exception {
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
        copy.maxStack = copy.maxLocals = 0;
        for (AbstractInsnNode i : copy.instructions.toArray()) {
            if (i instanceof FrameNode || i instanceof LineNumberNode) copy.instructions.remove(i);
        }
        ClassNode isolated = new ClassNode();
        isolated.version = Opcodes.V17;
        isolated.access = Opcodes.ACC_PUBLIC;
        isolated.name = "Reviewed";
        isolated.superName = "java/lang/Object";
        isolated.methods.add(copy);
        ClassWriter writer = new ClassWriter(0);
        isolated.accept(writer);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
    }
}
