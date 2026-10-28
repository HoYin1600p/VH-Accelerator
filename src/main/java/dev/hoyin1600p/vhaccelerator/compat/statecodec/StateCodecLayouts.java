package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Checks {@code StateDefinition} against the shape the lazy property codec was written for (Minecraft 1.18.2,
 * Forge 40.3.x), before the mixin is woven and again on the woven class. Members are found by descriptor, so
 * the checks hold for Mojang and SRG names alike.
 *
 * <p>The mixin replaces the one {@code appendPropertyCodec} call of the constructor, and {@link LazyStateCodec}
 * carries a copy of that method. The copy is only equal to the original while the original's body is the
 * reviewed one, so the original's instruction fingerprint (calls, lambda handles and the bodies of its four
 * lambdas, with Minecraft member names left out) is pinned here. Any other mixin that has touched the class,
 * other than the one reviewed ModernFix hook, makes the optimization step aside.</p>
 */
public final class StateCodecLayouts {
    public static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.statecodec";
    public static final String MIXIN = GROUP + ".StateDefinitionLazyCodecMixin";
    public static final String STATE_DEFINITION = "net/minecraft/world/level/block/state/StateDefinition";
    /** The reviewed ModernFix 5.18 hook: it replaces the states map local, never the codec. */
    public static final String MODERNFIX_MIXIN =
            "org.embeddedt.modernfix.common.mixin.perf.state_definition_construct.StateDefinitionMixin";
    /** Lightspeed: two RETURN injects on the constructor, a HEAD inject on getProperty, a field and an interface. */
    public static final String LIGHTSPEED_MIXIN = "com.ccr4ft3r.lightspeed.mixin.misc.StateDefinitonMixin";
    /** VHA's own ModernFix backport: the same states-map local hook as ModernFix's. */
    public static final String VHA_BACKPORT_MIXIN =
            "dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.blockstate.definition.StateDefinitionMixin";
    private static final java.util.Set<String> ALLOWED_OWNERS =
            java.util.Set.of(MIXIN, MODERNFIX_MIXIN, LIGHTSPEED_MIXIN, VHA_BACKPORT_MIXIN);

    static final String APPEND_DESC = "(Lcom/mojang/serialization/MapCodec;Ljava/util/function/Supplier;"
            + "Ljava/lang/String;Lnet/minecraft/world/level/block/state/properties/Property;)"
            + "Lcom/mojang/serialization/MapCodec;";
    static final String CONSTRUCTOR_DESC = "(Ljava/util/function/Function;Ljava/lang/Object;"
            + "Lnet/minecraft/world/level/block/state/StateDefinition$Factory;Ljava/util/Map;)V";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String HANDLER = "vhaccelerator$lazyAppend";

    /**
     * The fingerprint of the original append method: its own tokens, with each lambda's tokens in braces
     * directly after its invokedynamic. {@code mc:} stands for a Minecraft member matched by descriptor only.
     */
    static final List<String> EXPECTED_APPEND = List.of(
            "INVOKEVIRTUAL mc:()Lcom/mojang/serialization/Codec;",
            "INVOKEINTERFACE com/mojang/serialization/Codec.fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;",
            "INDY accept()Ljava/util/function/Consumer;{}",
            "INDY get(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/util/function/Supplier;)Ljava/util/function/Supplier;{"
                    + "INVOKEINTERFACE java/util/function/Supplier.get()Ljava/lang/Object;"
                    + " CHECKCAST net/minecraft/world/level/block/state/StateHolder"
                    + " INVOKEVIRTUAL mc:(Lnet/minecraft/world/level/block/state/StateHolder;)Lnet/minecraft/world/level/block/state/properties/Property$Value;}",
            "INVOKEVIRTUAL com/mojang/serialization/MapCodec.orElseGet(Ljava/util/function/Consumer;Ljava/util/function/Supplier;)Lcom/mojang/serialization/MapCodec;",
            "INVOKESTATIC com/mojang/serialization/Codec.mapPair(Lcom/mojang/serialization/MapCodec;Lcom/mojang/serialization/MapCodec;)Lcom/mojang/serialization/MapCodec;",
            "INDY apply(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/util/function/Function;{"
                    + "INVOKEVIRTUAL com/mojang/datafixers/util/Pair.getFirst()Ljava/lang/Object;"
                    + " CHECKCAST net/minecraft/world/level/block/state/StateHolder"
                    + " INVOKEVIRTUAL com/mojang/datafixers/util/Pair.getSecond()Ljava/lang/Object;"
                    + " CHECKCAST net/minecraft/world/level/block/state/properties/Property$Value"
                    + " INVOKEVIRTUAL mc:()Ljava/lang/Comparable;"
                    + " INVOKEVIRTUAL mc:(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Object;"
                    + " CHECKCAST net/minecraft/world/level/block/state/StateHolder}",
            "INDY apply(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/util/function/Function;{"
                    + "INVOKEVIRTUAL mc:(Lnet/minecraft/world/level/block/state/StateHolder;)Lnet/minecraft/world/level/block/state/properties/Property$Value;"
                    + " INVOKESTATIC com/mojang/datafixers/util/Pair.of(Ljava/lang/Object;Ljava/lang/Object;)Lcom/mojang/datafixers/util/Pair;}",
            "INVOKEVIRTUAL com/mojang/serialization/MapCodec.xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;");

