package dev.hoyin1600p.vhaccelerator.tools.bgtemplates;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Original packing/compression over raw tag surrogates, not original registry-backed BlockData. */
final class OriginalRawTemplateCodec {
    private static final String COMPRESSOR = "com.direwolf20.buildinggadgets.common.util.compression.DataCompressor";
    private static final Map<String, String> GETTERS = Map.of("m_123341_", "getX", "m_123342_", "getY", "m_123343_", "getZ");
    final OriginalBuildingGadgetsCodecs raw;
    final String originalMathSha256;
    final String linkedMathSha256;
    final String originalCompressorSha256;
    final String originalDecompressorSha256;
    private final MethodHandle pack;
    private final MethodHandle includeState;
    private final MethodHandle compressorConstructor;
    private final MethodHandle compressorWrite;

    OriginalRawTemplateCodec(Path jar) throws IOException {
        raw = new OriginalBuildingGadgetsCodecs(jar);
        byte[] math;
        byte[] compressor;
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            math = read(archive, OriginalBuildingGadgetsCodecs.MATH);
            compressor = read(archive, COMPRESSOR);
            originalDecompressorSha256 = hash(read(archive, OriginalBuildingGadgetsCodecs.PALETTE));
        }
        originalMathSha256 = hash(math);
        originalCompressorSha256 = hash(compressor);
        byte[] linkedMath = linkCoordinateGetterNames(math);
        linkedMathSha256 = hash(linkedMath);
        Map<String, byte[]> definitions = Map.of(OriginalBuildingGadgetsCodecs.MATH, linkedMath, COMPRESSOR, compressor);
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
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
            Class<?> mathType = loader.loadClass(OriginalBuildingGadgetsCodecs.MATH);
            Class<?> compressorType = loader.loadClass(COMPRESSOR);
            pack = MethodHandles.publicLookup().findStatic(mathType, "posToLong", MethodType.methodType(long.class, BlockPos.class));
            includeState = MethodHandles.publicLookup().findStatic(mathType, "includeStateId", MethodType.methodType(long.class, long.class, int.class));
            compressorConstructor = MethodHandles.publicLookup().findConstructor(compressorType, MethodType.methodType(void.class));
            compressorWrite = MethodHandles.publicLookup().findVirtual(compressorType, "write", MethodType.methodType(ListTag.class, Function.class));
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IOException("Original raw-tag compression linkage failed", failure);
        }
        if (!OriginalBuildingGadgetsCodecs.sha256(jar).equals(OriginalBuildingGadgetsCodecs.JAR_SHA256)) {
            throw new IOException("Original archive changed during codec loading");
        }
    }

    static byte[] linkCoordinateGetterNames(byte[] original) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        byte[] canonicalOriginal = bytes(node);
        int changes = 0;
        for (var method : node.methods) {
            if (!method.name.equals("posToLong") || !method.desc.equals("(Lnet/minecraft/core/BlockPos;)J")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals("net/minecraft/core/BlockPos") && call.desc.equals("()I") && GETTERS.containsKey(call.name)) {
                    call.name = GETTERS.get(call.name);
                    changes++;
                }
            }
        }
        if (changes != 3) throw new IOException("Expected exactly three original coordinate getter namespace links");
        byte[] result = bytes(node);
        ClassNode restored = new ClassNode();
        new ClassReader(result).accept(restored, 0);
        int restoredNames = 0;
        for (var method : restored.methods) {
            if (!method.name.equals("posToLong") || !method.desc.equals("(Lnet/minecraft/core/BlockPos;)J")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals("net/minecraft/core/BlockPos") && call.desc.equals("()I")) {
                    for (var link : GETTERS.entrySet()) {
                        if (call.name.equals(link.getValue())) { call.name = link.getKey(); restoredNames++; break; }
                    }
                }
            }
        }
        if (restoredNames != 3 || !Arrays.equals(canonicalOriginal, bytes(restored))) {
            throw new IOException("Namespace linkage changed more than the three coordinate getter names");
        }
        return result;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter output = new ClassWriter(0);
        node.accept(output);
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked") ToIntFunction<Tag> compressor() {
        try { return (ToIntFunction<Tag>) compressorConstructor.invoke(); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    ListTag palette(ToIntFunction<Tag> compressor) {
        Function<Tag, Tag> identity = value -> value;
        try { return (ListTag) compressorWrite.invoke(compressor, identity); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    long packed(BlockPos position, int state) {
        try { return (long) includeState.invokeExact((long) pack.invokeExact(position), state); }
        catch (Throwable failure) { throw propagate(failure); }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Original raw codec failed", failure);
    }

    private static byte[] read(ZipFile archive, String name) throws IOException {
        var entry = archive.getEntry(name.replace('.', '/') + ".class");
        if (entry == null) throw new IOException("Missing original codec " + name);
        try (var input = archive.getInputStream(entry)) { return input.readAllBytes(); }
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
