package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class MixinTargetPreflightTest {
    private static final String TARGET =
            "dev/hoyin1600p/vhaccelerator/compat/PreflightTarget";

    static ClassNode read(String internalName) throws IOException {
        try (InputStream stream = MixinTargetPreflightTest.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static List<String> problems(Class<?> mixin) throws IOException {
        ClassNode node = read(mixin.getName().replace('.', '/'));
        List<String> problems = new ArrayList<>();
        for (String target : MixinTargetPreflight.targets(node)) {
            problems.addAll(MixinTargetPreflight.problems(
                    node,
                    target,
                    MixinTargetPreflightTest::read
            ));
        }
        return problems;
    }

    @Test
    void readsMixinTargets() throws IOException {
        assertEquals(
                List.of(TARGET),
                MixinTargetPreflight.targets(read(
                        MatchingMixin.class.getName().replace('.', '/')
                ))
        );
        assertEquals(
                List.of("com/example/Absent"),
                MixinTargetPreflight.targets(read(
                        MissingClassMixin.class.getName().replace('.', '/')
                ))
        );
    }

    @Test
    void acceptsMixinWhoseMembersStillExist() throws IOException {
        assertEquals(List.of(), problems(MatchingMixin.class));
    }

    @Test
    void acceptsShadowAccessorAndInvokerResolvedThroughSuperclass() throws IOException {
        assertEquals(List.of(), problems(InheritedMembersMixin.class));
    }

    @Test
    void reportsRemovedInjectionMethod() throws IOException {
        List<String> problems = problems(RemovedMethodMixin.class);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("removedMethod"));
    }

    @Test
    void reportsChangedInjectionPoint() throws IOException {
        List<String> problems = problems(ChangedPointMixin.class);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("renamedHelper"));
    }

    @Test
    void reportsMissingShadowAccessorAndInvoker() throws IOException {
        List<String> problems = problems(MissingMembersMixin.class);
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void reportsMissingTargetClass() throws IOException {
        List<String> problems = problems(MissingClassMixin.class);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("com/example/Absent"));
    }

    @Test
    void ignoresRemappedReferences() throws IOException {
        assertEquals(List.of(), problems(RemappedMixin.class));
    }

    @Test
    void ignoresPointsThatOptBackIntoRemappingOrTargetMinecraft() throws IOException {
        // Target Dummy shape: remap = false mixin on a mod class whose point
        // targets a Minecraft method through remap = true.
        assertEquals(List.of(), problems(ExplicitlyRemappedPointMixin.class));
        assertEquals(List.of(), problems(MinecraftOwnerPointMixin.class));
    }
}

class PreflightBase {
    protected int inherited;

    protected void inheritedHelper() {
    }
}

class PreflightTarget extends PreflightBase {
    private int count;

    void present() {
        helper();
        count++;
    }

    void helper() {
    }

    int redirectable() {
        return count;
    }
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class MatchingMixin {
    @Shadow
    private int count;

    @Inject(
            method = "present",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/hoyin1600p/vhaccelerator/compat/PreflightTarget;helper()V"
            )
    )
    private void beforeHelper(CallbackInfo callback) {
    }

    @Redirect(
            method = "redirectable()I",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/hoyin1600p/vhaccelerator/compat/PreflightTarget;count:I"
            )
    )
    private int readCount(PreflightTarget target) {
        return 0;
    }

    @Inject(method = "present", at = @At("HEAD"))
    private void atHead(CallbackInfo callback) {
    }
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class InheritedMembersMixin {
    @Shadow
    protected int inherited;

    @Shadow
    protected abstract void inheritedHelper();

    @Accessor("inherited")
    abstract int readInherited();

    @Invoker
    abstract void callInheritedHelper();
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class RemovedMethodMixin {
    @Inject(method = "removedMethod", at = @At("HEAD"))
    private void onRemoved(CallbackInfo callback) {
    }
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class ChangedPointMixin {
    @Inject(
            method = "present",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/hoyin1600p/vhaccelerator/compat/PreflightTarget;renamedHelper()V"
            )
    )
    private void beforeRenamed(CallbackInfo callback) {
    }
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class MissingMembersMixin {
    @Shadow
    private long missingField;

    @Accessor("missingAccessorField")
    abstract int readMissing();

    @Invoker("missingInvoked")
    abstract void callMissing();
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class ExplicitlyRemappedPointMixin {
    @Inject(
            method = "present",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/hoyin1600p/vhaccelerator/compat/PreflightTarget;devName()V",
                    remap = true
            )
    )
    private void beforeRemapped(CallbackInfo callback) {
    }
}

@Mixin(value = PreflightTarget.class, remap = false)
abstract class MinecraftOwnerPointMixin {
    @Inject(
            method = "present",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/DispenserBlock;registerBehavior(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/core/dispenser/DispenseItemBehavior;)V"
            )
    )
    private void beforeMinecraftCall(CallbackInfo callback) {
    }
}

@Mixin(targets = "com.example.Absent", remap = false)
abstract class MissingClassMixin {
}

@Mixin(PreflightTarget.class)
abstract class RemappedMixin {
    @Inject(method = "obfuscatedName", at = @At("HEAD"))
    private void remapped(CallbackInfo callback) {
    }
}
