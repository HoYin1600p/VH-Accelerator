package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;

class CompatibilityApplicationObserverTest {
    @Test
    void aMergedHandlerWithoutAnInjectionCallDoesNotConfirmACommonOptimization() {
        String mixin = "dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft.BBModelTextureDataMixin";
        ClassNode source = new ClassNode();
        MethodNode original = new MethodNode(Opcodes.ACC_PRIVATE, "observe", "()V", null, null);
        original.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(
                "Lorg/spongepowered/asm/mixin/injection/Inject;")));
        source.methods.add(original);
        ClassNode target = new ClassNode();
        target.name = "fixture/Target";
        MethodNode merged = new MethodNode(Opcodes.ACC_PRIVATE, "handler$000$observe", "()V", null, null);
        AnnotationNode origin = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        origin.values = new ArrayList<>(List.of("mixin", mixin));
        merged.visibleAnnotations = new ArrayList<>(List.of(origin));
        target.methods.add(merged);
        assertEquals(1, CompatibilityApplicationObserver.missingCommonInjectors(target, mixin, source).size());
        MethodNode caller = new MethodNode(Opcodes.ACC_PUBLIC, "load", "()V", null, null);
        caller.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, merged.name, merged.desc, false));
        target.methods.add(caller);
        assertEquals(List.of(), CompatibilityApplicationObserver.missingCommonInjectors(target, mixin, source));
    }
    @Test
    void observedMergedHandlerStillVerifiesAndReturnsItsOriginalResult() throws Exception {
        String mixin = "dev.hoyin1600p.vhaccelerator.mixin.compat.sample.ExampleMixin";
        ClassNode target = new ClassNode();
        target.version = Opcodes.V17;
        target.access = Opcodes.ACC_PUBLIC;
        target.name = "fixture/ObservedHandler";
        target.superName = "java/lang/Object";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "handler", "()I", null, null);
        AnnotationNode merged = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = new ArrayList<>(List.of("mixin", mixin));
        method.visibleAnnotations = new ArrayList<>(List.of(merged));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        target.methods.add(method);
        assertEquals(0, CompatibilityApplicationObserver.instrument(target, "unrelated.Mixin"));
        assertEquals(1, CompatibilityApplicationObserver.instrument(target, mixin));
        ClassWriter writer = new ClassWriter(0);
        target.accept(writer);
        byte[] bytecode = writer.toByteArray();
        Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass("fixture.ObservedHandler", bytecode, 0, bytecode.length); }
        }.define();
        assertEquals(7, loaded.getMethod("handler").invoke(null));
    }
}
