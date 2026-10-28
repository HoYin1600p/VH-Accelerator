package dev.hoyin1600p.vhaccelerator.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.jupiter.api.Test;

/** Reads the real Forge handler classes on the test classpath (instances made without running Forge). */
class ForgeHolderClassifierTest {
    private static final ClassLoader LOADER = ForgeHolderClassifierTest.class.getClassLoader();

    private static Unsafe unsafe() throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return new Unsafe(f.get(null));
    }

    private record Unsafe(Object instance) {
        Object allocate(Class<?> type) throws Exception {
            return instance.getClass().getMethod("allocateInstance", Class.class).invoke(instance, type);
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Set<String> actualHash(String className) throws Exception {
        return Set.of(ForgeHolderClassifier.hash(LOADER, Class.forName(className, false, LOADER)));
    }

    private static ForgeHolderClassifier trusting() throws Exception {
        return ForgeHolderClassifier.resolve(LOADER, actualHash(ForgeHolderClassifier.REF_CLASS),
                actualHash(ForgeHolderClassifier.KEYED_CLASS), actualHash(ForgeHolderClassifier.REGISTRY_CLASS));
    }

    @Test
    void anUnreviewedBuildIsNeverIndexed() {
        ForgeHolderClassifier none = ForgeHolderClassifier.resolve(LOADER, Set.of(), Set.of(), Set.of());
        assertFalse(none.registryClassReviewed());
        assertFalse(none.indexesObjectHolderRefs());
        assertFalse(none.indexesRegistryKeyHandlers());
        assertSame(HolderDispatchIndex.ALWAYS, none.classify(new Object()));
        assertSame(HolderDispatchIndex.ALWAYS, none.classify(null));
    }

    @Test
    void theReviewedLayoutsMatchTheForgeOnTheClasspathOrAreReportedByHash() throws Exception {
        ForgeHolderClassifier production = ForgeHolderClassifier.resolve(LOADER);
        // The development classpath may carry re-mapped bytes; the field layout must still be the reviewed one.
        ForgeHolderClassifier trusting = trusting();
        assertTrue(trusting.indexesObjectHolderRefs(), "ObjectHolderRef field layout");
        assertTrue(trusting.indexesRegistryKeyHandlers(), "RegistryObject$2 field layout");
        assertTrue(trusting.registryClassReviewed());
        assertTrue(production.registryClassReviewed(), "ObjectHolderRegistry hash on the classpath");
        System.out.println("production hash match: refs=" + production.indexesObjectHolderRefs()
                + " keyed=" + production.indexesRegistryKeyHandlers());
    }

    @Test
    void keyedHandlersAreClassifiedLikeTheirAcceptMethod() throws Exception {
        ForgeHolderClassifier classifier = trusting();
        Unsafe unsafe = unsafe();
        Class<?> keyedClass = Class.forName(ForgeHolderClassifier.KEYED_CLASS, false, LOADER);
        Class<?> objectClass = Class.forName(ForgeHolderClassifier.OBJECT_CLASS, false, LOADER);
        ResourceLocation key = new ResourceLocation("minecraft", "block");

        Object outer = unsafe.allocate(objectClass);
        Object handler = unsafe.allocate(keyedClass);
        set(handler, "val$registryName", key);
        set(handler, "this$0", outer);
        // Required registry not confirmed yet: accept() still has its one-time check to run.
        assertSame(HolderDispatchIndex.UNSETTLED, classifier.classify(handler));
        set(handler, "registryExists", true);
        assertEquals(key, classifier.classify(handler));
        set(handler, "registryExists", false);
        set(handler, "invalidRegistry", true);
        assertEquals(key, classifier.classify(handler));
        set(handler, "invalidRegistry", false);
        set(outer, "optionalRegistry", true);
        assertEquals(key, classifier.classify(handler));
        // A handler with no name or no owner is never trusted.
        set(handler, "val$registryName", null);
        assertSame(HolderDispatchIndex.ALWAYS, classifier.classify(handler));
        set(handler, "val$registryName", key);
        set(handler, "this$0", null);
        set(handler, "registryExists", false);
        assertSame(HolderDispatchIndex.ALWAYS, classifier.classify(handler));
    }

    @Test
    void objectHolderRefsAreClassifiedByTheirRegistryName() throws Exception {
        ForgeHolderClassifier classifier = trusting();
        Unsafe unsafe = unsafe();
        Class<?> refClass = Class.forName(ForgeHolderClassifier.REF_CLASS, false, LOADER);
        ResourceLocation key = new ResourceLocation("minecraft", "item");
        Object registry = unsafe.allocate(ForgeRegistry.class);
        Field name = ForgeRegistry.class.getDeclaredField("name");
        name.setAccessible(true);
        name.set(registry, key);
        Object ref = unsafe.allocate(refClass);
        assertSame(HolderDispatchIndex.ALWAYS, classifier.classify(ref)); // registry == null
        set(ref, "registry", registry);
        assertEquals(key, classifier.classify(ref));
    }

    @Test
    void otherHandlerShapesRunOnEveryEvent() throws Exception {
        ForgeHolderClassifier classifier = trusting();
        java.util.function.Consumer<java.util.function.Predicate<ResourceLocation>> lambda = pred -> { };
        assertSame(HolderDispatchIndex.ALWAYS, classifier.classify(lambda));
        Class<?> oldShape = Class.forName("net.minecraftforge.registries.RegistryObject$1", false, LOADER);
        assertSame(HolderDispatchIndex.ALWAYS, classifier.classify(unsafe().allocate(oldShape)));
    }
}
