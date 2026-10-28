package dev.hoyin1600p.vhaccelerator.compat.statecodec;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** The pre-weave shape check and the post-weave ownership check, on the real 1.18.2 class and edited copies. */
class StateCodecLayoutsTest {
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    private static ClassNode original() throws Exception {
        try (InputStream stream = StateCodecLayoutsTest.class.getClassLoader()
                .getResourceAsStream(StateCodecLayouts.STATE_DEFINITION + ".class")) {
            assertNotNull(stream, "StateDefinition on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(stream.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode append(ClassNode node) {
        return node.methods.stream().filter(method -> method.desc.equals(StateCodecLayouts.APPEND_DESC)).findFirst().orElseThrow();
    }

    private static MethodNode constructor(ClassNode node) {
        return node.methods.stream().filter(method -> method.name.equals("<init>")
                && method.desc.equals(StateCodecLayouts.CONSTRUCTOR_DESC)).findFirst().orElseThrow();
    }

    private static MethodNode handler(String owner, String name) {
        MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, name,
                StateCodecLayouts.APPEND_DESC, null, null);
        handler.instructions = new InsnList();
        handler.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        handler.instructions.add(new InsnNode(Opcodes.ARETURN));
        AnnotationNode merged = new AnnotationNode(MERGED);
        merged.values = new ArrayList<>(List.of("mixin", owner));
        handler.visibleAnnotations = new ArrayList<>(List.of(merged));
        return handler;
    }

    /**
     * What Sponge Mixin leaves behind for the redirect: a merged private instance handler and, in place of the
     * static call, the four arguments saved to new locals, {@code this}, the arguments reloaded and an invokespecial.
     */
    private static ClassNode woven(String handlerName) throws Exception {
        ClassNode node = original();
        MethodNode original = append(node);
        node.methods.add(handler(StateCodecLayouts.MIXIN, handlerName));
        MethodNode constructor = constructor(node);
        MethodInsnNode target = null;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(original.name) && call.desc.equals(original.desc)) {
                target = call;
            }
        }
        assertNotNull(target);
        int base = Math.max(constructor.maxLocals, 10);
        InsnList glue = new InsnList();
        for (int index = 3; index >= 0; index--) glue.add(new VarInsnNode(Opcodes.ASTORE, base + index));
        glue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        for (int index = 0; index < 4; index++) glue.add(new VarInsnNode(Opcodes.ALOAD, base + index));
        glue.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, handlerName, StateCodecLayouts.APPEND_DESC, false));
        constructor.instructions.insertBefore(target, glue);
        constructor.instructions.remove(target);
        return node;
    }

    /** The static-handler shape that crashed build-015 (a static handler on the constructor) is not accepted. */
    @Test
    void a_static_handler_or_a_bare_call_is_not_confirmed() throws Exception {
        ClassNode staticHandler = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        staticHandler.methods.stream().filter(method -> method.name.contains("lazyAppend"))
                .forEach(method -> method.access |= Opcodes.ACC_STATIC);
        assertTrue(StateCodecLayouts.applicationProblems(staticHandler).stream()
                .anyMatch(problem -> problem.contains("exactly once")));

        ClassNode bare = original();
        bare.methods.add(handler(StateCodecLayouts.MIXIN, "redirect$zzz000$vhaccelerator$lazyAppend"));
        for (var instruction : constructor(bare).instructions) {
            if (instruction instanceof MethodInsnNode call && call.desc.equals(StateCodecLayouts.APPEND_DESC)) {
                call.name = "redirect$zzz000$vhaccelerator$lazyAppend";
                call.setOpcode(Opcodes.INVOKESPECIAL);
            }
        }
        assertTrue(StateCodecLayouts.applicationProblems(bare).stream()
                .anyMatch(problem -> problem.contains("exactly once")));
    }

    @Test
    void the_real_class_has_the_reviewed_shape() throws Exception {
        assertEquals(List.of(), StateCodecLayouts.problems(name -> {
            assertEquals(StateCodecLayouts.STATE_DEFINITION, name);
            return original();
        }));
        assertEquals(StateCodecLayouts.EXPECTED_APPEND, StateCodecLayouts.fingerprint(original(), append(original())));
    }

    @Test
    void an_access_transformer_widening_the_method_is_accepted() throws Exception {
        // ModernFix 5.18 ships an access transformer that makes appendPropertyCodec public.
        ClassNode node = original();
        MethodNode append = append(node);
        append.access = (append.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
        assertEquals(List.of(), StateCodecLayouts.problems(name -> node));
    }

    @Test
    void a_missing_class_is_a_problem() {
        assertFalse(StateCodecLayouts.problems(name -> null).isEmpty());
    }

    @Test
    void a_changed_append_body_is_rejected() throws Exception {
        ClassNode node = original();
        MethodNode append = append(node);
        for (var instruction : append.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("fieldOf")) {
                call.name = "optionalFieldOf";
            }
        }
        List<String> problems = StateCodecLayouts.problems(name -> node);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("differs from the reviewed body")), problems.toString());
    }

    @Test
    void a_second_append_call_or_a_changed_base_is_rejected() throws Exception {
        ClassNode twice = original();
        MethodNode constructor = constructor(twice);
        MethodInsnNode call = null;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode candidate && candidate.desc.equals(StateCodecLayouts.APPEND_DESC)) call = candidate;
        }
        assertNotNull(call);
        constructor.instructions.insertBefore(call, call.clone(java.util.Map.of()));
        assertFalse(StateCodecLayouts.problems(name -> twice).isEmpty());

        ClassNode base = original();
        for (var instruction : constructor(base).instructions) {
            if (instruction instanceof MethodInsnNode candidate && candidate.name.equals("unit")) candidate.name = "other";
        }
        assertTrue(StateCodecLayouts.problems(name -> base).stream().anyMatch(problem -> problem.contains("empty base")));
    }

    @Test
    void the_woven_class_with_only_this_mixin_passes() throws Exception {
        assertEquals(List.of(), StateCodecLayouts.applicationProblems(woven("redirect$zzz000$vhaccelerator$lazyAppend")));
    }

    @Test
    void the_reviewed_modernfix_hook_may_share_the_class() throws Exception {
        ClassNode node = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        MethodNode hook = handler(StateCodecLayouts.MODERNFIX_MIXIN, "modify$aaa000$modernfix$useArrayMap");
        hook.desc = "(Ljava/util/Map;)Ljava/util/Map;";
        node.methods.add(hook);
        assertEquals(List.of(), StateCodecLayouts.applicationProblems(node));
    }

    @Test
    void lightspeed_and_vhas_own_backport_hooks_may_share_the_class() throws Exception {
        ClassNode node = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        node.methods.add(handler(StateCodecLayouts.LIGHTSPEED_MIXIN, "inject$aaa000$lightspeed$initReturnInjected"));
        node.methods.add(handler(StateCodecLayouts.VHA_BACKPORT_MIXIN, "modify$bbb000$vhaccelerator$useArrayMap"));
        // Their handlers take other descriptors than the codec step.
        node.methods.stream().filter(method -> method.name.contains("$aaa000$") || method.name.contains("$bbb000$"))
                .forEach(method -> method.desc = "()V");
        assertEquals(List.of(), StateCodecLayouts.applicationProblems(node));
    }

    @Test
    void a_reviewed_owner_may_not_merge_a_codec_method() throws Exception {
        ClassNode node = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        node.methods.add(handler(StateCodecLayouts.MODERNFIX_MIXIN, "modify$aaa000$modernfix$codec"));
        List<String> problems = StateCodecLayouts.applicationProblems(node);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("merged codec method")), problems.toString());
    }

    @Test
    void any_other_mixin_owner_disables_the_optimization() throws Exception {
        ClassNode node = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        node.methods.add(handler("some.other.mod.mixin.StateDefinitionMixin", "inject$aaa000$other$tail"));
        List<String> problems = StateCodecLayouts.applicationProblems(node);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("unreviewed mixin")), problems.toString());
    }

    @Test
    void an_untouched_class_or_a_foreign_redirect_is_not_confirmed() throws Exception {
        List<String> untouched = StateCodecLayouts.applicationProblems(original());
        assertTrue(untouched.stream().anyMatch(problem -> problem.contains("exactly once")), untouched.toString());

        // Another mixin redirected the same call first; this mixin's handler never got installed.
        ClassNode foreign = original();
        foreign.methods.add(handler("some.other.mod.mixin.StateDefinitionMixin", "redirect$aaa000$other$append"));
        for (var instruction : constructor(foreign).instructions) {
            if (instruction instanceof MethodInsnNode call && call.desc.equals(StateCodecLayouts.APPEND_DESC)) {
                call.name = "redirect$aaa000$other$append";
            }
        }
        assertFalse(StateCodecLayouts.applicationProblems(foreign).isEmpty());
    }

    @Test
    void a_changed_original_after_weaving_is_rejected() throws Exception {
        ClassNode node = woven("redirect$zzz000$vhaccelerator$lazyAppend");
        for (var instruction : append(node).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("xmap")) call.name = "flatXmap";
        }
        assertTrue(StateCodecLayouts.applicationProblems(node).stream()
                .anyMatch(problem -> problem.contains("changed by another transformer")));
    }

    @Test
    void the_contract_names_the_setting_and_the_dedicated_side_only() {
        var contract = StateCodecLayouts.contract();
        assertEquals("optimizations.lazyBlockStatePropertyCodecs", contract.get("setting"));
        assertEquals(List.of("DEDICATED_SERVER"), contract.get("sides"));
    }
}
