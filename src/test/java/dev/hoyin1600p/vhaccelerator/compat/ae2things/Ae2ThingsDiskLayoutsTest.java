package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityAudit;
import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityLayouts;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

class Ae2ThingsDiskLayoutsTest {
    private static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things";

    private static ClassNode manager(int readAccess, String readDesc, int disksAccess, String disksDesc) {
        ClassNode node = new ClassNode();
        node.name = Ae2ThingsDiskLayouts.MANAGER;
        node.methods.add(new MethodNode(readAccess, "readNbt", readDesc, null, null));
        node.fields.add(new FieldNode(disksAccess, "disks", disksDesc, null, null));
        return node;
    }

    private static ClassNode storage(int access, String desc) {
        ClassNode node = new ClassNode();
        node.name = Ae2ThingsDiskLayouts.STORAGE;
        node.fields.add(new FieldNode(access, "stackKeys", desc, null, null));
        return node;
    }

    private static MixinTargetPreflight.ClassLookup lookup(ClassNode manager, ClassNode storage) {
        Map<String, ClassNode> classes = new HashMap<>();
        if (manager != null) classes.put(manager.name, manager);
        if (storage != null) classes.put(storage.name, storage);
        return classes::get;
    }

    private static ClassNode goodManager() {
        return manager(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Ae2ThingsDiskLayouts.READ_NBT_DESCRIPTOR,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "Ljava/util/Map;");
    }

    private static ClassNode goodStorage() {
        return storage(Opcodes.ACC_PUBLIC, "Lnet/minecraft/nbt/ListTag;");
    }

    @Test
    void reviewedLayoutHasNoProblems() {
        assertEquals(List.of(), Ae2ThingsDiskLayouts.problems(lookup(goodManager(), goodStorage())));
        assertEquals(List.of(), CommonCompatibilityLayouts.problems(GROUP, lookup(goodManager(), goodStorage())));
    }

    @Test
    void absentModReportsProblemsInsteadOfThrowing() {
        assertEquals(2, Ae2ThingsDiskLayouts.problems(lookup(null, null)).size());
        MixinTargetPreflight.ClassLookup throwing = name -> { throw new IllegalStateException("no archive"); };
        assertEquals(2, Ae2ThingsDiskLayouts.problems(throwing).size());
    }

    @Test
    void anyChangedMemberIsAProblem() {
        int pub = Opcodes.ACC_PUBLIC;
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(
                manager(pub, Ae2ThingsDiskLayouts.READ_NBT_DESCRIPTOR, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "Ljava/util/Map;"),
                goodStorage())).isEmpty(), "readNbt must be static");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(
                manager(pub | Opcodes.ACC_STATIC, "(Lnet/minecraft/nbt/CompoundTag;)Ljava/lang/Object;",
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "Ljava/util/Map;"),
                goodStorage())).isEmpty(), "readNbt must return the manager");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(
                manager(pub | Opcodes.ACC_STATIC, Ae2ThingsDiskLayouts.READ_NBT_DESCRIPTOR, Opcodes.ACC_PRIVATE, "Ljava/util/Map;"),
                goodStorage())).isEmpty(), "disks must be final");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(
                manager(pub | Opcodes.ACC_STATIC, Ae2ThingsDiskLayouts.READ_NBT_DESCRIPTOR,
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "Ljava/util/HashMap;"),
                goodStorage())).isEmpty(), "disks must be declared as a Map");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(goodManager(),
                storage(Opcodes.ACC_PRIVATE, "Lnet/minecraft/nbt/ListTag;"))).isEmpty(), "stackKeys must be public");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(goodManager(),
                storage(pub, "Lnet/minecraft/nbt/CompoundTag;"))).isEmpty(), "stackKeys must be a ListTag");
        assertFalse(Ae2ThingsDiskLayouts.problems(lookup(goodManager(),
                storage(pub | Opcodes.ACC_STATIC, "Lnet/minecraft/nbt/ListTag;"))).isEmpty(), "stackKeys must be per instance");
    }

    @Test
    void groupIsRegisteredAsACommonGroupWithItsContract() {
        assertTrue(CommonCompatibilityLayouts.isCommonGroup(GROUP));
        assertTrue(CommonCompatibilityLayouts.isCommonMixin(Ae2ThingsDiskStrings.MIXIN));
        assertEquals("compatibility.shareAe2ThingsDiskStrings", CommonCompatibilityLayouts.contract(GROUP).get("setting"));
        assertEquals(true, CommonCompatibilityLayouts.contract(GROUP).get("settingDefault"));
    }

    @Test
    void mixinAndHelpersHaveNoReachableClientLinkage() throws Exception {
        MixinTargetPreflight.ClassLookup lookup = name -> {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
                if (in == null) return null;
                ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, 0);
                return node;
            }
        };
        var audit = CommonCompatibilityAudit.inspect(List.of(Ae2ThingsDiskStrings.MIXIN), lookup);
        assertEquals(List.of(), audit.problems(), audit.helperClasses().toString());
        assertTrue(audit.helperClasses().contains("dev/hoyin1600p/vhaccelerator/compat/ae2things/Ae2ThingsDiskStrings"));
        ClassNode mixin = lookup.find(Ae2ThingsDiskStrings.MIXIN.replace('.', '/'));
        assertEquals(List.of("io/github/projectet/ae2things/util/StorageManager"), MixinTargetPreflight.targets(mixin));
        assertEquals(List.of(), MixinTargetPreflight.problems(mixin, Ae2ThingsDiskLayouts.MANAGER,
                lookup(goodManager(), goodStorage())));
    }

    /** Optional: the real AE2Things 1.0.7 archive, passed with -Pae2things_jar. */
    @Test
    void realArchiveMatchesTheReviewedLayout() throws Exception {
        String jar = System.getProperty("ae2things.jar");
        assumeTrue(jar != null, "ae2things.jar not configured");
        try (ZipFile zip = new ZipFile(Path.of(jar).toFile())) {
            MixinTargetPreflight.ClassLookup lookup = name -> {
                var entry = zip.getEntry(name + ".class");
                if (entry == null) return null;
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
                    return node;
                }
            };
            assertEquals(List.of(), Ae2ThingsDiskLayouts.problems(lookup));
        }
    }
}
