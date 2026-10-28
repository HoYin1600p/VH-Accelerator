package dev.hoyin1600p.vhaccelerator.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.*;

/** Bounded bytecode audit of common-side handlers and transitively called helpers; never loads classes. */
public final class CommonCompatibilityAudit {
    private static final String OWN = "dev/hoyin1600p/vhaccelerator/";
    private static final String FERRITE = "malte0811/ferritecore/";
    private static final int CLASS_LIMIT = 256;
    private static final int METHOD_LIMIT = 2048;
    private final MixinTargetPreflight.ClassLookup lookup;
    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Set<String> missing = new LinkedHashSet<>();
    private final Set<String> problems = new LinkedHashSet<>();
    private final Set<MethodRef> visited = new LinkedHashSet<>();
    private final ArrayDeque<MethodRef> pending = new ArrayDeque<>();

    private CommonCompatibilityAudit(MixinTargetPreflight.ClassLookup lookup) {
        this.lookup = lookup;
    }

    public static Result inspect(List<String> roots, MixinTargetPreflight.ClassLookup lookup) {
        CommonCompatibilityAudit audit = new CommonCompatibilityAudit(lookup);
        for (String root : roots) {
            ClassNode node = audit.resolve(root.replace('.', '/'));
            if (node != null) {
                MixinTargetPreflight.targets(node).forEach(audit::checkName);
                node.methods.forEach(method -> audit.pending.add(new MethodRef(node.name, method.name, method.desc)));
            }
        }
        while (!audit.pending.isEmpty()) {
            MethodRef ref = audit.pending.removeFirst();
            if (!audit.visited.add(ref)) continue;
            if (audit.visited.size() > METHOD_LIMIT) {
                audit.problems.add("common-side method audit limit exceeded");
                break;
            }
            ClassNode node = audit.resolve(ref.owner());
            if (node == null) continue;
            MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals(ref.name())
                    && candidate.desc.equals(ref.descriptor())).findFirst().orElse(null);
            if (method == null) {
                // Constructors and class initializers are not inherited; absent <clinit> is ordinary.
                if (!ref.name().startsWith("<") && node.superName != null && audit.follow(node.superName)) {
                    audit.pending.add(new MethodRef(node.superName, ref.name(), ref.descriptor()));
                }
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) audit.instruction(instruction);
        }
        return new Result(List.copyOf(audit.classes.keySet()), audit.visited.size(), List.copyOf(audit.problems));
    }

    private ClassNode resolve(String name) {
        checkName(name);
        if (classes.containsKey(name)) return classes.get(name);
        if (missing.contains(name)) return null;
        if (classes.size() >= CLASS_LIMIT) {
            problems.add("common-side class audit limit exceeded");
            return null;
        }
        ClassNode node;
        try {
            node = lookup.find(name);
        } catch (Exception | LinkageError failure) {
            node = null;
        }
        if (node == null) {
            missing.add(name);
            problems.add("common-side helper bytecode unavailable: " + name);
            return null;
        }
        classes.put(name, node);
        checkName(node.superName);
        node.interfaces.forEach(this::checkName);
        signature(node.signature);
        node.fields.forEach(field -> { descriptor(field.desc); signature(field.signature); });
        node.methods.forEach(method -> {
            descriptor(method.desc);
            signature(method.signature);
            if (method.exceptions != null) method.exceptions.forEach(this::checkName);
        });
        pending.add(new MethodRef(name, "<clinit>", "()V"));
        return node;
    }

    private void instruction(AbstractInsnNode instruction) {
        if (instruction instanceof MethodInsnNode call) {
            method(call.owner, call.name, call.desc);
        } else if (instruction instanceof FieldInsnNode field) {
            checkName(field.owner);
            descriptor(field.desc);
            if (follow(field.owner)) resolve(field.owner);
        } else if (instruction instanceof TypeInsnNode type) {
            checkName(type.desc);
            if (follow(type.desc)) resolve(type.desc);
        } else if (instruction instanceof MultiANewArrayInsnNode array) {
            descriptor(array.desc);
        } else if (instruction instanceof LdcInsnNode constant) {
            constant(constant.cst);
        } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
            descriptor(dynamic.desc);
            constant(dynamic.bsm);
            for (Object argument : dynamic.bsmArgs) constant(argument);
        }
    }

    private void constant(Object value) {
        if (value instanceof Type type) {
            type(type);
        } else if (value instanceof Handle handle) {
            if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
                checkName(handle.getOwner());
                descriptor(handle.getDesc());
                if (follow(handle.getOwner())) resolve(handle.getOwner());
            } else {
                method(handle.getOwner(), handle.getName(), handle.getDesc());
            }
        } else if (value instanceof String name && (name.startsWith("net.minecraft.client.")
                || name.startsWith("net.minecraftforge.client.") || name.startsWith("com.mojang.blaze3d."))) {
            problems.add("reflective client reference: " + name);
        }
    }

    private void method(String owner, String name, String desc) {
        checkName(owner);
        descriptor(desc);
        if (follow(owner)) {
            resolve(owner);
            pending.add(new MethodRef(owner, name, desc));
        }
    }

    private boolean follow(String owner) {
        return owner != null && (owner.startsWith(OWN) || owner.startsWith(FERRITE));
    }

    private void descriptor(String descriptor) {
        if (descriptor != null) type(Type.getType(descriptor));
    }

    private void signature(String signature) {
        if (signature == null) return;
        try {
            new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {
                @Override
                public void visitClassType(String name) { checkName(name); }
            });
        } catch (IllegalArgumentException malformed) {
            problems.add("unreadable generic signature");
        }
    }

    private void type(Type type) {
        if (type.getSort() == Type.METHOD) {
            type(type.getReturnType());
            for (Type argument : type.getArgumentTypes()) type(argument);
        } else if (type.getSort() == Type.ARRAY) {
            type(type.getElementType());
        } else if (type.getSort() == Type.OBJECT) {
            checkName(type.getInternalName());
        }
    }

    private void checkName(String name) {
        if (name == null) return;
        if (name.startsWith("[")) {
            descriptor(name);
        } else if (name.startsWith("net/minecraft/client/") || name.startsWith("net/minecraftforge/client/")
                || name.startsWith("com/mojang/blaze3d/") || name.startsWith(OWN + "client/")) {
            problems.add("client-side reference: " + name);
        }
    }

    private record MethodRef(String owner, String name, String descriptor) { }
    public record Result(List<String> helperClasses, int visitedMethods, List<String> problems) { }
}
