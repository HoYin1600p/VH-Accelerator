package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.CompatibilityApplicationObserver;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Offline stand-in for Sponge Mixin on Vault's original {@code CDF} class, for the lazy compact hooks.
 * It copies the compiled production mixin members into the target, rewrites the constructor call the way
 * an instance {@code @Redirect} does, adds the cancellable HEAD sequence the way {@code @Inject} does, and can
 * overwrite {@code compute}/{@code getProbability} with the real VHAPI jar's bodies the way
 * {@code @Overwrite} does. It is not a Sponge transformer: real weaving is left to a server run.
 */
final class VaultLootCdfLazyTestHost {
    static final String COMPUTE_ENTRIES = "test$computeEntries";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String CDF = VaultLootCdfLayouts.CDF;
    private static final String MAP = VaultLootCdfLayouts.MAP;
    private static final String LAZY = VaultLootCdfLayouts.LAZY_MIXIN.replace('.', '/');
    private static final String VHAPI = VaultLootCdfLayouts.VHAPI_MIXIN.replace('.', '/');

    private VaultLootCdfLazyTestHost() { }

    /** The transformed class: optional eager hook, optional VHAPI overwrite, optional lazy hooks. */
    static ClassNode build(byte[] original, boolean eager, Path vhapiJar, boolean lazy) throws Exception {
        ClassNode node = eager ? VaultLootCdfTestHost.adapt(original) : VaultLootCdfTestHost.read(original);
        if (vhapiJar != null) overwriteWithVhapi(node, vhapiJar);
        if (lazy) addLazyHooks(node);
        return node;
    }

