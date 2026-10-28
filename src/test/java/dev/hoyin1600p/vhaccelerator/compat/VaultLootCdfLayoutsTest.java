package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class VaultLootCdfLayoutsTest {
    @Test
    void reviewedArchiveBodiesMatchAndUnknownVersionOrChangedBytesReject() throws Exception {
        int archives = 0;
        try (var files = Files.list(Path.of("libs"))) {
            for (Path path : files.filter(candidate -> candidate.getFileName().toString().startsWith("the_vault-")
                    && candidate.toString().endsWith(".jar")).toList()) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    Map<String, byte[]> classes = read(zip);
                    assertTrue(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION, classes::get), path.toString());
                    assertTrue(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION_6884, classes::get), path.toString());
                    assertFalse(VaultLootCdfLayouts.supports(null, classes::get));
                    assertFalse(VaultLootCdfLayouts.supports("1.18.2-3.21.63", classes::get));
                    classes.get(VaultLootCdfLayouts.CDF)[10] ^= 1;
                    assertFalse(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION, classes::get));
                    classes.remove(VaultLootCdfLayouts.KEY);
                    assertFalse(VaultLootCdfLayouts.supports(VaultLootCdfLayouts.SUPPORTED_VERSION, classes::get));
                    archives++;
                }
            }
        }
        assertEquals(8, archives, "all local Vault compatibility fixtures are required");
    }

    @Test
    void commonHandlerAndHelpersHaveNoClientLinkageAndTargetLayoutIsEager() throws Exception {
        try (ZipFile zip = new ZipFile("libs/the_vault-1.18.2-3.21.62.jar")) {
            Map<String, byte[]> classes = read(zip);
            Map<String, ClassNode> nodes = new HashMap<>();
            classes.forEach((name, content) -> {
                ClassNode node = new ClassNode();
                new ClassReader(content).accept(node, 0);
                nodes.put(name, node);
            });
            MixinTargetPreflight.ClassLookup lookup = name -> nodes.containsKey(name)
                    ? nodes.get(name) : MixinTargetPreflightTest.read(name);
            assertEquals(List.of(), VaultLootCdfLayouts.problems(lookup));
            assertEquals(List.of(), CommonCompatibilityAudit.inspect(List.of(VaultLootCdfLayouts.MIXIN), lookup).problems());
            assertTrue(CommonCompatibilityLayouts.isCommonGroup(VaultLootCdfLayouts.GROUP));
            assertTrue(CommonCompatibilityLayouts.isCommonMixin(VaultLootCdfLayouts.MIXIN));
            assertFalse(CommonCompatibilityLayouts.isCommonMixin(VaultLootCdfLayouts.GROUP + ".FutureMixin"));
            ClassNode cdf = nodes.get(VaultLootCdfLayouts.CDF);
            var compute = cdf.methods.stream().filter(method -> method.name.equals("compute")).findFirst().orElseThrow();
            var originalInstruction = compute.instructions.getFirst();
            compute.instructions.insert(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.NOP));
            assertTrue(VaultLootCdfLayouts.problems(lookup).stream().anyMatch(reason -> reason.contains("method bodies changed")));
            compute.instructions.remove(compute.instructions.getFirst());
            assertSame(originalInstruction, compute.instructions.getFirst());
            assertEquals(List.of(), VaultLootCdfLayouts.problems(lookup));
            var constructor = cdf.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
            var assignment = java.util.Arrays.stream(constructor.instructions.toArray())
                    .filter(instruction -> instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                            && field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD && field.name.equals("map"))
                    .map(instruction -> (org.objectweb.asm.tree.FieldInsnNode) instruction).findFirst().orElseThrow();
            assignment.name = "changedPublication";
            assertTrue(VaultLootCdfLayouts.problems(lookup).stream().anyMatch(reason -> reason.contains("method bodies changed")));
            assignment.name = "map";
            assertEquals(List.of(), VaultLootCdfLayouts.problems(lookup));
            cdf.fields.stream().filter(field -> field.name.equals("map")).findFirst().orElseThrow().desc = "Ljava/util/Map;";
            assertTrue(VaultLootCdfLayouts.problems(lookup).stream().anyMatch(reason -> reason.contains("final field changed: map")));
            cdf.methods.removeIf(method -> method.name.equals("<init>"));
            assertTrue(VaultLootCdfLayouts.problems(lookup).stream().anyMatch(reason -> reason.contains("constructor")));
        }
    }

    private static Map<String, byte[]> read(ZipFile zip) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        for (String name : List.of(VaultLootCdfLayouts.OUTER, VaultLootCdfLayouts.CDF, VaultLootCdfLayouts.KEY)) {
            try (var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
                result.put(name, input.readAllBytes());
            }
        }
        return result;
    }
}