    private StateCodecLayouts() { }

    /** Problems with the untransformed class; empty when the lazy codec may be woven. */
    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        ClassNode node;
        try {
            node = lookup.find(STATE_DEFINITION);
        } catch (Exception | LinkageError unavailable) {
            node = null;
        }
        List<String> problems = new ArrayList<>();
        if (node == null) {
            problems.add("StateDefinition bytecode is unavailable");
            return problems;
        }
        shapeProblems(node, problems);
        return problems;
    }

    static void shapeProblems(ClassNode node, List<String> problems) {
        MethodNode append = single(node, APPEND_DESC, Opcodes.ACC_STATIC, problems,
                "appendPropertyCodec");
        MethodNode constructor = node.methods.stream()
                .filter(method -> method.name.equals("<init>") && method.desc.equals(CONSTRUCTOR_DESC))
                .findFirst().orElse(null);
        if (constructor == null) {
            problems.add("StateDefinition constructor shape changed");
        } else if (append != null) {
            if (count(constructor, Opcodes.INVOKESTATIC, node.name, append.name, APPEND_DESC) != 1) {
                problems.add("the constructor no longer calls appendPropertyCodec exactly once");
            }
            if (count(constructor, Opcodes.INVOKESTATIC, "com/mojang/serialization/Encoder", "empty",
                    "()Lcom/mojang/serialization/MapEncoder;") != 1
                    || count(constructor, Opcodes.INVOKESTATIC, "com/mojang/serialization/Decoder", "unit",
                    "(Ljava/util/function/Supplier;)Lcom/mojang/serialization/MapDecoder;") != 1
                    || count(constructor, Opcodes.INVOKESTATIC, "com/mojang/serialization/MapCodec", "of",
                    "(Lcom/mojang/serialization/MapEncoder;Lcom/mojang/serialization/MapDecoder;)"
                            + "Lcom/mojang/serialization/MapCodec;") != 1) {
                problems.add("the empty base codec of the constructor changed");
            }
        }
        if (append != null) {
            List<String> actual = fingerprint(node, append);
            if (!actual.equals(EXPECTED_APPEND)) {
                problems.add("appendPropertyCodec differs from the reviewed body: " + actual);
            }
        }
    }

    /**
     * Problems with the woven class, checked after every standard Mixin pass: the one call must now go to this
     * mixin's handler, the original must no longer be called, and no other mixin (except the reviewed
     * ModernFix hook on a different local) may have merged anything into the class.
     */
    public static List<String> applicationProblems(ClassNode target) {
        List<String> problems = new ArrayList<>();
        MethodNode append = single(target, APPEND_DESC, Opcodes.ACC_STATIC, problems,
                "appendPropertyCodec");
        MethodNode constructor = target.methods.stream()
                .filter(method -> method.name.equals("<init>") && method.desc.equals(CONSTRUCTOR_DESC))
                .findFirst().orElse(null);
        if (constructor == null || append == null) {
            problems.add("StateDefinition constructor or appendPropertyCodec missing after application");
            return problems;
        }
        int original = 0;
        int handler = 0;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(target.name)
                    && call.desc.equals(APPEND_DESC)) {
                if (call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals(append.name)) {
                    original++;
                } else if (call.name.contains(HANDLER) && mergedFromOwnMixin(target, call.name)
                        && instanceHandlerCall(call)) {
                    handler++;
                }
            }
        }
        if (original != 0 || handler != 1) {
            problems.add("the constructor does not call the lazy codec handler exactly once (original calls "
                    + original + ", handler calls " + handler + ")");
        }
        for (MethodNode method : target.methods) {
            String owner = mergedOwner(method);
            if (owner == null || owner.equals(MIXIN)) continue;
            if (!ALLOWED_OWNERS.contains(owner)) {
                problems.add("StateDefinition has a merged method from an unreviewed mixin: "
                        + method.name + " from " + owner);
            } else if (method.desc.contains("Lcom/mojang/serialization/MapCodec;")) {
                problems.add("StateDefinition has a merged codec method from a reviewed mixin that only "
                        + "reviewed it for other members: " + method.name + " from " + owner);
            }
        }
        if (!shapeStillReviewed(target, append)) {
            problems.add("appendPropertyCodec was changed by another transformer");
        }
        return problems;
    }

    private static boolean shapeStillReviewed(ClassNode target, MethodNode append) {
        return fingerprint(target, append).equals(EXPECTED_APPEND);
    }

    /** The merged handler must be an instance method: the redirect sits in the constructor, which is one. */
    private static boolean mergedFromOwnMixin(ClassNode target, String name) {
        return target.methods.stream().anyMatch(method -> method.name.equals(name)
                && (method.access & Opcodes.ACC_STATIC) == 0 && MIXIN.equals(mergedOwner(method)));
    }

    /**
     * Mixin replaces the static call with an instance handler as: the four arguments saved to fresh locals (last
     * argument first), {@code this}, the four arguments reloaded in order, then a private (invokespecial) call.
     * Any other shape is not the expected redirect.
     */
    static boolean instanceHandlerCall(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESPECIAL || call.itf) return false;
        AbstractInsnNode cursor = call;
        int[] loaded = new int[4];
        for (int index = 3; index >= 0; index--) {
            cursor = previousReal(cursor);
            if (!(cursor instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) return false;
            loaded[index] = load.var;
        }
        cursor = previousReal(cursor);
        if (!(cursor instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0) return false;
        for (int index = 0; index < 4; index++) {
            cursor = previousReal(cursor);
            if (!(cursor instanceof VarInsnNode save) || save.getOpcode() != Opcodes.ASTORE
                    || save.var != loaded[index]) return false;
        }
        return true;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode node) {
        AbstractInsnNode previous = node == null ? null : node.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    static String mergedOwner(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (MERGED.equals(annotation.desc) && annotation.values != null) {
                for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                    if ("mixin".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof String name) {
                        return name;
                    }
                }
                return "unavailable";
            }
        }
        return null;
    }

    /** Ordered tokens for the call, field and cast instructions of {@code method}, lambdas expanded. */
    static List<String> fingerprint(ClassNode owner, MethodNode method) {
        List<String> tokens = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                tokens.add(opcode(call.getOpcode()) + " " + member(owner, call.owner, call.name, call.desc));
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                StringBuilder token = new StringBuilder("INDY ").append(dynamic.name).append(dynamic.desc).append('{');
                for (Object argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals(owner.name)) {
                        MethodNode lambda = owner.methods.stream().filter(candidate -> candidate.name.equals(handle.getName())
                                && candidate.desc.equals(handle.getDesc())).findFirst().orElse(null);
                        if (lambda != null) token.append(String.join(" ", fingerprint(owner, lambda)));
                    }
                }
                tokens.add(token.append('}').toString());
            } else if (instruction instanceof FieldInsnNode field) {
                tokens.add(opcode(field.getOpcode()) + " " + member(owner, field.owner, field.name, field.desc));
            } else if (instruction instanceof TypeInsnNode type) {
                tokens.add(opcode(type.getOpcode()) + " " + type.desc);
            }
        }
        return tokens;
    }

    private static String member(ClassNode owner, String memberOwner, String name, String desc) {
        return memberOwner.startsWith("net/minecraft/") || memberOwner.equals(owner.name)
                ? "mc:" + desc : memberOwner + "." + name + desc;
    }

    private static String opcode(int opcode) {
        return switch (opcode) {
            case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
            case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
            case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
            case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
            case Opcodes.CHECKCAST -> "CHECKCAST";
            case Opcodes.INSTANCEOF -> "INSTANCEOF";
            case Opcodes.NEW -> "NEW";
            case Opcodes.GETFIELD -> "GETFIELD";
            case Opcodes.PUTFIELD -> "PUTFIELD";
            case Opcodes.GETSTATIC -> "GETSTATIC";
            case Opcodes.PUTSTATIC -> "PUTSTATIC";
            default -> "OP" + opcode;
        };
    }

    private static int count(MethodNode method, int opcode, String owner, String name, String desc) {
        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == opcode
                    && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
                found++;
            }
        }
        return found;
    }

    private static MethodNode single(ClassNode node, String desc, int access, List<String> problems, String label) {
        List<MethodNode> found = node.methods.stream()
                .filter(method -> method.desc.equals(desc) && (method.access & access) == access
                        && mergedOwner(method) == null).toList();
        if (found.size() != 1) {
            problems.add(label + " is not a single static method of the reviewed descriptor (" + found.size() + ")");
            return null;
        }
        return found.get(0);
    }

    public static Map<String, Object> contract() {
        return Map.of(
                "contractVersion", 1, "modId", "minecraft", "supportedVersions", List.of("1.18.2 with Forge 40.3.x StateDefinition shape"),
                "setting", "optimizations.lazyBlockStatePropertyCodecs", "settingDefault", true,
                "sides", List.of("DEDICATED_SERVER"),
                "selection", "dedicated server; common master and setting; Compare Mode off; reviewed StateDefinition shape by descriptor and instruction fingerprint",
                "activation", "redirect confirmed on the woven class; no merged method from any mixin except this one and the reviewed ModernFix 5.18 StateDefinitionMixin; any failure keeps the eager codec chain; FerriteCore 4.2.2 and ModernFix 5.18 never touch appendPropertyCodec or propertiesCodec, and any other merged StateDefinition method disables it",
                "injectionSites", List.of("StateDefinition.<init> appendPropertyCodec(...) redirect"),
                "requiredDependencies", List.of("StateDefinition.appendPropertyCodec body equals the pinned instruction fingerprint",
                        "StateDefinition.<init> builds MapCodec.of(Encoder.empty(), Decoder.unit(supplier)) and calls appendPropertyCodec once",
                        "StateHolder.propertiesCodec is read only through MapCodec methods"));
    }
}
