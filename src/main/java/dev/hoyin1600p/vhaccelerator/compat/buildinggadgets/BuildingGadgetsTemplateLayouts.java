package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import com.google.common.collect.ImmutableMap;
import cpw.mods.modlauncher.api.INameMappingService;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Literal reviewed archive, fresh-publication bodies and runtime lookup dependencies. */
public final class BuildingGadgetsTemplateLayouts {
    public static final String VERSION = "3.13.2-build.21+mc1.18.2";
    public static final String ARCHIVE_SHA256 = "5eae7d0201adc89c4d6cdf80defb2807768a269090bb4ce7bc7c901cd208813a";
    public static final String TEMPLATE = "com/direwolf20/buildinggadgets/common/tainted/template/Template";
    public static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets";
    private static final String TEMPLATE_SHA256 = "e7134fdebd52b67d12ff7f440012e42615ad6078aa305758d748af88615e6ba2";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String ITERATING_OWNER = "com/google/common/collect/ImmutableSet";
    private static final String ITERATOR_DESC = "()Lcom/google/common/collect/UnmodifiableIterator;";
    private static final String ITERATOR_HANDLER_DESC = "(Lcom/google/common/collect/ImmutableSet;)Lcom/google/common/collect/UnmodifiableIterator;";
    private static final String VIEW_OWNER = "com/direwolf20/buildinggadgets/common/tainted/building/view/PositionalBuildView";
    private static final String VIEW_HANDLER_DESC = "(Ljava/lang/Object;Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;";
    /** Methods that only iterate the private {@code map} field's entries, once each; they stream packed templates. */
    private static final Set<String> ITERATING = Set.of("serialize", "rotate", "mirror", "replace", "normalize");
    private static final Map<String, String> GUAVA = Map.of(
            "com/google/common/collect/ImmutableMap", "69692a5c1ee6adccae4fbd83205538250faebc64cf194431af7cc6df478ff0f7",
            "com/google/common/collect/ImmutableList", "c95324d7fe0320971e1b89a8286d49211e45624b63612d2e2e1b7628dbdac1ef",
            "com/google/common/collect/ImmutableSet", "896ff98dad4636cf7ae86b130bbbdef606990f0f785f9171897a22a1c40196e4",
            "com/google/common/collect/ImmutableCollection", "6757bcb166863e470b2cde475c9c2edbf1437c7ddd770805c6c8fc261d0f9917",
            "com/google/common/collect/JdkBackedImmutableMap", "fc03382b2c795942c62a709b027bece001445dd7c523131e7ef301fb835b18d6");
    private static volatile byte[] reviewedTemplate;

    private BuildingGadgetsTemplateLayouts() { }

    /** Called once during early discovery, never on a template lookup. */
    public static boolean supportsArchive(Path archive, String version) {
        return supportsArchive(archive, version, BuildingGadgetsTemplateLayouts::guavaBytes);
    }

