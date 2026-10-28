package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

class BuildingGadgetsTemplateLayoutsTest {
    private static Path archive() {
        String path = System.getProperty("bgadgets.jar");
        assumeTrue(path != null && !path.isBlank(), "Requires explicit original archive fixture");
        return Path.of(path);
    }

    private static ClassNode original() throws Exception {
        Path path = archive();
        assertTrue(BuildingGadgetsTemplateLayouts.supportsArchive(path, BuildingGadgetsTemplateLayouts.VERSION));
        try (ZipFile zip = new ZipFile(path.toFile()); InputStream input = zip.getInputStream(
                zip.getEntry(BuildingGadgetsTemplateLayouts.TEMPLATE + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @Test void originalArchiveAndAllConstructorPathsMatchReviewedBytes() throws Exception {
        ClassNode node = original();
        assertTrue(BuildingGadgetsTemplateLayouts.problems(name -> node).isEmpty());
        assertEquals(3, node.methods.stream().filter(method -> method.name.equals("<init>")).count());
        assertEquals(1, node.methods.stream().filter(method -> method.name.equals("<init>"))
                .flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof FieldInsnNode field
                        && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("map")).count());
    }

    @Test void unsupportedMetadataArchiveAndGuavaBytesRejectBeforeActivation(@TempDir Path directory) throws Exception {
        assertFalse(BuildingGadgetsTemplateLayouts.supportsArchive(archive(), "3.13.2-build.21"));
        Path invalid = directory.resolve("unknown.jar");
        Files.writeString(invalid, "unknown archive");
        assertFalse(BuildingGadgetsTemplateLayouts.supportsArchive(invalid, BuildingGadgetsTemplateLayouts.VERSION));
        assertFalse(BuildingGadgetsTemplateLayouts.supportsArchive(archive(),
                BuildingGadgetsTemplateLayouts.VERSION, name -> new byte[] {1}));
    }

    @Test void changedBodiesFieldsFutureMethodsAndForeignMixinsReject() throws Exception {
        ClassNode node = original();
        node.methods.stream().filter(method -> method.name.equals("normalize")).findFirst().orElseThrow()
                .instructions.insert(new InsnNode(Opcodes.NOP));
        assertFalse(BuildingGadgetsTemplateLayouts.problems(name -> node).isEmpty());
        ClassNode field = original();
        field.fields.get(0).access &= ~Opcodes.ACC_FINAL;
        assertFalse(BuildingGadgetsTemplateLayouts.problems(name -> field).isEmpty());
        ClassNode foreign = original();
        MethodNode method = foreign.methods.get(0);
        AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = List.of("mixin", "foreign.TemplateMixin");
        method.visibleAnnotations = List.of(merged);
        assertFalse(BuildingGadgetsTemplateLayouts.problems(name -> foreign).isEmpty());
        ClassNode future = original();
        future.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "future", "()V", null, null));
        assertFalse(BuildingGadgetsTemplateLayouts.problems(name -> future).isEmpty());
    }

    @Test void actualVanillaCoordinateDependenciesAreSupportedAndUnknownClassesRefuse() {
        assertTrue(BuildingGadgetsTemplateLayouts.runtimeDependenciesSafe(BlockPos.class, Vec3i.class));
        assertFalse(BuildingGadgetsTemplateLayouts.runtimeDependenciesSafe(BlockPos.MutableBlockPos.class, Vec3i.class));
    }

    @Test void onlyCompleteReturnScaffoldsPreserveReviewedMethodBodies() throws Exception {
        ClassNode target = original();
        ClassNode source = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream(
                "/dev/hoyin1600p/vhaccelerator/mixin/compat/buildinggadgets/TemplateLookupMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(source, 0);
        }
        assertTrue(source.fields.isEmpty());
        assertTrue(source.methods.stream().noneMatch(method -> method.name.equals("<clinit>")));
        List<MethodNode> handlers = source.methods.stream().filter(method -> method.name.startsWith("vhaccelerator$")).toList();
        assertEquals(4, handlers.size());
        List<MethodNode> redirects = handlers.stream().filter(handler -> handler.name.startsWith("vhaccelerator$packed")).toList();
        for (MethodNode handler : handlers) {
            AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            merged.values = List.of("mixin", BuildingGadgetsTemplateCompaction.MIXIN);
            handler.visibleAnnotations = List.of(merged);
        }
        for (MethodNode method : target.methods) {
            boolean loaded = method.name.equals("deserialize");
            boolean fresh = method.name.equals("normalize") || method.name.equals("mirror")
                    || (method.name.equals("rotate") && method.desc.startsWith("(Lnet/minecraft/core/Direction$Axis;"));
            if (!loaded && !fresh) continue;
            MethodNode handler = handlers.stream().filter(candidate -> !redirects.contains(candidate)
                    && ((candidate.access & Opcodes.ACC_STATIC) != 0) == loaded).findFirst().orElseThrow();
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                InsnList scaffold = new InsnList();
                scaffold.add(new InsnNode(Opcodes.DUP));
                scaffold.add(new VarInsnNode(Opcodes.ASTORE, method.maxLocals));
                if (!loaded) scaffold.add(new VarInsnNode(Opcodes.ALOAD, 0));
                String callback = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
                scaffold.add(new TypeInsnNode(Opcodes.NEW, callback));
                scaffold.add(new InsnNode(Opcodes.DUP));
                scaffold.add(new LdcInsnNode(method.name));
                scaffold.add(new InsnNode(Opcodes.ICONST_0));
                scaffold.add(new VarInsnNode(Opcodes.ALOAD, method.maxLocals));
                scaffold.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, callback, "<init>",
                        "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
                scaffold.add(new MethodInsnNode(loaded ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                        target.name, handler.name, handler.desc, false));
                method.instructions.insertBefore(instruction, scaffold);
            }
        }
        target.methods.addAll(handlers);
        // Without the map-read redirects the application is incomplete.
        assertFalse(BuildingGadgetsTemplateLayouts.applicationProblems(target,
                BuildingGadgetsTemplateCompaction.MIXIN).isEmpty());
        int sites = redirectMapReads(target, redirects, null);
        assertEquals(6, sites);
        assertEquals(List.of(), BuildingGadgetsTemplateLayouts.applicationProblems(target,
                BuildingGadgetsTemplateCompaction.MIXIN));
        MethodNode changed = target.methods.stream().filter(method -> method.name.equals("normalize")).findFirst().orElseThrow();
        changed.instructions.insert(new InsnNode(Opcodes.NOP));
        assertFalse(BuildingGadgetsTemplateLayouts.applicationProblems(target,
                BuildingGadgetsTemplateCompaction.MIXIN).isEmpty());
    }

