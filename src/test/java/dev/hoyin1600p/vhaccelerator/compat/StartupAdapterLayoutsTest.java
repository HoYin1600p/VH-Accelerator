package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class StartupAdapterLayoutsTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.startup.";

    @Test
    void seleneLayoutFixtureIsRenderedFromTheExactArchive() throws Exception {
        Path fixture = Path.of("src/test/resources/dev/hoyin1600p/vhaccelerator/compat/selene-1.18.2-1.17.17-layout.json");
        var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        String archivePath = System.getenv("VHA_SELENE_FIXTURE_JAR");
        if (archivePath != null && "1".equals(System.getenv("VHA_REGENERATE_SAMPLE_SNAPSHOT"))) {
            byte[] bytes = java.nio.file.Files.readAllBytes(Path.of(archivePath));
            try (ZipFile archive = new ZipFile(archivePath)) {
                var attributes = new java.util.jar.Manifest(archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF"))).getMainAttributes();
                assertEquals("1.18.2-1.17.17", attributes.getValue("Implementation-Version"));
                try (var input = archive.getInputStream(archive.getEntry("net/mehvahdjukaar/selene/resourcepack/DynamicResourcePack.class"))) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, 0);
                    var rendered = java.util.Map.of("schemaVersion", 1, "implementationVersion", "1.18.2-1.17.17",
                            "sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
                            "className", node.name, "methods", node.methods.stream()
                                    .map(method -> java.util.Map.of("name", method.name, "descriptor", method.desc, "access", method.access)).toList());
                    java.nio.file.Files.createDirectories(fixture.getParent());
                    java.nio.file.Files.writeString(fixture, gson.toJson(rendered) + System.lineSeparator());
                }
            }
        }
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(fixture)).getAsJsonObject();
        ClassNode target = new ClassNode();
        target.name = json.get("className").getAsString();
        for (var method : json.getAsJsonArray("methods")) {
            var value = method.getAsJsonObject();
            target.methods.add(new org.objectweb.asm.tree.MethodNode(value.get("access").getAsInt(),
                    value.get("name").getAsString(), value.get("descriptor").getAsString(), null, null));
        }
        String mixin = BASE + "selene.SeleneResourceObserverMixin";
        ClassNode source = MixinTargetPreflightTest.read(mixin.replace('.', '/'));
        assertTrue(StartupAdapterLayouts.supports(mixin, id -> json.get("implementationVersion").getAsString()));
        assertEquals(List.of(), StartupAdapterLayouts.problems(mixin, source, name -> target));
        assertEquals(List.of(), CommonCompatibilityAudit.inspect(List.of(mixin),
                name -> name.equals(target.name) ? target : MixinTargetPreflightTest.read(name)).problems());
        target.methods.removeIf(method -> method.name.equals("addBytes"));
        assertFalse(StartupAdapterLayouts.problems(mixin, source, name -> target).isEmpty());
    }

    @Test
    void exactVersionTargetsMatchInstalledArchivesAndChangedLayoutsReject() throws Exception {
        for (String[] fixture : List.of(
                new String[]{"CraftTweaker-forge-1.18.2-9.1.213.jar", "crafttweaker", "9.1.213", "CraftTweakerExecutionObserverMixin", "CraftTweakerActionObserverMixin"},
                new String[]{"kubejs-forge-1802.5.5-build.569.jar", "kubejs", "1802.5.5-build.569", "KubeScriptObserverMixin", "KubeRecipeObserverMixin"})) {
            try (ZipFile archive = new ZipFile(Path.of("libs", fixture[0]).toFile())) {
                MixinTargetPreflight.ClassLookup lookup = name -> {
                    var entry = archive.getEntry(name + ".class");
                    if (entry == null) return MixinTargetPreflightTest.read(name);
                    try (var input = archive.getInputStream(entry)) {
                        ClassNode node = new ClassNode();
                        new ClassReader(input).accept(node, 0);
                        return node;
                    }
                };
                for (int i = 3; i < fixture.length; i++) {
                    String mixin = BASE + fixture[1] + "." + fixture[i];
                    ClassNode source = lookup.find(mixin.replace('.', '/'));
                    assertTrue(StartupAdapterLayouts.supports(mixin, id -> fixture[2]));
                    assertFalse(StartupAdapterLayouts.supports(mixin, id -> fixture[2] + ".changed"));
                    assertFalse(StartupAdapterLayouts.supports(mixin, id -> null));
                    assertEquals(List.of(), StartupAdapterLayouts.problems(mixin, source, lookup));
                    assertEquals(List.of(), MixinTargetPreflight.problems(source, MixinTargetPreflight.targets(source).get(0), lookup));
                    assertEquals(List.of(), CommonCompatibilityAudit.inspect(List.of(mixin), lookup).problems());
                    assertFalse(StartupAdapterLayouts.problems(mixin, source, name -> new ClassNode()).isEmpty());
                }
            }
        }
        assertFalse(StartupAdapterLayouts.isMixin(BASE + "crafttweaker.FutureMixin"));
    }
}
