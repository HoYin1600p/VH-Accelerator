package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalScriptInputsTest {
    @TempDir
    Path game;

    private Path write(String relative, String content) throws IOException {
        Path path = game.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setLastModifiedTime(path, FileTime.fromMillis(1_000_000L));
        return path;
    }

    @Test
    void emptyInstallationHasNoInputs() {
        assertEquals(List.of(), LocalScriptInputs.collect(game));
    }

    @Test
    void includesCraftTweakerAndKubeJsSourcesButNotExportedOutput() throws IOException {
        write("scripts/fuel.zs", "burn");
        write("kubejs/server_scripts/recipes.js", "event");
        write("kubejs/assets/pack/models/item/x.json", "{}");
        write("kubejs/exported/tags.json", "generated");
        write("logs/kubejs/server.log", "log");

        List<String> inputs = LocalScriptInputs.collect(game);
        assertEquals(
                List.of(
                        "script=scripts/fuel.zs:4:1000000",
                        "script=kubejs/server_scripts/recipes.js:5:1000000",
                        "script=kubejs/assets/pack/models/item/x.json:2:1000000"
                ),
                inputs
        );
    }

    @Test
    void editingAScriptChangesTheInputs() throws IOException {
        Path script = write("scripts/fuel.zs", "burn");
        List<String> before = LocalScriptInputs.collect(game);
        Files.writeString(script, "burn longer");
        assertNotEquals(before, LocalScriptInputs.collect(game));
    }

    @Test
    void inputsAreOrderedIndependentlyOfCreationOrder() throws IOException {
        write("scripts/b.zs", "b");
        write("scripts/a.zs", "a");
        List<String> inputs = LocalScriptInputs.collect(game);
        assertEquals("script=scripts/a.zs:1:1000000", inputs.get(0));
        assertEquals("script=scripts/b.zs:1:1000000", inputs.get(1));
    }
}
