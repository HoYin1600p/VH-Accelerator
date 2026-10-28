package dev.hoyin1600p.vhaccelerator.tools.shapes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * An offline stand-in for Sponge Mixin, for tests only. It copies the compiled, unmodified
 * mixin members into the target class and adds the HEAD / RETURN / NEW call sites that each
 * {@code @Inject} annotation declares, passing a real {@code CallbackInfo(Returnable)}. It also
 * handles what Canary's shape mixins use: {@code @Overwrite}, {@code @Shadow} / {@code @Mutable}
 * members, merged static initialisers, added interfaces and constructor RETURN injections.
 * Sponge Mixin itself does not run, so refmap remapping, injection-point resolution and mixin
 * priority are not covered (the order of the mixin list stands in for priority); the mixin
 * handler bytecode is the production bytecode.
 */
final class MiniWeaver {
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";

    /** {@code memberNames} renames members of the mixin body (SRG to official); may be null. */
    record Mixin(String internalName, byte[] bytes, Function<String, String> memberNames) {
        Mixin(String internalName, byte[] bytes) {
            this(internalName, bytes, null);
        }
    }

    private record Handler(MethodNode method, boolean isStatic, String targetName, String targetDesc,
                           String location, String locationTarget, boolean cancellable) { }

    private MiniWeaver() { }