    static void addLazyHooks(ClassNode target) throws Exception {
        ClassNode mixin = readResource(LAZY);
        for (FieldNode field : mixin.fields) {
            if (field.name.equals("vhaccelerator$resolved")) {
                target.fields.add(new FieldNode(field.access, field.name, field.desc, null, null));
            }
        }
        for (MethodNode method : mixin.methods) {
            if (method.name.startsWith("<") || (method.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            MethodNode copy = copyRemapped(method, LAZY, target.name);
            copy.visibleAnnotations = new ArrayList<>(List.of(merged(VaultLootCdfLayouts.LAZY_MIXIN)));
            target.methods.add(copy);
        }
        MethodNode constructor = method(target, "<init>");
        MethodInsnNode compute = null;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("compute") && call.owner.equals(CDF)) {
                assertNull(compute, "exactly one compute call in the constructor");
                compute = call;
            }
        }
        assertNotNull(compute);
        // Sponge's InvokeRedirector for an instance handler: save the receiver, push the mixin this, reload, invoke.
        MethodNode handler = target.methods.stream().filter(m -> m.name.contains("vhaccelerator$deferCompute"))
                .findFirst().orElseThrow();
        assertEquals(0, handler.access & Opcodes.ACC_STATIC, "a redirect inside an instance method needs an instance handler");
        int saved = constructor.maxLocals;
        constructor.maxLocals++;
        InsnList glue = new InsnList();
        glue.add(new VarInsnNode(Opcodes.ASTORE, saved));
        glue.add(new VarInsnNode(Opcodes.ALOAD, 0));
        glue.add(new VarInsnNode(Opcodes.ALOAD, saved));
        glue.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, handler.name, handler.desc, false));
        constructor.instructions.insertBefore(compute, glue);
        constructor.instructions.remove(compute);
        inject(target, "get", "([I)D", "vhaccelerator$getLazily", "[I", true);
        inject(target, "getMap", "()" + MAP, "vhaccelerator$getMapLazily", "", false);
    }

    private static void inject(ClassNode target, String name, String descriptor, String handlerBase,
            String arguments, boolean primitiveDouble) {
        MethodNode method = target.methods.stream().filter(candidate -> candidate.name.equals(name)
                && candidate.desc.equals(descriptor)).findFirst().orElseThrow();
        int local = method.maxLocals;
        InsnList head = new InsnList();
        head.add(new TypeInsnNode(Opcodes.NEW, CALLBACK));
        head.add(new InsnNode(Opcodes.DUP));
        head.add(new LdcInsnNode(name));
        head.add(new InsnNode(Opcodes.ICONST_1));
        head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CALLBACK, "<init>", "(Ljava/lang/String;Z)V", false));
        head.add(new VarInsnNode(Opcodes.ASTORE, local));
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (primitiveDouble) head.add(new VarInsnNode(Opcodes.ALOAD, 1));
        head.add(new VarInsnNode(Opcodes.ALOAD, local));
        head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, handlerName(target, handlerBase),
                "(" + arguments + "L" + CALLBACK + ";)V", false));
        head.add(new VarInsnNode(Opcodes.ALOAD, local));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "isCancelled", "()Z", false));
        LabelNode original = new LabelNode();
        head.add(new JumpInsnNode(Opcodes.IFEQ, original));
        head.add(new VarInsnNode(Opcodes.ALOAD, local));
        if (primitiveDouble) {
            head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "getReturnValueD", "()D", false));
            head.add(new InsnNode(Opcodes.DRETURN));
        } else {
            head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "getReturnValue", "()Ljava/lang/Object;", false));
            head.add(new TypeInsnNode(Opcodes.CHECKCAST, "it/unimi/dsi/fastutil/longs/Long2DoubleMap"));
            head.add(new InsnNode(Opcodes.ARETURN));
        }
        head.add(original);
        method.instructions.insert(head);
        method.maxLocals++;
    }

    private static String handlerName(ClassNode target, String base) {
        // Real Sponge decorates handler names; the production checks only require the original name inside.
        for (MethodNode method : target.methods) if (method.name.contains(base)) return method.name;
        throw new AssertionError("handler not copied: " + base);
    }

    static void overwriteWithVhapi(ClassNode target, Path jar) throws Exception {
        ClassNode mixin;
        try (ZipFile zip = new ZipFile(jar.toFile());
             var input = zip.getInputStream(zip.getEntry(VHAPI + ".class"))) {
            mixin = VaultLootCdfTestHost.read(input.readAllBytes());
        }
        target.methods.removeIf(method -> (method.name.equals("compute") && method.desc.equals("()" + MAP))
                || (method.name.equals("getProbability") && method.desc.equals("([I)D")));
        Map<String, String> renames = new java.util.HashMap<>();
        for (MethodNode method : mixin.methods) {
            if (method.name.startsWith("lambda$")) {
                // Mixin 0.8.5 names a merged synthetic lambda md<session>$<name>$<index>.
                renames.put(method.name, "md380e25$" + method.name + "$" + renames.size());
            }
        }
        for (MethodNode method : mixin.methods) {
            if (method.name.equals("<init>") || (method.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            MethodNode copy = copyRemapped(method, VHAPI, target.name, renames);
            if (method.name.equals("<clinit>")) {
                AbstractInsnNode last = copy.instructions.getLast();
                while (last.getOpcode() != Opcodes.RETURN) last = last.getPrevious();
                copy.instructions.remove(last);
                method(target, "<clinit>").instructions.insert(copy.instructions);
                continue;
            }
            copy.visibleAnnotations = new ArrayList<>(List.of(merged(VaultLootCdfLayouts.VHAPI_MIXIN)));
            target.methods.add(copy);
        }
        for (FieldNode field : mixin.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0) {
                target.fields.add(new FieldNode(field.access, field.name, field.desc, field.signature, null));
            }
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodNode copyRemapped(MethodNode source, String from, String to) {
        return copyRemapped(source, from, to, Map.of());
    }

    private static MethodNode copyRemapped(MethodNode source, String from, String to, Map<String, String> renames) {
        MethodNode copy = new MethodNode(source.access, renames.getOrDefault(source.name, source.name),
                source.desc.replace(from, to), null, null);
        source.accept(new MethodVisitor(Opcodes.ASM9, copy) {
            private String remap(String value) { return value == null ? null : value.replace(from, to); }
            private Handle remap(Handle value) {
                String name = value.getOwner().equals(from) ? renames.getOrDefault(value.getName(), value.getName())
                        : value.getName();
                return new Handle(value.getTag(), remap(value.getOwner()), name,
                        remap(value.getDesc()), value.isInterface());
            }
            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                super.visitFieldInsn(opcode, remap(owner), name, remap(descriptor));
            }
            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                super.visitMethodInsn(opcode, remap(owner), name, remap(descriptor), itf);
            }
            @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... args) {
                Object[] mapped = args.clone();
                for (int i = 0; i < mapped.length; i++) {
                    if (mapped[i] instanceof Handle handle) mapped[i] = remap(handle);
                    else if (mapped[i] instanceof Type type) mapped[i] = Type.getType(remap(type.getDescriptor()));
                }
                super.visitInvokeDynamicInsn(name, remap(descriptor), remap(bootstrap), mapped);
            }
        });
        copy.localVariables = null;
        copy.visibleAnnotations = null;
        copy.invisibleAnnotations = null;
        copy.visibleParameterAnnotations = null;
        copy.invisibleParameterAnnotations = null;
        return copy;
    }

    private static ClassNode readResource(String internalName) throws Exception {
        try (var input = VaultLootCdfLazyTestHost.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(input, internalName);
            return VaultLootCdfTestHost.read(input.readAllBytes());
        }
    }

    private static AnnotationNode merged(String owner) {
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", owner);
        return annotation;
    }

    /** Test-only counter of compute() entries, written before the class is defined; never in production code. */
    static byte[] executable(ClassNode proof) {
        ClassNode node = new ClassNode();
        proof.accept(node);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, COMPUTE_ENTRIES, "J", null, null));
        MethodNode compute = method(node, "compute");
        InsnList count = new InsnList();
        count.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, COMPUTE_ENTRIES, "J"));
        count.add(new InsnNode(Opcodes.LCONST_1));
        count.add(new InsnNode(Opcodes.LADD));
        count.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, COMPUTE_ENTRIES, "J"));
        compute.instructions.insert(count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String one, String two) { return "java/lang/Object"; }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    /** Publish the real activation record for the lazy group, after the production final verification. */
    static CompatMixinGroups publish(String version, Function<String, byte[]> bytes, ClassNode finalTarget,
            boolean includeEager, boolean vhapiLoaded, boolean physicalClient, boolean confirm) throws Exception {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = physicalClient;
        facts.modListKnown = true;
        facts.vaultHuntersLoaded = true;
        facts.vhapiLoaded = vhapiLoaded;
        facts.vaultCdfCompatible = ModPresence.vaultCdfCompatible(version, vhapiLoaded, bytes);
        facts.vaultCdfLazyCompatible = VaultLootCdfLayouts.supports(version, bytes);
        GateInputs inputs = (GateInputs) java.lang.reflect.Proxy.newProxyInstance(GateInputs.class.getClassLoader(),
                new Class<?>[]{GateInputs.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("CDF gate unexpectedly requested " + method.getName());
                });
        MixinGateRules rules = new MixinGateRules(facts.build(), inputs);
        MixinTargetPreflight.ClassLookup lookup = name -> {
            byte[] content = bytes.apply(name);
            if (content == null) {
                try (var input = VaultLootCdfLazyTestHost.class.getClassLoader().getResourceAsStream(name + ".class")) {
                    if (input == null) return null;
                    content = input.readAllBytes();
                }
            }
            return VaultLootCdfTestHost.read(content);
        };
        List<String> configured = includeEager
                ? List.of(VaultLootCdfLayouts.MIXIN, VaultLootCdfLayouts.LAZY_MIXIN)
                : List.of(VaultLootCdfLayouts.LAZY_MIXIN);
        CompatMixinGroups group = new CompatMixinGroups(configured, (target, mixin) -> rules.shouldApply(mixin), lookup);
        group.publishDiagnostics(Map.of("physicalSide", physicalClient ? "CLIENT" : "DEDICATED_SERVER", "debug", true));
        assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN));
        if (confirm) {
            for (String mixin : configured) {
                if (!rules.shouldApply(mixin)) continue;
                List<String> problems = new ArrayList<>(mixin.equals(VaultLootCdfLayouts.LAZY_MIXIN)
                        ? VaultLootCdfLayouts.lazyApplicationProblems(finalTarget)
                        : VaultLootCdfLayouts.applicationProblems(finalTarget, physicalClient));
                if (problems.isEmpty()) problems.addAll(CompatibilityApplicationObserver.missingCommonInjectors(
                        finalTarget, mixin, lookup.find(mixin.replace('.', '/'))));
                if (problems.isEmpty()) group.applied(mixin, CDF.replace('/', '.'));
                else group.applicationRejected(mixin, CDF.replace('/', '.'), problems);
            }
        }
        return group;
    }

    /** One transformed copy of Vault's CDF class in its own loader, driven by reflection. */
    static final class Arm extends ClassLoader implements AutoCloseable {
        private static final String GENERATOR = "iskallia.vault.core.world.loot.generator.TieredLootTableGenerator";
        final ClassNode transformed;
        private final ZipFile zip;
        private final byte[] cdfBytes;
        private final Constructor<?> keyConstructor;
        private final Constructor<?> cdfConstructor;
        private final Method get;
        private final Method getMap;
        private final Field mapField;
        private final Field resolvedField;
        private final Class<?> cdfClass;

        Arm(Path vaultJar, ClassNode transformed) throws Exception {
            super(VaultLootCdfLazyTestHost.class.getClassLoader());
            this.transformed = transformed;
            this.zip = new ZipFile(vaultJar.toFile());
            this.cdfBytes = executable(transformed);
            Class<?> keyType = loadClass(GENERATOR + "$CDFKey");
            keyConstructor = keyType.getConstructor(double[].class);
            cdfClass = loadClass(GENERATOR + "$CDF");
            cdfConstructor = cdfClass.getDeclaredConstructor(keyType);
            cdfConstructor.setAccessible(true);
            get = cdfClass.getMethod("get", int[].class);
            getMap = cdfClass.getMethod("getMap");
            mapField = cdfClass.getDeclaredField("map");
            mapField.setAccessible(true);
            Field resolved = null;
            try { resolved = cdfClass.getDeclaredField("vhaccelerator$resolved"); resolved.setAccessible(true); }
            catch (NoSuchFieldException absent) { /* arm without lazy hooks */ }
            resolvedField = resolved;
        }

        Object create(int samples, double[] weights) throws Throwable {
            double[] key = new double[weights.length + 1];
            key[0] = samples;
            System.arraycopy(weights, 0, key, 1, weights.length);
            try {
                return cdfConstructor.newInstance(keyConstructor.newInstance((Object) key));
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        double get(Object cdf, int[] frequencies) {
            try {
                return (Double) get.invoke(cdf, (Object) frequencies);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new AssertionError(failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        Long2DoubleMap getMap(Object cdf) {
            try {
                return (Long2DoubleMap) getMap.invoke(cdf);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        /** The final {@code map} field: null while a lazy distribution has not been built or was not deferred. */
        Object mapField(Object cdf) throws Exception { return mapField.get(cdf); }

        /** The lazy hook's published distribution; null before first use and on arms without lazy hooks. */
        Object resolved(Object cdf) throws Exception { return resolvedField == null ? null : resolvedField.get(cdf); }

        long computeEntries() throws Exception { return cdfClass.getField(COMPUTE_ENTRIES).getLong(null); }

        Class<?> type() { return cdfClass; }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            boolean side = name.equals("net.minecraftforge.fml.loading.FMLEnvironment");
            boolean policy = name.equals("dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfPolicy");
            if (!name.startsWith("iskallia.vault.") && !side && !policy) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        byte[] content;
                        if (side) content = VaultLootCdfTestHost.sideBinding();
                        else if (policy) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            assertNotNull(input);
                            content = input.readAllBytes();
                        } else {
                            var entry = zip.getEntry(name.replace('.', '/') + ".class");
                            if (entry == null) throw new ClassNotFoundException(name);
                            try (var input = zip.getInputStream(entry)) { content = input.readAllBytes(); }
                            if (name.equals(GENERATOR + "$CDF")) content = cdfBytes;
                        }
                        loaded = defineClass(name, content, 0, content.length);
                    } catch (java.io.IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        @Override public void close() throws Exception { zip.close(); }
    }

    /** Every composition of {@code total} into {@code parts} non-negative parts, in Vault's permute order. */
    static void forEachComposition(int total, int parts, Consumer<int[]> action) {
        permute(0, total, 0, new int[parts], action);
    }

    private static void permute(int sum, int total, int depth, int[] stack, Consumer<int[]> action) {
        if (depth == stack.length) {
            action.accept(stack);
            return;
        }
        if (depth == stack.length - 1) {
            stack[depth] = total - sum;
            permute(total, total, depth + 1, stack, action);
            return;
        }
        for (int i = 0; i <= total - sum; i++) {
            stack[depth] = i;
            permute(sum + i, total, depth + 1, stack, action);
        }
    }
}
