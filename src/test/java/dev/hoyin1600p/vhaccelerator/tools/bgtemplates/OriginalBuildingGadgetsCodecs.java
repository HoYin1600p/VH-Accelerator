package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Executes selected unchanged, registry-free methods from one reviewed original archive. */
public final class OriginalBuildingGadgetsCodecs {
    public static final String JAR_SHA256 = "5eae7d0201adc89c4d6cdf80defb2807768a269090bb4ce7bc7c901cd208813a";
    public static final String JAR_VERSION = "3.13.2-build.21+mc1.18.2";
    public static final String MATH = "com.direwolf20.buildinggadgets.common.util.tools.MathUtils";
    public static final String PALETTE = "com.direwolf20.buildinggadgets.common.util.compression.DataDecompressor";
    private final MethodHandle position;
    private final MethodHandle stateId;
    private final MethodHandle paletteConstructor;

    public OriginalBuildingGadgetsCodecs(Path originalJar) throws IOException {
        if (!sha256(originalJar).equals(JAR_SHA256)) throw new IOException("Unsupported Building Gadgets archive hash");
        Map<String, byte[]> definitions;
        try (ZipFile archive = new ZipFile(originalJar.toFile())) {
            var manifestEntry = archive.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry == null) throw new IOException("Missing original archive manifest");
            try (var input = archive.getInputStream(manifestEntry)) {
                if (!JAR_VERSION.equals(new Manifest(input).getMainAttributes().getValue("Implementation-Version"))) {
                    throw new IOException("Unsupported Building Gadgets archive version");
                }
            }
            definitions = Map.of(MATH, readClass(archive, MATH), PALETTE, readClass(archive, PALETTE));
        }
        if (!sha256(originalJar).equals(JAR_SHA256)) throw new IOException("Original archive changed while reading");
        ClassLoader loader = new ClassLoader(OriginalBuildingGadgetsCodecs.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        try {
            Class<?> math = loader.loadClass(MATH);
            Class<?> decompressor = loader.loadClass(PALETTE);
            position = MethodHandles.publicLookup().findStatic(math, "posFromLong", MethodType.methodType(BlockPos.class, long.class));
            stateId = MethodHandles.publicLookup().findStatic(math, "readStateId", MethodType.methodType(int.class, long.class));
            paletteConstructor = MethodHandles.publicLookup().findConstructor(decompressor,
                    MethodType.methodType(void.class, ListTag.class, Function.class, IntFunction.class));
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IOException("Original registry-free codec linkage unavailable", failure);
        }
        verifyOriginalMethods();
    }

    private static byte[] readClass(ZipFile archive, String name) throws IOException {
        var entry = archive.getEntry(name.replace('.', '/') + ".class");
        if (entry == null) throw new IOException("Original codec class missing: " + name);
        try (var input = archive.getInputStream(entry)) { return input.readAllBytes(); }
    }

    public BlockPos position(long packed) {
        try { return (BlockPos) position.invokeExact(packed); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    public int stateId(long packed) {
        try { return (int) stateId.invokeExact(packed); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    @SuppressWarnings("unchecked")
    public IntFunction<Tag> palette(ListTag values) {
        Function<Tag, Tag> identity = value -> value;
        IntFunction<Tag> missing = index -> { throw new IllegalArgumentException("Missing palette index: " + index); };
        try { return (IntFunction<Tag>) paletteConstructor.invoke(values, identity, missing); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Original codec invocation failed", failure);
    }

    private void verifyOriginalMethods() {
        if (!position(0).equals(BlockPos.ZERO) || !position(-1).equals(new BlockPos(65535, 255, 65535))
                || stateId(0) != 0 || stateId(-1) != 16777215) {
            throw new IllegalStateException("Original coordinate codec failed reviewed boundary checks");
        }
        ListTag values = new ListTag();
        values.add(StringTag.valueOf("first"));
        values.add(StringTag.valueOf("second"));
        IntFunction<Tag> palette = palette(values);
        if (palette.apply(0) != values.get(0) || palette.apply(1) != values.get(1)) {
            throw new IllegalStateException("Original palette codec failed identity checks");
        }
        try { palette.apply(2); }
        catch (IllegalArgumentException expected) { return; }
        throw new IllegalStateException("Original palette codec accepted a missing index");
    }

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[131072];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
