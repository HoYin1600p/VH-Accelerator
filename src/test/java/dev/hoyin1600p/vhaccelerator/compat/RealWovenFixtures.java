package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Reads pinned real Mixin exports as data only; no exported class is defined or packaged.
 * Supply {@code -Preal_woven_dir=<exports>} with each release extracted into
 * {@code <exports>/<site>/classes/<internal name>.class}. The manifest pins the release,
 * archive and individual bytes. Third-party exports are external because their licences
 * do not authorize redistribution with these tests.
 */
public final class RealWovenFixtures {
    private RealWovenFixtures() { }

    public static ClassNode read(String site, String name) throws Exception {
        return read(site, name, "vha.test.realWovenDir");
    }

    public static ClassNode read(String site, String name, String property) throws Exception {
        String directory = System.getProperty(property);
        assumeTrue(directory != null, "Supply the external server exports for " + property);
        try (var input = RealWovenFixtures.class.getResourceAsStream("real-woven-fixtures.json")) {
            assertNotNull(input);
            var entry = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject(site).getAsJsonObject(name);
            byte[] bytes = Files.readAllBytes(Path.of(directory, site, "classes", name + ".class"));
            assertEquals(entry.get("bytes").getAsInt(), bytes.length, "fixture size: " + site + "/" + name);
            assertEquals(entry.get("sha256").getAsString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)), "fixture identity: " + site + "/" + name);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            assertEquals(name, node.name);
            return node;
        }
    }
}
