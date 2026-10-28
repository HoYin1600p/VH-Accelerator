package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/**
 * Relocates the running JDK's actual IdentityHashMap bytecode as test data and pauses its key store.
 * No JDK source or class bytes are distributed. Only relocation, private view-cache fields (normally
 * inherited package-private from AbstractMap), and one scheduling hook differ from the original map.
 */
public final class IdentityMapRaceOracle {
    private static final String ORIGINAL = "java/util/IdentityHashMap";
    private static final String RELOCATED = "dev/hoyin1600p/vhaccelerator/backport/fix/oracle/IdentityMap";
    private static final String HOOK = IdentityMapRaceOracle.class.getName().replace('.', '/');
    static volatile Scenario scenario;

    private IdentityMapRaceOracle() { }

    static final class Scenario {
        final Map<Object, Object> map;
        final Object key = new Object();
        final Object registry = new Object();
        final CountDownLatch keyStored = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean heldRegistryMonitor;

        Scenario(Map<Object, Object> map) { this.map = map; }
    }

    /** Called only by the isolated test class, after key publication and before value publication. */
    public static void afterKeyStore(Object map, Object key) {
        Scenario active = scenario;
        if (active == null || active.map != map || active.key != key) return;
        active.heldRegistryMonitor = Thread.holdsLock(active.registry);
        active.keyStored.countDown();
        try {
            if (!active.release.await(5, TimeUnit.SECONDS)) throw new AssertionError("oracle release timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<Object, Object> newMap() throws Exception {
        ClassLoader loader = new ClassLoader(IdentityMapRaceOracle.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                String internal = name.replace('.', '/');
                if (!internal.equals(RELOCATED) && !internal.startsWith(RELOCATED + "$")) {
                    throw new ClassNotFoundException(name);
                }
                String original = ORIGINAL + internal.substring(RELOCATED.length());
                try (InputStream input = ClassLoader.getSystemResourceAsStream(original + ".class")) {
                    if (input == null) throw new ClassNotFoundException(original);
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(new ClassRemapper(node, new Remapper() {
                        @Override public String map(String type) {
                            return type.equals(ORIGINAL) || type.startsWith(ORIGINAL + "$")
                                    ? RELOCATED + type.substring(ORIGINAL.length()) : type;
                        }
                    }), 0);
                    if (internal.equals(RELOCATED)) prepareRoot(node);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    byte[] bytes = writer.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (Exception | LinkageError failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        };
        return (Map<Object, Object>) loader.loadClass(RELOCATED.replace('/', '.')).getConstructor(int.class)
                .newInstance(1);
    }

    private static void prepareRoot(ClassNode node) {
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT,
                "keySet", "Ljava/util/Set;", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT,
                "values", "Ljava/util/Collection;", null, null));
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals("java/util/AbstractMap")
                    && (field.name.equals("keySet") || field.name.equals("values"))) field.owner = RELOCATED;
        }
        MethodNode put = node.methods.stream().filter(m -> m.name.equals("put")
                && m.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")).findFirst().orElseThrow();
        int hooks = 0;
        for (AbstractInsnNode instruction : put.instructions.toArray()) {
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            // JDK 17: local 3 holds the masked key, while local 2 holds the value.
            if (instruction.getOpcode() == Opcodes.AASTORE && previous instanceof VarInsnNode key
                    && key.getOpcode() == Opcodes.ALOAD && key.var == 3) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "afterKeyStore",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
                put.instructions.insert(instruction, hook);
                hooks++;
            }
        }
        if (hooks != 1) throw new AssertionError("JDK IdentityHashMap key-store boundary changed: " + hooks);
    }

    static int capacity(Map<?, ?> map) throws Exception {
        var table = map.getClass().getDeclaredField("table");
        table.setAccessible(true);
        return ((Object[]) table.get(map)).length;
    }
}
