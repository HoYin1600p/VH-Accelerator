package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Runs the preflight over every shipped compatibility mixin against each
 * supported Vault Hunters and JEI build in {@code libs/}, so the check never
 * disables a supported build.
 */
class ShippedCompatMixinPreflightTest {
    private static final Path LIBS = Path.of("libs");

    @Test
    void shippedCompatibilityMixinsMatchEverySupportedBuild() throws Exception {
        List<Path> jars;
        try (Stream<Path> files = Files.list(LIBS)) {
            jars = files.filter(path -> path.toString().endsWith(".jar")).sorted().toList();
        }
        List<Path> vaultJars = jars.stream()
                .filter(path -> path.getFileName().toString().startsWith("the_vault-"))
                .toList();
        List<Path> jeiJars = jars.stream()
                .filter(path -> path.getFileName().toString().startsWith("jei-"))
                .toList();
        List<Path> shared = jars.stream()
                .filter(path -> !vaultJars.contains(path) && !jeiJars.contains(path))
                .toList();
        assertFalse(vaultJars.isEmpty());
        assertFalse(jeiJars.isEmpty());

        List<String> configured = CompatMixinGroups.readConfiguredMixins(
                getClass().getClassLoader(),
                "vhaccelerator.mixins.json"
        );
        List<String> problems = new ArrayList<>();
        int checkedTargets = 0;
        for (Path vault : vaultJars) {
            for (Path jei : jeiJars) {
                List<Path> profile = new ArrayList<>(shared);
                profile.add(vault);
                profile.add(jei);
                checkedTargets += check(configured, profile, problems);
            }
        }
        assertTrue(checkedTargets > 100, "checked " + checkedTargets + " targets");
        assertEquals(List.of(), problems.stream().distinct().toList());
    }

    private static int check(
            List<String> configured,
            List<Path> profile,
            List<String> problems
    ) throws IOException {
        List<ZipFile> archives = new ArrayList<>();
        try {
            for (Path jar : profile) {
                archives.add(new ZipFile(jar.toFile()));
            }
            MixinTargetPreflight.ClassLookup lookup = name -> find(name, archives);
            Map<String, Set<Boolean>> groupPresence = new HashMap<>();
            int checked = 0;
            for (String mixin : configured) {
                if (CompatMixinGroups.groupOf(mixin) == null) {
                    continue;
                }
                ClassNode node = MixinTargetPreflightTest.read(mixin.replace('.', '/'));
                assertNotNull(node, mixin);
                for (String target : MixinTargetPreflight.targets(node)) {
                    boolean present = find(target, archives) != null;
                    groupPresence.computeIfAbsent(
                            CompatMixinGroups.groupOf(mixin),
                            group -> new HashSet<>()
                    ).add(present);
                    if (!present) {
                        continue; // Target mod or generation is absent here.
                    }
                    checked++;
                    for (String problem : MixinTargetPreflight.problems(node, target, lookup)) {
                        problems.add(mixin + ": " + problem);
                    }
                }
            }
            // At runtime a group whose mod is installed but has one missing
            // target is skipped, so a supported build must resolve all or none.
            groupPresence.forEach((group, presence) -> {
                if (presence.size() > 1) {
                    problems.add(group + ": only some targets exist in " + profile);
                }
            });
            return checked;
        } finally {
            for (ZipFile archive : archives) {
                archive.close();
            }
        }
    }

    private static ClassNode find(String internalName, List<ZipFile> archives)
            throws IOException {
        for (ZipFile archive : archives) {
            ZipEntry entry = archive.getEntry(internalName + ".class");
            if (entry != null) {
                try (InputStream stream = archive.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(stream).accept(node, 0);
                    return node;
                }
            }
        }
        return MixinTargetPreflightTest.read(internalName);
    }
}
