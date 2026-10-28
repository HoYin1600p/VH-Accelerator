package dev.hoyin1600p.vhaccelerator.mixinmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCompareMode;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import sun.misc.Unsafe;

class MixinTreeReleaserTest {
    private static final String PACKAGE = "org.spongepowered.asm.mixin.transformer.";

    // ---- release policy, with a fake Mixin ----

    private static final class FakeMixin {
        final List<String> targets;
        final boolean pseudo;
        ClassNode tree;

        FakeMixin(List<String> targets, boolean pseudo, ClassNode tree) {
            this.targets = targets;
            this.pseudo = pseudo;
            this.tree = tree;
        }
    }

    private static class FakeAccess implements MixinTreeAccess {
        final Object lock = new Object();
        final List<Object> mixins = new ArrayList<>();
        int mixinsCalls;

        @Override
        public Object lock() {
            return this.lock;
        }

        @Override
        public List<Object> mixins() {
            assertTrue(Thread.holdsLock(this.lock), "the mixin list must be read inside Mixin's lock");
            this.mixinsCalls++;
            return this.mixins;
        }

        @Override
        public List<String> targets(Object mixin) {
            assertTrue(Thread.holdsLock(this.lock), "trees must only be touched inside Mixin's lock");
            return ((FakeMixin) mixin).targets;
        }

        @Override
        public boolean pseudo(Object mixin) {
            return ((FakeMixin) mixin).pseudo;
        }

        @Override
        public ClassNode tree(Object mixin) {
            assertTrue(Thread.holdsLock(this.lock), "trees must only be touched inside Mixin's lock");
            return ((FakeMixin) mixin).tree;
        }

        @Override
        public void setTree(Object mixin, ClassNode tree) {
            assertTrue(Thread.holdsLock(this.lock), "trees must only be touched inside Mixin's lock");
            ((FakeMixin) mixin).tree = tree;
        }
    }

    private static ClassNode someTree() throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(ReleasedMixinTreeTest.classBytes(MixinTreeReleaserTest.class))
                .accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    @Test
    void releasesOnlyMixinsWhoseTargetsAreAllLoaded() throws Exception {
        FakeAccess access = new FakeAccess();
        FakeMixin loaded = new FakeMixin(List.of("a.Loaded", "a.AlsoLoaded"), false, someTree());
        FakeMixin partlyLoaded = new FakeMixin(List.of("a.Loaded", "a.Late"), false, someTree());
        FakeMixin unloaded = new FakeMixin(List.of("a.Late"), false, someTree());
        FakeMixin pseudo = new FakeMixin(List.of("a.Loaded"), true, someTree());
        FakeMixin untargeted = new FakeMixin(List.of(), false, someTree());
        FakeMixin noState = new FakeMixin(List.of("a.Loaded"), false, null);
        ClassNode partlyTree = partlyLoaded.tree;
        ClassNode unloadedTree = unloaded.tree;
        ClassNode pseudoTree = pseudo.tree;
        ClassNode untargetedTree = untargeted.tree;
        access.mixins.addAll(List.of(loaded, partlyLoaded, unloaded, pseudo, untargeted, noState));

        MixinTreeReleaser.Statistics statistics =
                MixinTreeReleaser.sweep(access, Set.of("a.Loaded", "a.AlsoLoaded")::contains);

        assertInstanceOf(ReleasedMixinTree.class, loaded.tree);
        assertSame(partlyTree, partlyLoaded.tree);
        assertSame(unloadedTree, unloaded.tree);
        assertSame(pseudoTree, pseudo.tree);
        assertSame(untargetedTree, untargeted.tree);
        assertNull(noState.tree);
        assertEquals(6, statistics.mixins());
        assertEquals(1, statistics.released());
        assertEquals(2, statistics.keptUnloadedTargets());
        assertEquals(2, statistics.keptPseudoOrUntargeted());
        assertEquals(1, statistics.keptNoState());
        assertEquals(0, statistics.failed());
    }

    @Test
    void aTargetThatLoadsLaterIsReleasedByTheNextSweepAndAReleasedMixinIsNotTouchedAgain() throws Exception {
        FakeAccess access = new FakeAccess();
        FakeMixin mixin = new FakeMixin(List.of("a.Late"), false, someTree());
        access.mixins.add(mixin);

        assertEquals(0, MixinTreeReleaser.sweep(access, name -> false).released());
        assertFalse(mixin.tree instanceof ReleasedMixinTree);
        assertEquals(1, MixinTreeReleaser.sweep(access, name -> true).released());
        ClassNode first = mixin.tree;
        MixinTreeReleaser.Statistics again = MixinTreeReleaser.sweep(access, name -> true);
        assertEquals(0, again.released());
        assertEquals(1, again.alreadyReleased());
        assertSame(first, mixin.tree);
    }

