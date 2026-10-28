package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.mixin.VHAcceleratorMixinPlugin;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

class VaultLootCdfApplicationTest {
    private static final String LEGACY = "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.TieredLootTableGeneratorCdfMixin";
    private static final String FOREIGN = "example.othermod.ChangedCdfMixin";

    @Test
    void permitsOnlyReviewedMergedOwnersAndLegacyLazyOnlyOnAClient() throws Exception {
        ClassNode cdf = read();
        cdf.methods.add(merged("vhaccelerator$eager", VaultLootCdfLayouts.MIXIN, true));
        assertEquals(List.of(), VaultLootCdfLayouts.applicationProblems(cdf, false));
        cdf.methods.add(merged("vhaccelerator$lazy", LEGACY, false));
        assertEquals(List.of(), VaultLootCdfLayouts.applicationProblems(cdf, true));
        assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                .anyMatch(reason -> reason.contains("unreviewed merged method owner")));
    }

    @Test
    void rejectsForeignInjectHandlersAndOverwriteOriginsOnTheFinalTarget() throws Exception {
        for (boolean visible : new boolean[] {false, true}) {
            ClassNode cdf = read();
            cdf.methods.add(merged("handler$foreign$injected", FOREIGN, visible));
            assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                    .anyMatch(reason -> reason.contains(FOREIGN)));
        }
        for (String kernel : List.of("compute", "getProbability")) for (boolean visible : new boolean[]{false, true}) {
            ClassNode cdf = read();
            MethodNode method = cdf.methods.stream().filter(candidate -> candidate.name.equals(kernel)).findFirst().orElseThrow();
            var annotation = merged("overwrite", FOREIGN, visible);
            method.visibleAnnotations = annotation.visibleAnnotations;
            method.invisibleAnnotations = annotation.invisibleAnnotations;
            assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                    .anyMatch(reason -> reason.contains(kernel) && reason.contains(FOREIGN)));
            (visible ? method.visibleAnnotations : method.invisibleAnnotations).get(0).values = List.of("mixin", 123);
            assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                    .anyMatch(reason -> reason.contains("from unavailable")));
        }
    }

    @Test
    void rejectsUnmarkedChangesToEitherKernelEvenWithoutMergedMetadata() throws Exception {
        for (String kernel : List.of("compute", "getProbability")) {
            ClassNode target = read();
            target.methods.stream().filter(method -> method.name.equals(kernel)).findFirst().orElseThrow()
                    .instructions.insert(new InsnNode(Opcodes.NOP));
            assertFalse(VaultLootCdfLayouts.applicationProblems(target, false).isEmpty(), kernel);
        }
    }

    @Test
    void rejectsUnmarkedConstructorAndStaticInitializerChangesAndMutablePublication() throws Exception {
        for (String methodName : List.of("<init>", "<clinit>")) {
            ClassNode cdf = read();
            MethodNode method = cdf.methods.stream().filter(candidate -> candidate.name.equals(methodName))
                    .findFirst().orElseThrow();
            method.instructions.insert(new InsnNode(Opcodes.NOP));
            assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                    .anyMatch(reason -> reason.contains("initialization method changed: " + methodName)));
        }
        ClassNode cdf = read();
        cdf.fields.stream().filter(field -> field.name.equals("map")).findFirst().orElseThrow().access &= ~Opcodes.ACC_FINAL;
        assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, false).stream()
                .anyMatch(reason -> reason.contains("final field changed: map")));
    }

    @Test
    void existingClientLazyOwnerAllowsItsConstructorRedirectButNeverStaticChanges() throws Exception {
        ClassNode cdf = read();
        cdf.methods.add(merged("vhaccelerator$lazy", LEGACY, true));
        cdf.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow()
                .instructions.insert(new InsnNode(Opcodes.NOP));
        assertEquals(List.of(), VaultLootCdfLayouts.applicationProblems(cdf, true));
        cdf.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElseThrow()
                .instructions.insert(new InsnNode(Opcodes.NOP));
        assertTrue(VaultLootCdfLayouts.applicationProblems(cdf, true).stream()
                .anyMatch(reason -> reason.contains("initialization method changed: <clinit>")));
    }

    @Test
    void postApplyRejectsForeignOwnershipBeforeActivationWithoutThrowing() throws Exception {
        try (ZipFile zip = new ZipFile("libs/the_vault-1.18.2-3.21.62.jar")) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                var entry = zip.getEntry(name + ".class");
                if (entry == null) return MixinTargetPreflightTest.read(name);
                ClassNode node = new ClassNode();
                try (var input = zip.getInputStream(entry)) { new ClassReader(input).accept(node, 0); }
                return node;
            };
            CompatMixinGroups group = new CompatMixinGroups(List.of(VaultLootCdfLayouts.MIXIN),
                    (target, mixin) -> true, lookup);
            group.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", true));
            assertTrue(group.allows(VaultLootCdfLayouts.MIXIN));
            assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN));
            VHAcceleratorMixinPlugin plugin = new VHAcceleratorMixinPlugin();
            var field = VHAcceleratorMixinPlugin.class.getDeclaredField("compatMixinGroups");
            field.setAccessible(true);
            field.set(plugin, group);
            ClassNode finalTarget = read();
            finalTarget.methods.add(merged("handler$foreign$injected", FOREIGN, true));
            assertDoesNotThrow(() -> plugin.postApply(VaultLootCdfLayouts.CDF.replace('/', '.'), finalTarget,
                    VaultLootCdfLayouts.MIXIN, null));
            assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN));
            assertEquals("rejected", group.snapshot().get(0).get("state"));
            assertTrue(group.snapshot().get(0).get("reason").toString().contains(FOREIGN));
            ClassNode pristine = read();
            assertEquals(List.of(), VaultLootCdfLayouts.applicationProblems(pristine, false));
            assertDoesNotThrow(() -> plugin.postApply(VaultLootCdfLayouts.CDF.replace('/', '.'), pristine,
                    VaultLootCdfLayouts.MIXIN, null), "missing offline Mixin service must also fail closed");
            assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN));
        }
    }

    private static MethodNode merged(String name, String owner, boolean visible) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, name, "()V", null, null);
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", owner);
        if (visible) method.visibleAnnotations = List.of(annotation);
        else method.invisibleAnnotations = List.of(annotation);
        return method;
    }

    private static ClassNode read() throws Exception {
        try (ZipFile zip = new ZipFile("libs/the_vault-1.18.2-3.21.62.jar");
             var input = zip.getInputStream(zip.getEntry(VaultLootCdfLayouts.CDF + ".class"))) {
            assertTrue(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION, name -> {
                try (var bytes = zip.getInputStream(zip.getEntry(name + ".class"))) { return bytes.readAllBytes(); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }));
            ClassNode cdf = new ClassNode();
            new ClassReader(input).accept(cdf, 0);
            return cdf;
        }
    }
}
