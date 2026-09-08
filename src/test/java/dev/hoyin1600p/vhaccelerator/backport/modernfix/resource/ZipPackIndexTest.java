package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZipPackIndexTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesOnlyPackRootsAndPreservesListingRules() throws Exception {
        Path archive = this.temporaryDirectory.resolve("pack.zip");
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            write(zip, "pack.mcmeta");
            write(zip, "META-INF/manifest.mf");
            write(zip, "assets/demo/root.json");
            write(zip, "assets/demo/models/block/example.json");
            write(zip, "assets/demo/models/block/example.json.mcmeta");
            write(zip, "assets/UPPER/models/item/ignored.json");
            write(zip, "data/servermod/recipes/example.json");
        }

        ZipPackIndex index = new ZipPackIndex(archive);
        assertEquals(
                Set.of("demo"),
                index.namespaces(PackType.CLIENT_RESOURCES)
        );
        assertEquals(
                Set.of("servermod"),
                index.namespaces(PackType.SERVER_DATA)
        );
        assertEquals(
                Set.of(new ResourceLocation("demo", "root.json")),
                Set.copyOf(index.resources(
                        PackType.CLIENT_RESOURCES,
                        "demo",
                        "",
                        1,
                        name -> name.endsWith(".json")
                ))
        );
        assertEquals(
                Set.of(new ResourceLocation(
                        "demo",
                        "models/block/example.json"
                )),
                Set.copyOf(index.resources(
                        PackType.CLIENT_RESOURCES,
                        "demo",
                        "models/block",
                        Integer.MAX_VALUE,
                        name -> name.endsWith(".json")
                ))
        );
    }

    private static void write(ZipOutputStream zip, String name)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Test
    void rejectsTruncatedArchiveInsteadOfReturningEmptyResources() throws Exception {
        Path archive = temporaryDirectory.resolve("truncated.zip");
        Files.write(archive, new byte[] {80, 75, 3, 4});
        assertThrows(java.io.IOException.class, () -> new ZipPackIndex(archive));
    }

    @Test
    void rejectsPartialDirectoryInsteadOfPublishingOnlyFirstResource() throws Exception {
        Path archive = temporaryDirectory.resolve("partial.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(zip, "assets/demo/models/first.json");
            write(zip, "assets/demo/models/second.json");
        }
        byte[] bytes = Files.readAllBytes(archive);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int end = bytes.length - 22;
        int first = buffer.getInt(end + 16);
        int second = first + 46 + Short.toUnsignedInt(buffer.getShort(first + 28))
                + Short.toUnsignedInt(buffer.getShort(first + 30))
                + Short.toUnsignedInt(buffer.getShort(first + 32));
        buffer.putInt(second, 0);
        Files.write(archive, bytes);
        assertThrows(java.io.IOException.class, () -> new ZipPackIndex(archive));
    }

    @Test
    void acceptsAValidEmptyArchive() throws Exception {
        Path archive = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            // A valid EOCD with zero entries must remain an empty pack.
        }
        assertEquals(Set.of(), new ZipPackIndex(archive).namespaces(PackType.CLIENT_RESOURCES));
    }
}
