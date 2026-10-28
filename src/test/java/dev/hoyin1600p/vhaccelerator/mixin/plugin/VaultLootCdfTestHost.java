package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.CompatibilityApplicationObserver;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * A small offline adapter, not a Sponge transformer. Copies the compiled production handler,
 * remaps its shadow references, and adds the cancellable HEAD call declared by its annotation.
 * Original constructor/final publication and original compute fallback are left intact.
 */
final class VaultLootCdfTestHost implements AutoCloseable {
    static final String BODY_ENTRIES = "test$originalComputeEntries";
    private static final String HANDLER = "vhaccelerator$computeEagerly";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String MIXIN = VaultLootCdfLayouts.MIXIN.replace('.', '/');
    private final Map<Field, Object> saved = new java.util.LinkedHashMap<>();
    private final List<Field> sideFields = new ArrayList<>();
    private final List<Method> clientRegistrations = new ArrayList<>();
    private Dist side;
    private boolean clientPermission;

    VaultLootCdfTestHost() throws Exception {
        save(ForgeConfigSpec.class, "childConfig", VHAcceleratorConfig.COMMON_SPEC);
        save(BootstrapCompareMode.class, "launchValue", null);
        save(CompatMixinGroups.class, "published", null);
        save(CompatMixinGroups.class, "usageDiagnostics", null);
        CommentedConfig config = CommentedConfig.inMemory();
        VHAcceleratorConfig.COMMON_SPEC.correct(config);
        VHAcceleratorConfig.COMMON_SPEC.setConfig(config);
        runtime(true, true, false, Dist.DEDICATED_SERVER, true);
    }

    void runtime(boolean master, boolean setting, boolean compare, Dist side, boolean clientPermission)
            throws Exception {
        VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(master);
        VHAcceleratorConfig.COMMON.optimizeVaultLootCdf.set(setting);
        BootstrapCompareMode.set(compare);
        this.side = side;
        this.clientPermission = clientPermission;
        for (Field field : sideFields) field.set(null, side);
        for (Method method : clientRegistrations) method.invoke(null, (BooleanSupplier) () -> clientPermission);
    }

    void attach(ClassLoader loader) throws Exception {
        Field field = loader.loadClass("net.minecraftforge.fml.loading.FMLEnvironment").getField("dist");
        field.set(null, side);
        sideFields.add(field);
        Method method = loader.loadClass("dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfPolicy")
                .getMethod("registerClientPermission", BooleanSupplier.class);
        method.invoke(null, (BooleanSupplier) () -> clientPermission);
        clientRegistrations.add(method);
    }

    /** Forge's real final dist singleton cannot switch sides inside one offline JVM. */
    static byte[] sideBinding() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "net/minecraftforge/fml/loading/FMLEnvironment",
                null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dist",
                "Lnet/minecraftforge/api/distmarker/Dist;", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    CompatMixinGroups publish(String version, Function<String, byte[]> bytes, ClassNode finalTarget,
            boolean confirm, boolean foreign) throws Exception {
        return publish(version, bytes, finalTarget, confirm, foreign, false, null);
    }

