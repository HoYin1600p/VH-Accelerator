package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ImmutablePathPackIndexTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void mutableFolderPacksAreNotIndexed() {
        assertNull(ImmutablePathPackIndex.create(
                this.temporaryDirectory,
                paths -> resolve(this.temporaryDirectory, paths)
        ));
    }

    @Test
    void forgeArchiveSourceUsesResolvedRootsAndPreservesSlotTextures() throws Exception {
        Path archive = this.temporaryDirectory.resolve("forge-mod.jar");
        try (FileSystem fs = FileSystems.newFileSystem(
                URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            Path root = fs.getPath("/");
            write(root, "assets/demo/textures/gui/empty_upgrade.png");
            write(root, "assets/demo/textures/gui/empty_upgrade.png.mcmeta");
            write(root, "assets/demo/models/item/memory.json");
            write(root, "data/demo/recipes/upgrade.json");
            java.util.concurrent.atomic.AtomicInteger resolutions =
                    new java.util.concurrent.atomic.AtomicInteger();
            ImmutablePathPackIndex index = ImmutablePathPackIndex.create(archive, paths -> {
                resolutions.incrementAndGet();
                return resolve(root, paths);
            });
            assertNotNull(index, "Forge supplies an outer disk path, not a ZIP root");
            assertEquals(2, resolutions.get());
            assertEquals(Set.of(new ResourceLocation("demo", "textures/gui/empty_upgrade.png")),
                    Set.copyOf(index.resources(PackType.CLIENT_RESOURCES, "demo",
                            "textures", Integer.MAX_VALUE, name -> true)));
            assertTrue(index.hasResource("assets/demo/textures/gui/empty_upgrade.png.mcmeta"));
            assertTrue(index.hasResource("assets/demo/models/item/memory.json"));
            assertTrue(index.hasResource("data/demo/recipes/upgrade.json"));
            assertEquals(2, resolutions.get(), "Keep the roots we actually validated");
        }
    }

    @Test
    void immutableSourceCannotAuthorizeMixedMutableResourceRoots() throws Exception {
        Path archive = this.temporaryDirectory.resolve("mixed.jar");
        try (FileSystem fs = FileSystems.newFileSystem(
                URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            Path root = fs.getPath("/");
            assertNull(ImmutablePathPackIndex.create(root, paths ->
                    paths[0].equals("assets") ? resolve(root, paths)
                            : resolve(this.temporaryDirectory, paths)));
            assertNull(ImmutablePathPackIndex.create(this.temporaryDirectory,
                    paths -> resolve(root, paths)),
                    "A virtual resolver must not opt an exploded mod folder into caching");
            assertNull(ImmutablePathPackIndex.create(archive, paths -> null));
            assertNull(ImmutablePathPackIndex.create(archive, paths -> {
                throw new IllegalStateException("Unavailable mod resource root");
            }));
        }
    }

    @Test
    void validatedVanillaRootsAreNotChangedByCallerMapMutation() throws Exception {
        Path archive = this.temporaryDirectory.resolve("snapshot.jar");
        try (FileSystem fs = FileSystems.newFileSystem(
                URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            Path root = fs.getPath("/");
            write(root, "assets/demo/models/kept.json");
            EnumMap<PackType, Path> roots = new EnumMap<>(PackType.class);
            roots.put(PackType.CLIENT_RESOURCES, root.resolve("assets"));
            roots.put(PackType.SERVER_DATA, root.resolve("data"));
            ImmutablePathPackIndex index = ImmutablePathPackIndex.createVanilla(roots);
            roots.put(PackType.CLIENT_RESOURCES, this.temporaryDirectory);
            assertNotNull(index);
            assertTrue(index.hasResource("assets/demo/models/kept.json"));
        }
    }

    @Test
    void immutableTreeAnswersListingsAndExistenceWithoutLosingMetadata()
            throws Exception {
        Path archive = this.temporaryDirectory.resolve("resources.jar");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                uri,
                Map.of("create", "true")
        )) {
            Path root = fileSystem.getPath("/");
            write(root, "assets/demo/models/block/example.json");
            write(root, "assets/demo/models/block/example.json.mcmeta");
            write(root, "assets/demo/models/item/other.json");

            ImmutablePathPackIndex index = ImmutablePathPackIndex.create(
                    root,
                    paths -> resolve(root, paths)
            );
            assertNull(index.cachedNamespaces(PackType.CLIENT_RESOURCES));

            Collection<ResourceLocation> resources = index.resources(
                    PackType.CLIENT_RESOURCES,
                    "demo",
                    "models/block",
                    Integer.MAX_VALUE,
                    name -> name.endsWith(".json")
            );

            assertEquals(
                    Set.of(new ResourceLocation(
                            "demo",
                            "models/block/example.json"
                    )),
                    Set.copyOf(resources)
            );
            assertTrue(index.hasResource(
                    "assets/demo/models/block/example.json.mcmeta"
            ));
            assertFalse(index.hasResource(
                    "assets/demo/models/block/missing.json"
            ));
            assertEquals(
                    Set.of("demo"),
                    index.cachedNamespaces(PackType.CLIENT_RESOURCES)
            );
            assertEquals(
                    Set.of("demo"),
                    index.cachedNamespaces(PackType.SERVER_DATA)
            );
        }
    }

    @Test
    void emptyPrefixesAndAbsoluteDepthMatchForgePathPackSemantics()
            throws Exception {
        Path archive = this.temporaryDirectory.resolve("depth.jar");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                uri,
                Map.of("create", "true")
        )) {
            Path root = fileSystem.getPath("/");
            write(root, "assets/demo/root.json");
            write(root, "assets/demo/deep/child.json");

            ImmutablePathPackIndex index = ImmutablePathPackIndex.create(
                    root,
                    paths -> resolve(root, paths)
            );
            Collection<ResourceLocation> shallow = index.resources(
                    PackType.CLIENT_RESOURCES,
                    "demo",
                    "",
                    1,
                    name -> true
            );

            assertEquals(
                    Set.of(new ResourceLocation("demo", "root.json")),
                    Set.copyOf(shallow)
            );
        }
    }

    @Test
    void vanillaFactoryUsesResolvedImmutableRoots() throws Exception {
        Path archive = this.temporaryDirectory.resolve("vanilla.jar");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(
                uri,
                Map.of("create", "true")
        )) {
            Path root = fileSystem.getPath("/");
            Path assets = root.resolve("assets");
            Path data = root.resolve("data");
            write(root, "assets/minecraft/models/block/stone.json");
            write(root, "data/minecraft/recipes/stone.json");
            EnumMap<PackType, Path> roots = new EnumMap<>(PackType.class);
            roots.put(PackType.CLIENT_RESOURCES, assets);
            roots.put(PackType.SERVER_DATA, data);

            ImmutablePathPackIndex index =
                    ImmutablePathPackIndex.createVanilla(roots);

            Collection<ResourceLocation> resources = index.resources(
                    PackType.CLIENT_RESOURCES,
                    "minecraft",
                    "models/block",
                    Integer.MAX_VALUE,
                    name -> true
            );
            assertEquals(
                    Set.of(new ResourceLocation(
                            "minecraft",
                            "models/block/stone.json"
                    )),
                    Set.copyOf(resources)
            );
            resources.add(new ResourceLocation(
                    "minecraft",
                    "models/block/generated.json"
            ));
            assertEquals(2, resources.size());

            Collection<ResourceLocation> missing = index.resources(
                    PackType.CLIENT_RESOURCES,
                    "minecraft",
                    "missing",
                    Integer.MAX_VALUE,
                    name -> true
            );
            missing.add(new ResourceLocation(
                    "minecraft",
                    "generated.json"
            ));
            assertEquals(1, missing.size());
        }
    }

    private static void write(Path root, String path) throws Exception {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "{}");
    }

    private static Path resolve(Path root, String... paths) {
        Path result = root;
        for (String path : paths) {
            result = result.resolve(path);
        }
        return result;
    }
}
