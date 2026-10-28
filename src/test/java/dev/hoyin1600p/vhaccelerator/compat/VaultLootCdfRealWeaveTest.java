package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Final verification runs directly on two real exported classes, never a synthesized positive target. */
class VaultLootCdfRealWeaveTest {
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    private static void pinOriginal() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of("libs", "the_vault-1.18.2-3.21.62.jar").toFile())) {
            assertTrue(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION, name -> {
                try (var input = jar.getInputStream(jar.getEntry(name + ".class"))) {
                    return input.readAllBytes();
                } catch (Exception missing) { return null; }
            }));
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodInsnNode call(ClassNode node, String method, String name) {
        for (var instruction : method(node, method).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.contains(name)) return call;
        }
        throw new AssertionError("Missing call " + name);
    }

    private static void foreignOwner(ClassNode node, String name) {
        MethodNode method = method(node, name);
        var annotations = Stream.of(method.visibleAnnotations, method.invisibleAnnotations)
                .filter(java.util.Objects::nonNull).flatMap(List::stream);
        AnnotationNode merged = annotations.filter(a -> a.desc.equals(MERGED)).findFirst().orElseThrow();
        int owner = merged.values.indexOf("mixin") + 1;
        assertTrue(owner > 0);
        merged.values.set(owner, "other.mod.CdfMixin");
    }

    private record Mutation(String name, Consumer<ClassNode> edit) { }

    private static List<Mutation> mutations() {
        return List.of(
                new Mutation("primitive accessor name", n -> call(n, "get", "getReturnValueD").name = "getReturnValue"),
                new Mutation("primitive accessor descriptor", n -> call(n, "get", "getReturnValueD").desc = "()F"),
                new Mutation("primitive accessor owner", n -> call(n, "get", "getReturnValueD").owner = "other/Callback"),
                new Mutation("primitive accessor interface", n -> call(n, "get", "getReturnValueD").itf = true),
                new Mutation("primitive return opcode", n -> {
                    MethodNode m = method(n, "get");
                    for (var i : m.instructions) if (i.getOpcode() == Opcodes.DRETURN) {
                        m.instructions.set(i, new InsnNode(Opcodes.ARETURN)); break;
                    }
                }),
                new Mutation("cancellation branch", n -> {
                    for (var i : method(n, "get").instructions) if (i instanceof JumpInsnNode j) {
                        j.setOpcode(Opcodes.IFNE); break;
                    }
                }),
                new Mutation("argument local", n -> {
                    for (var i : method(n, "get").instructions) if (i instanceof VarInsnNode v
                            && v.getOpcode() == Opcodes.ALOAD && v.var == 1) { v.var = 0; break; }
                }),
                new Mutation("handler interface", n -> call(n, "get", "vhaccelerator$getLazily").itf = true),
                new Mutation("handler static invocation", n -> call(n, "get", "vhaccelerator$getLazily").setOpcode(Opcodes.INVOKESTATIC)),
                new Mutation("handler foreign owner", n -> foreignOwner(n, call(n, "get", "vhaccelerator$getLazily").name)),
                new Mutation("getMap original body", n -> {
                    for (var i : method(n, "getMap").instructions) if (i instanceof FieldInsnNode f) { f.name = "otherMap"; break; }
                }),
                new Mutation("get original body", n -> call(n, "get", "pack").name = "otherPack"),
                new Mutation("constructor original body", n -> {
                    for (var i : method(n, "<init>").instructions) if (i instanceof FieldInsnNode f && f.name.equals("samples")) {
                        f.name = "otherSamples"; break;
                    }
                }),
                new Mutation("constructor redirect owner", n -> foreignOwner(n, call(n, "<init>", "vhaccelerator$deferCompute").name)),
                new Mutation("compute foreign owner", n -> foreignOwner(n, "compute")),
                new Mutation("probability foreign owner", n -> foreignOwner(n, "getProbability")),
                new Mutation("missing final field", n -> n.fields.removeIf(f -> f.name.equals("map"))),
                new Mutation("duplicate hook", n -> {
                    MethodNode original = method(n, "get");
                    MethodNode copy = new MethodNode(original.access, original.name, original.desc,
                            original.signature, original.exceptions.toArray(String[]::new));
                    original.accept(copy);
                    n.methods.add(copy);
                }));
    }

    @TestFactory
    Stream<DynamicTest> actualServerExportsAndTheirMutations() {
        return Stream.of("s4", "wolds").flatMap(site -> Stream.concat(
                Stream.of(DynamicTest.dynamicTest(site + " real export", () -> {
                    pinOriginal();
                    ClassNode node = RealWovenFixtures.read(site, VaultLootCdfLayouts.CDF);
                    assertEquals(CALLBACK, call(node, "get", "getReturnValueD").owner);
                    assertEquals(List.of(), VaultLootCdfLayouts.lazyApplicationProblems(node));
                })),
                mutations().stream().map(mutation -> DynamicTest.dynamicTest(site + " rejects " + mutation.name(), () -> {
                    pinOriginal();
                    ClassNode node = RealWovenFixtures.read(site, VaultLootCdfLayouts.CDF);
                    mutation.edit().accept(node);
                    assertFalse(VaultLootCdfLayouts.lazyApplicationProblems(node).isEmpty(), mutation.name());
                }))));
    }
}
