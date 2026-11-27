package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** VHA's event dispatch snapshot applies only to Vault builds whose Event copies listeners per invoke. */
class VaultEventDispatchGateTest {
    @Test
    void onlyVault22AndLaterAndMainLine322CacheTheirOwnListeners() throws Exception {
        List<Path> jars;
        try (var files = Files.list(Path.of("libs"))) {
            jars = files.filter(path -> path.getFileName().toString().startsWith("the_vault-")
                    && path.toString().endsWith(".jar")).sorted().toList();
        }
        int cached = 0;
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry entry = zip.getEntry("iskallia/vault/core/event/Event.class");
                byte[] bytes;
                try (InputStream stream = zip.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                boolean copies = ModPresence.eventDispatchCopies(bytes);
                String name = jar.getFileName().toString();
                boolean ownCache = name.startsWith("the_vault-1.18.2-22.") || name.startsWith("the_vault-1.18.2-3.22.");
                assertEquals(!ownCache, copies, jar.toString());
                if (!copies) cached++;
            }
        }
        assertEquals(3, cached, "the Vault 22.0.0 (7152, 7209) and 3.22.0 fixtures are required");
    }
}
