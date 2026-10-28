package dev.hoyin1600p.vhaccelerator.backport.fix;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import dev.hoyin1600p.vhaccelerator.tools.bgtemplates.RetainedGraphAgent;

/** Test-only scheduling hooks; original dependency bytes are read locally, never distributed. */
public final class EventBusWrapperCacheOracle implements AutoCloseable {
    static final String WRITER = "vha-cache-resize-writer";
    static volatile Pause pause;
    private final Instrumentation instrumentation;
    private final ClassFileTransformer transformer;

    static final class Pause {
        final CountDownLatch contains = new CountDownLatch(1);
        final CountDownLatch read = new CountDownLatch(1);
        volatile Thread reader;
    }

    public static void afterContains(boolean hit) {
        Pause active = pause;
        if (active == null || active.reader != Thread.currentThread() || !hit) return;
        active.contains.countDown();
        try {
            if (!active.read.await(5, TimeUnit.SECONDS)) throw new AssertionError("cache reader barrier timed out");
        } catch (InterruptedException failure) { throw new AssertionError(failure); }
    }

    EventBusWrapperCacheOracle() throws Exception {
        instrumentation = RetainedGraphAgent.instrumentation();
        if (!instrumentation.isRetransformClassesSupported()) throw new AssertionError("test agent cannot retransform");
        transformer = new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                              ProtectionDomain domain, byte[] original) {
                if (!"java/util/HashMap".equals(name)) return null;
                ClassNode node = new ClassNode();
                new ClassReader(original).accept(node, 0);
                int inserted = 0;
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("resize")) continue;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD
                                || !field.owner.equals("java/util/HashMap") || !field.name.equals("table")) continue;
                        // Pause the real writer immediately after new-table publication, before transfer.
                        // Uses boot classes only: no helper link, copied HashMap algorithm or app-class injection.
                        LabelNode loop = new LabelNode(), done = new LabelNode();
                        InsnList hook = new InsnList();
                        hook.add(loop);
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Thread","currentThread","()Ljava/lang/Thread;",false));
                        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/Thread","getName","()Ljava/lang/String;",false));
                        hook.add(new LdcInsnNode(WRITER));
                        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
                        hook.add(new JumpInsnNode(Opcodes.IFEQ,done));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/concurrent/locks/LockSupport","park","()V",false));
                        hook.add(new JumpInsnNode(Opcodes.GOTO,loop));
                        hook.add(done);
                        method.instructions.insert(instruction,hook);
                        inserted++;
                    }
                }
                if (inserted != 1) throw new AssertionError("JDK HashMap resize boundary changed");
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        };
        instrumentation.addTransformer(transformer,true);
        try { instrumentation.retransformClasses(HashMap.class); }
        catch (Throwable failure) { instrumentation.removeTransformer(transformer); throw failure; }
    }

    @Override public void close() throws Exception {
        instrumentation.removeTransformer(transformer);
        instrumentation.retransformClasses(HashMap.class);
        pause = null;
    }

    static Class<?> load(boolean readerBarrier) throws Exception {
        String owner = EventBusWrapperCacheFix.OWNER;
        java.nio.file.Path originalJar=java.nio.file.Path.of(System.getProperty("eventbus.wrapper.jar"));
        if (!"81f34dea30f014aa45ec172370ae712476a6aa7b887428d9a85f9e2bd8eda2f9".equals(
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(java.nio.file.Files.readAllBytes(originalJar))))) {
            throw new AssertionError("Oracle requires the pinned original EventBus5.0.3 jar");
        }
        ClassLoader loader = new ClassLoader(EventBusWrapperCacheOracle.class.getClassLoader()) {
            @Override public java.net.URL getResource(String name) {
                if(name.startsWith(owner.replace('.','/'))) {
                    try { return new java.net.URL("jar:"+originalJar.toUri().toURL()+"!/"+name); }
                    catch(java.net.MalformedURLException failure) { throw new AssertionError(failure); }
                }
                return super.getResource(name);
            }
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(owner) && !name.startsWith(owner + "$")) return super.loadClass(name,resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> found=findLoadedClass(name);
                    if (found == null) {
                        try (InputStream stream=getResourceAsStream(name.replace('.','/')+".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            byte[] bytes=stream.readAllBytes();
                            if (name.equals(owner)) {
                                byte[] nested;
                                try (InputStream inner=getResourceAsStream(name.replace('.','/')+"$ASMClassLoader.class")) {
                                    nested=inner.readAllBytes();
                                }
                                if (EventBusWrapperCacheFix.layoutProblem("5.0.3",bytes,nested) != null) {
                                    throw new AssertionError("Oracle requires original EventBus5.0.3 bytes");
                                }
                                if (readerBarrier) {
                                    ClassNode node=new ClassNode(); new ClassReader(bytes).accept(node,0);
                                    int hooks=0;
                                    for (MethodNode method:node.methods) {
                                        if (!method.name.equals("createWrapper")) continue;
                                        for (AbstractInsnNode instruction:method.instructions.toArray()) {
                                            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/HashMap")
                                                    && call.name.equals("containsKey")) {
                                                InsnList hook=new InsnList(); hook.add(new InsnNode(Opcodes.DUP));
                                                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,EventBusWrapperCacheOracle.class.getName().replace('.','/'),
                                                        "afterContains","(Z)V",false));
                                                method.instructions.insert(instruction,hook); hooks++;
                                            }
                                        }
                                    }
                                    if (hooks != 1) throw new AssertionError("Cache hit boundary changed");
                                    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); bytes=writer.toByteArray();
                                }
                            }
                            found=defineClass(name,bytes,0,bytes.length);
                        } catch (Exception failure) { throw new ClassNotFoundException(name,failure); }
                    }
                    if (resolve) resolveClass(found);
                    return found;
                }
            }
        };
        return Class.forName(owner,true,loader);
    }

    static Object construct(Class<?> type, Object target, Method method) throws Exception {
        try { return type.getConstructor(Object.class,Method.class,boolean.class).newInstance(target,method,false); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }
}
