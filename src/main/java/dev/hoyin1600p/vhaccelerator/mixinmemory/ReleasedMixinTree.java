package dev.hoyin1600p.vhaccelerator.mixinmemory;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Stands in for the class tree Mixin keeps for a mixin that has already been applied.
 *
 * <p>Mixin only ever reads that tree through {@code ClassNode.accept(ClassVisitor)}, which it uses to
 * copy the tree into a fresh working node each time the mixin is applied to a target. This class
 * answers that one call by rebuilding the tree from the class stored here as deflated bytes, so a
 * mixin applied later (a target loaded after the release, or the same target looked up again) still
 * sees an equivalent tree. Nothing else on the node is meaningful; the identifying members are kept
 * only so a debugger or a stack trace names the class.
 */
public final class ReleasedMixinTree extends ClassNode {
    private final byte[] deflated;
    private final int length;

    private ReleasedMixinTree(ClassNode source, byte[] deflated, int length) {
        super(Opcodes.ASM9);
        this.version = source.version;
        this.access = source.access;
        this.name = source.name;
        this.superName = source.superName;
        this.deflated = deflated;
        this.length = length;
    }

    /**
     * Stores {@code tree} as bytes, or returns null when the bytes would not rebuild an identical tree.
     * "Identical" is checked as a fixed point: reading the bytes back and writing them again must give
     * the same bytes, so a tree that does not survive the round trip stays in memory unchanged.
     */
    public static ReleasedMixinTree capture(ClassNode tree) {
        byte[] first = write(tree);
        ClassNode reread = new ClassNode();
        new ClassReader(first).accept(reread, ClassReader.EXPAND_FRAMES);
        if (!Arrays.equals(first, write(reread))) {
            return null;
        }
        return new ReleasedMixinTree(tree, deflate(first), first.length);
    }

    /** Visits the rebuilt tree exactly as the original tree would have been visited. */
    @Override
    public void accept(ClassVisitor visitor) {
        new ClassReader(inflate()).accept(visitor, ClassReader.EXPAND_FRAMES);
    }

    public int storedSize() {
        return this.deflated.length;
    }

    public int classSize() {
        return this.length;
    }

    private static byte[] write(ClassNode tree) {
        ClassWriter writer = new ClassWriter(0);
        tree.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] deflate(byte[] bytes) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(bytes);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length / 3 + 16);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private byte[] inflate() {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(this.deflated);
            byte[] bytes = new byte[this.length];
            int filled = 0;
            while (filled < bytes.length) {
                int read = inflater.inflate(bytes, filled, bytes.length - filled);
                if (read == 0 && (inflater.finished() || inflater.needsInput())) {
                    break;
                }
                filled += read;
            }
            if (filled != bytes.length) {
                throw new IllegalStateException("Stored mixin class is truncated: " + this.name);
            }
            return bytes;
        } catch (DataFormatException exception) {
            throw new IllegalStateException("Stored mixin class is corrupt: " + this.name, exception);
        } finally {
            inflater.end();
        }
    }
}
