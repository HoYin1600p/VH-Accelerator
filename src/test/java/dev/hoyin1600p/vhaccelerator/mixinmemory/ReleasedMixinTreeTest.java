package dev.hoyin1600p.vhaccelerator.mixinmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

class ReleasedMixinTreeTest {
    /** VHA's own compiled classes (mostly mixins) and Mixin 0.8.5's classes: real tree shapes of every kind. */
    @Test
    void rebuiltTreesEqualTheOriginalsOnRealClasses() throws Exception {
        List<byte[]> corpus = new ArrayList<>();
        collectDirectory(codeSource(VHAccelerator.class), corpus);
        collectJar(codeSource(IMixinInfo.class), corpus, "org/spongepowered/asm/mixin/transformer/");
        assertTrue(corpus.size() > 300, "corpus too small: " + corpus.size());

        int unstable = 0;
        long classBytes = 0;
        long storedBytes = 0;
        for (byte[] bytes : corpus) {
            ClassNode original = read(bytes);
            ReleasedMixinTree released = ReleasedMixinTree.capture(original);
            if (released == null) {
                unstable++;
                continue;
            }
            assertEquals(dump(original), dumpVisit(released), "rebuilt tree differs for " + original.name);
            // Rebuilding twice gives the same tree again (the replacement stays usable).
            assertEquals(dumpVisit(released), dumpVisit(released));
            assertEquals(original.name, released.name);
            classBytes += released.classSize();
            storedBytes += released.storedSize();
        }
        assertEquals(0, unstable, "classes that would not have been released safely");
        assertTrue(storedBytes < classBytes, "deflated bytes should be smaller than the class bytes");
    }

    @Test
    void replacementAnswersTheMixinCopyCallIntoAFreshWorkingNode() throws Exception {
        ClassNode original = read(classBytes(ReleasedMixinTreeTest.class));
        ReleasedMixinTree released = ReleasedMixinTree.capture(original);
        assertNotNull(released);
        // This is the one call Mixin makes: copy the stored tree into a new working node.
        ClassNode copy = new ClassNode();
        released.accept(copy);
        assertEquals(original.methods.size(), copy.methods.size());
        assertEquals(original.name, copy.name);
        assertEquals(original.sourceFile, copy.sourceFile);
    }

    private static String dump(ClassNode node) {
        StringWriter text = new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(text)));
        return text.toString();
    }

    private static String dumpVisit(ClassNode node) {
        return dump(node);
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    static byte[] classBytes(Class<?> type) throws IOException {
        try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return stream.readAllBytes();
        }
    }

    static Path codeSource(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static void collectDirectory(Path root, List<byte[]> into) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                into.add(Files.readAllBytes(file));
            }
        }
    }

    private static void collectJar(Path jar, List<byte[]> into, String prefix) throws IOException {
        if (!Files.isRegularFile(jar)) {
            return;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith(prefix) && entry.getName().endsWith(".class")) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        into.add(stream.readAllBytes());
                    }
                }
            }
        }
    }
}