    @Test
    void aFailingMixinKeepsItsTreeAndTheSweepContinues() throws Exception {
        FakeAccess access = new FakeAccess() {
            @Override
            public List<String> targets(Object mixin) {
                if (((FakeMixin) mixin).targets.contains("a.Boom")) {
                    throw new IllegalStateException("boom");
                }
                return super.targets(mixin);
            }
        };
        FakeMixin broken = new FakeMixin(List.of("a.Boom"), false, someTree());
        FakeMixin fine = new FakeMixin(List.of("a.Fine"), false, someTree());
        ClassNode brokenTree = broken.tree;
        access.mixins.addAll(List.of(broken, fine));

        MixinTreeReleaser.Statistics statistics = MixinTreeReleaser.sweep(access, name -> true);

        assertSame(brokenTree, broken.tree);
        assertInstanceOf(ReleasedMixinTree.class, fine.tree);
        assertEquals(1, statistics.failed());
        assertEquals(1, statistics.released());
    }

    @Test
    void largeSweepsTakeMixinsLockInBatches() throws Exception {
        FakeAccess access = new FakeAccess();
        ClassNode tree = someTree();
        for (int index = 0; index < MixinTreeReleaser.BATCH * 3 + 5; index++) {
            access.mixins.add(new FakeMixin(List.of("a.T" + index), false, tree));
        }
        MixinTreeReleaser.Statistics statistics = MixinTreeReleaser.sweep(access, name -> true);
        assertEquals(MixinTreeReleaser.BATCH * 3 + 5, statistics.released());
    }

    // ---- guards ----

    @Test
    void openRefusesOtherMixinVersionsHotSwapAndMissingTransformer() {
        assertTrue(MixinTreeAccess.open("0.8.7", false, new Object()).failure().contains("0.8.7"));
        assertTrue(MixinTreeAccess.open("0.8.5", true, new Object()).failure().contains("hot-swap"));
        assertTrue(MixinTreeAccess.open("0.8.5", false, null).failure().contains("transformer"));
        // Not Mixin's transformer class: the layout check refuses it and nothing is touched.
        MixinTreeAccess.Result other = MixinTreeAccess.open("0.8.5", false, new Object());
        assertNull(other.access());
        assertTrue(other.failure().contains("layout"));
    }

    @Test
    void theFieldsTheReleaserReadsExistInTheLiveMixinClasses() throws Exception {
        ClassLoader loader = IMixinInfo.class.getClassLoader();
        assertField(loader, "MixinTransformer", "processor", PACKAGE + "MixinProcessor");
        assertField(loader, "MixinProcessor", "configs", "java.util.List");
        assertField(loader, "MixinConfig", "mixins", "java.util.List");
        assertField(loader, "MixinInfo", "state", PACKAGE + "MixinInfo$State");
        assertField(loader, "MixinInfo", "virtual", "boolean");
        assertField(loader, "MixinInfo$State", "classNode", "org.objectweb.asm.tree.ClassNode");
        assertTrue(IMixinInfo.class.isAssignableFrom(Class.forName(PACKAGE + "MixinInfo", false, loader)));
    }

    private static void assertField(ClassLoader loader, String owner, String name, String type) throws Exception {
        Field field = Class.forName(PACKAGE + owner, false, loader).getDeclaredField(name);
        assertEquals(type, field.getType().getName());
    }

    // ---- the real MixinInfo, without a Mixin environment ----

    @Test
    void releasesAndRebuildsTheTreeOfARealMixinInfo() throws Exception {
        Unsafe unsafe = unsafe();
        ClassLoader loader = IMixinInfo.class.getClassLoader();
        Class<?> infoClass = Class.forName(PACKAGE + "MixinInfo", true, loader);
        Class<?> stateClass = Class.forName(PACKAGE + "MixinInfo$State", true, loader);
        Object info = unsafe.allocateInstance(infoClass);
        Object state = unsafe.allocateInstance(stateClass);
        ClassNode original = someTree();
        set(stateClass, state, "classNode", original);
        set(stateClass, state, "this$0", info);
        set(infoClass, info, "state", state);
        set(infoClass, info, "targetClassNames", new ArrayList<>(List.of("a/Target", "b/Other")));
        set(infoClass, info, "virtual", false);

        IMixinInfo mixin = (IMixinInfo) info;
        String before = dump(mixin.getClassNode(0));
        MixinTreeAccess access = MixinTreeAccess.Reflective.forTest(new Object(), List.of(info));
        assertEquals(List.of("a.Target", "b.Other"), access.targets(info));
        assertFalse(access.pseudo(info));

        // One target not loaded: untouched.
        assertEquals(0, MixinTreeReleaser.sweep(access, name -> name.equals("a.Target")).released());
        assertSame(original, access.tree(info));

        // Both loaded: replaced, and Mixin's own copy call still returns the same tree.
        assertEquals(1, MixinTreeReleaser.sweep(access, name -> true).released());
        assertInstanceOf(ReleasedMixinTree.class, access.tree(info));
        assertEquals(before, dump(mixin.getClassNode(0)));
        assertEquals(before, dump(mixin.getClassNode(ClassReader.EXPAND_FRAMES)));
    }