    @Test void mapReadRedirectsMustCoverExactlyTheReviewedReaders() throws Exception {
        for (String skipped : List.of("rotate", "serialize", "createViewInContext")) {
            ClassNode target = redirectedTarget(skipped, null);
            assertFalse(BuildingGadgetsTemplateLayouts.applicationProblems(target,
                    BuildingGadgetsTemplateCompaction.MIXIN).isEmpty(), "missing " + skipped);
        }
        assertEquals(List.of(), BuildingGadgetsTemplateLayouts.applicationProblems(redirectedTarget(null, null),
                BuildingGadgetsTemplateCompaction.MIXIN));
    }

    /** Applies every return scaffold and the map-read redirects, optionally skipping or adding one method. */
    private static ClassNode redirectedTarget(String skipped, Object unused) throws Exception {
        ClassNode target = original();
        ClassNode source = new ClassNode();
        try (InputStream input = BuildingGadgetsTemplateLayoutsTest.class.getResourceAsStream(
                "/dev/hoyin1600p/vhaccelerator/mixin/compat/buildinggadgets/TemplateLookupMixin.class")) {
            new ClassReader(input).accept(source, 0);
        }
        List<MethodNode> handlers = source.methods.stream().filter(method -> method.name.startsWith("vhaccelerator$")).toList();
        for (MethodNode handler : handlers) {
            AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            merged.values = List.of("mixin", BuildingGadgetsTemplateCompaction.MIXIN);
            handler.visibleAnnotations = List.of(merged);
        }
        List<MethodNode> redirects = handlers.stream().filter(handler -> handler.name.startsWith("vhaccelerator$packed")).toList();
        for (MethodNode method : target.methods) {
            boolean loaded = method.name.equals("deserialize");
            boolean fresh = method.name.equals("normalize") || method.name.equals("mirror")
                    || (method.name.equals("rotate") && method.desc.startsWith("(Lnet/minecraft/core/Direction$Axis;"));
            if (!loaded && !fresh) continue;
            MethodNode handler = handlers.stream().filter(candidate -> !redirects.contains(candidate)
                    && ((candidate.access & Opcodes.ACC_STATIC) != 0) == loaded).findFirst().orElseThrow();
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                InsnList scaffold = new InsnList();
                scaffold.add(new InsnNode(Opcodes.DUP));
                scaffold.add(new VarInsnNode(Opcodes.ASTORE, method.maxLocals));
                if (!loaded) scaffold.add(new VarInsnNode(Opcodes.ALOAD, 0));
                String callback = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
                scaffold.add(new TypeInsnNode(Opcodes.NEW, callback));
                scaffold.add(new InsnNode(Opcodes.DUP));
                scaffold.add(new LdcInsnNode(method.name));
                scaffold.add(new InsnNode(Opcodes.ICONST_0));
                scaffold.add(new VarInsnNode(Opcodes.ALOAD, method.maxLocals));
                scaffold.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, callback, "<init>",
                        "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
                scaffold.add(new MethodInsnNode(loaded ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                        target.name, handler.name, handler.desc, false));
                method.instructions.insertBefore(instruction, scaffold);
            }
        }
        target.methods.addAll(handlers);
        redirectMapReads(target, redirects, skipped);
        return target;
    }

    private static final java.util.Set<String> ITERATING = java.util.Set.of("serialize", "rotate", "mirror", "replace", "normalize");

    /**
     * What Mixin emits: the entrySet().iterator() call of each iterating method becomes a static handler call, and
     * createViewInContext's createUnsafe call becomes a static handler call followed by a cast of its result.
     */
    private static int redirectMapReads(ClassNode target, List<MethodNode> redirects, String skipped) {
        int sites = 0;
        MethodNode iterator = redirects.stream().filter(handler -> handler.name.contains("packedEntries")).findFirst().orElseThrow();
        MethodNode view = redirects.stream().filter(handler -> handler.name.contains("packedView")).findFirst().orElseThrow();
        for (MethodNode method : target.methods) {
            boolean iterating = ITERATING.contains(method.name);
            if (!(iterating || method.name.equals("createViewInContext")) || method.name.equals(skipped)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)) continue;
                // Mixin parks the call's arguments in fresh locals, pushes this, reloads them, calls the private handler.
                int slot = method.maxLocals;
                if (iterating && call.name.equals("iterator") && call.owner.equals("com/google/common/collect/ImmutableSet")) {
                    method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ASTORE, slot));
                    method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                    method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, slot));
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name,
                            iterator.name, iterator.desc, false));
                    method.instructions.remove(instruction);
                    sites++;
                } else if (!iterating && call.name.equals("createUnsafe")) {
                    for (int k = 2; k >= 0; k--) method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ASTORE, slot + k));
                    method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, 0));
                    for (int k = 0; k < 3; k++) method.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ALOAD, slot + k));
                    method.instructions.insertBefore(instruction, new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name,
                            view.name, view.desc, false));
                    method.instructions.insertBefore(instruction, new TypeInsnNode(Opcodes.CHECKCAST, call.owner));
                    method.instructions.remove(instruction);
                    sites++;
                }
            }
        }
        return sites;
    }
}
