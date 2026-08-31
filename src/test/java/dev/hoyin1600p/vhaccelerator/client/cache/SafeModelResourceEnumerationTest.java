package dev.hoyin1600p.vhaccelerator.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.resource.PathResourcePack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeModelResourceEnumerationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void skipsMalformedLoosePackPathsWithoutDroppingValidModels()
            throws IOException {
        Path models = temporaryDirectory.resolve(
                "assets/example/models/block"
        );
        Files.createDirectories(models);
        Files.writeString(models.resolve("valid_model.json"), "{}");
        Files.writeString(models.resolve("Invalid Model.json"), "{}");

        PathResourcePack pack = new PathResourcePack(
                "test loose pack",
                temporaryDirectory
        );
        ResourceManager manager = new PackOnlyResourceManager(pack);

        Collection<ResourceLocation> recovered =
                SafeModelResourceEnumeration.recover(manager);

        assertEquals(1, recovered.size());
        assertTrue(recovered.contains(ResourceLocation.fromNamespaceAndPath(
                "example",
                "models/block/valid_model.json"
        )));
        assertFalse(recovered.stream().anyMatch(location ->
                location.getPath().contains("Invalid")
        ));
    }

    @Test
    void enumeratesEmbeddedJarModelsWithoutUsingItsPathFileSystem()
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeEntry(
                    archive,
                    "assets/example/models/block/valid_model.json"
            );
            writeEntry(
                    archive,
                    "assets/example/models/block/Invalid Model.json"
            );
            writeEntry(
                    archive,
                    "assets/example/textures/block/not_a_model.json"
            );
        }

        Set<ResourceLocation> recovered = new LinkedHashSet<>();
        AtomicInteger invalid = new AtomicInteger();
        SafeModelResourceEnumeration.collectModelArchiveEntries(
                new ByteArrayInputStream(bytes.toByteArray()),
                "embedded test pack",
                "example",
                recovered,
                invalid
        );

        assertEquals(1, recovered.size());
        assertTrue(recovered.contains(ResourceLocation.fromNamespaceAndPath(
                "example",
                "models/block/valid_model.json"
        )));
        assertEquals(1, invalid.get());
    }

    private static void writeEntry(
            ZipOutputStream archive,
            String name
    ) throws IOException {
        archive.putNextEntry(new ZipEntry(name));
        archive.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        archive.closeEntry();
    }

    private record PackOnlyResourceManager(PackResources pack)
            implements ResourceManager {
        @Override
        public Set<String> getNamespaces() {
            return pack.getNamespaces(
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES
            );
        }

        @Override
        public Resource getResource(ResourceLocation location)
                throws IOException {
            throw new IOException("Not used by this test");
        }

        @Override
        public boolean hasResource(ResourceLocation location) {
            return false;
        }

        @Override
        public List<Resource> getResources(ResourceLocation location) {
            return List.of();
        }

        @Override
        public Collection<ResourceLocation> listResources(
                String path,
                Predicate<String> filter
        ) {
            throw new UnsupportedOperationException(
                    "Recovery must enumerate packs directly"
            );
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.of(pack);
        }
    }
}