    static byte[] weave(byte[] targetBytes, List<Mixin> mixins, Function<String, byte[]> classBytes) {
        ClassNode target = new ClassNode();
        new ClassReader(targetBytes).accept(target, 0);
        List<Handler> handlers = new ArrayList<>();
        List<MethodNode> initialisers = new ArrayList<>();
        int index = 0;
        for (Mixin mixin : mixins) {
            String suffix = "$m" + index++;
            ClassNode original = new ClassNode();
            new ClassReader(mixin.bytes()).accept(original, 0);
            ClassNode copy = new ClassNode();
            original.accept(new ClassRemapper(copy, new Remapper() {
                @Override public String map(String internalName) {
                    return internalName.equals(mixin.internalName()) ? target.name : internalName;
                }
                @Override public String mapMethodName(String owner, String name, String descriptor) {
                    if (owner.equals(mixin.internalName()) && name.startsWith("vhaccelerator$") && !name.equals("vhaccelerator$leafContent")) return name + suffix;
                    return mixin.memberNames() == null ? name : mixin.memberNames().apply(name);
                }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    if (owner.equals(mixin.internalName()) && name.startsWith("vhaccelerator$") && !name.equals("vhaccelerator$leafContent")) return name + suffix;
                    return mixin.memberNames() == null ? name : mixin.memberNames().apply(name);
                }
            }));
            for (String type : copy.interfaces) if (!target.interfaces.contains(type)) target.interfaces.add(type);
            for (FieldNode field : copy.fields) {
                if (has(field.visibleAnnotations, field.invisibleAnnotations, SHADOW)) {
                    if (has(field.visibleAnnotations, field.invisibleAnnotations, MUTABLE)) {
                        for (FieldNode existing : target.fields) {
                            if (existing.name.equals(field.name)) existing.access &= ~Opcodes.ACC_FINAL;
                        }
                    }
                    continue;
                }
                for (FieldNode existing : target.fields) {
                    if (existing.name.equals(field.name)) throw new IllegalStateException("Field clash " + field.name);
                }
                target.fields.add(field);
            }
            for (MethodNode method : copy.methods) {
                if (method.name.equals("<init>")) continue;
                if (method.name.equals("<clinit>")) {
                    initialisers.add(method);
                    continue;
                }
                if (has(method.visibleAnnotations, method.invisibleAnnotations, SHADOW)) continue;
                boolean overwrite = has(method.visibleAnnotations, method.invisibleAnnotations, OVERWRITE);
                boolean existing = target.methods.removeIf(m -> m.name.equals(method.name) && m.desc.equals(method.desc));
                if (existing && !overwrite) throw new IllegalStateException("Unannotated clash " + method.name);
                if (overwrite && !existing) throw new IllegalStateException("Overwrite without target " + method.name);
                target.methods.add(method);
                handlers.addAll(handlersOf(method));
            }
        }
        for (MethodNode initialiser : initialisers) mergeInitialiser(target, initialiser);
        for (Handler handler : handlers) inject(target, handler);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) {
                if (a.equals(b)) return a;
                List<String> chain = new ArrayList<>();
                for (String c = a; c != null; c = superOf(c, classBytes)) chain.add(c);
                for (String c = b; c != null; c = superOf(c, classBytes)) if (chain.contains(c)) return c;
                return "java/lang/Object";
            }
        };
        target.accept(writer);
        return writer.toByteArray();
    }

    private static boolean has(List<AnnotationNode> visible, List<AnnotationNode> invisible, String descriptor) {
        for (List<AnnotationNode> list : List.of(visible == null ? List.<AnnotationNode>of() : visible,
                invisible == null ? List.<AnnotationNode>of() : invisible)) {
            for (AnnotationNode node : list) if (descriptor.equals(node.desc)) return true;
        }
        return false;
    }

    /** Appends a mixin's static initialiser to the target's, as Mixin does, with its locals moved clear of the target's. */
    private static void mergeInitialiser(ClassNode target, MethodNode initialiser) {
        MethodNode existing = target.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
        if (existing == null) {
            existing = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            existing.instructions.add(new InsnNode(Opcodes.RETURN));
            existing.maxLocals = 0;
            existing.maxStack = 0;
            target.methods.add(existing);
        }
        int offset = Math.max(existing.maxLocals, 8);
        InsnList additions = new InsnList();
        for (AbstractInsnNode insn : initialiser.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) continue;
            if (insn instanceof VarInsnNode var) var.var += offset;
            else if (insn instanceof IincInsnNode iinc) iinc.var += offset;
            initialiser.instructions.remove(insn);
            additions.add(insn);
        }
        AbstractInsnNode last = existing.instructions.getLast();
        while (last != null && last.getOpcode() != Opcodes.RETURN) last = last.getPrevious();
        existing.instructions.insertBefore(last, additions);
        existing.maxLocals = offset + initialiser.maxLocals + 8;
    }

    private static String superOf(String name, Function<String, byte[]> classBytes) {
        if (name.equals("java/lang/Object")) return null;
        byte[] bytes = classBytes.apply(name);
        if (bytes == null) {
            try {
                Class<?> type = Class.forName(name.replace('/', '.'), false, MiniWeaver.class.getClassLoader());
                return type.getSuperclass() == null ? null : type.getSuperclass().getName().replace('.', '/');
            } catch (ClassNotFoundException missing) {
                return "java/lang/Object";
            }
        }
        return new ClassReader(bytes).getSuperName();
    }

    private static List<Handler> handlersOf(MethodNode method) {
        List<Handler> result = new ArrayList<>();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (!INJECT.equals(annotation.desc)) continue;
            List<String> targets = new ArrayList<>();
            List<AnnotationNode> at = new ArrayList<>();
            boolean cancellable = false;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if (key.equals("method")) for (Object v : (List<?>) value) targets.add((String) v);
                else if (key.equals("at")) {
                    if (value instanceof AnnotationNode single) at.add(single);
                    else for (Object v : (List<?>) value) at.add((AnnotationNode) v);
                } else if (key.equals("cancellable")) cancellable = (Boolean) value;
            }
            for (String name : targets) for (AnnotationNode point : at) {
                String location = null, locationTarget = null;
                for (int i = 0; i < point.values.size(); i += 2) {
                    String key = (String) point.values.get(i);
                    if (key.equals("value")) location = (String) point.values.get(i + 1);
                    else if (key.equals("target")) locationTarget = (String) point.values.get(i + 1);
                }
                int paren = name.indexOf('(');
                String methodName = paren < 0 ? name : name.substring(0, paren);
                String methodDesc = paren < 0 ? null : name.substring(paren);
                result.add(new Handler(method, (method.access & Opcodes.ACC_STATIC) != 0, methodName, methodDesc,
                        location, locationTarget, cancellable));
            }
        }
        return result;
    }

    private static void inject(ClassNode target, Handler handler) {
        List<MethodNode> candidates = target.methods.stream()
                .filter(m -> m.name.equals(handler.targetName())
                        && (handler.targetDesc() == null || m.desc.equals(handler.targetDesc()))).toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException("Expected exactly one target " + handler.targetName()
                    + handler.targetDesc() + " in " + target.name + ", found " + candidates.size());
        }
        MethodNode method = candidates.get(0);
        boolean targetStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        if (!handler.isStatic() && targetStatic) throw new IllegalStateException("Instance handler for static target");
        switch (handler.location()) {
            case "HEAD" -> {
                InsnList list = entry(target, method, handler);
                method.instructions.insert(list);
            }
            case "NEW" -> {
                String type = handler.locationTarget();
                int matches = 0;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof TypeInsnNode node && node.getOpcode() == Opcodes.NEW && node.desc.equals(type)) {
                        method.instructions.insertBefore(insn, entry(target, method, handler));
                        matches++;
                    }
                }
                if (matches == 0) throw new IllegalStateException("No NEW " + type + " in " + method.name);
            }
            case "RETURN" -> {
                boolean isVoid = Type.getReturnType(method.desc).getSort() == Type.VOID;
                int opcode = isVoid ? Opcodes.RETURN : Opcodes.ARETURN;
                AbstractInsnNode last = null;
                int matches = 0;
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == opcode) {
                        last = insn;
                        matches++;
                    }
                }
                if (matches == 0) throw new IllegalStateException("No return in " + method.name);
                if (isVoid) {
                    // Like Mixin for a constructor: only the final return.
                    method.instructions.insertBefore(last, exit(target, method, handler));
                } else {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn.getOpcode() == opcode) method.instructions.insertBefore(insn, exit(target, method, handler));
                    }
                }
            }
            default -> throw new IllegalStateException("Unsupported injection point " + handler.location());
        }
    }

    private static boolean returnsVoid(MethodNode method) {
        return Type.getReturnType(method.desc).getSort() == Type.VOID;
    }

    /** Creates the callback, calls the handler, and returns early if the handler cancelled. */
    private static InsnList entry(ClassNode target, MethodNode method, Handler handler) {
        int cir = method.maxLocals++;
        InsnList list = new InsnList();
        newCallback(list, method, handler, -1);
        list.add(new VarInsnNode(Opcodes.ASTORE, cir));
        callHandler(list, target, method, handler, cir);
        if (handler.cancellable()) {
            if (returnsVoid(method)) throw new IllegalStateException("Cancellable void injection is not supported");
            LabelNode continueLabel = new LabelNode();
            list.add(new VarInsnNode(Opcodes.ALOAD, cir));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CIR, "isCancelled", "()Z", false));
            list.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
            list.add(new VarInsnNode(Opcodes.ALOAD, cir));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CIR, "getReturnValue", "()Ljava/lang/Object;", false));
            list.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getReturnType(method.desc).getInternalName()));
            list.add(new InsnNode(Opcodes.ARETURN));
            list.add(continueLabel);
        }
        return list;
    }

    /** Wraps the value about to be returned, calls the handler, and returns the (possibly replaced) value. */
    private static InsnList exit(ClassNode target, MethodNode method, Handler handler) {
        int cir = method.maxLocals++;
        InsnList list = new InsnList();
        if (returnsVoid(method)) {
            newCallback(list, method, handler, -1);
            list.add(new VarInsnNode(Opcodes.ASTORE, cir));
            callHandler(list, target, method, handler, cir);
            return list;
        }
        int value = method.maxLocals++;
        list.add(new VarInsnNode(Opcodes.ASTORE, value));
        newCallback(list, method, handler, value);
        list.add(new VarInsnNode(Opcodes.ASTORE, cir));
        callHandler(list, target, method, handler, cir);
        if (handler.cancellable()) {
            list.add(new VarInsnNode(Opcodes.ALOAD, cir));
            list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CIR, "getReturnValue", "()Ljava/lang/Object;", false));
            list.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getReturnType(method.desc).getInternalName()));
        } else {
            list.add(new VarInsnNode(Opcodes.ALOAD, value));
        }
        return list;
    }

    /** Builds the callback; a non-negative {@code valueLocal} supplies the pending return value. */
    private static void newCallback(InsnList list, MethodNode method, Handler handler, int valueLocal) {
        String type = returnsVoid(method) ? CI : CIR;
        list.add(new TypeInsnNode(Opcodes.NEW, type));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(new LdcInsnNode(handler.targetName()));
        list.add(new InsnNode(handler.cancellable() ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        if (valueLocal >= 0) {
            list.add(new VarInsnNode(Opcodes.ALOAD, valueLocal));
            list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, type, "<init>", "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
        } else {
            list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, type, "<init>", "(Ljava/lang/String;Z)V", false));
        }
    }

    private static void callHandler(InsnList list, ClassNode target, MethodNode method, Handler handler, int cir) {
        int local = 0;
        if (!handler.isStatic()) {
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        if ((method.access & Opcodes.ACC_STATIC) == 0) local = 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            list.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }
        list.add(new VarInsnNode(Opcodes.ALOAD, cir));
        int opcode = handler.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL;
        list.add(new MethodInsnNode(opcode, target.name, handler.method().name, handler.method().desc, false));
    }

    static byte[] read(ClassLoader loader, String internalName) {
        try (InputStream stream = loader.getResourceAsStream(internalName + ".class")) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
