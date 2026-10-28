package dev.hoyin1600p.vhaccelerator.compat.lootjs;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The exact LootJS and KubeJS bytecode the regex short-circuit relies on. The reviewed bodies are pinned by
 * their ordered calls, so a changed {@code isEmpty}, {@code getFirst} or {@code getStacks} (or a LootJS
 * that no longer asks {@code isEmpty} exactly once) keeps the original behavior.
 */
public final class LootJsRegexFilterLayouts {
    private static final String PLUGIN = "com/almostreliable/lootjs/kube/LootJSPlugin";
    private static final String INGREDIENT = "dev/latvian/mods/kubejs/item/ingredient/IngredientJS";
    private static final String REGEX = "dev/latvian/mods/kubejs/item/ingredient/RegexIngredientJS";
    private static final String STACK = "dev/latvian/mods/kubejs/item/ItemStackJS";
    private static final String STACK_DESC = "Ldev/latvian/mods/kubejs/item/ItemStackJS;";
    public static final String OF_ITEM_FILTER_DESC = "(Ljava/lang/Object;)Lcom/almostreliable/lootjs/filters/ItemFilter;";

    private LootJsRegexFilterLayouts() { }

    public static List<String> problems(MixinTargetPreflight.ClassLookup lookup) {
        List<String> problems = new ArrayList<>();
        ClassNode plugin = find(lookup, PLUGIN);
        ClassNode ingredient = find(lookup, INGREDIENT);
        ClassNode regex = find(lookup, REGEX);
        ClassNode stack = find(lookup, STACK);
        if (plugin == null) problems.add("LootJS plugin bytecode unavailable");
        if (ingredient == null) problems.add("KubeJS IngredientJS bytecode unavailable");
        if (regex == null) problems.add("KubeJS RegexIngredientJS bytecode unavailable");
        if (stack == null) problems.add("KubeJS ItemStackJS bytecode unavailable");
        if (!problems.isEmpty()) return problems;

        MethodNode ofItemFilter = method(plugin, "ofItemFilter", OF_ITEM_FILTER_DESC);
        if (ofItemFilter == null || (ofItemFilter.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE))
                != (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) {
            problems.add("required private static method missing: " + PLUGIN + ".ofItemFilter" + OF_ITEM_FILTER_DESC);
        } else if (calls(ofItemFilter).stream().filter(call -> call.equals(
                Opcodes.INVOKEINTERFACE + " " + INGREDIENT + ".isEmpty()Z")).count() != 1) {
            problems.add("ofItemFilter must call IngredientJS.isEmpty() exactly once");
        }

        // isEmpty = getFirst().isEmpty()
        expectCalls(ingredient, "isEmpty", "()Z", problems, List.of(
                Opcodes.INVOKEINTERFACE + " " + INGREDIENT + ".getFirst()" + STACK_DESC,
                Opcodes.INVOKEVIRTUAL + " " + STACK + ".isEmpty()Z"));
        // getFirst: first non-empty of getStacks(), as withCount(getCount()), else EMPTY.
        expectCalls(ingredient, "getFirst", "()" + STACK_DESC, problems, List.of(
                Opcodes.INVOKEINTERFACE + " " + INGREDIENT + ".getStacks()Ljava/util/Set;",
                Opcodes.INVOKEINTERFACE + " java/util/Set.iterator()Ljava/util/Iterator;",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.hasNext()Z",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.next()Ljava/lang/Object;",
                Opcodes.INVOKEVIRTUAL + " " + STACK + ".isEmpty()Z",
                Opcodes.INVOKEINTERFACE + " " + INGREDIENT + ".getCount()I",
                Opcodes.INVOKEVIRTUAL + " " + STACK + ".withCount(I)" + STACK_DESC));
        // getStacks: every item of ItemStackJS.getList() that this.test accepts, copied, in list order.
        expectCalls(ingredient, "getStacks", "()Ljava/util/Set;", problems, List.of(
                Opcodes.INVOKESPECIAL + " java/util/LinkedHashSet.<init>()V",
                Opcodes.INVOKESTATIC + " " + STACK + ".getList()Ljava/util/List;",
                Opcodes.INVOKEINTERFACE + " java/util/List.iterator()Ljava/util/Iterator;",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.hasNext()Z",
                Opcodes.INVOKEINTERFACE + " java/util/Iterator.next()Ljava/lang/Object;",
                Opcodes.INVOKEINTERFACE + " " + INGREDIENT + ".test(" + STACK_DESC + ")Z",
                Opcodes.INVOKEVIRTUAL + " " + STACK + ".copy()" + STACK_DESC,
                Opcodes.INVOKEINTERFACE + " java/util/Set.add(Ljava/lang/Object;)Z"));
        MethodNode count = method(ingredient, "getCount", "()I");
        if (count == null || (count.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) != 0
                || !calls(count).isEmpty()) {
            problems.add("IngredientJS.getCount() must be a plain default method");
        } else if (!returnsOne(count)) {
            problems.add("IngredientJS.getCount() must return 1");
        }

        if (!regex.interfaces.contains(INGREDIENT) || !"java/lang/Object".equals(regex.superName)) {
            problems.add("RegexIngredientJS must directly implement IngredientJS");
        }
        for (String name : List.of("isEmpty", "getFirst", "getStacks", "getCount")) {
            if (regex.methods.stream().anyMatch(method -> method.name.equals(name))) {
                problems.add("RegexIngredientJS must not override " + name);
            }
        }
        if (method(regex, "test", "(" + STACK_DESC + ")Z") == null) {
            problems.add("required method missing: " + REGEX + ".test(" + STACK_DESC + ")Z");
        }
        MethodNode list = method(stack, "getList", "()Ljava/util/List;");
        if (list == null || (list.access & Opcodes.ACC_STATIC) == 0 || (list.access & Opcodes.ACC_PUBLIC) == 0) {
            problems.add("required public static method missing: " + STACK + ".getList()Ljava/util/List;");
        }
        return problems;
    }

    private static boolean returnsOne(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.ICONST_1) return true;
        }
        return false;
    }

    private static void expectCalls(ClassNode owner, String name, String descriptor, List<String> problems,
            List<String> expected) {
        MethodNode method = method(owner, name, descriptor);
        if (method == null || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) != 0) {
            problems.add("required default method missing: " + owner.name + "." + name + descriptor);
            return;
        }
        List<String> actual = calls(method);
        if (!actual.equals(expected)) {
            problems.add("reviewed body changed: " + owner.name + "." + name + descriptor + " calls " + actual);
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.PUTSTATIC) {
                problems.add("reviewed body writes a static field: " + owner.name + "." + name);
            }
        }
    }

    private static List<String> calls(MethodNode method) {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call.getOpcode() + " " + call.owner + "." + call.name + call.desc);
            }
        }
        return calls;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElse(null);
    }

    private static ClassNode find(MixinTargetPreflight.ClassLookup lookup, String owner) {
        try {
            return lookup.find(owner);
        } catch (Exception | LinkageError unavailable) {
            return null;
        }
    }
}
