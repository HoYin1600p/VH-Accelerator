package dev.hoyin1600p.vhaccelerator.tools.shapes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Loads Minecraft, the Forge API classes and this mod child-first, so every instance gets its own
 * copy of the vanilla static state, and merges the chosen mixins into {@code Shapes},
 * {@code VoxelShape}, {@code CubeVoxelShape} and {@code CubePointRange}. With no components the
 * classes are exactly vanilla.
 *
 * <p>Components are applied in the order given (it stands in for mixin priority). VHA components
 * are the production classes; Canary components are the original, unmodified Canary 0.2.7 mixin
 * and shape classes read from the pinned jar, with only their SRG member names renamed to the
 * official names of this test runtime (same approach as {@link CfmLoader}).</p>
 */
final class WeavingLoader extends ClassLoader {
    static final String CANARY_SHA256 = "9bab7b2d897bf1b2b06673189273e6a7a9c3f533d952111cffdb10c1e388b04c";
    private static final String SHAPES = "net/minecraft/world/phys/shapes/Shapes";
    private static final String VOXEL_SHAPE = "net/minecraft/world/phys/shapes/VoxelShape";
    private static final String CUBE_SHAPE = "net/minecraft/world/phys/shapes/CubeVoxelShape";
    private static final String CUBE_RANGE = "net/minecraft/world/phys/shapes/CubePointRange";
    private static final String MIXINS = "dev/hoyin1600p/vhaccelerator/mixin/shape/";
    private static final String CANARY = "com/abdelaziz/canary/mixin/";
    private static final List<String> OWN = List.of(
            "net.minecraft.", "com.mojang.", "net.minecraftforge.", "dev.hoyin1600p.", "it.unimi.");

    /** component name -> {target class, mixin class, whether it is Canary's}. */
    private static final Map<String, String[]> COMPONENTS = new HashMap<>();
    static {
        COMPONENTS.put("vha-box", new String[] {SHAPES, MIXINS + "ShapesBoxCacheMixin", "no"});
        COMPONENTS.put("vha-join", new String[] {SHAPES, MIXINS + "ShapesLaunchCacheMixin", "no"});
        COMPONENTS.put("vha-merge", new String[] {SHAPES, MIXINS + "ShapesCoordinateMergerMixin", "no"});
        COMPONENTS.put("vha-optimize", new String[] {VOXEL_SHAPE, MIXINS + "VoxelShapeLaunchCacheMixin", "no"});
        COMPONENTS.put("vha-leaf", new String[] {VOXEL_SHAPE, MIXINS + "VoxelShapeLeafContentMixin", "no"});
        COMPONENTS.put("canary-box", new String[] {SHAPES, CANARY + "shapes/specialized_shapes/ShapesMixin", "yes"});
        COMPONENTS.put("canary-voxel", new String[] {VOXEL_SHAPE, CANARY + "shapes/specialized_shapes/VoxelShapeMixin", "yes"});
        COMPONENTS.put("canary-merge", new String[] {SHAPES, CANARY + "shapes/shape_merging/ShapesMixin", "yes"});
        COMPONENTS.put("canary-cube", new String[] {CUBE_SHAPE, CANARY + "shapes/precompute_shape_arrays/CubeVoxelShapeMixin", "yes"});
        COMPONENTS.put("canary-range", new String[] {CUBE_RANGE, CANARY + "shapes/precompute_shape_arrays/CubePointRangeMixin", "yes"});
        COMPONENTS.put("canary-moving", new String[] {VOXEL_SHAPE, CANARY + "block/moving_block_shapes/VoxelShapeMixin", "yes"});
    }

    /** Named arms: every Canary shape mixin the harness applies, plus the VHA combinations. */
    static List<String> arm(String name) {
        return switch (name) {
            case "vanilla" -> List.of();
            case "vha" -> List.of("vha-box", "vha-join", "vha-merge", "vha-optimize");
            case "vha-box" -> List.of("vha-box");
            case "vha-join" -> List.of("vha-join", "vha-optimize");
            case "vha-merge" -> List.of("vha-merge");
            case "canary" -> List.of("canary-box", "canary-voxel", "canary-merge", "canary-cube", "canary-range", "canary-moving");
            case "canary-box" -> List.of("canary-box", "canary-voxel");
            case "canary-merge" -> List.of("canary-merge");
            // The fallback when the box hook does not run beside Canary: content matching of Canary's cuboids only.
            case "canary+join" -> List.of("canary-box", "canary-voxel", "canary-merge", "canary-cube", "canary-range",
                    "canary-moving", "vha-join", "vha-optimize", "vha-leaf");
            case "canary+vha-merge-first" -> List.of("vha-merge", "canary-box", "canary-voxel", "canary-merge",
                    "canary-cube", "canary-range", "canary-moving", "vha-join", "vha-optimize");
            // The shipped Canary layout: Canary's boxes wrapped by VHA's box sharing (applied after Canary's overwrite),
            // join and optimize caching, no merger; the content matching is present but stays idle once the box hook runs.
            case "canary+vha" -> List.of("canary-box", "canary-voxel", "canary-merge", "canary-cube", "canary-range",
                    "canary-moving", "vha-box", "vha-join", "vha-optimize", "vha-leaf");
            default -> throw new IllegalArgumentException("Unknown arm " + name);
        };
    }

    private final ClassLoader source;
    private final List<String> components;
    private final ZipFile canaryJar;
    private final Map<String, String> srgNames;

