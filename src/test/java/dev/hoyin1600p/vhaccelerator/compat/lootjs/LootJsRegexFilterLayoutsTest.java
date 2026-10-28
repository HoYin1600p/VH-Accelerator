package dev.hoyin1600p.vhaccelerator.compat.lootjs;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class LootJsRegexFilterLayoutsTest {
    private static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs";
    private static final String PLUGIN = "com/almostreliable/lootjs/kube/LootJSPlugin";
    private static final String INGREDIENT = "dev/latvian/mods/kubejs/item/ingredient/IngredientJS";

    private static ClassNode kubeJs(String internalName) throws IOException {
        try (InputStream stream = LootJsRegexFilterLayoutsTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(stream.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static ClassNode plugin(int isEmptyCalls, int access) {
        ClassNode node = new ClassNode();
        node.name = PLUGIN;
        MethodNode method = new MethodNode(access, "ofItemFilter", LootJsRegexFilterLayouts.OF_ITEM_FILTER_DESC, null, null);
        method.instructions = new InsnList();
        for (int i = 0; i < isEmptyCalls; i++) {
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, INGREDIENT, "isEmpty", "()Z", true));
        }
        node.methods.add(method);
        return node;
    }

    private static MixinTargetPreflight.ClassLookup lookup(ClassNode plugin, Map<String, ClassNode> overrides) throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();
        for (String name : List.of(INGREDIENT, "dev/latvian/mods/kubejs/item/ingredient/RegexIngredientJS",
                "dev/latvian/mods/kubejs/item/ItemStackJS")) {
            classes.put(name, kubeJs(name));
        }
        if (plugin != null) classes.put(PLUGIN, plugin);
        classes.putAll(overrides);
        return classes::get;
    }

    private static final int PRIVATE_STATIC = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;

    @Test
    void reviewedKubeJsLayoutWithAnOneCallLootJsHasNoProblems() throws IOException {
        assertEquals(List.of(), LootJsRegexFilterLayouts.problems(lookup(plugin(1, PRIVATE_STATIC), Map.of())));
        assertEquals(List.of(), CommonCompatibilityLayouts.problems(GROUP, lookup(plugin(1, PRIVATE_STATIC), Map.of())));
    }

    @Test
    void aLootJsThatDoesNotAskIsEmptyExactlyOnceOrChangedItsMethodIsRejected() throws IOException {
        assertFalse(LootJsRegexFilterLayouts.problems(lookup(plugin(0, PRIVATE_STATIC), Map.of())).isEmpty());
        assertFalse(LootJsRegexFilterLayouts.problems(lookup(plugin(2, PRIVATE_STATIC), Map.of())).isEmpty());
        assertFalse(LootJsRegexFilterLayouts.problems(lookup(plugin(1, Opcodes.ACC_PRIVATE), Map.of())).isEmpty());
        assertFalse(LootJsRegexFilterLayouts.problems(lookup(null, Map.of())).isEmpty());
    }

    @Test
    void aChangedKubeJsBodyOrAnOverrideIsRejected() throws IOException {
        ClassNode regex = kubeJs("dev/latvian/mods/kubejs/item/ingredient/RegexIngredientJS");
        regex.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "isEmpty", "()Z", null, null));
        assertTrue(LootJsRegexFilterLayouts.problems(lookup(plugin(1, PRIVATE_STATIC),
                Map.of(regex.name, regex))).stream().anyMatch(problem -> problem.contains("must not override isEmpty")));

        ClassNode ingredient = kubeJs(INGREDIENT);
        MethodNode firstBody = ingredient.methods.stream()
                .filter(method -> method.name.equals("getFirst") && method.desc.startsWith("()Ldev/latvian/mods/kubejs/item/ItemStackJS;"))
                .findFirst().orElseThrow();
        firstBody.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
        assertTrue(LootJsRegexFilterLayouts.problems(lookup(plugin(1, PRIVATE_STATIC),
                Map.of(ingredient.name, ingredient))).stream().anyMatch(problem -> problem.contains("reviewed body changed")));
    }

    @Test
    void theRealLootJs277PluginMatchesWhenItsJarIsProvided() throws Exception {
        String jar = System.getProperty("lootjs.jar");
        assumeTrue(jar != null, "pass -Plootjs_jar=<lootjs-forge-1.18.2-2.7.7.jar> to check the shipped bytecode");
        try (ZipFile zip = new ZipFile(Path.of(jar).toFile())) {
            ClassNode node = new ClassNode();
            try (InputStream stream = zip.getInputStream(zip.getEntry(PLUGIN + ".class"))) {
                new ClassReader(stream.readAllBytes()).accept(node, 0);
            }
            assertEquals(List.of(), LootJsRegexFilterLayouts.problems(lookup(node, Map.of())));
        }
    }
}
