package dev.hoyin1600p.vhaccelerator.compat.thermalfuels;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Runs the layout guard against the real Thermal Foundation bytes (pass {@code -Pmixin_scan_jars=<folder with
 * thermal_foundation-*.jar>}; skipped otherwise) and against deliberately damaged copies of them.
 */
class ThermalFuelLayoutsTest {
    private static final String STIRLING = "cofh/thermal/core/util/managers/dynamo/StirlingFuelManager";
    private static final String GOURMAND = "cofh/thermal/core/util/managers/dynamo/GourmandFuelManager";
    private static final String SUPER = "cofh/thermal/lib/util/managers/SingleItemFuelManager";

    private static Map<String, ClassNode> realClasses() throws IOException {
        String list = System.getProperty("mixin.scan.jars");
        assumeTrue(list != null && !list.isBlank(), "pass -Pmixin_scan_jars with Thermal Foundation");
        Map<String, ClassNode> found = new HashMap<>();
        for (String entry : list.split(Pattern.quote(java.io.File.pathSeparator))) {
            Path path = Path.of(entry.trim());
            List<Path> jars = new java.util.ArrayList<>();
            if (Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    files.filter(file -> file.getFileName().toString().startsWith("thermal_foundation")
                            && file.toString().endsWith(".jar")).forEach(jars::add);
                }
            } else if (path.getFileName().toString().startsWith("thermal_foundation")) {
                jars.add(path);
            }
            for (Path jar : jars) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (String name : List.of(STIRLING, GOURMAND, SUPER)) {
                        ZipEntry zipEntry = zip.getEntry(name + ".class");
                        if (zipEntry == null) continue;
                        try (InputStream stream = zip.getInputStream(zipEntry)) {
                            ClassNode node = new ClassNode();
                            new ClassReader(stream.readAllBytes()).accept(node, 0);
                            found.put(name, node);
                        }
                    }
                }
            }
        }
        assumeTrue(found.containsKey(STIRLING) && found.containsKey(GOURMAND), "Thermal Foundation jar not found");
        return found;
    }

    private static MixinTargetPreflight.ClassLookup lookup(Map<String, ClassNode> classes) {
        return classes::get;
    }

    private static MethodNode scan(ClassNode manager) {
        return manager.methods.stream().filter(method -> method.name.equals("createConvertedRecipes")).findFirst().orElseThrow();
    }

    private static List<String> damaged(Consumer<ClassNode> damage) throws IOException {
        Map<String, ClassNode> classes = realClasses();
        ClassNode copy = new ClassNode();
        classes.get(STIRLING).accept(copy);
        damage.accept(copy);
        classes.put(STIRLING, copy);
        return ThermalFuelLayouts.problems(lookup(classes));
    }

    @Test
    void theReviewedThermalFoundationHasNoProblems() throws IOException {
        Map<String, ClassNode> classes = realClasses();
        assertEquals(List.of(), ThermalFuelLayouts.problems(lookup(classes)));
        assertEquals(List.of(), CommonCompatibilityLayouts.problems(ThermalConvertedFuels.GROUP, lookup(classes)));
    }

    @Test
    void anExtraCallInTheScanIsRejected() throws IOException {
        assertFalse(damaged(node -> scan(node).instructions.insert(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, STIRLING, "addFuel", "(I)V", false))).isEmpty());
    }

    @Test
    void aMissingCallInTheScanIsRejected() throws IOException {
        assertFalse(damaged(node -> {
            for (AbstractInsnNode instruction : scan(node).instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("validFuel")) {
                    scan(node).instructions.remove(call);
                    break;
                }
            }
        }).isEmpty());
    }

    @Test
    void aWriteToAnotherFieldIsRejected() throws IOException {
        assertFalse(damaged(node -> scan(node).instructions.insert(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.PUTFIELD, SUPER, "fuelMap", "Ljava/util/Map;"))).isEmpty());
    }

    @Test
    void aMissingHookTargetIsRejected() throws IOException {
        assertFalse(damaged(node -> node.methods.removeIf(method -> method.name.equals("getConvertedFuels"))).isEmpty());
        assertFalse(damaged(node -> node.methods.removeIf(method -> method.name.equals("clear"))).isEmpty());
        assertFalse(damaged(node -> node.fields.removeIf(field -> field.name.equals("convertedFuels"))).isEmpty());
    }

    @Test
    void aChangedSuperclassOrStaticScanIsRejected() throws IOException {
        assertFalse(damaged(node -> node.superName = "java/lang/Object").isEmpty());
        assertFalse(damaged(node -> scan(node).access |= Opcodes.ACC_STATIC).isEmpty());
    }

    @Test
    void absentClassesAreReportedWithoutThrowing() {
        List<String> problems = ThermalFuelLayouts.problems(name -> null);
        assertEquals(2, problems.size());
        MixinTargetPreflight.ClassLookup failing = name -> {
            throw new IllegalStateException("no bytes");
        };
        assertEquals(2, ThermalFuelLayouts.problems(failing).size());
    }

    @Test
    void theGroupIsRegisteredAsACommonServerGroupWithAContract() {
        assertTrue(CommonCompatibilityLayouts.isCommonGroup(ThermalConvertedFuels.GROUP));
        assertTrue(CommonCompatibilityLayouts.isCommonMixin(ThermalConvertedFuels.STIRLING_MIXIN));
        assertTrue(CommonCompatibilityLayouts.isCommonMixin(ThermalConvertedFuels.GOURMAND_MIXIN));
        assertEquals("compatibility.deferThermalConvertedFuels",
                CommonCompatibilityLayouts.contract(ThermalConvertedFuels.GROUP).get("setting"));
    }
}
