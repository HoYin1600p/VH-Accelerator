package dev.hoyin1600p.vhaccelerator.backport.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class RegistryWriteLayoutTest {
    private static ClassNode read(String internalName) throws Exception {
        try (InputStream in = RegistryWriteLayoutTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String desc) {
        return node.methods.stream()
                .filter(m -> m.desc.equals(desc) && (m.access & Opcodes.ACC_BRIDGE) == 0)
                .findFirst().orElseThrow();
    }

    @Test
    void installedMinecraftRegistryClassesHaveTheReviewedShape() {
        MixinTargetPreflight.ClassLookup lookup = RegistryWriteLayoutTest::read;
        assertEquals(List.of(), RegistryWriteLayout.problems(lookup));
    }

    /**
     * Optional: the SRG-named Forge jar (what the game runs) via -Pregistry_srg_jar=PATH, because the
     * check matches by descriptor and must hold for the runtime names too.
     */
    @Test
    void runtimeNamedClassesHaveTheReviewedShape() throws Exception {
        String path = System.getProperty("registry.srg.jar");
        org.junit.jupiter.api.Assumptions.assumeTrue(path != null, "registry.srg.jar not set");
        try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(path)) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                java.util.zip.ZipEntry entry = jar.getEntry(name + ".class");
                if (entry == null) {
                    return null;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, 0);
                    return node;
                }
            };
            ClassNode mapped = lookup.find(RegistryWriteLayout.MAPPED_REGISTRY);
            assertTrue(mapped.methods.stream().anyMatch(m -> m.name.startsWith("m_")),
                    "expected SRG member names in " + path);
            assertEquals(List.of(), RegistryWriteLayout.problems(lookup));
        }
    }

    @Test
    void anAlreadySynchronizedWriteMethodIsRejected() throws Exception {
        ClassNode registry = read(RegistryWriteLayout.MAPPED_REGISTRY);
        method(registry, RegistryWriteLayout.REGISTER_DESC).access |= Opcodes.ACC_SYNCHRONIZED;
        List<String> problems = new ArrayList<>();
        RegistryWriteLayout.mappedRegistryProblems(registry, problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("already synchronized")), problems.toString());
    }

    @Test
    void aMissingOrExtraMethodIsRejected() throws Exception {
        ClassNode registry = read(RegistryWriteLayout.MAPPED_REGISTRY);
        registry.methods.remove(method(registry, RegistryWriteLayout.OVERRIDE_DESC));
        List<String> problems = new ArrayList<>();
        RegistryWriteLayout.mappedRegistryProblems(registry, problems);
        assertFalse(problems.isEmpty());

        ClassNode doubled = read(RegistryWriteLayout.MAPPED_REGISTRY);
        MethodNode copy = method(doubled, RegistryWriteLayout.HOLDER_DESC);
        MethodNode twin = new MethodNode(Opcodes.ACC_PUBLIC, "twin", copy.desc, null, null);
        doubled.methods.add(twin);
        problems = new ArrayList<>();
        RegistryWriteLayout.mappedRegistryProblems(doubled, problems);
        assertFalse(problems.isEmpty());
    }

    @Test
    void aChangedInternalRegisterIsRejected() throws Exception {
        ClassNode registry = read(RegistryWriteLayout.REGISTRY);
        MethodNode internal = method(registry, RegistryWriteLayout.INTERNAL_REGISTER_DESC);
        internal.instructions.clear();
        List<String> problems = new ArrayList<>();
        RegistryWriteLayout.registryProblems(registry, problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("Map.put")), problems.toString());
    }

    @Test
    void missingClassesAreReported() {
        List<String> problems = RegistryWriteLayout.problems(name -> null);
        assertEquals(2, problems.size());
    }
}