    CompatMixinGroups publish(String version, Function<String, byte[]> bytes, ClassNode finalTarget,
            boolean confirm, boolean foreign, boolean vhapi, String overwrittenMethod) throws Exception {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = side == Dist.CLIENT;
        facts.modListKnown = true;
        facts.vaultHuntersLoaded = true;
        facts.vhapiLoaded = vhapi;
        // Use the same version + exact archive check as production discovery.
        facts.vaultCdfCompatible = ModPresence.vaultCdfCompatible(version, vhapi, bytes);
        GateInputs inputs = (GateInputs) Proxy.newProxyInstance(GateInputs.class.getClassLoader(),
                new Class<?>[]{GateInputs.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("CDF gate unexpectedly requested " + method.getName());
                });
        MixinGateRules rules = new MixinGateRules(facts.build(), inputs);
        MixinTargetPreflight.ClassLookup lookup = name -> {
            byte[] content = bytes.apply(name);
            if (content == null) {
                try (var input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
                    if (input == null) return null;
                    content = input.readAllBytes();
                }
            }
            return read(content);
        };
        CompatMixinGroups group = new CompatMixinGroups(List.of(VaultLootCdfLayouts.MIXIN),
                (target, mixin) -> rules.shouldApply(mixin), lookup);
        group.publishDiagnostics(Map.of("physicalSide", side.name(), "debug", true));
        assertEquals(facts.vaultCdfCompatible, rules.shouldApply(VaultLootCdfLayouts.MIXIN));
        assertFalse(CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN));
        if (confirm && facts.vaultCdfCompatible) {
            if (foreign) {
                MethodNode extra = new MethodNode(Opcodes.ACC_PRIVATE, "test$foreignHandler", "()V", null, null);
                extra.instructions.add(new InsnNode(Opcodes.RETURN));
                extra.visibleAnnotations = new ArrayList<>(List.of(merged("example.ForeignCdfMixin")));
                finalTarget.methods.add(extra);
            }
            if (overwrittenMethod != null) {
                MethodNode replaced = finalTarget.methods.stream()
                        .filter(method -> method.name.equals(overwrittenMethod)).findFirst().orElseThrow();
                replaced.visibleAnnotations = new ArrayList<>(List.of(merged("example.ForeignKernelMixin")));
            }
            // The same production checks and confirmation as postApply, without MixinService.
            List<String> problems = new ArrayList<>(VaultLootCdfLayouts.applicationProblems(
                    finalTarget, side == Dist.CLIENT));
            if (problems.isEmpty()) problems.addAll(CompatibilityApplicationObserver.missingCommonInjectors(
                    finalTarget, VaultLootCdfLayouts.MIXIN, lookup.find(MIXIN)));
            if (problems.isEmpty()) group.applied(VaultLootCdfLayouts.MIXIN, VaultLootCdfLayouts.CDF.replace('/', '.'));
            else group.applicationRejected(VaultLootCdfLayouts.MIXIN, VaultLootCdfLayouts.CDF.replace('/', '.'), problems);
            assertEquals(!foreign && overwrittenMethod == null, CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.MIXIN),
                    () -> group.snapshot().toString());
        }
        return group;
    }

    static long uses(CompatMixinGroups group) {
        return ((Number) group.snapshot().get(0).get("optimizationUseCount")).longValue();
    }

    static ClassNode adapt(byte[] cdfBytes) throws Exception {
        ClassNode target = read(cdfBytes);
        ClassNode mixin;
        try (var input = VaultLootCdfTestHost.class.getClassLoader().getResourceAsStream(MIXIN + ".class")) {
            assertNotNull(input);
            mixin = read(input.readAllBytes());
        }
        MethodNode source = mixin.methods.stream().filter(method -> method.name.equals(HANDLER))
                .findFirst().orElseThrow();
        AnnotationNode injection = source.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElseThrow();
        assertTrue(injection.values.toString().contains("compute()" + VaultLootCdfLayouts.MAP));
        @SuppressWarnings("unchecked")
        List<AnnotationNode> locations = (List<AnnotationNode>) value(injection, "at");
        assertEquals("HEAD", value(locations.get(0), "value"));
        assertEquals(Boolean.TRUE, value(injection, "cancellable"));
        MethodNode handler = new MethodNode(source.access, source.name, source.desc, source.signature, null);
        source.accept(new MethodVisitor(Opcodes.ASM9, handler) {
            private String remap(String value) { return value.replace(MIXIN, VaultLootCdfLayouts.CDF); }
            private Handle remap(Handle value) {
                return new Handle(value.getTag(), remap(value.getOwner()), value.getName(),
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
        handler.visibleAnnotations = new ArrayList<>(List.of(merged(VaultLootCdfLayouts.MIXIN)));
        handler.invisibleAnnotations = null;
        target.methods.add(handler);
        MethodNode compute = target.methods.stream().filter(method -> method.name.equals("compute"))
                .findFirst().orElseThrow();
        int callbackLocal = compute.maxLocals;
        InsnList head = new InsnList();
        head.add(new TypeInsnNode(Opcodes.NEW, CALLBACK));
        head.add(new InsnNode(Opcodes.DUP));
        head.add(new LdcInsnNode("compute"));
        head.add(new InsnNode(Opcodes.ICONST_1));
        head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CALLBACK, "<init>", "(Ljava/lang/String;Z)V", false));
        head.add(new VarInsnNode(Opcodes.ASTORE, callbackLocal));
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
        head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, handler.name, handler.desc, false));
        head.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "isCancelled", "()Z", false));
        LabelNode fallback = new LabelNode();
        head.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        head.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
        head.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "getReturnValue", "()Ljava/lang/Object;", false));
        head.add(new TypeInsnNode(Opcodes.CHECKCAST, "it/unimi/dsi/fastutil/longs/Long2DoubleMap"));
        head.add(new InsnNode(Opcodes.ARETURN));
        head.add(fallback);
        compute.instructions.insert(head);
        compute.maxLocals++;
        return target;
    }

    /** Add a test-only counter AFTER the cancellation branch, never to the production helper. */
    static byte[] executable(ClassNode proof, boolean adapted) {
        ClassNode node = new ClassNode();
        proof.accept(node);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, BODY_ENTRIES, "J", null, null));
        MethodNode compute = node.methods.stream().filter(method -> method.name.equals("compute")).findFirst().orElseThrow();
        InsnList count = new InsnList();
        count.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, BODY_ENTRIES, "J"));
        count.add(new InsnNode(Opcodes.LCONST_1));
        count.add(new InsnNode(Opcodes.LADD));
        count.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, BODY_ENTRIES, "J"));
        if (adapted) {
            AbstractInsnNode fallback = java.util.Arrays.stream(compute.instructions.toArray())
                    .filter(instruction -> instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ)
                    .map(instruction -> ((JumpInsnNode) instruction).label).findFirst().orElseThrow();
            compute.instructions.insert(fallback, count);
        } else compute.instructions.insert(count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static AnnotationNode merged(String owner) {
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = List.of("mixin", owner);
        return annotation;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation property " + name);
    }

    private void save(Class<?> owner, String name, Object receiver) throws Exception {
        Field field = field(owner, name);
        saved.put(field, field.get(receiver));
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Override public void close() throws Exception {
        for (var entry : saved.entrySet()) {
            entry.getKey().set(entry.getKey().getDeclaringClass() == ForgeConfigSpec.class
                    ? VHAcceleratorConfig.COMMON_SPEC : null, entry.getValue());
        }
        VHAcceleratorConfig.COMMON_SPEC.afterReload();
    }
}