    WeavingLoader(ClassLoader source, List<String> components, Path canaryJar, Map<String, String> srgNames) {
        super(source);
        this.source = source;
        this.components = components;
        this.srgNames = srgNames;
        boolean needsCanary = components.stream().anyMatch(c -> c.startsWith("canary"));
        if (needsCanary && canaryJar == null) throw new IllegalArgumentException("A Canary arm needs the Canary jar");
        if (canaryJar != null) {
            try {
                String digest = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(canaryJar)));
                if (!CANARY_SHA256.equals(digest)) throw new IllegalArgumentException("Unsupported Canary jar fingerprint " + digest);
                this.canaryJar = new ZipFile(canaryJar.toFile());
            } catch (IOException | java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException(failure);
            }
        } else {
            this.canaryJar = null;
        }
    }

    /** Official names for SRG member names, from a TSRG file. */
    static Map<String, String> readSrgNames(Path mappings) throws IOException {
        Map<String, String> names = new HashMap<>();
        for (String line : Files.readAllLines(mappings)) {
            if (!line.startsWith("\t") || line.startsWith("\t\t")) continue;
            String[] words = line.trim().split("\\s+");
            if (words.length >= 2 && words[words.length - 1].matches("[mf]_\\d+_")) {
                names.put(words[words.length - 1], words[0]);
            }
        }
        if (!"joinUnoptimized".equals(names.get("m_83148_"))) throw new IllegalStateException("Unexpected SRG mapping contract");
        return names;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            boolean own = name.startsWith("com.abdelaziz.canary.") && canaryJar != null;
            for (String prefix : OWN) own |= name.startsWith(prefix);
            if (!own) return super.loadClass(name, resolve);
            Class<?> result = findLoadedClass(name);
            if (result == null) result = findClass(name);
            if (resolve) resolveClass(result);
            return result;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String internal = name.replace('.', '/');
        if (internal.equals("net/minecraftforge/fml/loading/FMLEnvironment")) {
            // Forge's real dist is a final field fixed by the launcher; a plain field lets a test choose the side.
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
            writer.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC, internal,
                    null, "java/lang/Object", null);
            writer.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "dist",
                    "Lnet/minecraftforge/api/distmarker/Dist;", null, null).visitEnd();
            writer.visitEnd();
            byte[] generated = writer.toByteArray();
            return defineClass(name, generated, 0, generated.length);
        }
        byte[] bytes = classBytes(internal);
        if (bytes == null) throw new ClassNotFoundException(name);
        if (internal.startsWith("com/abdelaziz/canary/")) {
            bytes = renamed(bytes);
        } else if (internal.equals(SHAPES) || internal.equals(VOXEL_SHAPE) || internal.equals(CUBE_SHAPE)
                || internal.equals(CUBE_RANGE)) {
            List<MiniWeaver.Mixin> mixins = new ArrayList<>();
            for (String component : components) {
                String[] spec = COMPONENTS.get(component);
                if (spec == null) throw new IllegalArgumentException("Unknown component " + component);
                if (spec[0].equals(internal)) mixins.add(mixin(spec[1], spec[2].equals("yes")));
            }
            if (!mixins.isEmpty()) bytes = MiniWeaver.weave(bytes, mixins, this::classBytes);
            if (canaryJar != null) bytes = publicize(bytes);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    private MiniWeaver.Mixin mixin(String internal, boolean canary) {
        byte[] bytes = classBytes(internal);
        if (bytes == null) throw new IllegalStateException("Missing mixin class " + internal);
        return canary
                ? new MiniWeaver.Mixin(internal, bytes, name -> srgNames.getOrDefault(name, name))
                : new MiniWeaver.Mixin(internal, bytes);
    }

    /**
     * Canary's access transformer (META-INF/accesstransformer.cfg) makes these shape members public, and
     * its shape classes call them from another package: VoxelShape.shape, getCoords and the constructor,
     * CubePointRange.&lt;init&gt;(I) and Shapes.findBits. Forge applies it in production; this does the same.
     */
    private static byte[] publicize(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int mask = ~(org.objectweb.asm.Opcodes.ACC_PRIVATE | org.objectweb.asm.Opcodes.ACC_PROTECTED);
        for (var field : node.fields) {
            if (node.name.equals(VOXEL_SHAPE) && field.name.equals("shape")) {
                field.access = (field.access & mask) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
            }
        }
        for (var method : node.methods) {
            boolean hit = node.name.equals(VOXEL_SHAPE) && (method.name.equals("getCoords")
                    || (method.name.equals("<init>") && method.desc.equals("(Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;)V")))
                    || node.name.equals(CUBE_RANGE) && method.name.equals("<init>") && method.desc.equals("(I)V")
                    || node.name.equals(SHAPES) && method.name.equals("findBits");
            if (hit) method.access = (method.access & mask) | org.objectweb.asm.Opcodes.ACC_PUBLIC;
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] renamed(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassRemapper(writer, new Remapper() {
            @Override public String mapMethodName(String owner, String name, String descriptor) { return srgNames.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return srgNames.getOrDefault(name, name); }
            @Override public String mapInvokeDynamicMethodName(String name, String descriptor) { return srgNames.getOrDefault(name, name); }
        }), 0);
        return writer.toByteArray();
    }

    private byte[] classBytes(String internalName) {
        if (internalName.startsWith("com/abdelaziz/canary/") && canaryJar != null) {
            ZipEntry entry = canaryJar.getEntry(internalName + ".class");
            if (entry == null) return null;
            try (InputStream stream = canaryJar.getInputStream(entry)) {
                return stream.readAllBytes();
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
        return MiniWeaver.read(source, internalName);
    }
}