    public static boolean supportsArchive(Path archive, String version, Function<String, byte[]> dependencies) {
        if (!VERSION.equals(version) || archive == null) return false;
        try {
            if (!ARCHIVE_SHA256.equals(sha(Files.readAllBytes(archive)))) return false;
            byte[] template;
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
                var classEntry = zip.getEntry(TEMPLATE + ".class");
                if (manifestEntry == null || classEntry == null) return false;
                try (InputStream input = zip.getInputStream(manifestEntry)) {
                    if (!VERSION.equals(new Manifest(input).getMainAttributes().getValue("Implementation-Version"))) return false;
                }
                try (InputStream input = zip.getInputStream(classEntry)) { template = input.readAllBytes(); }
            }
            if (!TEMPLATE_SHA256.equals(sha(template))) return false;
            for (var dependency : GUAVA.entrySet()) {
                byte[] bytes = dependencies.apply(dependency.getKey());
                if (bytes == null || !dependency.getValue().equals(sha(bytes))) return false;
            }
            reviewedTemplate = template;
            return true;
        } catch (Exception | LinkageError unavailable) {
            return false;
        }
    }

    private static byte[] guavaBytes(String name) {
        try (InputStream input = ImmutableMap.class.getResourceAsStream("/" + name + ".class")) {
            return input == null ? null : input.readAllBytes();
        } catch (Exception | LinkageError unavailable) { return null; }
    }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        try { return layoutProblems(lookup.find(TEMPLATE), null); }
        catch (Exception | LinkageError unavailable) { return List.of("Building Gadgets template bytecode unavailable"); }
    }

    public static List<String> applicationProblems(ClassNode target, String mixin) {
        return layoutProblems(target, mixin);
    }

    private static List<String> layoutProblems(ClassNode target, String mixin) {
        byte[] bytes = reviewedTemplate;
        if (bytes == null || target == null) return List.of("reviewed Building Gadgets archive not confirmed");
        ClassNode original = node(bytes);
        List<String> problems = new ArrayList<>();
        if (!target.name.equals(original.name) || !target.superName.equals(original.superName)
                || target.access != original.access || !target.interfaces.equals(original.interfaces)
                || !fields(target).equals(fields(original))) problems.add("Template class/field layout changed");
        Set<String> handlers = new HashSet<>();
        for (MethodNode method : target.methods) {
            String owner = mergedOwner(method);
            if (owner != null) {
                if (mixin == null || !owner.equals(mixin)) problems.add("foreign Template mixin method: " + method.name);
                else handlers.add(method.name + method.desc);
            }
        }
        for (MethodNode expected : original.methods) {
            MethodNode actual = target.methods.stream().filter(method -> method.name.equals(expected.name)
                    && method.desc.equals(expected.desc)).findFirst().orElse(null);
            if (actual == null) { problems.add("Template method missing: " + expected.name + expected.desc); continue; }
            Set<AbstractInsnNode> omitted = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            Map<AbstractInsnNode, String> restored = new IdentityHashMap<>();
            if (mixin != null) {
                removeCallbacks(actual, expected, target.name, handlers, omitted, problems);
                restoreMapReads(actual, expected, target.name, handlers, omitted, restored, problems);
            }
            if (actual.access != expected.access || !body(actual, omitted, restored).equals(body(expected, Set.of(), Map.of()))) {
                problems.add("Template method body changed: " + expected.name + expected.desc);
            }
        }
        for (MethodNode actual : target.methods) {
            if (!handlers.contains(actual.name + actual.desc) && original.methods.stream().noneMatch(method ->
                    method.name.equals(actual.name) && method.desc.equals(actual.desc))) {
                problems.add("unknown Template method: " + actual.name + actual.desc);
            }
        }
        if (mixin != null && handlers.size() != 4) problems.add("Template return handlers incomplete");
        return problems;
    }

    private static List<String> fields(ClassNode node) {
        return node.fields.stream().map(field -> field.name + ":" + field.desc + ":" + field.access)
                .sorted().toList();
    }

    private static String mergedOwner(MethodNode method) {
        for (AnnotationNode annotation : annotations(method)) {
            if (!MERGED.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("mixin")) return String.valueOf(annotation.values.get(i + 1));
            }
        }
        return null;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) result.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) result.addAll(method.invisibleAnnotations);
        return result;
    }

    /** Only noncancellable RETURN scaffolding is removed; original instructions stay bytecode-identical. */
    private static void removeCallbacks(MethodNode actual, MethodNode original, String owner, Set<String> handlers,
                                         Set<AbstractInsnNode> omitted, List<String> problems) {
        List<AbstractInsnNode> instructions = real(actual, Set.of());
        int callbacks = 0;
        for (int i = 0; i < instructions.size(); i++) {
            if (!(instructions.get(i) instanceof MethodInsnNode call) || !call.owner.equals(owner)
                    || !handlers.contains(call.name + call.desc) || call.desc.equals(ITERATOR_HANDLER_DESC) || call.desc.equals(VIEW_HANDLER_DESC)) continue;
            callbacks++;
            if (i + 1 >= instructions.size() || instructions.get(i + 1).getOpcode() != Opcodes.ARETURN) {
                problems.add("Template callback is not immediately before original return"); continue;
            }
            int start = i - 1;
            while (start >= 0 && i - start <= 12 && !(instructions.get(start) instanceof TypeInsnNode type
                    && type.getOpcode() == Opcodes.NEW && type.desc.equals(CALLBACK))) start--;
            if (start < 0 || i - start > 12) { problems.add("unknown Template callback scaffold"); continue; }
            if (start > 0 && instructions.get(start - 1) instanceof VarInsnNode load
                    && load.getOpcode() == Opcodes.ALOAD && load.var == 0) start--;
            if (start > 1 && instructions.get(start - 1) instanceof VarInsnNode store
                    && store.getOpcode() == Opcodes.ASTORE && store.var >= original.maxLocals
                    && instructions.get(start - 2).getOpcode() == Opcodes.DUP) start -= 2;
            boolean valid = true;
            for (int j = start; j <= i; j++) {
                AbstractInsnNode instruction = instructions.get(j);
                int opcode = instruction.getOpcode();
                if (instruction instanceof MethodInsnNode invocation) {
                    valid &= invocation == call || (invocation.owner.equals(CALLBACK)
                            && invocation.name.equals("<init>")
                            && invocation.desc.equals("(Ljava/lang/String;ZLjava/lang/Object;)V"));
                } else if (instruction instanceof TypeInsnNode type) {
                    valid &= opcode == Opcodes.NEW && type.desc.equals(CALLBACK);
                } else if (instruction instanceof VarInsnNode variable) {
                    valid &= (opcode == Opcodes.ALOAD && (variable.var == 0 || variable.var >= original.maxLocals))
                            || (opcode == Opcodes.ASTORE && variable.var >= original.maxLocals);
                } else if (instruction instanceof LdcInsnNode constant) valid &= constant.cst instanceof String;
                else valid &= opcode == Opcodes.DUP || opcode == Opcodes.ICONST_0;
            }
            if (!valid) problems.add("unexpected Template callback instructions");
            else for (int j = start; j <= i; j++) omitted.add(instructions.get(j));
        }
        boolean fresh = original.name.equals("deserialize") || original.name.equals("normalize")
                || original.name.equals("mirror") || (original.name.equals("rotate")
                && original.desc.startsWith("(Lnet/minecraft/core/Direction$Axis;"));
        long required = fresh ? real(original, Set.of()).stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.ARETURN).count() : 0;
        if (callbacks != required) problems.add("Template fresh-method return coverage changed: " + original.name);
    }

    /**
     * The iterating methods call {@code entrySet().iterator()} once; Mixin swaps that call for a static handler
     * (and {@code createViewInContext}'s {@code createUnsafe} call for another, plus a cast of its result). The
     * handler calls are recorded as the original calls, so the body comparison still proves nothing else changed,
     * and the number of redirected calls in each method must equal the number of original ones.
     */
    private static void restoreMapReads(MethodNode actual, MethodNode original, String owner, Set<String> handlers,
                                        Set<AbstractInsnNode> omitted, Map<AbstractInsnNode, String> restored,
                                        List<String> problems) {
        List<MethodInsnNode> iterators = new ArrayList<>();
        List<MethodInsnNode> views = new ArrayList<>();
        for (AbstractInsnNode instruction : real(original, Set.of())) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && call.owner.equals(ITERATING_OWNER) && call.name.equals("iterator")
                    && call.desc.equals(ITERATOR_DESC)) iterators.add(call);
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(VIEW_OWNER) && call.name.equals("createUnsafe")) views.add(call);
        }
        List<AbstractInsnNode> instructions = real(actual, Set.of());
        int redirectedIterators = 0;
        int redirectedViews = 0;
        for (int i = 0; i < instructions.size(); i++) {
            if (!(instructions.get(i) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                    || !call.owner.equals(owner) || !handlers.contains(call.name + call.desc)) continue;
            boolean iterator = call.desc.equals(ITERATOR_HANDLER_DESC);
            if (!iterator && !call.desc.equals(VIEW_HANDLER_DESC)) continue;
            List<MethodInsnNode> originals = iterator ? iterators : views;
            int index = iterator ? redirectedIterators++ : redirectedViews++;
            if (index >= originals.size()) continue;
            MethodInsnNode target = originals.get(index);
            // Mixin parks the original call's arguments in fresh locals, pushes this, and reloads them:
            // ASTORE x (per argument), ALOAD 0, ALOAD x (per argument), INVOKESPECIAL handler.
            int arguments = iterator ? 1 : 3;
            if (i < 2 * arguments + 1 || !isLoad(instructions.get(i - arguments - 1), 0)) {
                problems.add("unknown Template map-read scaffold: " + original.name);
                continue;
            }
            boolean scaffold = true;
            for (int k = 0; k < arguments; k++) {
                AbstractInsnNode stored = instructions.get(i - arguments - 1 - arguments + k);
                AbstractInsnNode loaded = instructions.get(i - arguments + k);
                int slot = stored instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE
                        && store.var >= original.maxLocals ? store.var : -1;
                AbstractInsnNode reload = instructions.get(i - 1 - k);
                scaffold &= slot >= 0 && loaded instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD
                        && load.var >= original.maxLocals && reload instanceof VarInsnNode;
            }
            // The stores run last argument first, the loads first argument first: the sets of slots must match.
            Set<Integer> storedSlots = new HashSet<>();
            Set<Integer> loadedSlots = new HashSet<>();
            for (int k = 0; scaffold && k < arguments; k++) {
                storedSlots.add(((VarInsnNode) instructions.get(i - 2 * arguments - 1 + k)).var);
                loadedSlots.add(((VarInsnNode) instructions.get(i - arguments + k)).var);
            }
            if (!scaffold || storedSlots.size() != arguments || !storedSlots.equals(loadedSlots)) {
                problems.add("unknown Template map-read scaffold: " + original.name);
                continue;
            }
            for (int k = i - 2 * arguments - 1; k < i; k++) omitted.add(instructions.get(k));
            restored.put(call, target.getOpcode() + ":" + target.owner + "." + target.name + target.desc + target.itf + ";");
            if (!iterator) {
                if (i + 1 < instructions.size() && instructions.get(i + 1) instanceof TypeInsnNode cast
                        && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(VIEW_OWNER)) omitted.add(cast);
                else problems.add("unknown Template build-view scaffold: " + original.name);
            }
        }
        int expectedIterators = ITERATING.contains(original.name) ? iterators.size() : 0;
        int expectedViews = original.name.equals("createViewInContext") ? views.size() : 0;
        if (redirectedIterators != expectedIterators || redirectedViews != expectedViews) {
            problems.add("Template map-read coverage changed: " + original.name + original.desc);
        }
    }

    private static boolean isLoad(AbstractInsnNode instruction, int slot) {
        return instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == slot;
    }

    private static List<AbstractInsnNode> real(MethodNode method, Set<AbstractInsnNode> omitted) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0 && !omitted.contains(instruction)) result.add(instruction);
        }
        return result;
    }

    private static String body(MethodNode method, Set<AbstractInsnNode> omitted, Map<AbstractInsnNode, String> restored) {
        List<AbstractInsnNode> instructions = real(method, omitted);
        Map<AbstractInsnNode, Integer> indexes = new IdentityHashMap<>();
        for (int i = 0; i < instructions.size(); i++) indexes.put(instructions.get(i), i);
        Function<LabelNode, Integer> label = node -> {
            AbstractInsnNode cursor = node;
            while (cursor != null && !indexes.containsKey(cursor)) cursor = cursor.getNext();
            return cursor == null ? instructions.size() : indexes.get(cursor);
        };
        StringBuilder text = new StringBuilder();
        for (AbstractInsnNode instruction : instructions) {
            String replacement = restored.get(instruction);
            if (replacement != null) { text.append(replacement); continue; }
            text.append(instruction.getOpcode()).append(':');
            if (instruction instanceof VarInsnNode v) text.append(v.var);
            else if (instruction instanceof IntInsnNode v) text.append(v.operand);
            else if (instruction instanceof FieldInsnNode v) text.append(v.owner).append('.').append(memberName(v.owner, v.name, false)).append(v.desc);
            else if (instruction instanceof MethodInsnNode v) text.append(v.owner).append('.').append(memberName(v.owner, v.name, true)).append(v.desc).append(v.itf);
            else if (instruction instanceof TypeInsnNode v) text.append(v.desc);
            else if (instruction instanceof LdcInsnNode v) text.append(v.cst);
            else if (instruction instanceof JumpInsnNode v) text.append(label.apply(v.label));
            else if (instruction instanceof IincInsnNode v) text.append(v.var).append(',').append(v.incr);
            else if (instruction instanceof InvokeDynamicInsnNode v) {
                text.append(v.name).append(v.desc).append(v.bsm).append(java.util.Arrays.toString(v.bsmArgs));
            } else if (instruction instanceof TableSwitchInsnNode v) {
                text.append(v.min).append(',').append(v.max).append(',').append(label.apply(v.dflt));
                v.labels.forEach(target -> text.append(',').append(label.apply(target)));
            } else if (instruction instanceof LookupSwitchInsnNode v) {
                text.append(v.keys).append(',').append(label.apply(v.dflt));
                v.labels.forEach(target -> text.append(',').append(label.apply(target)));
            } else if (instruction instanceof MultiANewArrayInsnNode v) text.append(v.desc).append(',').append(v.dims);
            text.append(';');
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            text.append("catch:").append(label.apply(block.start)).append(',').append(label.apply(block.end))
                    .append(',').append(label.apply(block.handler)).append(',').append(block.type);
        }
        return text.toString();
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Normalize the reviewed SRG references through Forge's installed naming service. */
    private static String memberName(String owner, String name, boolean method) {
        if (!owner.startsWith("net/minecraft/")) return name;
        try {
            return ObfuscationReflectionHelper.remapName(method ? INameMappingService.Domain.METHOD
                    : INameMappingService.Domain.FIELD, name);
        } catch (RuntimeException | LinkageError unavailable) { return name; }
    }

    public static boolean runtimeDependenciesSafe() { return RuntimeDependencies.SAFE; }

    private static final class RuntimeDependencies {
        private static final boolean SAFE = runtimeDependenciesSafe(BlockPos.class, Vec3i.class);
    }

    /** Checks loaded members too: resources alone do not reveal methods added by a foreign mixin. */
    static boolean runtimeDependenciesSafe(Class<?> blockPos, Class<?> vector) {
        try {
            if (blockPos != BlockPos.class || vector != Vec3i.class || blockPos.getSuperclass() != vector) return false;
            if (BlockPos.MutableBlockPos.class.getSuperclass() != blockPos) return false;
            for (Class<?> type : List.of(blockPos, vector, BlockPos.MutableBlockPos.class)) {
                byte[] bytes;
                try (InputStream input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    if (input == null) return false;
                    bytes = input.readAllBytes();
                }
                ClassNode source = node(bytes);
                Set<String> sourceMethods = new HashSet<>();
                for (MethodNode method : source.methods) {
                    if (mergedOwner(method) != null) return false;
                    if (!method.name.startsWith("<")) sourceMethods.add(memberName(source.name, method.name, true) + method.desc);
                }
                Set<String> actualMethods = new HashSet<>();
                for (var method : type.getDeclaredMethods()) {
                    for (var annotation : method.getDeclaredAnnotations()) {
                        if (annotation.annotationType().getName().endsWith(".MixinMerged")) return false;
                    }
                    actualMethods.add(method.getName() + Type.getMethodDescriptor(method));
                }
                if (!actualMethods.equals(sourceMethods)) return false;
                Set<String> actualFields = new HashSet<>();
                for (var field : type.getDeclaredFields()) actualFields.add(field.getName() + Type.getDescriptor(field.getType()));
                Set<String> sourceFields = new HashSet<>();
                source.fields.forEach(field -> sourceFields.add(memberName(source.name, field.name, false) + field.desc));
                if (!actualFields.equals(sourceFields)) return false;
                if (type != vector && source.methods.stream().anyMatch(method -> !Modifier.isStatic(method.access)
                        && (method.name.equals("equals") || method.name.equals("hashCode")
                        || List.of("getX", "getY", "getZ", "m_123341_", "m_123342_", "m_123343_").contains(method.name)))) return false;
                if (type == vector && !coordinateMethods(source)) return false;
            }
            return true;
        } catch (Exception | LinkageError unavailable) { return false; }
    }

    private static boolean coordinateMethods(ClassNode vector) {
        List<List<String>> names = List.of(List.of("getX", "m_123341_", "x", "f_123285_"),
                List.of("getY", "m_123342_", "y", "f_123286_"), List.of("getZ", "m_123343_", "z", "f_123289_"));
        for (List<String> axis : names) {
            MethodNode getter = vector.methods.stream().filter(method -> (method.name.equals(axis.get(0))
                    || method.name.equals(axis.get(1))) && method.desc.equals("()I")).findFirst().orElse(null);
            if (getter == null) return false;
            List<AbstractInsnNode> instructions = real(getter, Set.of());
            if (instructions.size() != 3 || !(instructions.get(0) instanceof VarInsnNode load)
                    || load.getOpcode() != Opcodes.ALOAD || load.var != 0
                    || !(instructions.get(1) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(vector.name) || !field.desc.equals("I")
                    || !(field.name.equals(axis.get(2)) || field.name.equals(axis.get(3)))
                    || instructions.get(2).getOpcode() != Opcodes.IRETURN) return false;
        }
        MethodNode hash = vector.methods.stream().filter(method -> method.name.equals("hashCode")
                && method.desc.equals("()I")).findFirst().orElse(null);
        MethodNode equals = vector.methods.stream().filter(method -> method.name.equals("equals")
                && method.desc.equals("(Ljava/lang/Object;)Z")).findFirst().orElse(null);
        if (hash == null || equals == null) return false;
        if (!(opcodes(hash).equals(List.of(25,182,25,182,16,104,96,16,104,25,182,96,172))
                && opcodes(equals).equals(List.of(25,25,166,4,172,25,193,154,3,172,25,192,58,
                        25,182,25,182,159,3,172,25,182,25,182,159,3,172,25,182,25,182,159,3,172,4,172))
                && real(hash, Set.of()).stream().filter(instruction -> instruction instanceof IntInsnNode)
                        .allMatch(instruction -> ((IntInsnNode) instruction).operand == 31))) return false;
        if (!coordinateCalls(hash, vector.name, List.of(1, 2, 0), names)
                || !coordinateCalls(equals, vector.name, List.of(0, 0, 1, 1, 2, 2), names)) return false;
        List<AbstractInsnNode> equality = real(equals, Set.of());
        int[] branchSites = {2, 7, 17, 24, 31};
        int[] branchTargets = {5, 10, 20, 27, 34};
        for (int i = 0; i < branchSites.length; i++) {
            JumpInsnNode branch = (JumpInsnNode) equality.get(branchSites[i]);
            AbstractInsnNode target = branch.label;
            while (target != null && target.getOpcode() < 0) target = target.getNext();
            if (target != equality.get(branchTargets[i])) return false;
        }
        return equality.stream().filter(instruction -> instruction instanceof TypeInsnNode)
                .allMatch(instruction -> ((TypeInsnNode) instruction).desc.equals(vector.name))
                && equality.stream().filter(instruction -> instruction instanceof VarInsnNode)
                .map(instruction -> ((VarInsnNode) instruction).var)
                .toList().equals(List.of(0, 1, 1, 1, 2, 0, 2, 0, 2, 0, 2))
                && real(hash, Set.of()).stream().filter(instruction -> instruction instanceof VarInsnNode)
                .allMatch(instruction -> ((VarInsnNode) instruction).var == 0);
    }

    private static boolean coordinateCalls(MethodNode method, String owner, List<Integer> axes,
                                            List<List<String>> names) {
        List<MethodInsnNode> calls = real(method, Set.of()).stream()
                .filter(instruction -> instruction instanceof MethodInsnNode)
                .map(instruction -> (MethodInsnNode) instruction).toList();
        if (calls.size() != axes.size()) return false;
        for (int i = 0; i < calls.size(); i++) {
            MethodInsnNode call = calls.get(i);
            List<String> axis = names.get(axes.get(i));
            if (!call.owner.equals(owner) || !call.desc.equals("()I")
                    || !(call.name.equals(axis.get(0)) || call.name.equals(axis.get(1)))) return false;
        }
        return true;
    }

    private static List<Integer> opcodes(MethodNode method) {
        return real(method, Set.of()).stream().map(AbstractInsnNode::getOpcode).toList();
    }
}
