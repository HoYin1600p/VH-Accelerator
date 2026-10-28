package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class ForgeIntrusiveHolderLayoutTest {
    private static ClassNode read(String name) throws Exception {
        try (var input = ForgeIntrusiveHolderLayoutTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode create(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals("createIntrusiveHolder")
                && m.desc.equals(ForgeIntrusiveHolderLayout.CREATE)).findFirst().orElseThrow();
    }

    @Test void actualForgeClassMatchesTheRealServerExportPin() throws Exception {
        assertEquals(List.of(), ForgeIntrusiveHolderLayout.problems(read(ForgeIntrusiveHolderLayout.TARGET)));
    }

    @Test void unavailableTargetAndChangedFieldsMethodsOwnersAndBodiesFailClosed() throws Exception {
        assertFalse(ForgeIntrusiveHolderLayout.problems((ClassNode) null).isEmpty());
        assertFalse(ForgeIntrusiveHolderLayout.problems((dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight.ClassLookup)
                name -> { throw new IllegalStateException("unavailable"); }).isEmpty());
        List<Consumer<ClassNode>> mutations = List.of(
                n -> n.fields.removeIf(f -> f.name.equals("self")),
                n -> n.fields.stream().filter(f -> f.name.equals("self")).findFirst().orElseThrow().access &= ~Opcodes.ACC_FINAL,
                n -> n.fields.stream().filter(f -> f.name.equals("holders")).findFirst().orElseThrow().desc = "Ljava/util/Set;",
                n -> n.methods.remove(create(n)),
                n -> {
                    MethodNode source = create(n);
                    MethodNode twin = new MethodNode(source.access, source.name, source.desc, null, null);
                    source.accept(twin); n.methods.add(twin);
                },
                n -> create(n).access |= Opcodes.ACC_STATIC,
                n -> create(n).access |= Opcodes.ACC_SYNCHRONIZED,
                n -> {
                    var owner = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
                    owner.values = new ArrayList<>(List.of("mixin", "other.mod.RegistryMixin"));
                    create(n).visibleAnnotations = new ArrayList<>(List.of(owner));
                },
                n -> {
                    for (var i : create(n).instructions) if (i instanceof MethodInsnNode call
                            && call.name.equals("computeIfAbsent")) { call.name = "get"; break; }
                },
                n -> create(n).instructions.insert(new InsnNode(Opcodes.NOP)));
        for (Consumer<ClassNode> mutation : mutations) {
            ClassNode node = read(ForgeIntrusiveHolderLayout.TARGET);
            mutation.accept(node);
            assertFalse(ForgeIntrusiveHolderLayout.problems(node).isEmpty());
        }
    }

    @Test void actualRedirectDelegatesToTheProductionMonitorAndIsOptional() throws Exception {
        ClassNode mixin = read("dev/hoyin1600p/vhaccelerator/mixin/fix/registry/NamespacedHolderHelperWriteLockMixin");
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("vha$createIntrusiveHolderUnderWriteLock"))
                .findFirst().orElseThrow();
        assertEquals(0, handler.access & Opcodes.ACC_STATIC);
        assertEquals("(Ljava/util/Map;Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", handler.desc);
        assertTrue(java.util.Arrays.stream(handler.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("dev/hoyin1600p/vhaccelerator/backport/fix/RegistryWriteLock")
                && call.name.equals("computeHolder")));
        AnnotationNode redirect = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")).findFirst().orElseThrow();
        assertEquals(0, redirect.values.get(redirect.values.indexOf("require") + 1));
    }
}
