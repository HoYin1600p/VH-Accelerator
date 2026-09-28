package dev.hoyin1600p.vhaccelerator.client.compat.kubejs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KubeJsPackFileIndexTest {
    @TempDir
    Path directory;

    @Test
    void answersExactlyAsFilesExists() throws Exception {
        Path assets = directory.resolve("kubejs").resolve("assets");
        Path texture = assets.resolve("kubejs/textures/item/coin.png");
        Files.createDirectories(texture.getParent());
        Files.writeString(texture, "png");
        Files.createDirectories(assets.resolve("Mixed/Case"));
        Files.writeString(directory.resolve("outside.png"), "png");

        KubeJsPackFileIndex index = KubeJsPackFileIndex.create(assets);
        assertNotNull(index);
        List<Path> probes = List.of(
                texture,
                texture.getParent(),
                assets.resolve("kubejs/textures/item/missing.png"),
                assets.resolve("KUBEJS/textures/item/COIN.png"),
                assets.resolve("mixed/case"),
                assets.resolve("kubejs/textures/../textures/item/coin.png"),
                assets.resolve("../outside.png"),
                assets.resolve("../missing.png"),
                assets);
        for (Path probe : probes) {
            assertEquals(Files.exists(probe), KubeJsPackFileIndex.exists(index, probe), probe.toString());
        }
        assertNull(index.exists(directory.resolve("outside.png")), "outside the listing asks the disk");
    }

    @Test
    void missingFolderListsNothing() {
        Path absent = directory.resolve("kubejs").resolve("data");
        KubeJsPackFileIndex index = KubeJsPackFileIndex.create(absent);
        assertNotNull(index);
        assertEquals(Files.exists(absent.resolve("x/y.json")),
                KubeJsPackFileIndex.exists(index, absent.resolve("x/y.json")));
    }
}
