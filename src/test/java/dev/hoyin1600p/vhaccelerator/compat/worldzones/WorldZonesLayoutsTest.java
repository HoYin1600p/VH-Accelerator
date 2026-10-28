package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.compat.CompatibilityApplicationObserver;
import dev.hoyin1600p.vhaccelerator.compat.RealWovenFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class WorldZonesLayoutsTest {
    static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    static void pin(WorldZonesOriginalHost host, String version) {
        assertTrue(WorldZonesLayouts.supports(version, name -> {
            try { return host.raw(name); } catch (Exception failure) { throw new IllegalStateException(failure); }
        }));
    }

    @Test void exactBothArchivesVersionsAndEveryClassPinFailClosed() throws Exception {
        for (String version : List.of("1.18.2-3.21.62", "1.18.2-3.21.6.6884")) {
            try (var host = new WorldZonesOriginalHost("the_vault-" + version + ".jar")) {
                pin(host, version);
                assertEquals(List.of(), WorldZonesLayouts.problems(name -> read(host.raw(name))));
                for (String bad : List.of("3.21.62", "1.18.2-3.21.63", "1.18.2-3.21.6.6885", "")) {
                    assertFalse(WorldZonesLayouts.supports(bad, name -> { throw new AssertionError("Must short circuit"); }));
                }
                for (String broken : List.of(WorldZonesLayouts.TARGET, "iskallia/vault/core/world/storage/WorldZone",
                        "iskallia/vault/core/world/storage/BlockCuboid", "iskallia/vault/world/data/WorldZonesData")) {
                    assertFalse(WorldZonesLayouts.supports(version, name -> {
                        try { byte[] bytes = host.raw(name); if (name.equals(broken)) bytes[bytes.length - 1] ^= 1; return bytes; }
                        catch (Exception failure) { throw new IllegalStateException(failure); }
                    }));
                }
            }
        }
    }

    @Test void compiledHandlerBothCallsUnchangedBodiesAndFieldsAreRequired() throws Exception {
        try (var host = new WorldZonesOriginalHost("the_vault-1.18.2-3.21.62.jar")) {
            pin(host, "1.18.2-3.21.62");
            ClassNode original = read(host.raw(WorldZonesLayouts.TARGET));
            assertFalse(WorldZonesLayouts.applicationProblems(original).isEmpty());
            ClassNode good = woven(original);
            assertEquals(List.of(), WorldZonesLayouts.applicationProblems(good));
            for (String name : List.of("readBits", "readNbt")) {
                ClassNode missing = woven(read(host.raw(WorldZonesLayouts.TARGET)));
                MethodNode method = missing.methods.stream().filter(m -> m.name.equals(name) && m.desc.endsWith(")V")).findFirst().orElseThrow();
                for (var instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode call && call.name.contains(WorldZonesLayouts.HANDLER)) method.instructions.remove(call);
                assertFalse(WorldZonesLayouts.applicationProblems(missing).isEmpty());
            }
            ClassNode changed = woven(read(host.raw(WorldZonesLayouts.TARGET)));
            changed.methods.stream().filter(m -> m.name.equals("get") && m.desc.startsWith("(III)")).findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP));
            assertFalse(WorldZonesLayouts.applicationProblems(changed).isEmpty());
            ClassNode foreign = woven(read(host.raw(WorldZonesLayouts.TARGET)));
            var extra = new MethodNode(Opcodes.ACC_PRIVATE, "foreign", "()V", null, null);
            extra.visibleAnnotations = List.of(merged("example.ForeignMixin")); foreign.methods.add(extra);
            assertFalse(WorldZonesLayouts.applicationProblems(foreign).isEmpty());
            good.fields.get(0).access |= Opcodes.ACC_PUBLIC;
            assertFalse(WorldZonesLayouts.applicationProblems(good).isEmpty());
        }
    }

    @Test void cachedVoidCallbacksAtHeadNormalizeBothArchiveLayouts() throws Exception {
        for (String version : List.of("1.18.2-3.21.62", "1.18.2-3.21.6.6884")) {
            try (var host = new WorldZonesOriginalHost("the_vault-" + version + ".jar")) {
                pin(host, version);
                assertEquals(List.of(), WorldZonesLayouts.applicationProblems(cached(woven(read(host.raw(WorldZonesLayouts.TARGET))))));
            }
        }
    }

    private static ClassNode realWoven() throws Exception {
        return RealWovenFixtures.read("s4-worldzones-024", WorldZonesLayouts.TARGET, "vha.test.worldZonesWovenDir");
    }

    @Test void actualServerExportConfirmsBothInjectorsAndUnchangedBodiesAgainstBothArchives() throws Exception {
        ClassNode exported = realWoven();
        ClassNode source;
        try (var input = getClass().getResourceAsStream('/' + WorldZonesLayouts.MIXIN.replace('.', '/') + ".class")) {
            assertNotNull(input); source = read(input.readAllBytes());
        }
        // These are the two production postApply checks; the exported class is read as data only.
        assertEquals(List.of(), CompatibilityApplicationObserver.missingCommonInjectors(exported, WorldZonesLayouts.MIXIN, source));
        for (String version : List.of("1.18.2-3.21.62", "1.18.2-3.21.6.6884")) {
            try (var host = new WorldZonesOriginalHost("the_vault-" + version + ".jar")) {
                pin(host, version);
                assertEquals(List.of(), WorldZonesLayouts.applicationProblems(exported));
                assertFalse(WorldZonesLayouts.applicationProblems(read(host.raw(WorldZonesLayouts.TARGET))).isEmpty(),
                        "the original-only attachment is not proof of an applied hook");
            }
        }
    }

    @Test void actualServerExportRejectsChangedCallbackLocalsAndConstruction() throws Exception {
        try (var host = new WorldZonesOriginalHost("the_vault-1.18.2-3.21.62.jar")) {
            pin(host, "1.18.2-3.21.62");
            ClassNode changedLocal = realWoven();
            ((VarInsnNode) head(load(changedLocal), 5)).var++;
            assertFalse(WorldZonesLayouts.applicationProblems(changedLocal).isEmpty());
            ClassNode cancellable = realWoven();
            load(cancellable).instructions.set(head(load(cancellable), 3), new InsnNode(Opcodes.ICONST_1));
            assertFalse(WorldZonesLayouts.applicationProblems(cancellable).isEmpty());
            ClassNode shared = realWoven();
            int slot = ((VarInsnNode) head(load(shared), 5)).var;
            load(shared).instructions.insertBefore(head(load(shared), 6), new VarInsnNode(Opcodes.ALOAD, slot));
            assertFalse(WorldZonesLayouts.applicationProblems(shared).isEmpty());
        }
    }

    @Test void actualServerExportRejectsMissingHooksOtherTransformsAndChangedHandlers() throws Exception {
        try (var host = new WorldZonesOriginalHost("the_vault-1.18.2-3.21.62.jar")) {
            pin(host, "1.18.2-3.21.62");
            for (String name : List.of("readBits", "readNbt")) {
                ClassNode missing = realWoven();
                MethodNode method = missing.methods.stream().filter(m -> m.name.equals(name)
                        && !m.desc.contains("Lnet/minecraft/nbt/Tag;")).findFirst().orElseThrow();
                method.instructions.remove(callback(method));
                assertFalse(WorldZonesLayouts.applicationProblems(missing).isEmpty());
            }
            ClassNode changed = realWoven();
            changed.methods.stream().filter(m -> m.name.equals("get") && m.desc.startsWith("(III)"))
                    .findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP));
            assertFalse(WorldZonesLayouts.applicationProblems(changed).isEmpty());
            ClassNode changedHandler = realWoven(); handler(changedHandler).access |= Opcodes.ACC_STATIC;
            assertFalse(WorldZonesLayouts.applicationProblems(changedHandler).isEmpty());
            ClassNode field = realWoven(); field.fields.get(0).access |= Opcodes.ACC_PUBLIC;
            assertFalse(WorldZonesLayouts.applicationProblems(field).isEmpty());
            ClassNode foreign = realWoven(); handler(foreign).visibleAnnotations = List.of(merged("example.ForeignMixin"));
            assertFalse(WorldZonesLayouts.applicationProblems(foreign).isEmpty());
        }
    }

    @Test void cachedCallbackConstructionLocalOwnershipAndReturnBoundaryFailClosed() throws Exception {
        try (var host = new WorldZonesOriginalHost("the_vault-1.18.2-3.21.62.jar")) {
            pin(host, "1.18.2-3.21.62");
            List<java.util.function.Consumer<ClassNode>> changes = List.of(
                    node -> load(node).instructions.set(head(load(node), 3), new InsnNode(Opcodes.ICONST_1)),
                    node -> ((LdcInsnNode) head(load(node), 2)).cst = "otherMethod",
                    node -> ((TypeInsnNode) head(load(node), 0)).desc = "example/OtherCallback",
                    node -> ((VarInsnNode) head(load(node), 5)).var++,
                    node -> load(node).instructions.insert(new InsnNode(Opcodes.NOP)),
                    node -> { MethodNode method = load(node); int slot = ((VarInsnNode) head(method, 5)).var;
                        method.instructions.insertBefore(head(method, 6), new VarInsnNode(Opcodes.ALOAD, slot)); },
                    node -> load(node).instructions.insertBefore(callback(load(node)), new InsnNode(Opcodes.NOP)),
                    node -> callback(load(node)).setOpcode(Opcodes.INVOKESTATIC),
                    node -> callback(load(node)).itf = true,
                    node -> load(node).instructions.insert(callback(load(node)), new InsnNode(Opcodes.NOP)),
                    node -> handler(node).access |= Opcodes.ACC_STATIC,
                    node -> handler(node).instructions.insert(new InsnNode(Opcodes.NOP)),
                    node -> ((MethodInsnNode) head(handler(node), 1)).name = "unreviewed",
                    node -> handler(node).visibleAnnotations = List.of(merged("example.ForeignMixin")),
                    node -> { MethodNode duplicate = new MethodNode(Opcodes.ACC_PRIVATE, "extra$" + WorldZonesLayouts.HANDLER,
                            handler(node).desc, null, null); handler(node).accept(duplicate);
                        // accept() preserves the constructor name supplied above.
                        node.methods.add(duplicate); });
            for (int i = 0; i < changes.size(); i++) {
                ClassNode node = cached(woven(read(host.raw(WorldZonesLayouts.TARGET))));
                changes.get(i).accept(node);
                assertFalse(WorldZonesLayouts.applicationProblems(node).isEmpty(), "malformed cached callback " + i);
            }
        }
    }

    private static MethodNode load(ClassNode node) {
        return node.methods.stream().filter(m -> (m.name + m.desc).equals("readBits(Liskallia/vault/core/net/BitBuffer;)V"))
                .findFirst().orElseThrow();
    }
    private static MethodNode handler(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.contains(WorldZonesLayouts.HANDLER)).findFirst().orElseThrow();
    }
    private static AbstractInsnNode head(MethodNode method, int index) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList().get(index);
    }
    private static MethodInsnNode callback(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.name.contains(WorldZonesLayouts.HANDLER)).map(i -> (MethodInsnNode) i).findFirst().orElseThrow();
    }

    /** Model of Sponge 0.8.5's totalInjections > 1 non-cancellable void callback, not a server export. */
    static ClassNode cached(ClassNode target) {
        for (MethodNode method : target.methods) {
            if (!method.name.equals("readBits") && !(method.name.equals("readNbt") && method.desc.contains("CompoundTag"))) continue;
            MethodInsnNode call = callback(method);
            List<AbstractInsnNode> code = java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
            int at = code.indexOf(call), slot = method.maxLocals++;
            InsnList construction = new InsnList();
            for (AbstractInsnNode instruction : code.subList(at - 5, at)) {
                method.instructions.remove(instruction); construction.add(instruction);
            }
            construction.add(new VarInsnNode(Opcodes.ASTORE, slot));
            method.instructions.insert(construction);
            method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, slot));
        }
        return target;
    }

    /** Offline model of the declared RETURN injection; executes the actual compiled production handler. */
    static ClassNode woven(ClassNode target) throws Exception {
        ClassNode mixin;
        try (var input = WorldZonesLayoutsTest.class.getResourceAsStream('/' + WorldZonesLayouts.MIXIN.replace('.', '/') + ".class")) {
            assertNotNull(input); mixin = read(input.readAllBytes());
        }
        MethodNode source = mixin.methods.stream().filter(m -> m.name.equals(WorldZonesLayouts.HANDLER)).findFirst().orElseThrow();
        assertEquals(0, source.access & Opcodes.ACC_STATIC, "instance target needs instance handler");
        AnnotationNode inject = source.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        assertTrue(inject.values.contains(0), "optional require=0");
        MethodNode handler = new MethodNode(source.access, source.name, source.desc, null, null); source.accept(handler);
        handler.visibleAnnotations = new ArrayList<>(List.of(merged(WorldZonesLayouts.MIXIN)));
        target.methods.add(handler);
        for (MethodNode method : target.methods) {
            if (!(method.name + method.desc).equals("readNbt(Lnet/minecraft/nbt/CompoundTag;)V")
                    && !(method.name + method.desc).equals("readBits(Liskallia/vault/core/net/BitBuffer;)V")) continue;
            for (var instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList callback = new InsnList();
                callback.add(new VarInsnNode(Opcodes.ALOAD, 0));
                String info = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
                callback.add(new TypeInsnNode(Opcodes.NEW, info)); callback.add(new InsnNode(Opcodes.DUP));
                callback.add(new LdcInsnNode(method.name)); callback.add(new InsnNode(Opcodes.ICONST_0));
                callback.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, info, "<init>", "(Ljava/lang/String;Z)V", false));
                callback.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, handler.name, handler.desc, false));
                method.instructions.insertBefore(instruction, callback);
            }
        }
        return target;
    }
    static AnnotationNode merged(String mixin) {
        AnnotationNode a = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        a.values = List.of("mixin", mixin); return a;
    }
}
