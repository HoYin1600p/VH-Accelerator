package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
