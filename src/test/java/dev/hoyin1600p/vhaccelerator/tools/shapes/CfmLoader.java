package dev.hoyin1600p.vhaccelerator.tools.shapes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Loads the original, unmodified MrCrayfish's Furniture classes from a local jar and only renames
 * Minecraft SRG member names to the official names of this test runtime (same approach as the
 * round 6A furniture harness). Nothing is committed or packaged.
 */
final class CfmLoader extends ClassLoader implements AutoCloseable {
    static final String SHA256 = "48d55a12e01d5d2de970b4be4f3eea188202f8a95058c8bb47b50f12d61661b2";
    private final ZipFile archive;
    private final Map<String, String> names = new HashMap<>();

    CfmLoader(ClassLoader parent, Path jar, Path mappings) throws Exception {
        super(parent);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        if (!SHA256.equals(digest)) throw new IllegalArgumentException("Unsupported CFM jar fingerprint");
        archive = new ZipFile(jar.toFile());
        for (String line : Files.readAllLines(mappings)) {
            if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
            String[] words = line.trim().split("\\s+");
            if (words.length >= 2 && words[words.length - 1].matches("[mf]_\\d+_")) {
                names.put(words[words.length - 1], words[0]);
            }
        }
        if (!"joinUnoptimized".equals(names.get("m_83148_"))) {
            throw new IllegalStateException("Unexpected SRG mapping contract");
        }
    }

    java.util.List<String> blockClassNames() throws IOException {
        var result = new java.util.ArrayList<String>();
        var entries = archive.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("com/mrcrayfish/furniture/block/") || !name.endsWith(".class")) continue;
            var node = new org.objectweb.asm.tree.ClassNode();
            new ClassReader(archive.getInputStream(entry)).accept(node, 0);
            for (var method : node.methods) {
                for (var insn : method.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.endsWith("/VoxelShapeHelper") && call.name.equals("combineAll")) {
                        result.add(node.name.replace('/', '.'));
                    }
                }
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if (!name.startsWith("com.mrcrayfish.furniture.")) return super.loadClass(name, resolve);
            Class<?> result = findLoadedClass(name);
            if (result == null) result = findClass(name);
            if (resolve) resolveClass(result);
            return result;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var entry = archive.getEntry(name.replace('.', '/') + ".class");
        if (entry == null) throw new ClassNotFoundException(name);
        try {
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(archive.getInputStream(entry)).accept(new ClassRemapper(writer, new Remapper() {
                @Override public String mapMethodName(String owner, String name, String descriptor) { return names.getOrDefault(name, name); }
                @Override public String mapFieldName(String owner, String name, String descriptor) { return names.getOrDefault(name, name); }
                @Override public String mapInvokeDynamicMethodName(String name, String descriptor) { return names.getOrDefault(name, name); }
            }), 0);
            byte[] bytes = writer.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    @Override public void close() throws IOException { archive.close(); }
}