    @Test
    void aPseudoRealMixinInfoIsKept() throws Exception {
        Unsafe unsafe = unsafe();
        ClassLoader loader = IMixinInfo.class.getClassLoader();
        Class<?> infoClass = Class.forName(PACKAGE + "MixinInfo", true, loader);
        Class<?> stateClass = Class.forName(PACKAGE + "MixinInfo$State", true, loader);
        Object info = unsafe.allocateInstance(infoClass);
        Object state = unsafe.allocateInstance(stateClass);
        ClassNode original = someTree();
        set(stateClass, state, "classNode", original);
        set(infoClass, info, "state", state);
        set(infoClass, info, "targetClassNames", new ArrayList<>(List.of("a/Target")));
        set(infoClass, info, "virtual", true);

        MixinTreeAccess access = MixinTreeAccess.Reflective.forTest(new Object(), List.of(info));
        assertEquals(0, MixinTreeReleaser.sweep(access, name -> true).released());
        assertSame(original, access.tree(info));
    }

    // ---- config defaults and fail-closed startup ----

    @Test
    void theOptionDefaultsOnButStaysInactiveBeforeConfigLoads() {
        ConfigSettingCatalog.Setting setting = ConfigSettingCatalog.all().stream()
                .filter(value -> value.id().equals("releaseMixinClassTrees"))
                .findFirst().orElseThrow();
        assertTrue(setting.restartRequired());
        assertTrue(ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS.contains("releaseMixinClassTrees"));
        net.minecraftforge.common.ForgeConfigSpec.ValueSpec spec =
                VHAcceleratorConfig.COMMON_SPEC.getSpec().get("optimizations.releaseMixinClassTrees");
        assertEquals(Boolean.TRUE, spec.getDefault());
        assertFalse(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
    }

    @Test
    void loadedDefaultsHonorTheOptionMasterAndCompareMode() throws Exception {
        var child = ForgeConfigSpec.class.getDeclaredField("childConfig");
        child.setAccessible(true);
        var previousConfig = (CommentedConfig)
                child.get(VHAcceleratorConfig.COMMON_SPEC);
        var compare = BootstrapCompareMode.class
                .getDeclaredField("launchValue");
        compare.setAccessible(true);
        Object previousCompare = compare.get(null);
        try {
            BootstrapCompareMode.set(false);
            VHAcceleratorConfig.COMMON_SPEC.setConfig(null);
            assertFalse(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
            var loaded = CommentedConfig.inMemory();
            VHAcceleratorConfig.COMMON_SPEC.correct(loaded);
            VHAcceleratorConfig.COMMON_SPEC.setConfig(loaded);
            assertTrue(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
            VHAcceleratorConfig.COMMON.releaseMixinClassTrees.set(false);
            assertFalse(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
            VHAcceleratorConfig.COMMON.releaseMixinClassTrees.set(true);
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(false);
            assertFalse(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
            VHAcceleratorConfig.COMMON.enableCommonOptimizations.set(true);
            assertTrue(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
            BootstrapCompareMode.set(true);
            assertFalse(VHAcceleratorConfig.releaseMixinClassTreesEnabled());
        } finally {
            VHAcceleratorConfig.COMMON_SPEC.setConfig(previousConfig);
            compare.set(null, previousCompare);
        }
    }

    @Test
    void beforeConfigLoadsTheHookDoesNotTouchMixinOrLog() {
        List<String> calls = new ArrayList<>();
        InvocationHandler recorder = (proxy, method, args) -> {
            calls.add(method.getName());
            return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
        };
        Logger logger = (Logger) Proxy.newProxyInstance(
                Logger.class.getClassLoader(), new Class<?>[] {Logger.class}, recorder);
        MixinTreeReleaseHook.serverStarted(logger);
        assertTrue(calls.isEmpty(), "logger was used: " + calls);
        assertNotNull(VHAccelerator.MOD_ID);
    }

    // ---- helpers ----

    private static String dump(ClassNode node) {
        StringWriter text = new StringWriter();
        node.accept(new TraceClassVisitor(new PrintWriter(text)));
        return text.toString();
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
